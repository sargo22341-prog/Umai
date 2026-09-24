package org.opensources.umai.cooking.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.opensources.umai.MainActivity
import org.opensources.umai.cooking.domain.CookingTimer

/** A cooking mode to open at a given step, asked for by a timer notification. */
data class CookingStepRequest(val slug: String, val servings: Int, val step: Int)

/**
 * The intents behind the timer notifications and the wake-up alarm. Every one
 * is explicit and immutable: nothing outside the app can reach or alter them.
 */
object TimerIntents {

    const val ACTION_WAKE_UP = "org.opensources.umai.action.TIMER_WAKE_UP"
    const val ACTION_PAUSE = "org.opensources.umai.action.TIMER_PAUSE"
    const val ACTION_RESUME = "org.opensources.umai.action.TIMER_RESUME"
    const val ACTION_DISMISS = "org.opensources.umai.action.TIMER_DISMISS"
    const val EXTRA_TIMER_ID = "timer_id"

    private const val ACTION_OPEN_COOKING = "org.opensources.umai.action.OPEN_COOKING"
    private const val EXTRA_SLUG = "slug"
    private const val EXTRA_SERVINGS = "servings"
    private const val EXTRA_STEP = "step"

    /** Wakes the app up when the next timer reaches zero. */
    fun wakeUp(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimerActionReceiver::class.java).setAction(ACTION_WAKE_UP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** One notification action on one timer; each pair gets its own request code. */
    fun action(context: Context, action: String, timerId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(timerId, action),
        Intent(context, TimerActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TIMER_ID, timerId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Brings the app back on the cooking mode of [timer], at the step it was started from. */
    fun openCooking(context: Context, timer: CookingTimer): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode(timer.id, ACTION_OPEN_COOKING),
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_COOKING)
            .putExtra(EXTRA_SLUG, timer.recipe.slug)
            .putExtra(EXTRA_SERVINGS, timer.recipe.servings)
            .putExtra(EXTRA_STEP, timer.stepIndex)
            // The running activity receives it in onNewIntent rather than a second copy opening.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The cooking mode [intent] asks for, `null` when it is not a timer's. */
    fun cookingStep(intent: Intent): CookingStepRequest? {
        if (intent.action != ACTION_OPEN_COOKING) return null
        val slug = intent.getStringExtra(EXTRA_SLUG) ?: return null
        return CookingStepRequest(
            slug = slug,
            servings = intent.getIntExtra(EXTRA_SERVINGS, 0),
            step = intent.getIntExtra(EXTRA_STEP, 0),
        )
    }

    private fun requestCode(timerId: Int, action: String): Int = timerId * ACTIONS + when (action) {
        ACTION_PAUSE -> 1
        ACTION_RESUME -> 2
        ACTION_DISMISS -> 3
        else -> 4
    }

    private const val ACTIONS = 5
}
