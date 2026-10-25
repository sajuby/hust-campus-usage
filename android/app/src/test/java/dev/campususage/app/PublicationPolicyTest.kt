package dev.campususage.app

import dev.campususage.app.core.model.*
import dev.campususage.app.data.DemoRepository
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class PublicationPolicyTest {
    @Test fun nextMorningBoundaryAndYearChange() {
        val date = LocalDate.parse("2026-10-22")
        assertFalse(PublicationPolicy.isExpected(date,ZonedDateTime.parse("2026-10-23T07:59:59+08:00")))
        assertTrue(PublicationPolicy.isExpected(date,ZonedDateTime.parse("2026-10-23T08:00:00+08:00")))
        assertEquals("2027-01-01T08:00+08:00[Asia/Shanghai]",PublicationPolicy.expectedAfter(LocalDate.parse("2026-12-31")).toString())
    }
    @Test fun zeroAndMissingRemainDifferent() {
        val repository=DemoRepository(); val date=LocalDate.parse("2026-10-22"); val now=ZonedDateTime.parse("2026-10-23T09:00+08:00")
        assertEquals("0.0000",repository.query(Category.ENERGY,date,Scenario.AVAILABLE,now)?.total?.toPlainString())
        assertNull(repository.query(Category.ELECTRICITY,date,Scenario.PENDING,now))
        assertNotNull(repository.query(Category.ELECTRICITY,date,Scenario.OFFLINE,now))
    }
}
