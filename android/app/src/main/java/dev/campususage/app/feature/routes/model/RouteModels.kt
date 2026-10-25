package dev.campususage.app.feature.routes.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

val routeZone: ZoneId = ZoneId.of("Asia/Shanghai")

enum class QuerySection(val label: String) { ELECTRICITY("电费"), ENERGY("能源"), NETWORK("校园网"), ROUTES("查路线") }
enum class CoordinateSystem { WGS84, GCJ02 }
data class GeoPoint(val longitude: Double, val latitude: Double, val system: CoordinateSystem = CoordinateSystem.GCJ02) {
    init { require(longitude.isFinite() && latitude.isFinite() && longitude in -180.0..180.0 && latitude in -90.0..90.0) }
    fun wire(): String { require(system == CoordinateSystem.GCJ02); return "$longitude,$latitude" }
}
data class RouteCity(val name: String, val code: String) {
    init { require(name.isNotBlank() && Regex("[0-9]{6}").matches(code)) }
    companion object {
        val wuhan = RouteCity("武汉市", "420100")
        val choices = listOf(wuhan, RouteCity("北京市","110100"), RouteCity("上海市","310000"), RouteCity("广州市","440100"),
            RouteCity("深圳市","440300"), RouteCity("成都市","510100"), RouteCity("重庆市","500100"), RouteCity("杭州市","330100"),
            RouteCity("南京市","320100"), RouteCity("西安市","610100"), RouteCity("郑州市","410100"), RouteCity("苏州市","320500"))
    }
}
data class RoutePlace(val id: String, val name: String, val address: String, val cityName: String, val adCode: String, val point: GeoPoint,
                      val accuracyMeters: Float? = null, val locatedAt: Instant? = null)
enum class RouteStrategy(val label: String, val wire: Int) { RECOMMENDED("综合推荐",0), SUBWAY("地铁优先",7), WALK_LESS("步行少",3), TRANSFER_LESS("换乘少",2) }
data class RouteRequest(val city: RouteCity, val origin: RoutePlace, val destination: RoutePlace, val departure: ZonedDateTime, val strategy: RouteStrategy)
data class RouteStop(val id: String, val name: String)
sealed interface RouteSegment {
    val durationSeconds: Long?
    data class Walk(val distanceMeters: Int, override val durationSeconds: Long?, val note: String = "步行") : RouteSegment
    data class Ride(val lineId: String, val name: String, val direction: String, val subway: Boolean, val origin: RouteStop, val destination: RouteStop,
                    val via: List<RouteStop>, override val durationSeconds: Long?, val first: String?, val last: String?, val color: String? = null) : RouteSegment
}
data class TransitRoute(val id: String, val durationSeconds: Long?, val walkMeters: Int, val fare: String?, val segments: List<RouteSegment>) {
    val rides get() = segments.filterIsInstance<RouteSegment.Ride>()
    val transfers get() = (rides.size - 1).coerceAtLeast(0)
    val stations get() = rides.sumOf { it.via.size + 1 }
}
data class RouteResult(val request: RouteRequest, val routes: List<TransitRoute>, val fetchedAt: Instant,
                       val source: String = "城市通", val demonstration: Boolean = false) {
    fun freshAt(now: Instant): Boolean = !now.isBefore(fetchedAt) && java.time.Duration.between(fetchedAt, now).seconds <= 120
}
class RouteFailure(val explanation: String) : Exception(explanation)

/** A UI response can only belong to the exact input snapshot that initiated it. */
class RequestGeneration {
    private var value = 0L
    @Synchronized fun next(): Long = ++value
    @Synchronized fun current(id: Long): Boolean = value == id
    @Synchronized fun ifCurrent(id: Long, action: () -> Unit) { if (value == id) action() }
}
