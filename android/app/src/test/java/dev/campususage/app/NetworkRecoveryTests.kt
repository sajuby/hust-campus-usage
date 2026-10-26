package dev.campususage.app

import dev.campususage.app.data.auth.*
import dev.campususage.app.data.remote.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class NetworkRecoveryTests {
    private class Memory : SessionStorage {
        private var value: String? = null
        @Synchronized override fun read(system: String) = value?.let(::JSONObject)
        @Synchronized override fun write(system: String, value: JSONObject) { this.value = value.toString() }
        @Synchronized override fun clear(system: String) { value = null }
    }
    private val portal = "https://hkwxy.hust.edu.cn/tp_up/apps/campusNetwork/servicePlan".toHttpUrl()
    private val cas = "https://pass.hust.edu.cn/cas/login".toHttpUrl()
    private val scope = accountHash("network|test-user")
    private fun entry(host: String, raw: String) = JSONObject().put("origin", host).put("raw", raw)
    private fun seed(memory: Memory, schema: Int = 1, cookies: JSONArray = JSONArray()
        .put(entry(portal.host, "JSESSIONID=test-old; Path=/tp_up/; Secure; HttpOnly"))
        .put(entry(cas.host, "CASTGC=test-cas; Path=/cas/; Secure; HttpOnly"))) {
        memory.write("network", JSONObject().put("scope", scope).put("cookies", cookies).put("cookieSchemaVersion", schema)
            .put("userAgent", "CampusUsageTest/1.0"))
    }
    private fun plan() = """<div id="servicePlanApp">
        <div class="panel-card-header"><p>套餐</p><p>虚构套餐</p></div>
        <div class="panel-card-header"><p>余额</p><p>123.45</p></div>
        <div class="panel-card-header"><p>到期时间</p><p>2027-01-31</p></div>
        <div class="panel-card-header"><p>免费流量</p></div>
        <div class="panel-card-header"><p>已用：1.00G</p><p>剩余9.00G / 共10.00G</p></div>
        <div class="panel-card-header"><p>计费流量</p></div>
        <div class="panel-card-header"><p>已用：2.00G</p><p>剩余18.00G / 共20.00G</p></div>
        <div class="panel-card-header"><p>不限使用流量</p></div>
        <div class="panel-card-header"><p>已用：3.00G</p></div></div>"""
    private fun authenticated(principal: String = "test-user") = MockResponse().setBody("<script>var id_number = '$principal';</script><main>校园门户</main>")
    private fun login() = MockResponse().setBody("<form id='fm1'><input type='password'></form>")
    private fun redirect(url: String) = MockResponse().setResponseCode(302).setHeader("Location", url)
    private fun <T> server(block: (MockWebServer, NetworkClient, Memory) -> T): T {
        val certificate = HeldCertificate.Builder().commonName("test-school-server")
            .addSubjectAlternativeName(portal.host).addSubjectAlternativeName(cas.host).build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false); server.start()
            val memory = Memory(); seed(memory)
            val transport = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                .dns(object : Dns { override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1")) }).build()
            val client = NetworkClient(memory, transport, portal.newBuilder().port(server.port).build(),
                "https://${portal.host}:${server.port}/tp_up/v2".toHttpUrl())
            return block(server, client, memory)
        }
    }
    private fun request(server: MockWebServer) = server.takeRequest(2, TimeUnit.SECONDS)!!

    @Test fun validSessionUsesOneRequestAndPreservesLoginUserAgent() = server { server, client, memory ->
        server.enqueue(MockResponse().setBody(plan()))
        val result = client.fetch()
        assertEquals(scope, result.first); assertEquals("123.45", result.second.balance.toPlainString())
        val request = request(server)
        assertEquals("/tp_up/apps/campusNetwork/servicePlan", request.path)
        assertEquals("CampusUsageTest/1.0", request.getHeader("User-Agent"))
        assertFalse(request.getHeader("Cookie")!!.contains("CASTGC"))
        assertEquals(1, server.requestCount)
        assertFalse(memory.read("network")!!.optBoolean("reauthRequired"))
        assertTrue(memory.read("network")!!.getLong("lastVerifiedAt") > 0)
    }
    @Test fun business500RecoversThroughCasAndRetriesExactlyOnce() = server { server, client, memory ->
        server.enqueue(MockResponse().setResponseCode(500).setHeader("Set-Cookie", "JSESSIONID=test-empty; Path=/tp_up; Secure; HttpOnly"))
        server.enqueue(redirect("https://${cas.host}:${server.port}/cas/login?service=test-service"))
        server.enqueue(redirect("https://${portal.host}:${server.port}/tp_up/v2?ticket=test-ticket"))
        server.enqueue(authenticated().setHeader("Set-Cookie", "JSESSIONID=test-renewed; Path=/tp_up; Secure; HttpOnly"))
        server.enqueue(MockResponse().setBody(plan()))
        assertEquals("虚构套餐", client.fetch().second.plan)
        val requests = (1..5).map { request(server) }
        assertEquals("/tp_up/v2", requests[1].path)
        assertTrue(requests[2].getHeader("Cookie")!!.contains("CASTGC=test-cas"))
        assertFalse(requests[2].getHeader("Cookie")!!.contains("test-empty"))
        assertEquals("JSESSIONID=test-renewed", requests.last().getHeader("Cookie"))
        assertTrue(requests.all { it.getHeader("User-Agent") == "CampusUsageTest/1.0" })
        assertEquals(5, server.requestCount)
        assertEquals(2, memory.read("network")!!.getInt("cookieSchemaVersion"))
    }
    @Test fun expiredCasRequestsUserAuthenticationWithoutRemovingCachedScope() = server { server, client, memory ->
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(redirect("https://${cas.host}:${server.port}/cas/login"))
        server.enqueue(login())
        assertThrows(AuthenticationRequired::class.java) { client.fetch() }
        assertEquals(3, server.requestCount)
        assertEquals(scope, memory.read("network")!!.getString("scope"))
        assertTrue(memory.read("network")!!.getBoolean("reauthRequired"))
        assertTrue(memory.read("network")!!.getJSONArray("cookies").length() > 0)
    }
    @Test fun genuineBusiness500IsNotLabelledAsAuthenticationFailure() = server { server, client, memory ->
        server.enqueue(MockResponse().setResponseCode(500)); server.enqueue(authenticated()); server.enqueue(MockResponse().setResponseCode(500))
        val error = assertThrows(SchoolFailure::class.java) { client.fetch() }
        assertFalse(error is AuthenticationRequired); assertTrue(error.explanation.contains("HTTP 500"))
        assertEquals(3, server.requestCount); assertFalse(memory.read("network")!!.optBoolean("reauthRequired"))
    }
    @Test fun failedAuthenticationServiceDoesNotClearSavedIdentity() = server { server, client, memory ->
        server.enqueue(MockResponse().setResponseCode(500)); server.enqueue(MockResponse().setResponseCode(503))
        val error = assertThrows(SchoolFailure::class.java) { client.fetch() }
        assertFalse(error is AuthenticationRequired); assertEquals(scope, memory.read("network")!!.getString("scope"))
        assertEquals(2, server.requestCount)
    }
    @Test fun changedAccountDuringRecoveryCannotBeStoredUnderOldScope() = server { server, client, memory ->
        server.enqueue(MockResponse().setResponseCode(500)); server.enqueue(authenticated("test-other-user"))
        assertThrows(AuthenticationRequired::class.java) { client.fetch() }
        assertEquals(2, server.requestCount); assertEquals(scope, memory.read("network")!!.getString("scope"))
    }
    @Test fun secondAuthenticationFailureDoesNotLoop() = server { server, client, _ ->
        server.enqueue(MockResponse().setResponseCode(401)); server.enqueue(authenticated()); server.enqueue(login())
        assertThrows(AuthenticationRequired::class.java) { client.fetch() }
        assertEquals(3, server.requestCount)
    }
    @Test fun unavailableServiceDoesNotTriggerAnUnnecessaryLogin() = server { server, client, _ ->
        server.enqueue(MockResponse().setResponseCode(503))
        val error = assertThrows(SchoolFailure::class.java) { client.fetch() }
        assertFalse(error is AuthenticationRequired); assertEquals(1, server.requestCount)
    }
    @Test fun untrustedRedirectIsRejectedBeforeSendingCookies() = server { server, client, _ ->
        server.enqueue(MockResponse().setResponseCode(500)); server.enqueue(redirect("https://example.invalid/cas/login"))
        assertThrows(SchoolFailure::class.java) { client.fetch() }
        assertEquals(2, server.requestCount)
    }
    @Test fun httpsCannotBeDowngradedDuringRecovery() = server { server, client, _ ->
        server.enqueue(MockResponse().setResponseCode(500)); server.enqueue(redirect("http://${portal.host}:${server.port}/tp_up/v2"))
        assertThrows(SchoolFailure::class.java) { client.fetch() }; assertEquals(2, server.requestCount)
    }
    @Test fun redirectsHaveAFiniteBudget() = server { server, client, _ ->
        server.enqueue(MockResponse().setResponseCode(500))
        repeat(8) { server.enqueue(redirect("https://${portal.host}:${server.port}/tp_up/v2")) }
        assertThrows(SchoolFailure::class.java) { client.fetch() }; assertEquals(9, server.requestCount)
    }
    @Test fun successfulQueryAfterReauthenticationResetsTheFlag() = server { server, client, memory ->
        memory.write("network", memory.read("network")!!.put("reauthRequired", true))
        server.enqueue(MockResponse().setBody(plan())); client.fetch()
        assertFalse(memory.read("network")!!.getBoolean("reauthRequired"))
    }
    @Test fun malformedPlanKeepsAUsefulFailureInsteadOfStartingLogin() = server { server, client, _ ->
        server.enqueue(MockResponse().setBody("<div id='servicePlanApp'>结构已改变</div>"))
        val error = assertThrows(SchoolFailure::class.java) { client.fetch() }
        assertFalse(error is AuthenticationRequired); assertEquals(1, server.requestCount)
    }
    @Test fun missingSessionDoesNotContactSchool() = server { server, client, memory ->
        memory.clear("network"); assertThrows(AuthenticationRequired::class.java) { client.fetch() }; assertEquals(0, server.requestCount)
    }
    @Test fun oldImportedPathIsMigratedBeforeRenewalAndDoesNotSurviveDeletion() {
        val memory = Memory(); seed(memory); val jar = SchoolCookieJar(memory)
        assertEquals("/tp_up", jar.loadForRequest(portal).single().path)
        jar.saveFromResponse(portal, listOf(Cookie.parse(portal, "JSESSIONID=test-new; Path=/tp_up; Secure; HttpOnly")!!))
        assertEquals("test-new", jar.loadForRequest(portal).single().value)
        jar.saveFromResponse(portal, listOf(Cookie.parse(portal, "JSESSIONID=; Max-Age=0; Path=/tp_up; Secure; HttpOnly")!!))
        assertTrue(jar.loadForRequest(portal).isEmpty()); assertEquals("test-cas", jar.loadForRequest(cas).single().value)
    }
    @Test fun realCanonicalCookieWinsOverLegacyCookieInEitherOrder() {
        val old = entry(portal.host, "JSESSIONID=test-old; Path=/tp_up/; Secure")
        val fresh = entry(portal.host, "JSESSIONID=test-fresh; Path=/tp_up; Secure")
        for (entries in listOf(listOf(old, fresh), listOf(fresh, old))) {
            val memory = Memory(); seed(memory, cookies = JSONArray(entries))
            assertEquals("test-fresh", SchoolCookieJar(memory).loadForRequest(portal).single().value)
        }
    }
    @Test fun upgradedBrowserLoginMigratesCookiesWithoutNeedingANativeQueryFirst() {
        val memory = Memory(); seed(memory)
        val restored = NetworkCookies.browserSnapshot(memory.read("network")!!)
        val byName = (0 until restored.length()).map { restored.getJSONObject(it) }
            .associate { item -> val cookie = Cookie.parse("https://${item.getString("origin")}/".toHttpUrl(), item.getString("raw"))!!; cookie.name to cookie.path }
        assertEquals("/tp_up", byName["JSESSIONID"]); assertEquals("/cas", byName["CASTGC"])
        // Preparing the WebView snapshot must not clear identity or require a successful business request.
        assertEquals(scope, memory.read("network")!!.getString("scope"))
    }
    @Test fun currentSchemaPreservesLegitimateDistinctPaths() {
        val memory = Memory(); seed(memory, schema = 2, cookies = JSONArray()
            .put(entry(portal.host, "JSESSIONID=test-root; Path=/; Secure"))
            .put(entry(portal.host, "JSESSIONID=test-app; Path=/tp_up; Secure")))
        assertEquals(listOf("/tp_up", "/"), SchoolCookieJar(memory).loadForRequest(portal).map { it.path })
    }
    @Test fun legacyLoadBalancerCookieMigratesToRootAndCanRotateOrBeDeleted() {
        val memory = Memory(); seed(memory, cookies = JSONArray()
            .put(entry(portal.host, "BIGipServerpool-wxb-portal2=test-old-routing; Path=/tp_up/; Secure"))
            .put(entry(portal.host, "foobar=test-marker; Path=/tp_up/")))
        val jar = SchoolCookieJar(memory)
        assertTrue(jar.loadForRequest(portal).all { it.path == "/" })
        jar.saveFromResponse(portal, listOf(Cookie.parse(portal, "BIGipServerpool-wxb-portal2=test-new-routing; Path=/; Secure")!!))
        assertEquals("test-new-routing", jar.loadForRequest(portal).single { it.name.startsWith("BIGipServer") }.value)
        jar.saveFromResponse(portal, listOf(Cookie.parse(portal, "BIGipServerpool-wxb-portal2=; Max-Age=0; Path=/; Secure")!!))
        assertFalse(jar.loadForRequest(portal).any { it.name.startsWith("BIGipServer") })
    }
    @Test fun fullWebViewCookieMetadataSurvivesStorageAndRemainsHostScoped() {
        val values = NetworkCookies.browserEntries(portal,
            listOf("JSESSIONID=test-full; Path=/tp_up; Secure; HttpOnly; Max-Age=3600"), null)
        val memory = Memory(); seed(memory, schema = 2, cookies = values)
        val jar = SchoolCookieJar(memory); val cookie = jar.loadForRequest(portal).single()
        assertEquals("/tp_up", cookie.path); assertTrue(cookie.secure); assertTrue(cookie.httpOnly); assertTrue(cookie.persistent)
        assertTrue(cookie.expiresAt < System.currentTimeMillis() + 3_610_000)
        assertTrue(jar.loadForRequest(cas).isEmpty())
        assertTrue(jar.loadForRequest("http://hkwxy.hust.edu.cn/tp_up/".toHttpUrl()).isEmpty())
    }
    @Test fun oldWebViewFallbackUsesTheObservedCanonicalSessionPath() {
        val values = NetworkCookies.browserEntries(portal, null, "JSESSIONID=test-fallback; test-routing=test-value")
        val state = JSONObject().put("cookies", values).put("cookieSchemaVersion", 2)
        val cookies = NetworkCookies.decode(state).associateBy { it.cookie.name }
        assertEquals("/tp_up", cookies["JSESSIONID"]!!.cookie.path)
        assertEquals("/", cookies["test-routing"]!!.cookie.path)
        assertFalse(cookies["JSESSIONID"]!!.cookie.persistent)
    }
    @Test fun explicitEmptyFullSnapshotDoesNotReviveAStaleHeader() {
        assertEquals(0, NetworkCookies.browserEntries(portal, emptyList(), "JSESSIONID=test-stale").length())
    }
    @Test fun cookiesFromAnUntrustedOriginAreIgnored() {
        val memory = Memory(); seed(memory, cookies = JSONArray().put(entry("example.invalid", "JSESSIONID=test-other; Path=/; Secure")))
        assertTrue(SchoolCookieJar(memory).loadForRequest(portal).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { NetworkCookies.browserEntries("https://example.invalid/".toHttpUrl(), null, "JSESSIONID=test-other") }
    }
}
