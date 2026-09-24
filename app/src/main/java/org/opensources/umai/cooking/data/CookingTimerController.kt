package org.opensources.umai.cooking.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.TimerAlarm
import org.opensources.umai.cooking.domain.TimerHost
import org.opensources.umai.cooking.domain.TimerRecipe
import org.opensources.umai.core.settings.CookingTimerOptions
import java.time.Duration

/**
 * The cooking timers of the whole app. They outlive the cooking mode: leaving
 * it, or the app, leaves them counting down, shown by the system through
 * [host], and ringing through [alarm] when they reach zero.
 *
 * Confined to the main thread: every call comes from a screen, a notification
 * action or the wake-up alarm, all delivered there, and [scope] runs there too.
 * [clock] is monotonic, in milliseconds, and keeps counting while the device
 * sleeps: a timer must neither jump when the wall clock is changed nor stall
 * while the screen is off.
 */
class CookingTimerController(
    private val scope: CoroutineScope,
    private val alarm: TimerAlarm,
    private val host: TimerHost,
    options: Flow<CookingTimerOptions>,
    private val clock: () -> Long,
) {
    private val _timers = MutableStateFlow(CookingTimers())
    val timers: StateFlow<CookingTimers> = _timers.asStateFlow()

    private var options = CookingTimerOptions()

    /** Finished timers the alarm already rang for: each rings once. */
    private var announced = emptySet<Int>()

    /** When the alarm started ringing, `null` while it is silent. */
    private var ringingSince: Long? = null

    /** Wakes the controller up when the next timer ends or the alarm has rung long enough. */
    private var nextCheck: Job? = null

    init {
        scope.launch { options.collect { this@CookingTimerController.options = it } }
    }

    fun start(recipe: TimerRecipe, stepIndex: Int, duration: Duration) =
        change { timers, now -> timers.start(recipe, stepIndex, duration, now) }

    fun pause(id: Int) = change { timers, now -> timers.pause(id, now) }

    fun resume(id: Int) = change { timers, now -> timers.resume(id, now) }

    /** Cancels a running timer, or silences one that finished. */
    fun dismiss(id: Int) = change { timers, _ -> timers.remove(id) }

    /**
     * Looks at the timers again: the system woke the app up because one should
     * have reached zero, or the notifications were just allowed and can show them.
     */
    fun refresh() = check()

    /** The clock the timers are read against: every second while one counts down. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun ticks(): Flow<Long> = timers.flatMapLatest { timers ->
        flow {
            while (true) {
                val now = clock()
                emit(now)
                if (!timers.anyRunning(now)) break
                delay(TICK_MILLIS - now % TICK_MILLIS)
            }
        }
    }

    private fun change(block: (CookingTimers, Long) -> CookingTimers) {
        _timers.value = block(_timers.value, clock())
        check()
    }

    private fun check() {
        val now = clock()
        val timers = _timers.value
        val finished = timers.finished(now).map { it.id }.toSet()
        val fresh = finished - announced
        // Dismissed timers are forgotten; the ones that just finished are remembered.
        announced = (announced intersect timers.timers.map { it.id }.toSet()) + fresh
        val since = ringingSince
        when {
            fresh.isNotEmpty() && (options.sound || options.vibrate) -> {
                alarm.start(sound = options.sound, vibrate = options.vibrate)
                ringingSince = now
            }
            since != null && (finished.isEmpty() || now - since >= MAX_RING_MILLIS) -> {
                alarm.stop()
                ringingSince = null
            }
        }
        host.update(timers, now)
        scheduleNextCheck(timers, now)
    }

    /**
     * The timers keep counting down between two changes: the controller looks
     * again when the next one ends. The host also asks the system to wake the
     * app up at that moment, as this delay stalls while the device sleeps.
     */
    private fun scheduleNextCheck(timers: CookingTimers, now: Long) {
        nextCheck?.cancel()
        val next = listOfNotNull(timers.nextEnd(now), ringingSince?.plus(MAX_RING_MILLIS)).minOrNull()
        nextCheck = next?.let { at ->
            scope.launch {
                delay(at - now)
                check()
            }
        }
    }

    companion object {
        private const val TICK_MILLIS = 1_000L

        /** A forgotten alarm falls silent after this long; the timer stays shown as finished. */
        const val MAX_RING_MILLIS = 120_000L
    }
}
