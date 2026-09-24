package org.opensources.umai.cooking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class CookingTimersTest {

    private val fifteen = Duration.ofMinutes(15)
    private val tart = TimerRecipe(slug = "tarte", name = "Tarte", servings = 6)
    private val curry = TimerRecipe(slug = "curry", name = "Curry", servings = 0)

    @Test
    fun `a started timer counts down from its duration`() {
        val timers = CookingTimers().start(tart, stepIndex = 2, duration = fifteen, now = 1_000)
        val timer = timers.timers.single()

        assertEquals(2, timer.stepIndex)
        assertEquals(tart, timer.recipe)
        assertEquals(fifteen.toMillis(), timer.remainingMillis(1_000))
        assertEquals(fifteen.toMillis() - 60_000, timer.remainingMillis(61_000))
        assertTrue(timers.anyRunning(61_000))
    }

    @Test
    fun `several timers run at once, each with its own id, whatever the recipe`() {
        val timers = CookingTimers()
            .start(tart, 0, fifteen, now = 0)
            .start(curry, 1, Duration.ofMinutes(5), now = 0)

        assertEquals(listOf(1, 2), timers.timers.map { it.id })
        assertEquals(listOf("tarte", "curry"), timers.timers.map { it.recipe.slug })
        assertEquals(listOf(2), timers.finished(Duration.ofMinutes(6).toMillis()).map { it.id })
        assertTrue(timers.anyRunning(Duration.ofMinutes(6).toMillis()))
    }

    @Test
    fun `a paused timer keeps what it had left until resumed`() {
        val paused = CookingTimers().start(tart, 0, fifteen, now = 0).pause(1, now = 60_000)
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
        val timers = CookingTimers().start(tart, 0, Duration.ofSeconds(30), now = 0)

        assertEquals(0L, timers.timers.single().remainingMillis(45_000))
        assertEquals(1, timers.finished(45_000).size)
        assertFalse(timers.anyRunning(45_000))
        // A finished timer cannot be paused back into a countdown.
        assertEquals(timers, timers.pause(1, now = 45_000))

        assertTrue(timers.remove(1).finished(45_000).isEmpty())
        assertTrue(timers.remove(1).isEmpty)
    }

    @Test
    fun `the next end is the soonest running timer, paused and finished ones aside`() {
        val timers = CookingTimers()
            .start(tart, 0, Duration.ofSeconds(10), now = 0)
            .start(tart, 1, Duration.ofMinutes(5), now = 0)
            .start(curry, 0, Duration.ofMinutes(1), now = 0)
            .pause(3, now = 1_000)

        assertEquals(10_000L, timers.nextEnd(now = 1_000))
        // The first one has finished: the next wake-up is for the second.
        assertEquals(300_000L, timers.nextEnd(now = 20_000))
        assertNull(timers.pause(2, now = 20_000).nextEnd(now = 20_000))
    }

    @Test
    fun `pending timers are the ones still to come, soonest first`() {
        val timers = CookingTimers()
            .start(tart, 0, Duration.ofMinutes(5), now = 0)
            .start(tart, 1, Duration.ofSeconds(10), now = 0)
            .start(curry, 0, Duration.ofMinutes(2), now = 0)

        assertEquals(listOf(2, 3, 1), timers.pending(now = 0).map { it.id })
        assertEquals(listOf(3, 1), timers.pending(now = 15_000).map { it.id })
    }
}
