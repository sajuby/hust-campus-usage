package dev.campususage.app.feature.routes.domain

import dev.campususage.app.feature.routes.model.*
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

enum class TransferRisk { EXPECTED, TIGHT, MISSED, UNKNOWN }
data class ScheduledRun(val id: String, val departs: ZonedDateTime, val arrives: ZonedDateTime, val reachesDestination: Boolean = true)
data class LegTimetable(val lineId: String, val originId: String, val destinationId: String, val serviceDate: java.time.LocalDate,
                        val validUntil: Instant, val source: String, val runs: List<ScheduledRun>, val complete: Boolean = true, val demonstration: Boolean = false)
data class TransferAssessment(val risk: TransferRisk, val latestDeparture: ZonedDateTime? = null, val arrival: ZonedDateTime? = null,
                              val runs: List<ScheduledRun> = emptyList(), val explanation: String, val source: String? = null)

object TransitTimeEvaluator {
    /** The provider's total is used once. Its candidate/waiting-time coverage is not independently verified. */
    fun estimatedArrival(route: TransitRoute, departure: ZonedDateTime): ZonedDateTime? = route.durationSeconds?.let { departure.plusSeconds(it) }

    /** Reverse over actual runs; never use a line-wide closing hour as a boarding-station timetable. */
    fun lastConnection(route: TransitRoute, departure: ZonedDateTime, now: Instant, timetables: List<LegTimetable>,
                       boardingBufferSeconds: Long = 240, entrySeconds: Long = 240, demonstration: Boolean = false): TransferAssessment {
        require(boardingBufferSeconds >= 0 && entrySeconds >= 0)
        val rides = route.rides
        if (rides.isEmpty()) return unknown()
        val tables = rides.map { ride -> timetables.find { it.lineId == ride.lineId && it.originId == ride.origin.id &&
            it.destinationId == ride.destination.id && it.serviceDate == departure.toLocalDate() && it.complete &&
            it.validUntil.isAfter(now) && it.source.isNotBlank() && it.demonstration == demonstration } ?: return unknown() }
        if (tables.any { table -> table.runs.isEmpty() || table.runs.any { it.arrives.isBefore(it.departs) } }) return unknown()
        val ridePositions = route.segments.indices.filter { route.segments[it] is RouteSegment.Ride }
        fun walks(from: Int, until: Int): Long? {
            var total = 0L
            for (i in from until until) {
                val segment = route.segments[i]
                if (segment !is RouteSegment.Walk || segment.durationSeconds == null) return null
                total += segment.durationSeconds!!
            }
            return total
        }
        val chosen = mutableListOf<ScheduledRun>()
        var followingDeparture: ZonedDateTime? = null
        for (i in rides.indices.reversed()) {
            val deadline = followingDeparture?.let { next ->
                val walking = walks(ridePositions[i] + 1, ridePositions[i + 1]) ?: return unknown()
                next.minusSeconds(walking + boardingBufferSeconds)
            }
            val run = tables[i].runs.filter { it.reachesDestination && (deadline == null || !it.arrives.isAfter(deadline)) }
                .maxByOrNull { it.departs.toInstant() }
                ?: return TransferAssessment(TransferRisk.MISSED, explanation = "已核实的班次无法完成全部换乘")
            chosen.add(0, run); followingDeparture = run.departs
        }
        val approach = walks(0, ridePositions.first()) ?: return unknown()
        val finalWalk = walks(ridePositions.last() + 1, route.segments.size) ?: return unknown()
        val latest = chosen.first().departs.minusSeconds(approach + entrySeconds + boardingBufferSeconds)
        val margin = Duration.between(departure, latest).seconds
        return TransferAssessment(when { margin < 0 -> TransferRisk.MISSED; margin < 5 * 60 -> TransferRisk.TIGHT; else -> TransferRisk.EXPECTED },
            latest, chosen.last().arrives.plusSeconds(finalWalk), chosen,
            if (margin < 0) "建议出发时间已过，请查看其他路线" else "按已提供的站点时刻表估算，已预留进站与换乘时间", tables.map { it.source }.distinct().joinToString("、"))
    }
    private fun unknown() = TransferAssessment(TransferRisk.UNKNOWN, explanation = "暂无本站方向与运营日的完整班次，暂无法核实末班接续")
}
