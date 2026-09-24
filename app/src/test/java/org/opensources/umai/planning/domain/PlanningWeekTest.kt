package org.opensources.umai.planning.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PlanningWeekTest {

    @Test
    fun `a week starts on the Monday of the day, whatever the day`() {
        val monday = LocalDate.of(2026, 9, 21)

        (0L until 7L).forEach { offset ->
            assertEquals(monday, PlanningWeek.startOf(monday.plusDays(offset)))
        }
        assertEquals(monday.plusWeeks(1), PlanningWeek.startOf(monday.plusDays(7)))
    }

    @Test
    fun `the week runs from Monday to Sunday`() {
        val days = PlanningWeek.days(LocalDate.of(2026, 9, 21))

        assertEquals(7, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, days.last().dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 27), days.last())
    }
}
