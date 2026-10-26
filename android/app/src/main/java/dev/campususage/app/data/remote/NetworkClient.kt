package dev.campususage.app.data.remote

import dev.campususage.app.core.model.*
import dev.campususage.app.data.auth.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class NetworkClient(
    private val sessions: SessionStorage,
    transport: OkHttpClient = OkHttpClient(),
    private val queryUrl: HttpUrl = QUERY_URL.toHttpUrl(),
    private val entryUrl: HttpUrl = LOGIN_URL.toHttpUrl()
) {
    companion object {
        const val LOGIN_URL = "https://hkwxy.hust.edu.cn/tp_up/v2#act=apps/campusNetwork/servicePlan"
        const val QUERY_URL = "https://hkwxy.hust.edu.cn/tp_up/apps/campusNetwork/servicePlan"
    }
    private val http = transport.newBuilder().cookieJar(SchoolCookieJar(sessions)).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).build()
    private data class Page(val url: HttpUrl, val status: Int, val html: String)
    private fun read(url: HttpUrl, userAgent: String): Page {
        var next = url
        repeat(8) {
            if (!NetworkCookies.allowed(next) || next.port !in setOf(queryUrl.port, entryUrl.port) || next.username.isNotEmpty() || next.password.isNotEmpty())
                throw SchoolFailure("学校认证跳转到了未支持的站点，请使用官方入口核对")
            http.newCall(Request.Builder().url(next).header("Accept", "text/html").header("User-Agent", userAgent).build()).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    next = response.header("Location")?.let { next.resolve(it) }
                        ?: throw SchoolFailure("学校认证跳转不完整，请稍后重试")
                } else {
                    val body = response.body
                    val source = body?.source()
                    source?.request(2_000_001)
                    if ((source?.buffer?.size ?: 0) > 2_000_000) throw SchoolFailure("学校查询页面异常，保留此前记录")
                    return Page(next, response.code, source?.readString(body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8).orEmpty())
                }
            }
        }
        throw SchoolFailure("学校认证跳转次数异常，已停止重试，保留此前记录")
    }
    private fun authentication(page: Page): Boolean {
        if (page.status in setOf(401, 403)) return true
        if (page.status !in 200..299) return false
        val document = Jsoup.parse(page.html)
        return page.url.host == "pass.hust.edu.cn" || document.selectFirst("form#fm1, form[action*='/cas/login']") != null ||
            (document.selectFirst("input[type=password]") != null && page.html.contains("统一身份认证"))
    }
    private fun requireAuthentication(): Nothing {
        sessions.read("network")?.let { state -> state.put("reauthRequired", true); sessions.write("network", state) }
        throw AuthenticationRequired("校园网登录已失效，请完成学校统一认证；已有查询记录保留")
    }
    private fun parse(page: Page): NetworkUsage {
        if (authentication(page)) requireAuthentication()
        if (page.status !in 200..299) throw SchoolFailure("校园网查询暂不可用（HTTP ${page.status}），保留此前记录")
        return NetworkParser.parse(page.html)
    }

    @Synchronized fun fetch(): Pair<String, NetworkUsage> {
        val saved = sessions.read("network")
        val scope = saved?.optString("scope")?.takeIf { it.isNotBlank() }
            ?: throw AuthenticationRequired("请先连接校园网统一认证")
        val agent = saved.optString("userAgent").takeIf { it.isNotBlank() && it.length <= 1024 && '\r' !in it && '\n' !in it } ?: "CampusUsage/0.5.1"
        val initial = read(queryUrl, agent)
        val value = if (initial.status in setOf(401, 403, 500) || authentication(initial)) {
            // A business 500 can mean an expired session. Probe the real entry, never assume all 500s are authentication failures.
            val entry = read(entryUrl, agent)
            if (authentication(entry)) requireAuthentication()
            if (entry.status !in 200..299) throw SchoolFailure("学校认证入口暂不可用（HTTP ${entry.status}），保留此前记录")
            val principal = Regex("""\bid_number\s*=\s*["']([^"'\\\r\n]{1,128})["']""").find(entry.html)?.groupValues?.get(1)
            if (principal != null && accountHash("network|$principal") != scope) requireAuthentication()
            // One recovery pass, one retry; a second server failure is not converted into an endless login loop.
            parse(read(queryUrl, agent))
        } else parse(initial)
        val current = sessions.read("network")
        if (current?.optString("scope") != scope) throw SchoolFailure("校园网账户已切换，本次查询未应用")
        current.put("reauthRequired", false).put("lastVerifiedAt", System.currentTimeMillis())
        sessions.write("network", current)
        return scope to value
    }
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
