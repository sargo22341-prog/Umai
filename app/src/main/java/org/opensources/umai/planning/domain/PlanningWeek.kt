package org.opensources.umai.planning.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The meal plan is read one week at a time. The week starts on the day the
 * household chose in Mealie ("first day of the week"), Monday until it is known.
 */
object PlanningWeek {

    const val LENGTH = 7

    /** The day weeks start on before the household preference has been read. */
    val DEFAULT_FIRST_DAY: DayOfWeek = DayOfWeek.MONDAY

    /** The first day of the week [date] falls in, weeks starting on [firstDay]. */
    fun startOf(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    /** The seven days of the week starting on [start]. */
    fun days(start: LocalDate): List<LocalDate> = (0 until LENGTH).map { start.plusDays(it.toLong()) }
}
