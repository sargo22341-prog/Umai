package org.opensources.umai.cooking.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.TimerAlarm
import org.opensources.umai.cooking.domain.TimerHost
import org.opensources.umai.cooking.domain.TimerRecipe
import org.opensources.umai.core.settings.CookingTimerOptions
import java.time.Duration

/**
 * The controller keeps the timers of the whole app. It runs on virtual time:
 * the clock is the test scheduler's, so a 15-minute timer is checked at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CookingTimerControllerTest {

    private val tart = TimerRecipe(slug = "tarte", name = "Tarte", servings = 6)
    private val alarm = RecordingAlarm()
    private val host = RecordingHost()

    private fun TestScope.controller(
        options: CookingTimerOptions = CookingTimerOptions(sound = true, vibrate = false),
        clock: () -> Long = { testScheduler.currentTime },
    ): CookingTimerController = CookingTimerController(
        scope = backgroundScope,
        alarm = alarm,
        host = host,
        options = MutableStateFlow(options),
        clock = clock,
    ).also { runCurrent() }

    @Test
    fun `a timer that reaches zero rings once, and stopping it silences the alarm`() = runTest {
        val timers = controller()
        timers.start(tart, stepIndex = 2, duration = Duration.ofSeconds(30))
        timers.start(tart, stepIndex = 3, duration = Duration.ofMinutes(15))

        advanceTimeBy(29_000)
        assertTrue(alarm.calls.isEmpty())

        advanceTimeBy(2_000)
        assertEquals(listOf("start sound=true vibrate=false"), alarm.calls)
        assertEquals(listOf(1), host.last.finished(testScheduler.currentTime).map { it.id })

        timers.dismiss(1)
        assertEquals(listOf("start sound=true vibrate=false", "stop"), alarm.calls)
        // The other timer keeps counting down, and is still shown by the system.
        assertEquals(listOf(2), timers.timers.value.timers.map { it.id })
        assertEquals(listOf(2), host.last.timers.map { it.id })
    }

    @Test
    fun `a forgotten alarm falls silent, the timer staying shown as finished`() = runTest {
        val timers = controller()
        timers.start(tart, 0, Duration.ofSeconds(10))

        advanceTimeBy(11_000)
        assertEquals(1, alarm.calls.size)

        advanceTimeBy(CookingTimerController.MAX_RING_MILLIS)
        assertEquals(listOf("start sound=true vibrate=false", "stop"), alarm.calls)
        assertEquals(1, timers.timers.value.finished(testScheduler.currentTime).size)
    }

    @Test
    fun `a silent timer finishes without ringing`() = runTest {
        val timers = controller(options = CookingTimerOptions(sound = false, vibrate = false))
        timers.start(tart, 0, Duration.ofSeconds(5))

        advanceTimeBy(6_000)

        assertTrue(alarm.calls.isEmpty())
        assertEquals(1, host.last.finished(testScheduler.currentTime).size)
    }

    @Test
    fun `the wake-up of the system rings a timer the sleeping device did not see end`() = runTest {
        // While the device sleeps the app's own delays stall; only the clock moves.
        var now = 0L
        val timers = controller(clock = { now })
        timers.start(tart, 0, Duration.ofSeconds(30))

        now = 31_000
        timers.refresh()

        assertEquals(listOf("start sound=true vibrate=false"), alarm.calls)
    }

    @Test
    fun `the system is told about every change, down to the last timer`() = runTest {
        val timers = controller()
        timers.start(tart, 1, Duration.ofMinutes(5))
        assertEquals(1, host.last.timers.size)
        assertEquals(tart, host.last.timers.single().recipe)

        timers.pause(1)
        assertTrue(!host.last.timers.single().isRunning)
        timers.resume(1)
        assertTrue(host.last.timers.single().isRunning)

        timers.dismiss(1)
        assertTrue(host.last.isEmpty)
    }

    @Test
    fun `a paused timer never rings`() = runTest {
        val timers = controller()
        timers.start(tart, 0, Duration.ofSeconds(10))
        advanceTimeBy(5_000)
        timers.pause(1)

        advanceTimeBy(60_000)

        assertTrue(alarm.calls.isEmpty())
        assertEquals(5_000L, timers.timers.value.timers.single().remainingMillis(testScheduler.currentTime))
    }

    @Test
    fun `the clock ticks every second while a timer counts down, then stops`() = runTest {
        val timers = controller()
        timers.start(tart, 0, Duration.ofSeconds(2))

        val ticks = timers.ticks().take(3).toList()

        assertEquals(listOf(0L, 1_000L, 2_000L), ticks)
    }
}

/** Records what the controller asks of the alarm. */
private class RecordingAlarm : TimerAlarm {
    val calls = mutableListOf<String>()

    override fun start(sound: Boolean, vibrate: Boolean) {
        calls += "start sound=$sound vibrate=$vibrate"
    }

    override fun stop() {
        calls += "stop"
    }
}

/** Keeps the last timers the controller handed to the system. */
private class RecordingHost : TimerHost {
    var last = CookingTimers()

    override fun update(timers: CookingTimers, now: Long) {
        last = timers
    }
}
