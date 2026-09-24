package org.opensources.umai.cooking.domain

import java.time.Duration

/**
 * One timer of the cooking mode. Time is read from a monotonic clock in
 * milliseconds: a running timer knows when it ends ([endsAt]), a paused one
 * how much it had left ([pausedRemaining]).
 */
data class CookingTimer(
    val id: Int,
    /** The step the timer was started from, `0` for the first one. */
    val stepIndex: Int,
    val duration: Duration,
    val endsAt: Long?,
    val pausedRemaining: Long?,
) {
    val isRunning: Boolean get() = endsAt != null

    fun remainingMillis(now: Long): Long = (endsAt?.let { it - now } ?: pausedRemaining ?: 0L).coerceAtLeast(0L)

    fun isFinished(now: Long): Boolean = remainingMillis(now) == 0L
}

/**
 * The timers running at once in the cooking mode. Every change takes the time
 * it happens at, so the whole set is plain state, tested without waiting.
 */
data class CookingTimers(
    val timers: List<CookingTimer> = emptyList(),
    private val nextId: Int = 1,
) {
    fun start(stepIndex: Int, duration: Duration, now: Long): CookingTimers = copy(
        timers = timers + CookingTimer(
            id = nextId,
            stepIndex = stepIndex,
            duration = duration,
            endsAt = now + duration.toMillis(),
            pausedRemaining = null,
        ),
        nextId = nextId + 1,
    )

    fun pause(id: Int, now: Long): CookingTimers = update(id) { timer ->
        if (!timer.isRunning || timer.isFinished(now)) timer
        else timer.copy(endsAt = null, pausedRemaining = timer.remainingMillis(now))
    }

    fun resume(id: Int, now: Long): CookingTimers = update(id) { timer ->
        val left = timer.pausedRemaining
        if (timer.isRunning || left == null || left == 0L) timer
        else timer.copy(endsAt = now + left, pausedRemaining = null)
    }

    fun remove(id: Int): CookingTimers = copy(timers = timers.filterNot { it.id == id })

    /** Whether a timer still counts down, so the clock has to keep ticking. */
    fun anyRunning(now: Long): Boolean = timers.any { it.isRunning && !it.isFinished(now) }

    /** The timers that reached zero: they ring until dismissed. */
    fun finished(now: Long): List<CookingTimer> = timers.filter { it.isRunning && it.isFinished(now) }

    private fun update(id: Int, change: (CookingTimer) -> CookingTimer) =
        copy(timers = timers.map { if (it.id == id) change(it) else it })
}

/** Rings when a timer reaches zero, in the ways the reader allowed. */
interface TimerAlarm {
    fun start(sound: Boolean, vibrate: Boolean)

    fun stop()
}
