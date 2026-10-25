package dev.campususage.app.core.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId

enum class Category(val label: String) { ELECTRICITY("电费"), ENERGY("能源"), NETWORK("校园网") }
enum class Scenario(val label: String) { AVAILABLE("正常统计"), ZERO("有效零用量"), PENDING("尚未出统计"), OFFLINE("离线缓存"), LOGIN("会话需恢复") }
data class DailyUsage(val date: LocalDate, val category: Category, val total: BigDecimal, val room: BigDecimal?, val publicArea: BigDecimal?, val fetchedAt: Long? = null)
data class TrafficUsage(val name: String, val used: BigDecimal, val unit: String, val remaining: BigDecimal?, val total: BigDecimal?)
data class NetworkUsage(val plan: String, val balance: BigDecimal, val expires: LocalDate, val traffic: List<TrafficUsage>, val fetchedAt: Long)
data class QueryKey(val account: String, val bindingVersion: String, val category: Category, val date: LocalDate)
sealed interface Availability { data class Available(val usage: DailyUsage): Availability; data object NoData: Availability; data object NotQueried: Availability }
sealed interface SessionState { data object NotConnected: SessionState; data object Ready: SessionState; data object Renewing: SessionState; data object ReLoginRequired: SessionState }
object PublicationPolicy {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    fun expectedAfter(date: LocalDate): ZonedDateTime = date.plusDays(1).atTime(8,0).atZone(zone)
    fun isExpected(date: LocalDate, now: ZonedDateTime): Boolean = !now.isBefore(expectedAfter(date))
}
