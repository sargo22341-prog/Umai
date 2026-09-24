package org.opensources.umai.cooking.domain

import java.time.Duration

/**
 * Reads the durations written in the text of a step — "cuire 15 min",
 * "1 h 30", "2 heures", "bake for 10 to 12 minutes", "30 secondes" — in French
 * and in English, so the cooking mode can offer a timer for each.
 *
 * A range gives its lower bound: the dish is checked at the first mark.
 */
object StepDurations {

    private const val NUMBER = """(\d+(?:[.,]\d+)?)"""
    private const val RANGE = """(?:\s*(?:à|-|–|—|to|ou|or)\s*\d+(?:[.,]\d+)?)?"""
    private const val MINUTE_UNIT = """(?:minutes?|mins?|mn)"""

    private val pattern = Regex(
        """(?<![\p{L}\d.,/])(?:""" +
            // "1 h", "1h30", "1 heure 30 min", "2 hours and 15 minutes"
            """$NUMBER$RANGE\s*(?:heures?|hours?|hrs?|h)(?:\s*(?:et|and)?\s*(\d{1,2})(?:\s*$MINUTE_UNIT)?)?""" +
            """|$NUMBER$RANGE\s*$MINUTE_UNIT""" +
            """|$NUMBER$RANGE\s*(?:secondes?|seconds?|secs?)""" +
            """)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    private val longest: Duration = Duration.ofHours(24)

    /** The durations of [text], in reading order, each once. */
    fun find(text: String): List<Duration> =
        pattern.findAll(text)
            .mapNotNull { match -> match.toDuration() }
            .filter { !it.isZero && !it.isNegative && it <= longest }
            .distinct()
            .toList()

    private fun MatchResult.toDuration(): Duration? {
        val (hours, extraMinutes, minutes, seconds) = destructured
        return when {
            hours.isNotEmpty() -> hours.amount()?.let { value ->
                Duration.ofSeconds((value * SECONDS_PER_HOUR).toLong())
                    .plusMinutes(extraMinutes.toLongOrNull() ?: 0)
            }
            minutes.isNotEmpty() -> minutes.amount()?.let { Duration.ofSeconds((it * SECONDS_PER_MINUTE).toLong()) }
            seconds.isNotEmpty() -> seconds.amount()?.let { Duration.ofSeconds(it.toLong()) }
            else -> null
        }
    }

    private fun String.amount(): Double? = replace(',', '.').toDoubleOrNull()

    private const val SECONDS_PER_HOUR = 3_600
    private const val SECONDS_PER_MINUTE = 60
}
