package dev.campususage.app

import dev.campususage.app.feature.routes.data.*
import dev.campususage.app.feature.routes.model.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.*

class OfficialMetroTests {
    private fun seed()=JSONObject(javaClass.classLoader!!.getResource("wuhan-reference.json")!!.readText())
    private fun route()=RouteDemo.result(RouteRequest(RouteCity.wuhan,RouteDemo.origin,RouteDemo.destination,RouteDemo.time,RouteStrategy.SUBWAY)).routes.first()
    private val clock=Clock.fixed(Instant.parse("2026-10-25T05:00:00Z"),ZoneOffset.UTC)
    private fun <T> directory(block:(File)->T):T { val file=Files.createTempDirectory("campus-official-metro-").toFile();try{return block(file)}finally{file.deleteRecursively()} }
    private fun times(code:String)=seed().getJSONObject("stations").getJSONObject(code).getJSONArray("data")
    private fun response(data:JSONArray)=MockResponse().setBody(JSONObject().put("rtCode","0000").put("success",true).put("rtData",data).toString())

    @Test fun bundledOfficialTimesMatchActualLinePlatformDirectionAndSunday()=directory { dir ->
        val report=OfficialMetroRepository(seed(),dir,clock=clock).view(route(),RouteCity.wuhan)
        assertEquals(2,report.legs.size)
        assertEquals("0458",report.legs[0].code);assertEquals("0244",report.legs[1].code)
        val sunday=LocalDate.parse("2026-10-25")
        assertEquals("06:30",report.legs[0].regularDay(sunday)!!.first.display())
        assertEquals("23:43",report.legs[1].regularDay(sunday)!!.last.display())
        assertEquals(MetroOrigin.BUNDLED,report.legs[1].origin)
        assertEquals(listOf("周一至周四","周五","周六","周日"),report.legs[1].days.map{it.label})
    }
    @Test fun oppositeDirectionAndWrongStationDoNotBorrowAnotherPlatformsTimes() {
        val lines=OfficialMetroParser.lines(seed().getJSONObject("index").getJSONArray("data"));val ride=route().rides.last()
        assertNull(OfficialMetroParser.match(lines,ride.copy(direction="佛祖岭")))
        assertNull(OfficialMetroParser.match(lines,ride.copy(origin=RouteStop("x","不存在的站"))))
        assertNull(OfficialMetroParser.match(lines,ride.copy(name="轨道交通9号线")))
    }
    @Test fun terminalArrivalNeverBecomesBoardingDeparture() {
        val lines=OfficialMetroParser.lines(seed().getJSONObject("index").getJSONArray("data"));val line=lines.single{it.code=="04"};val site=line.sites.single{it.code=="0458"}
        assertTrue(OfficialMetroParser.days(times("0458"),site,line,"武汉火车站").isEmpty())
        val arrival=OfficialMetroParser.clock("00:26（到达）")
        assertTrue(arrival.arrivalOnly);assertTrue(arrival.display().contains("到达"));assertNull(OfficialMetroParser.clock("——").time)
    }
    @Test fun postMidnightAndInvalidClocksKeepTheirActualMeaning() {
        assertEquals("次日 00:01",OfficialMetroParser.clock("00:01").display(OfficialMetroParser.clock("6:00")))
        assertNull(OfficialMetroParser.clock("25:99").time)
        assertEquals("—",OfficialMetroParser.clock("暂未公布").display())
        assertEquals("次日 00:12",OfficialMetroParser.clock("次日00:12").display())
    }
    @Test fun weekendClassesRemainDistinctAndGroupedResponseWorksForOtherLine()=directory { dir ->
        val ride=route().rides.last().copy(origin=RouteStop("a","江汉路"),destination=RouteStop("b","汉口火车站"))
        val leg=OfficialMetroRepository(seed(),dir,clock=clock).view(route().copy(segments=listOf(ride)),RouteCity.wuhan).legs.single()
        assertEquals("0240",leg.code)
        assertEquals("06:30",leg.regularDay(LocalDate.parse("2026-10-24"))!!.first.display())
        assertEquals("06:39",leg.regularDay(LocalDate.parse("2026-10-25"))!!.first.display())
    }
    @Test fun anonymousPublicContractUsesEmptyFormPostAndStringStationCode() {
        MockWebServer().use { server ->
            server.enqueue(response(seed().getJSONObject("index").getJSONArray("data")));server.enqueue(response(times("0447")))
            val client=OfficialMetroClient(server.url("/").toString());client.index();client.times("0244")
            val index=server.takeRequest();assertEquals("/siteInfo/getLineAndStation",index.path);assertEquals(0,index.bodySize.toInt())
            assertTrue(index.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
            val station=server.takeRequest();assertEquals("0244",JSONObject(station.body.readUtf8()).getString("siteCode"))
            for(request in listOf(index,station)){assertNull(request.getHeader("Cookie"));assertNull(request.getHeader("Authorization"));assertNull(request.getHeader("X-Token"))}
        }
    }
    @Test fun successCodeWithInvalidPayloadIsRejectedWithoutExposingProviderText() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"rtCode":"0000","success":true,"rtData":"JSON 解析失败 secret_marker"}"""))
            try{OfficialMetroClient(server.url("/").toString()).index();fail()}catch(e:RouteFailure){assertFalse(e.explanation.contains("secret_marker"))}
        }
    }
    @Test fun networkCacheSurvivesReopeningAndFailedRefreshDoesNotReplaceIt()=directory { dir ->
        MockWebServer().use { server ->
            server.enqueue(response(times("0458")));server.enqueue(response(times("0447")))
            val client=OfficialMetroClient(server.url("/").toString());val repo=OfficialMetroRepository(seed(),dir,client,clock)
            assertTrue(repo.refresh(route(),RouteCity.wuhan).legs.all{it.origin==MetroOrigin.NETWORK})
            val reopened=OfficialMetroRepository(seed(),dir,client,clock)
            assertTrue(reopened.view(route(),RouteCity.wuhan).legs.all{it.origin==MetroOrigin.CACHED})
            server.enqueue(MockResponse().setResponseCode(503));server.enqueue(MockResponse().setResponseCode(503))
            val failed=reopened.refresh(route(),RouteCity.wuhan)
            assertTrue(failed.message!!.contains("保留"));assertEquals("23:43",failed.legs[1].days.first().last.display())
            assertTrue(failed.legs.all{it.origin==MetroOrigin.CACHED})
        }
    }
    @Test fun corruptedCacheFallsBackToLabelledBundledData()=directory { dir ->
        File(dir,"0244.json").writeText("""{"fetchedAt":"2026-10-25T04:00:00Z","data":"bad"}""")
        val leg=OfficialMetroRepository(seed(),dir,clock=clock).view(route(),RouteCity.wuhan).legs[1]
        assertEquals(MetroOrigin.BUNDLED,leg.origin);assertEquals("23:43",leg.days.first().last.display())
    }
    @Test fun anotherCityCannotUseWuhanDataOrRequestWuhanEndpoint()=directory { dir ->
        MockWebServer().use { server ->
            val repo=OfficialMetroRepository(seed(),dir,OfficialMetroClient(server.url("/").toString()),clock)
            val report=repo.refresh(route(),RouteCity("北京市","110100"))
            assertFalse(report.supported);assertTrue(report.legs.isEmpty());assertEquals(0,server.requestCount)
        }
    }
    @Test fun duplicateDateClassesCannotOverwriteAValidCache() {
        val data=times("0447");data.put(data.getJSONObject(0))
        try{OfficialMetroParser.validateTimes(data);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun collectionTimestampSupportsOffsetMicrosecondsAndUtcCacheFormat() {
        assertEquals(Instant.parse("2026-10-25T04:05:55.115864Z"),OfficialMetroParser.collectedAt("2026-10-25T12:05:55.115864+08:00"))
        assertEquals(Instant.parse("2026-10-25T04:05:55Z"),OfficialMetroParser.collectedAt("2026-10-25T04:05:55Z"))
    }
}
