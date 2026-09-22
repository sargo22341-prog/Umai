package org.opensources.umai.core.format

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Mealie stores durations as free text ("15 minutes", "PT1H30M", "1 h"), so the
 * value is shown as typed unless it is an ISO-8601 duration, which is expanded
 * into readable units.
 */
object DurationText {

    /** Returns minutes for an ISO-8601 duration, or `null` for free text. */
    fun isoMinutes(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (!value.startsWith("PT", ignoreCase = true)) return null
        return runCatching { Duration.parse(value).toMinutes() }.getOrNull()
    }

    /**
     * [hourUnit] and [minuteUnit] come from string resources so the result is
     * localized by the caller.
     */
    fun format(raw: String?, hourUnit: String, minuteUnit: String): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val minutes = isoMinutes(value) ?: return value
        if (minutes <= 0) return null
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 -> "$hours $hourUnit $rest $minuteUnit"
            hours > 0 -> "$hours $hourUnit"
            else -> "$rest $minuteUnit"
        }
    }
}

/** Mealie sends dates as `yyyy-MM-dd` and timestamps as ISO-8601 with offset. */
object ApiDates {

    fun parseDate(raw: String?): LocalDate? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                LocalDate.parse(it.take(10))
            } catch (_: DateTimeParseException) {
                null
            }
        }

    fun parseDateTime(raw: String?): OffsetDateTime? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { OffsetDateTime.parse(it) }.getOrNull()
        }

    fun format(date: LocalDate): String = date.toString()
}

/**
 * Quantities arrive as doubles. Whole values are shown without a decimal part,
 * and the common cooking fractions are rendered with their typographic glyph.
 */
object QuantityText {

    private val fractions = mapOf(
        0.125 to "⅛", 0.25 to "¼", 0.333 to "⅓", 0.375 to "⅜",
        0.5 to "½", 0.625 to "⅝", 0.666 to "⅔", 0.75 to "¾",
        0.875 to "⅞",
    )

    fun format(quantity: Double?): String {
        val value = quantity ?: return ""
        if (value <= 0.0) return ""
        val whole = value.toLong()
        val remainder = value - whole
        val glyph = fractions.entries.firstOrNull { kotlin.math.abs(it.key - remainder) < 0.02 }?.value
        return when {
            remainder < 0.005 -> whole.toString()
            glyph != null && whole > 0L -> "$whole$glyph"
            glyph != null -> glyph
            else -> trimTrailingZeros(value)
        }
    }

    private fun trimTrailingZeros(value: Double): String {
        val text = String.format(java.util.Locale.ROOT, "%.2f", value)
        return text.trimEnd('0').trimEnd('.', ',')
    }
}
