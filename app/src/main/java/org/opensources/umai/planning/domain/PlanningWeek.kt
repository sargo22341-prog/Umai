package org.opensources.umai.planning.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The meal plan is read one calendar week at a time, from Monday to Sunday. */
object PlanningWeek {

    const val LENGTH = 7

    /** The Monday of the week [date] falls in. */
    fun startOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The seven days of the week starting on [start]. */
    fun days(start: LocalDate): List<LocalDate> = (0 until LENGTH).map { start.plusDays(it.toLong()) }
}
