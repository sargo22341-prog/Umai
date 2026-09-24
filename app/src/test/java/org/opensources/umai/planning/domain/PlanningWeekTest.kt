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
            assertEquals(monday, PlanningWeek.startOf(monday.plusDays(offset), DayOfWeek.MONDAY))
        }
        assertEquals(monday.plusWeeks(1), PlanningWeek.startOf(monday.plusDays(7), DayOfWeek.MONDAY))
    }

    @Test
    fun `a week can start on any day the household chose`() {
        val thursday = LocalDate.of(2026, 9, 24)

        assertEquals(LocalDate.of(2026, 9, 20), PlanningWeek.startOf(thursday, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 9, 19), PlanningWeek.startOf(thursday, DayOfWeek.SATURDAY))
        assertEquals(thursday, PlanningWeek.startOf(thursday, DayOfWeek.THURSDAY))
        assertEquals(LocalDate.of(2026, 9, 18), PlanningWeek.startOf(thursday, DayOfWeek.FRIDAY))
    }

    @Test
    fun `a week starting on Sunday ends on Saturday`() {
        val days = PlanningWeek.days(PlanningWeek.startOf(LocalDate.of(2026, 9, 24), DayOfWeek.SUNDAY))

        assertEquals(DayOfWeek.SUNDAY, days.first().dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, days.last().dayOfWeek)
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
