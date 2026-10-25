package dev.campususage.app.feature.routes.data

import dev.campususage.app.feature.routes.model.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Verified read-only, anonymous endpoints. No WeChat identity, application key or school session is sent. */
class CityTongClient(
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(22, TimeUnit.SECONDS).followRedirects(false).build(),
    private val baseUrl: String = "https://cvg.17usoft.com/subwayRealTime/line/",
    private val clock: Clock = Clock.systemUTC()
) {
    private fun post(action: String, body: JSONObject): Any? {
        require(action in setOf("poiSearch", "routePlan", "reGeo"))
        val request = Request.Builder().url(baseUrl + action).post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("User-Agent", "CampusUsage/0.3").build()
        http.newCall(request).execute().use { response ->
            if (response.code == 429) throw RouteFailure("城市通查询较频繁，请稍后重试")
            if (!response.isSuccessful) throw RouteFailure("城市通暂时无法连接（${response.code}），保留上次结果")
            val raw = response.body ?: throw RouteFailure("城市通未返回查询内容")
            if (raw.contentLength() > 4_000_000) throw RouteFailure("城市通响应异常，请稍后重试")
            val text = raw.charStream().use { it.readText() }
            if (text.length > 4_000_000) throw RouteFailure("城市通响应异常，请稍后重试")
            val json = runCatching { JSONObject(text) }.getOrElse { throw RouteFailure("城市通返回格式发生变化") }
            if (json.optString("code") != "1000") {
                // Provider error text can contain internal details; never surface the raw response.
                throw RouteFailure(when (json.optString("code")) {
                    "401", "403" -> "城市通访问条件发生变化，请稍后再试"
                    "400" -> "城市通无法识别本次查询地点，请重新选择起终点"
                    else -> "城市通暂未完成查询，请稍后更新"
                })
            }
            return json.opt("result").takeUnless { it == JSONObject.NULL }
        }
    }
    fun search(city: RouteCity, keyword: String): List<RoutePlace> {
        require(keyword.isNotBlank() && keyword.length <= 80)
        val array = post("poiSearch", JSONObject().put("cityCode", city.code).put("keywords", keyword)) as? JSONArray
            ?: return emptyList()
        return objects(array).mapNotNull { item ->
            val point = point(item.optString("location")) ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val ad = item.optString("adcode")
            if (!Regex("[0-9]{6}").matches(ad)) return@mapNotNull null
            RoutePlace(item.optString("id"), name, item.optString("address"), item.optString("cityname"), ad, point)
        }.distinctBy { it.name to it.point }.take(12)
    }
    fun reverse(point: GeoPoint, city: RouteCity = RouteCity.wuhan): RoutePlace {
        val data = post("reGeo", JSONObject().put("cityCode", city.code).put("location", point.wire())) as? JSONObject
            ?: throw RouteFailure("暂未识别当前位置，请手动选择起点")
        val component = data.optJSONObject("addressComponent") ?: throw RouteFailure("暂未识别所在城市，请手动选择")
        val cityValue = component.opt("city")
        val city = when (cityValue) { is JSONArray -> cityValue.optString(0); is String -> cityValue; else -> "" }
            .ifBlank { component.optString("province") }
        val ad = component.optString("adcode")
        if (!Regex("[0-9]{6}").matches(ad) || city.isBlank()) throw RouteFailure("暂未识别所在城市，请手动选择")
        return RoutePlace("", "我的位置", data.optString("formattedAddress"), city, ad, point, locatedAt = clock.instant())
    }
    fun routes(request: RouteRequest): RouteResult {
        require(request.origin.point != request.destination.point) { "起点和终点不能相同" }
        val body = JSONObject().put("cityCode", request.city.code).put("strategy", request.strategy.wire)
            .put("originCityCode", request.origin.adCode).put("destinationCityCode", request.destination.adCode)
            .put("origin", request.origin.point.wire()).put("destination", request.destination.point.wire())
        // Plain coordinates over HTTPS are accepted by the verified endpoint. No client signing secret is needed.
        val data = post("routePlan", body) as? JSONObject ?: return RouteResult(request, emptyList(), clock.instant())
        return RouteResult(request, parseRoutes(data), clock.instant())
    }
    companion object {
        fun point(value: String): GeoPoint? = runCatching {
            val parts = value.split(','); require(parts.size == 2); GeoPoint(parts[0].toDouble(), parts[1].toDouble())
        }.getOrNull()
        private fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        private fun seconds(value: Any?): Long? = value?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < 7 * 86400 }?.toLong()
        private fun time(value: String): String? {
            val cleaned = value.replace(":", "")
            if (!Regex("[0-9]{4}").matches(cleaned)) return null
            val hour = cleaned.take(2).toInt(); val minute = cleaned.takeLast(2).toInt()
            return if (hour in 0..23 && minute in 0..59) "${cleaned.take(2)}:${cleaned.takeLast(2)}" else null
        }
        fun parseRoutes(data: JSONObject): List<TransitRoute> = objects(data.optJSONArray("transits")).mapIndexedNotNull { index, transit ->
            val segments = mutableListOf<RouteSegment>()
            var unsupported = false
            for (segment in objects(transit.optJSONArray("segments"))) {
                segment.optJSONObject("walking")?.let { walk ->
                    val distance = seconds(walk.opt("distance"))?.coerceAtMost(1_000_000)?.toInt() ?: 0
                    val duration = seconds(walk.optJSONObject("cost")?.opt("duration"))
                    if (distance > 0 || (duration ?: 0) > 0) segments += RouteSegment.Walk(distance, duration)
                }
                // Multiple buslines are alternatives for a segment, not extra transfers. Use the first full alternative.
                val line = segment.optJSONObject("bus")?.optJSONArray("buslines")?.optJSONObject(0)
                line?.let {
                    val from = it.optJSONObject("departureStop"); val to = it.optJSONObject("arrivalStop")
                    if (from == null || to == null || from.optString("name").isBlank() || to.optString("name").isBlank()) unsupported = true
                    else {
                        val name = it.optString("name"); val direction = name.substringAfterLast("--", "").substringBefore(')')
                        val lineId = it.optString("id")
                        val color = data.optJSONObject("metroColor")?.optJSONObject(lineId)?.optString("color")
                        segments += RouteSegment.Ride(lineId, name.substringBefore('('), direction, it.optString("type").contains("地铁"),
                            RouteStop(from.optString("id"), from.optString("name")), RouteStop(to.optString("id"), to.optString("name")),
                            objects(it.optJSONArray("viaStops")).map { stop -> RouteStop(stop.optString("id"), stop.optString("name")) },
                            seconds(it.optJSONObject("cost")?.opt("duration")), time(it.optString("startTime")), time(it.optString("endTime")), color)
                    }
                }
                if (segment.optJSONObject("railway") != null || segment.optJSONObject("taxi") != null) unsupported = true
            }
            // Do not silently drop a rail/taxi segment and display an incomplete itinerary as a full route.
            if (unsupported || segments.none { it is RouteSegment.Ride }) return@mapIndexedNotNull null
            val cost = transit.optJSONObject("cost")
            val fare = runCatching { BigDecimal(cost?.optString("transitFee")).takeIf { it.signum() >= 0 }?.stripTrailingZeros()?.toPlainString() }.getOrNull()
            TransitRoute("citytong-$index", seconds(cost?.opt("duration")), segments.filterIsInstance<RouteSegment.Walk>().sumOf { it.distanceMeters }, fare, segments)
        }
    }
}
