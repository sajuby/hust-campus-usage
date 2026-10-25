package dev.campususage.app.feature.routes.data

import dev.campususage.app.feature.routes.domain.*
import dev.campususage.app.feature.routes.model.*
import java.time.*

/** Explicit design fixtures. Never used as a fallback for a failed live request. */
object RouteDemo {
    val origin = RoutePlace("", "武汉火车站[地铁站]", "轨道交通4号线", "武汉市", "420111", GeoPoint(114.424365,30.607384))
    val destination = RoutePlace("", "江汉路[地铁站]", "轨道交通2号线、6号线", "武汉市", "420103", GeoPoint(114.292314,30.579564))
    val transfer = RoutePlace("", "洪山广场[地铁站]", "轨道交通2号线、4号线", "武汉市", "420106", GeoPoint(114.336957,30.545217))
    val time: ZonedDateTime = ZonedDateTime.parse("2026-10-24T22:08:00+08:00")
    private val first = RouteSegment.Ride("demo-4","轨道交通4号线","柏林",true, RouteStop("demo-a","武汉火车站"),RouteStop("demo-b4","洪山广场"),
        (1..10).map { RouteStop("demo-v$it", "示例途经站$it") },1680,"06:30","23:00")
    private val second = RouteSegment.Ride("demo-2","轨道交通2号线","天河机场",true, RouteStop("demo-b2","洪山广场"),RouteStop("demo-c","江汉路"),
        listOf("小龟山","螃蟹岬","积玉桥").map { RouteStop("demo-$it",it) },960,"06:30","23:00")
    fun result(request: RouteRequest): RouteResult {
        if (request.city != RouteCity.wuhan || request.origin.point != origin.point || request.destination.point != destination.point)
            return RouteResult(request,emptyList(),Instant.now(),"界面演示",true)
        val route = TransitRoute("demo-main",2880,130,"5",listOf(RouteSegment.Walk(26,21),first,RouteSegment.Walk(70,179),second,RouteSegment.Walk(34,40)))
        return RouteResult(request,listOf(route,route.copy(id="demo-alt",durationSeconds=3060,walkMeters=90)),Instant.now(),"界面演示",true)
    }
    val lateRoute = TransitRoute("demo-last",4260,130,"5",listOf(RouteSegment.Walk(26,480),first,RouteSegment.Walk(70,300),second,RouteSegment.Walk(34,180)))
    fun lastAssessment(): TransferAssessment {
        fun at(value: String) = ZonedDateTime.parse("2026-10-24T$value:00+08:00")
        val tables = listOf(
            LegTimetable("demo-4","demo-a","demo-b4", time.toLocalDate(),at("23:59").toInstant(),"虚构演示时刻表",
                listOf(ScheduledRun("previous",at("22:23"),at("22:51")),ScheduledRun("too-late",at("22:31"),at("22:59"))),demonstration=true),
            LegTimetable("demo-2","demo-b2","demo-c", time.toLocalDate(),at("23:59").toInstant(),"虚构演示时刻表",
                listOf(ScheduledRun("last",at("23:00"),at("23:16"))),demonstration=true))
        return TransitTimeEvaluator.lastConnection(lateRoute,time,time.toInstant(),tables,entrySeconds=0,demonstration=true)
    }
}
