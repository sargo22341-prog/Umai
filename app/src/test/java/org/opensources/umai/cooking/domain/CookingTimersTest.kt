package org.opensources.umai.cooking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class CookingTimersTest {

    private val fifteen = Duration.ofMinutes(15)

    @Test
    fun `a started timer counts down from its duration`() {
        val timers = CookingTimers().start(stepIndex = 2, duration = fifteen, now = 1_000)
        val timer = timers.timers.single()

        assertEquals(2, timer.stepIndex)
        assertEquals(fifteen.toMillis(), timer.remainingMillis(1_000))
        assertEquals(fifteen.toMillis() - 60_000, timer.remainingMillis(61_000))
        assertTrue(timers.anyRunning(61_000))
    }

    @Test
    fun `several timers run at once, each with its own id`() {
        val timers = CookingTimers()
            .start(0, fifteen, now = 0)
            .start(1, Duration.ofMinutes(5), now = 0)

        assertEquals(listOf(1, 2), timers.timers.map { it.id })
        assertEquals(listOf(2), timers.finished(Duration.ofMinutes(6).toMillis()).map { it.id })
        assertTrue(timers.anyRunning(Duration.ofMinutes(6).toMillis()))
    }

    @Test
    fun `a paused timer keeps what it had left until resumed`() {
        val paused = CookingTimers().start(0, fifteen, now = 0).pause(1, now = 60_000)
        val timer = paused.timers.single()

        assertFalse(timer.isRunning)
        assertEquals(fifteen.toMillis() - 60_000, timer.remainingMillis(10_000_000))
        assertFalse(paused.anyRunning(10_000_000))

        val resumed = paused.resume(1, now = 100_000)
        assertEquals(fifteen.toMillis() - 60_000, resumed.timers.single().remainingMillis(100_000))
        assertTrue(resumed.anyRunning(100_000))
    }

    @Test
    fun `a timer that reached zero is finished and rings until removed`() {
        val timers = CookingTimers().start(0, Duration.ofSeconds(30), now = 0)

        assertEquals(0L, timers.timers.single().remainingMillis(45_000))
        assertEquals(1, timers.finished(45_000).size)
        assertFalse(timers.anyRunning(45_000))
        // A finished timer cannot be paused back into a countdown.
        assertEquals(timers, timers.pause(1, now = 45_000))

        assertTrue(timers.remove(1).finished(45_000).isEmpty())
    }
}
