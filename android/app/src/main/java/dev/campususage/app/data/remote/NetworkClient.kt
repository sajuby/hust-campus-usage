package dev.campususage.app.data.remote

import dev.campususage.app.core.model.*
import dev.campususage.app.data.auth.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class SchoolCookieJar(private val sessions: SessionStorage) : CookieJar {
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host !in setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn")) return
        val state = sessions.read("network") ?: return
        val saved = state.optJSONArray("cookies") ?: JSONArray()
        val map = mutableMapOf<String, Pair<String, Cookie>>()
        for (i in 0 until saved.length()) {
            val item = saved.getJSONObject(i); val origin = item.optString("origin")
            if (origin !in setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn")) continue
            Cookie.parse(HttpUrl.Builder().scheme("https").host(origin).build(), item.getString("raw"))?.let { map["${it.domain}|${it.path}|${it.name}"] = origin to it }
        }
        cookies.forEach { map["${it.domain}|${it.path}|${it.name}"] = url.host to it }
        val merged = JSONArray()
        map.values.filter { it.second.expiresAt > System.currentTimeMillis() }.forEach { (origin, cookie) -> merged.put(JSONObject().put("origin", origin).put("raw", cookie.toString())) }
        state.put("cookies", merged); sessions.write("network", state)
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (url.host !in setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn")) return emptyList()
        val cookies = sessions.read("network")?.optJSONArray("cookies") ?: return emptyList()
        return (0 until cookies.length()).mapNotNull { index ->
            val item = cookies.getJSONObject(index)
            // Parse against its own origin to preserve host-only cookies correctly.
            val origin = item.optString("origin")
            if (origin !in setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn")) null
            else Cookie.parse(HttpUrl.Builder().scheme("https").host(origin).build(), item.getString("raw"))
                ?.takeIf { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
        }.distinctBy { "${it.name}|${it.domain}|${it.path}" }
    }
}

class NetworkClient(private val sessions: SessionStorage) {
    companion object {
        const val LOGIN_URL = "https://hkwxy.hust.edu.cn/tp_up/v2#act=apps/campusNetwork/servicePlan"
        const val QUERY_URL = "https://hkwxy.hust.edu.cn/tp_up/apps/campusNetwork/servicePlan"
    }
    private val http = OkHttpClient.Builder().cookieJar(SchoolCookieJar(sessions)).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            if (url.scheme != "https" || url.host !in setOf("hkwxy.hust.edu.cn", "pass.hust.edu.cn")) throw SchoolFailure("学校认证跳转到了未支持的站点，请使用官方入口核对")
            chain.proceed(chain.request())
        }.build()

    @Synchronized fun fetch(): Pair<String, NetworkUsage> {
        val scope = sessions.read("network")?.optString("scope")?.takeIf { it.isNotBlank() }
            ?: throw AuthenticationRequired("请先连接校园网统一认证")
        http.newCall(Request.Builder().url(QUERY_URL).header("Accept", "text/html").build()).execute().use { response ->
            if (response.code == 401 || response.urlHost() == "pass.hust.edu.cn") throw AuthenticationRequired("校园网会话需要恢复，请完成学校统一认证")
            if (!response.isSuccessful) throw SchoolFailure("校园网查询暂不可用（HTTP ${response.code}）")
            val html = response.body?.string() ?: throw SchoolFailure("学校返回了空页面")
            if (Jsoup.parse(html).selectFirst("#servicePlanApp") == null) {
                if (html.contains("/cas/") || html.contains("统一身份认证")) throw AuthenticationRequired("校园网会话需要恢复，请完成学校统一认证")
                throw SchoolFailure("学校套餐页面结构已变化，请稍后核对官方入口")
            }
            return scope to NetworkParser.parse(html)
        }
    }
    private fun Response.urlHost() = request.url.host
}

object NetworkParser {
    fun parse(html: String): NetworkUsage {
        val root = Jsoup.parse(html).selectFirst("#servicePlanApp") ?: throw SchoolFailure("未取得校园网套餐页")
        var plan: String? = null; var balance: BigDecimal? = null; var expires: LocalDate? = null
        var trafficName: String? = null; val traffic = mutableListOf<TrafficUsage>()
        val usedPattern = Regex("已用[：:]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(GB|MB|TB|G|M|T)", RegexOption.IGNORE_CASE)
        val totalsPattern = Regex("剩余\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(GB|MB|TB|G|M|T)\\s*/\\s*共\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(GB|MB|TB|G|M|T)", RegexOption.IGNORE_CASE)
        root.select(".panel-card-header").forEach { row ->
            val fields = row.select("p").map { it.text().trim() }
            when (fields.firstOrNull()) {
                "套餐" -> plan = fields.getOrNull(1)?.takeIf { it.isNotBlank() }
                "余额" -> balance = fields.getOrNull(1)?.toBigDecimalOrNull()
                "到期时间" -> expires = runCatching { LocalDate.parse(fields.getOrNull(1)?.trim()) }.getOrNull()
                "免费流量", "计费流量", "不限使用流量" -> trafficName = fields[0]
                else -> if (trafficName != null && fields.isNotEmpty()) {
                    val used = usedPattern.find(fields[0]) ?: return@forEach
                    val totals = fields.getOrNull(1)?.let { totalsPattern.find(it) }
                    if (trafficName != "不限使用流量" && totals == null) throw SchoolFailure("学校流量剩余或总量字段已变化")
                    if (totals != null && (!totals.groupValues[2].equals(used.groupValues[2], true) || !totals.groupValues[4].equals(used.groupValues[2], true))) throw SchoolFailure("学校流量单位不一致")
                    traffic += TrafficUsage(trafficName!!, used.groupValues[1].toBigDecimal(), used.groupValues[2].uppercase(),
                        totals?.groupValues?.get(1)?.toBigDecimal(), totals?.groupValues?.get(3)?.toBigDecimal())
                    trafficName = null
                }
            }
        }
        if (plan == null || balance == null || expires == null || traffic.map { it.name }.toSet() != setOf("免费流量", "计费流量", "不限使用流量") || traffic.size != 3) throw SchoolFailure("学校套餐字段不完整，保留此前记录")
        return NetworkUsage(plan!!, balance!!, expires!!, traffic, System.currentTimeMillis())
    }
}
