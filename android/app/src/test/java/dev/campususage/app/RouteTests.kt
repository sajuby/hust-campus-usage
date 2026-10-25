package dev.campususage.app

import dev.campususage.app.feature.routes.data.*
import dev.campususage.app.feature.routes.domain.*
import dev.campususage.app.feature.routes.model.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RouteTests {
    private fun fixture()=JSONObject(javaClass.classLoader!!.getResource("citytong-route-response.json")!!.readText())
    @Test fun syntheticResponsePreservesStopsUnitsAndLineReferenceTimes() {
        val routes=CityTongClient.parseRoutes(fixture().getJSONObject("result"))
        assertEquals(2,routes.size)
        val route=routes.first();assertEquals(2520L,route.durationSeconds);assertEquals(100,route.walkMeters);assertEquals("4",route.fare)
        assertEquals(5,route.stations);assertEquals(1,route.transfers)
        assertEquals("示例换乘站",route.rides.first().destination.name);assertEquals("示例终点站",route.rides.last().direction)
        assertEquals("06:00",route.rides.last().first);assertEquals("22:30",route.rides.last().last)
        assertEquals(1800L,route.rides.first().durationSeconds)
    }
    @Test fun etaUsesProviderDurationOnceAndDoesNotInventWaitingTime() {
        val route=CityTongClient.parseRoutes(fixture().getJSONObject("result")).first()
        val departure=ZonedDateTime.parse("2026-10-24T20:16:00+08:00")
        assertEquals("20:58",TransitTimeEvaluator.estimatedArrival(route,departure)!!.toLocalTime().toString())
        assertNull(TransitTimeEvaluator.estimatedArrival(route.copy(durationSeconds=null),departure))
    }
    @Test fun noTimetableMeansUnknownEvenWhenLineHasFirstAndLast() {
        val route=CityTongClient.parseRoutes(fixture().getJSONObject("result")).first()
        val assessment=TransitTimeEvaluator.lastConnection(route,RouteDemo.time,RouteDemo.time.toInstant(),emptyList())
        assertEquals(TransferRisk.UNKNOWN,assessment.risk);assertNull(assessment.latestDeparture)
    }
    @Test fun missingDurationAndFareStayUnknownInsteadOfZero() {
        val json=fixture().getJSONObject("result");val cost=json.getJSONArray("transits").getJSONObject(0).getJSONObject("cost")
        cost.remove("duration");cost.remove("transitFee")
        val route=CityTongClient.parseRoutes(json).first();assertNull(route.durationSeconds);assertNull(route.fare)
    }
    @Test fun busAlternativesAreNotAdditionalRideSegments() {
        val json=fixture().getJSONObject("result");val lines=json.getJSONArray("transits").getJSONObject(0).getJSONArray("segments").getJSONObject(0).getJSONObject("bus").getJSONArray("buslines")
        lines.put(lines.getJSONObject(0))
        assertEquals(2,CityTongClient.parseRoutes(json).first().rides.size)
    }
    @Test fun unsupportedTransportIsNotSilentlyRemovedFromFullItinerary() {
        val json=fixture().getJSONObject("result")
        json.getJSONArray("transits").getJSONObject(0).getJSONArray("segments").getJSONObject(0).put("railway",JSONObject().put("name","示例铁路"))
        assertEquals(1,CityTongClient.parseRoutes(json).size)
    }
    @Test fun liveRequestContractSendsNoCredentialsAndPreservesExplicitCity() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(fixture().toString()))
            val request=RouteRequest(RouteCity.wuhan,RouteDemo.origin,RouteDemo.destination,RouteDemo.time,RouteStrategy.SUBWAY)
            val result=CityTongClient(baseUrl=server.url("/").toString()).routes(request)
            val wire=server.takeRequest();val body=JSONObject(wire.body.readUtf8())
            assertEquals("/routePlan",wire.path);assertEquals("420100",body.getString("cityCode"));assertEquals(7,body.getInt("strategy"))
            assertEquals(RouteDemo.origin.point.wire(),body.getString("origin"));assertFalse(body.has("openId"));assertFalse(body.has("sign"));assertFalse(body.has("key"))
            assertNull(wire.getHeader("Authorization"));assertNull(wire.getHeader("Cookie"));assertFalse(result.demonstration)
        }
    }
    @Test fun providerDenialDoesNotExposeRawTextOrBecomeSchoolLoginError() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"401","message":"sensitive_internal_value"}"""))
            try { CityTongClient(baseUrl=server.url("/").toString()).search(RouteCity.wuhan,"江汉路");fail() }
            catch(e:RouteFailure){assertFalse(e.explanation.contains("sensitive"));assertFalse(e.explanation.contains("学校"))}
        }
    }
    @Test fun invalidCoordinatesAreNotSelectableAndValidDuplicatesAreCollapsed() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"1000","result":[{"name":"错误","location":"NaN,30","adcode":"420103"},{"name":"江汉路","location":"114.29,30.58","adcode":"420103","cityname":"武汉市"},{"name":"江汉路","location":"114.29,30.58","adcode":"420103","cityname":"武汉市"}]}"""))
            assertEquals(1,CityTongClient(baseUrl=server.url("/").toString()).search(RouteCity.wuhan,"江汉路").size)
        }
    }
    @Test fun previousTrainIsChosenWhenLastFeederCannotMakeTransfer() {
        val a=RouteDemo.lastAssessment()
        assertEquals(TransferRisk.TIGHT,a.risk);assertEquals("previous",a.runs.first().id)
        assertEquals("22:11",a.latestDeparture!!.toLocalTime().toString());assertEquals("23:19",a.arrival!!.toLocalTime().toString())
    }
    private val route=RouteDemo.lateRoute
    private fun at(value:String)=ZonedDateTime.parse("2026-10-24T$value:00+08:00")
    private fun table(leg:Int,runs:List<ScheduledRun>)=route.rides[leg].let {
        LegTimetable(it.lineId,it.origin.id,it.destination.id,RouteDemo.time.toLocalDate(),at("23:59").toInstant(),"测试时刻表",runs)
    }
    private fun tables()=listOf(table(0,listOf(ScheduledRun("a",at("22:23"),at("22:51")))),table(1,listOf(ScheduledRun("b",at("23:00"),at("23:16")))))
    private fun evaluate(departure:ZonedDateTime,ts:List<LegTimetable> = tables())=TransitTimeEvaluator.lastConnection(route,departure,RouteDemo.time.toInstant(),ts,entrySeconds=0)
    @Test fun exactDeadlineIsAllowedButOneSecondAfterIsMissed() {
        assertEquals(TransferRisk.TIGHT,evaluate(at("22:11")).risk)
        assertEquals(TransferRisk.MISSED,evaluate(at("22:11").plusSeconds(1)).risk)
        assertEquals(TransferRisk.TIGHT,evaluate(at("22:11").minusSeconds(1)).risk)
    }
    @Test fun incompleteExpiredWrongDirectionOrDemoTablesCannotValidateLiveJourney() {
        val ts=tables()
        for(replacement in listOf(ts[0].copy(complete=false),ts[0].copy(validUntil=RouteDemo.time.toInstant()),ts[0].copy(originId="other-platform"),ts[0].copy(demonstration=true))) {
            assertEquals(TransferRisk.UNKNOWN,evaluate(RouteDemo.time,listOf(replacement,ts[1])).risk)
        }
    }
    @Test fun shortTurnAndInsufficientConnectionCannotProduceSafeResult() {
        val ts=tables();val first=ts[0].copy(runs=listOf(ScheduledRun("late",at("22:31"),at("22:59"))))
        assertEquals(TransferRisk.MISSED,evaluate(RouteDemo.time,listOf(first,ts[1])).risk)
        val short=ts[1].copy(runs=ts[1].runs.map { it.copy(reachesDestination=false) })
        assertEquals(TransferRisk.MISSED,evaluate(RouteDemo.time,listOf(ts[0],short)).risk)
    }
    @Test fun postMidnightArrivalRetainsNextDateAndServiceDay() {
        val ts=tables();val next=at("23:00").plusHours(2)
        val late=ts[1].copy(runs=listOf(ScheduledRun("overnight",next,next.plusMinutes(16))))
        val a=evaluate(RouteDemo.time,listOf(ts[0],late))
        assertEquals(java.time.LocalDate.parse("2026-10-25"),a.arrival!!.toLocalDate())
    }
    @Test fun cachedResultExpiresAndCannotBeFreshBeforeItWasFetched() {
        val request=RouteRequest(RouteCity.wuhan,RouteDemo.origin,RouteDemo.destination,RouteDemo.time,RouteStrategy.RECOMMENDED)
        val now=RouteDemo.time.toInstant();val result=RouteResult(request,listOf(route),now)
        assertTrue(result.freshAt(now.plusSeconds(120)));assertFalse(result.freshAt(now.plusSeconds(121)));assertFalse(result.freshAt(now.minusSeconds(1)))
    }
    @Test fun coordinateConversionIsExplicitStableAndNotAppliedTwice() {
        val value=Coordinates.gcj(GeoPoint(116.397128,39.916527,CoordinateSystem.WGS84))
        assertEquals(116.403372494, value.longitude, .0000001);assertEquals(39.917930749,value.latitude,.0000001)
        assertEquals(value,Coordinates.gcj(value))
        assertEquals(GeoPoint(-.12,51.5),Coordinates.gcj(GeoPoint(-.12,51.5,CoordinateSystem.WGS84)))
    }
    @Test fun oldGenerationCannotOverwriteNewInputs() {
        val generation=RequestGeneration();val first=generation.next();val second=generation.next()
        assertFalse(generation.current(first));assertTrue(generation.current(second))
    }
    @Test fun reverseLookupIncludesRequiredCityWithoutWeChatIdentity() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"code":"1000","result":{"formattedAddress":"武汉站","addressComponent":{"city":["武汉市"],"adcode":"420111"}}}"""))
            val place=CityTongClient(baseUrl=server.url("/").toString()).reverse(RouteDemo.origin.point,RouteCity.wuhan)
            val body=JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("420100",body.getString("cityCode"));assertFalse(body.has("openId"));assertEquals("武汉市",place.cityName)
        }
    }
}
