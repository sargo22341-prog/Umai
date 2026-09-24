package org.opensources.umai.cooking.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class TimerFormatTest {

    private val units = TimerFormat.Units(hour = "h", minute = "min", second = "s")

    @Test
    fun `a duration reads as it is written in a recipe`() {
        assertEquals("15 min", TimerFormat.duration(Duration.ofMinutes(15), units))
        assertEquals("1 h 30", TimerFormat.duration(Duration.ofMinutes(90), units))
        assertEquals("1 h", TimerFormat.duration(Duration.ofHours(1), units))
        assertEquals("1 min 30 s", TimerFormat.duration(Duration.ofSeconds(90), units))
        assertEquals("45 s", TimerFormat.duration(Duration.ofSeconds(45), units))
    }

    @Test
    fun `the countdown is rounded up to the second`() {
        assertEquals("15:00", TimerFormat.countdown(900_000))
        assertEquals("14:59", TimerFormat.countdown(898_001))
        assertEquals("0:07", TimerFormat.countdown(6_200))
        assertEquals("1:05:00", TimerFormat.countdown(3_900_000))
        assertEquals("0:00", TimerFormat.countdown(0))
    }
}
