package org.opensources.umai.cooking.domain

import java.time.Duration
import java.util.Locale

/**
 * How timers read, in the cooking mode, on the timer pills and in the
 * notifications alike. The unit words come from the resources of the caller.
 */
object TimerFormat {

    /** The localized abbreviations of hour, minute and second. */
    data class Units(val hour: String, val minute: String, val second: String)

    /** "1 h 30", "15 min", "1 min 30 s", "45 s". */
    fun duration(duration: Duration, units: Units): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        return buildList {
            if (hours > 0) add("$hours ${units.hour}")
            if (minutes > 0) add(if (hours > 0 && seconds == 0) "$minutes" else "$minutes ${units.minute}")
            if (seconds > 0) add("$seconds ${units.second}")
        }.joinToString(" ")
    }

    /** What is left, rounded up to the second: "1:05:00", "14:59", "0:07". */
    fun countdown(millis: Long): String {
        val total = (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
