package dev.campususage.app.data

import dev.campususage.app.core.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZonedDateTime

/** Isolated synthetic fixtures. No school endpoint or real login state is implemented here. */
class DemoRepository {
    fun query(category: Category, date: LocalDate, scenario: Scenario, now: ZonedDateTime): DailyUsage? {
        if (category == Category.NETWORK || scenario == Scenario.PENDING || !PublicationPolicy.isExpected(date,now)) return null
        if (scenario == Scenario.ZERO || category == Category.ENERGY) return DailyUsage(date,category,BigDecimal("0.0000"),BigDecimal("0.0000"),BigDecimal("0.0000"))
        val values = listOf("1.2000","2.4000","1.8000","3.0000","0.6000","2.1000","1.5000")
        val total = BigDecimal(values[date.dayOfMonth % values.size])
        val room = total.multiply(BigDecimal("0.76")).setScale(4,RoundingMode.HALF_UP)
        return DailyUsage(date,category,total,room,total.subtract(room))
    }
}

/** Contract to be implemented only after the school's normal authentication and query protocol is verified. */
interface SchoolGateway {
    suspend fun sessionState(): SessionState
    suspend fun queryDaily(key: QueryKey): DailyUsage?
}
