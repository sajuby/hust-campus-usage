package dev.campususage.app

import dev.campususage.app.core.model.Category
import dev.campususage.app.data.auth.*
import dev.campususage.app.data.remote.*
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

private class MemorySessions : SessionStorage {
    private val values = mutableMapOf<String, String>()
    override fun read(system: String) = values[system]?.let(::JSONObject)
    override fun write(system: String, value: JSONObject) { values[system] = value.toString() }
    override fun clear(system: String) { values.remove(system) }
}

class SchoolClientTest {
    private val date = LocalDate.parse("2026-10-21")
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun server(block: (MockWebServer, WaxyClient, SessionStorage) -> Unit) {
        MockWebServer().use { server ->
            server.start(); val sessions = MemorySessions()
            val http = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
            block(server, WaxyClient(sessions, server.url("/prod-api").toString().trimEnd('/'), http), sessions)
        }
    }
    private fun loginResponses(server: MockWebServer, token: String = "test-token-one", room: String = "room-a") {
        server.enqueue(json("""{"code":200,"captchaEnabled":false}"""))
        server.enqueue(json("""{"code":200,"token":"$token"}"""))
        server.enqueue(json("""{"code":200,"user":{"userId":7,"password":"test-server-hash-must-not-persist"}}"""))
        server.enqueue(json("""{"code":200,"data":{"roomId":"$room"}}"""))
    }
    @Test fun validZeroIsDifferentFromMissingAndPartialIsNotInvented() {
        assertNull(WaxyParser.daily(JSONArray(), date, Category.ENERGY))
        val zero = WaxyParser.daily(JSONArray("""[{"common":false,"usage":0},{"common":true,"usage":"0.0000"}]"""), date, Category.ENERGY)!!
        assertEquals(0, zero.total.signum()); assertNotNull(zero.room); assertNotNull(zero.publicArea)
        val partial = WaxyParser.daily(JSONArray("""[{"common":false,"usage":"1.2345"}]"""), date, Category.ELECTRICITY)!!
        assertNull(partial.publicArea); assertEquals("1.2345", partial.total.toPlainString())
    }
    @Test fun monthDoesNotFillMissingDaysWithZero() {
        val rows = JSONArray("""[{"month":10,"day":20,"common":false,"usage":1.5},{"month":10,"day":20,"common":true,"usage":0.25},{"month":10,"day":21,"common":false,"usage":0}]""")
        val values = WaxyParser.month(rows, YearMonth.of(2026, 10), Category.ELECTRICITY)
        assertEquals(listOf(20, 21), values.map { it.date.dayOfMonth }); assertEquals("1.75", values[0].total.toPlainString())
        assertEquals(0, values[1].total.signum())
    }
    @Test fun malformedUsageDoesNotBecomeZero() {
        assertThrows(SchoolFailure::class.java) { WaxyParser.daily(JSONArray("""[{"common":false,"usage":null}]"""), date, Category.ELECTRICITY) }
        assertThrows(SchoolFailure::class.java) { WaxyParser.daily(JSONArray("""[{"common":"unknown","usage":2}]"""), date, Category.ELECTRICITY) }
    }
    @Test fun expiredTokenRecoversOnceAndPersistsNewSession() = server { server, client, sessions ->
        loginResponses(server); client.login("test-user", "test-password", true)
        repeat(4) { server.takeRequest() }
        assertFalse(sessions.read("waxy").toString().contains("test-server-hash-must-not-persist"))
        server.enqueue(json("""{"code":401}""")); loginResponses(server, "test-token-two")
        server.enqueue(json("""{"code":200,"data":[{"common":false,"usage":"1.1250"},{"common":true,"usage":"0.3750"}]}"""))
        val value = client.daily(Category.ELECTRICITY, date).second!!
        assertEquals("1.5000", value.total.toPlainString()); assertEquals("test-token-two", sessions.read("waxy")!!.getString("token"))
        val requests = (0 until 6).map { server.takeRequest() }
        assertEquals(1, requests.count { it.path == "/prod-api/login" })
        assertEquals("Bearer test-token-two", requests.last().getHeader("Authorization"))
        assertEquals(10, server.requestCount)
    }
    @Test fun second401StopsInsteadOfLooping() = server { server, client, _ ->
        loginResponses(server); client.login("test-user", "test-password", true)
        server.enqueue(json("""{"code":401}""")); loginResponses(server, "test-token-two"); server.enqueue(json("""{"code":401}"""))
        assertThrows(AuthenticationRequired::class.java) { client.daily(Category.ELECTRICITY, date) }
        assertEquals(10, server.requestCount)
    }
    @Test fun captchaDuringRecoveryRequiresTheUser() = server { server, client, _ ->
        loginResponses(server); client.login("test-user", "test-password", true)
        server.enqueue(json("""{"code":401}""")); server.enqueue(json("""{"code":200,"captchaEnabled":true,"uuid":"test-challenge"}"""))
        assertThrows(CaptchaRequired::class.java) { client.daily(Category.ELECTRICITY, date) }
        assertEquals(6, server.requestCount)
    }
    @Test fun noRememberedPasswordDoesNotAttemptAutomaticLogin() = server { server, client, _ ->
        loginResponses(server); client.login("test-user", "test-password", false)
        server.enqueue(json("""{"code":401}"""))
        assertThrows(AuthenticationRequired::class.java) { client.daily(Category.ELECTRICITY, date) }
        assertEquals(5, server.requestCount)
    }
    @Test fun bindingChangeChangesCacheScope() = server { server, client, sessions ->
        loginResponses(server); val previous = client.login("test-user", "test-password", true)
        server.enqueue(json("""{"code":200,"data":{"roomId":"room-b"}}""")); client.refreshBinding()
        assertNotEquals(previous, sessions.read("waxy")!!.getString("scope"))
    }
    @Test fun campusCookieJarRestoresBothSystemsWithoutCrossOriginLeak() {
        val sessions = MemorySessions(); val portal = "https://hkwxy.hust.edu.cn/tp_up/".toHttpUrl(); val cas = "https://pass.hust.edu.cn/cas/".toHttpUrl()
        val cookies = JSONArray().put(JSONObject().put("origin", portal.host).put("raw", "JSESSIONID=test-old; Path=/tp_up; Secure; HttpOnly"))
            .put(JSONObject().put("origin", cas.host).put("raw", "CASTGC=test-cas; Path=/cas; Secure; HttpOnly"))
        sessions.write("network", JSONObject().put("scope", "test-scope").put("cookies", cookies))
        val jar = SchoolCookieJar(sessions)
        jar.saveFromResponse(portal, listOf(Cookie.parse(portal, "JSESSIONID=test-new; Path=/tp_up; Secure; HttpOnly")!!))
        val restored = SchoolCookieJar(sessions)
        assertEquals("test-new", restored.loadForRequest(portal).single().value)
        assertEquals("test-cas", restored.loadForRequest(cas).single().value)
        assertTrue(restored.loadForRequest("https://other.hust.edu.cn/".toHttpUrl()).isEmpty())
        assertTrue(restored.loadForRequest("http://hkwxy.hust.edu.cn/tp_up/".toHttpUrl()).isEmpty())
    }
    @Test fun officialHtmlPlanParsingRetainsUnlimitedSemantics() {
        val html = """<div id="servicePlanApp"><div class="panel-card-header"><p>套餐</p><p>测试套餐</p></div><div class="panel-card-header"><p>余额</p><p>123.45</p></div><div class="panel-card-header"><p>到期时间</p><p>2026-10-31</p></div><div class="panel-card-header"><p>免费流量</p></div><div class="panel-card-header"><p>已用：0.00G</p><p>剩余20.00G / 共20.00G</p></div><div class="panel-card-header"><p>计费流量</p></div><div class="panel-card-header"><p>已用：1.50G</p><p>剩余38.50G / 共40.00G</p></div><div class="panel-card-header"><p>不限使用流量</p></div><div class="panel-card-header"><p>已用：8.25G</p></div></div>"""
        val value = NetworkParser.parse(html)
        assertEquals("123.45", value.balance.toPlainString()); assertEquals("0.00", value.traffic[0].used.toPlainString())
        assertNull(value.traffic[2].remaining); assertNull(value.traffic[2].total)
        assertThrows(SchoolFailure::class.java) { NetworkParser.parse(html.replace("计费流量", "不认识的新分类")) }
        assertThrows(SchoolFailure::class.java) { NetworkParser.parse("<html>统一身份认证</html>") }
    }
}
