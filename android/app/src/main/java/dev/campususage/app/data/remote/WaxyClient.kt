package dev.campususage.app.data.remote

import dev.campususage.app.core.model.*
import dev.campususage.app.data.auth.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class WaxyClient(
    private val sessions: SessionStorage,
    private val base: String = "https://www.waxy.online:20443/prod-api",
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
) {
    fun saved() = sessions.read("waxy")
    private fun request(path: String, token: String? = null, body: JSONObject? = null): JSONObject {
        val builder = Request.Builder().url(base + path).header("Accept", "application/json")
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        http.newCall(builder.build()).execute().use { response ->
            if (response.code == 401) throw AuthenticationRequired()
            if (!response.isSuccessful) throw SchoolFailure("水电能源服务暂不可用（HTTP ${response.code}）")
            val result = try { JSONObject(response.body?.string() ?: "") } catch (_: Exception) { throw SchoolFailure("学校返回了无法识别的用量响应") }
            when (result.optInt("code", 200)) {
                200 -> return result
                401 -> throw AuthenticationRequired()
                else -> throw SchoolFailure(result.optString("msg", "学校查询失败").take(160))
            }
        }
    }
    fun captcha(): JSONObject = request("/captchaImage")

    /** Only an explicit user login or one bounded 401 recovery may submit credentials. */
    @Synchronized fun login(username: String, password: String, remember: Boolean, code: String = "", uuid: String = ""): String {
        require(username.isNotBlank() && password.isNotEmpty())
        val cap = if (uuid.isEmpty()) captcha() else null
        if (cap?.optBoolean("captchaEnabled", false) == true) throw CaptchaRequired()
        val response = request("/login", body = JSONObject().put("username", username.trim()).put("password", password).put("code", code).put("uuid", uuid))
        val token = response.optString("token").takeIf { it.isNotBlank() } ?: throw SchoolFailure("学校未返回有效登录凭证")
        val info = request("/getInfo", token).optJSONObject("user") ?: throw SchoolFailure("学校未返回账户身份")
        val userId = info.optString("userId").takeIf { it.isNotBlank() } ?: throw SchoolFailure("学校未返回账户标识")
        val resident = request("/rm/resident/current", token).opt("data")
        if (resident == null || resident == JSONObject.NULL) throw SchoolFailure("学校尚未返回宿舍绑定，请先在官方系统完成绑定")
        val binding = accountHash(canonical(resident))
        val account = accountHash("waxy|$userId")
        val scope = accountHash("$account|$binding")
        val session = JSONObject().put("token", token).put("scope", scope).put("account", account).put("binding", binding).put("remember", remember)
            .put("savedAt", System.currentTimeMillis())
        if (remember) session.put("username", username.trim()).put("password", password)
        sessions.write("waxy", session)
        return scope
    }

    @Synchronized private fun authorized(path: String): Pair<String, JSONObject> {
        var session = saved() ?: throw AuthenticationRequired("请先连接水电能源账户")
        try { return session.getString("scope") to request(path, session.getString("token")) }
        catch (_: AuthenticationRequired) {
            if (!session.optBoolean("remember") || session.optString("password").isEmpty()) throw AuthenticationRequired()
            // No fabricated refresh endpoint: the official client exposes /login only.
            // A captcha or invalid password stops here and returns control to the user.
            login(session.getString("username"), session.getString("password"), true)
            session = saved() ?: throw AuthenticationRequired()
            return session.getString("scope") to request(path, session.getString("token"))
        }
    }
    @Synchronized fun refreshBinding() {
        val (_, response) = authorized("/rm/resident/current")
        val resident = response.opt("data")
        if (resident == null || resident == JSONObject.NULL) throw SchoolFailure("学校尚未返回宿舍绑定，保留此前历史记录")
        val session = saved() ?: throw AuthenticationRequired()
        val binding = accountHash(canonical(resident))
        if (binding != session.optString("binding")) {
            session.put("binding", binding).put("scope", accountHash("${session.getString("account")}|$binding"))
            sessions.write("waxy", session)
        }
    }
    private fun rows(response: JSONObject): JSONArray {
        if (!response.has("data")) throw SchoolFailure("学校未返回统计字段，保留此前记录")
        if (response.isNull("data")) return JSONArray()
        return response.optJSONArray("data") ?: throw SchoolFailure("学校统计格式已变化，保留此前记录")
    }
    fun daily(category: Category, date: LocalDate): Pair<String, DailyUsage?> {
        val type = pathType(category)
        val (scope, response) = authorized("/echarts/app/$type/day/$date")
        return scope to WaxyParser.daily(rows(response), date, category)
    }
    fun month(category: Category, month: YearMonth): Pair<String, List<DailyUsage>> {
        val (scope, response) = authorized("/echarts/app/${pathType(category)}/month/$month")
        return scope to WaxyParser.month(rows(response), month, category)
    }
    private fun pathType(category: Category): String = when (category) {
        Category.ELECTRICITY -> "electric"
        Category.ENERGY -> "power/time"
        else -> throw IllegalArgumentException("Not an energy category")
    }
    private fun canonical(value: Any): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { "$it:${canonical(value.get(it))}" }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }
        else -> value.toString()
    }
}

object WaxyParser {
    private fun decimal(row: JSONObject): BigDecimal {
        val raw = row.opt("usage")
        if (raw == null || raw == JSONObject.NULL) throw SchoolFailure("学校返回的用量字段为空")
        return raw.toString().toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO }
            ?: throw SchoolFailure("学校返回的用量格式已变化")
    }
    fun daily(rows: JSONArray, date: LocalDate, category: Category): DailyUsage? {
        if (rows.length() == 0) return null
        var room: BigDecimal? = null; var publicArea: BigDecimal? = null
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val usage = decimal(row)
            when (row.opt("common")?.toString()?.lowercase()) {
                "true", "1" -> publicArea = (publicArea ?: BigDecimal.ZERO) + usage
                "false", "0" -> room = (room ?: BigDecimal.ZERO) + usage
                else -> throw SchoolFailure("学校返回的房间与公区分类已变化")
            }
        }
        return DailyUsage(date, category, (room ?: BigDecimal.ZERO) + (publicArea ?: BigDecimal.ZERO), room, publicArea, System.currentTimeMillis())
    }
    fun month(rows: JSONArray, month: YearMonth, category: Category): List<DailyUsage> {
        val days = mutableMapOf<Int, JSONArray>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val day = row.optString("day").toIntOrNull() ?: throw SchoolFailure("学校月统计的日期字段已变化")
            if (day !in 1..month.lengthOfMonth()) throw SchoolFailure("学校月统计包含无效日期")
            if (row.has("month") && row.optString("month").toIntOrNull() != month.monthValue) throw SchoolFailure("学校返回了其他月份的统计")
            days.getOrPut(day) { JSONArray() }.put(row)
        }
        return days.toSortedMap().map { (day, values) -> daily(values, month.atDay(day), category)!! }
    }
}
