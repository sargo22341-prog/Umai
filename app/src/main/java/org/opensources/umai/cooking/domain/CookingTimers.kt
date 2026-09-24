package org.opensources.umai.cooking.domain

import java.time.Duration

/**
 * The recipe a timer belongs to: enough to name it outside the cooking mode
 * and to open the cooking mode again on it, scaled as it was.
 */
data class TimerRecipe(
    val slug: String,
    val name: String,
    /** Servings chosen on the recipe page; `0` keeps the recipe's own count. */
    val servings: Int,
)

/**
 * One cooking timer. Time is read from a monotonic clock in milliseconds that
 * keeps counting while the device sleeps: a running timer knows when it ends
 * ([endsAt]), a paused one how much it had left ([pausedRemaining]).
 */
data class CookingTimer(
    val id: Int,
    val recipe: TimerRecipe,
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
 * Every timer running at once, whatever the recipe. Every change takes the
 * time it happens at, so the whole set is plain state, tested without waiting.
 */
data class CookingTimers(
    val timers: List<CookingTimer> = emptyList(),
    private val nextId: Int = 1,
) {
    val isEmpty: Boolean get() = timers.isEmpty()

    fun start(recipe: TimerRecipe, stepIndex: Int, duration: Duration, now: Long): CookingTimers = copy(
        timers = timers + CookingTimer(
            id = nextId,
            recipe = recipe,
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

    /** The timers still counting down or paused, soonest end first. */
    fun pending(now: Long): List<CookingTimer> =
        timers.filterNot { it.isRunning && it.isFinished(now) }.sortedBy { it.remainingMillis(now) }

    /** When the next running timer reaches zero, `null` when none counts down. */
    fun nextEnd(now: Long): Long? = timers.filter { it.isRunning && !it.isFinished(now) }.mapNotNull { it.endsAt }.minOrNull()

    private fun update(id: Int, change: (CookingTimer) -> CookingTimer) =
        copy(timers = timers.map { if (it.id == id) change(it) else it })
}

/** Rings when a timer reaches zero, in the ways the reader allowed. */
interface TimerAlarm {
    fun start(sound: Boolean, vibrate: Boolean)

    fun stop()
}

/**
 * What the system shows and schedules for the timers outside the app's
 * screens: the notifications, the service that keeps the app alive while a
 * timer runs, and the wake-up at the end of the countdown.
 */
interface TimerHost {
    /** Called after every change; an empty [timers] means there is nothing left to keep alive. */
    fun update(timers: CookingTimers, now: Long)
}
