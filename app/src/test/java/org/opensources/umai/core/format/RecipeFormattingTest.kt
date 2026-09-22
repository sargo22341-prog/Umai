package org.opensources.umai.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DurationTextTest {

    @Test
    fun `free text durations are shown as Mealie stored them`() {
        assertEquals("15 minutes", DurationText.format("15 minutes", "h", "min"))
        assertEquals("1 h 30", DurationText.format("1 h 30", "h", "min"))
    }

    @Test
    fun `iso durations are expanded`() {
        assertEquals("25 min", DurationText.format("PT25M", "h", "min"))
        assertEquals("1 h", DurationText.format("PT1H", "h", "min"))
        assertEquals("1 h 30 min", DurationText.format("PT1H30M", "h", "min"))
    }

    @Test
    fun `blank and zero durations produce nothing`() {
        assertNull(DurationText.format(null, "h", "min"))
        assertNull(DurationText.format("", "h", "min"))
        assertNull(DurationText.format("   ", "h", "min"))
        assertNull(DurationText.format("PT0M", "h", "min"))
    }

    @Test
    fun `iso minutes are exposed for callers that need a number`() {
        assertEquals(90L, DurationText.isoMinutes("PT1H30M"))
        assertNull(DurationText.isoMinutes("15 minutes"))
        assertNull(DurationText.isoMinutes("PTnope"))
    }
}

class QuantityTextTest {

    @Test
    fun `whole numbers lose their decimal part`() {
        assertEquals("2", QuantityText.format(2.0))
        assertEquals("12", QuantityText.format(12.0))
    }

    @Test
    fun `common cooking fractions use their glyph`() {
        assertEquals("½", QuantityText.format(0.5))
        assertEquals("¼", QuantityText.format(0.25))
        assertEquals("¾", QuantityText.format(0.75))
        assertEquals("1½", QuantityText.format(1.5))
    }

    @Test
    fun `uncommon values fall back to a short decimal`() {
        assertEquals("1.1", QuantityText.format(1.1))
    }

    @Test
    fun `missing or zero quantities render as nothing`() {
        assertEquals("", QuantityText.format(null))
        assertEquals("", QuantityText.format(0.0))
    }
}

class ApiDatesTest {

    @Test
    fun `plain dates are parsed`() {
        assertEquals(LocalDate.of(2026, 9, 22), ApiDates.parseDate("2026-09-22"))
    }

    @Test
    fun `timestamps are truncated to their date`() {
        assertEquals(
            LocalDate.of(2026, 9, 13),
            ApiDates.parseDate("2026-09-13T06:16:34.170113+00:00"),
        )
    }

    @Test
    fun `invalid dates yield null instead of throwing`() {
        assertNull(ApiDates.parseDate("not-a-date"))
        assertNull(ApiDates.parseDate(null))
        assertNull(ApiDates.parseDate(""))
    }

    @Test
    fun `dates are formatted the way the meal plan endpoint expects`() {
        assertEquals("2026-09-22", ApiDates.format(LocalDate.of(2026, 9, 22)))
    }

    @Test
    fun `offset timestamps are parsed`() {
        val parsed = ApiDates.parseDateTime("2026-09-13T06:16:34.170113+00:00")
        assertEquals(2026, parsed?.year)
        assertNull(ApiDates.parseDateTime("nope"))
    }
}
