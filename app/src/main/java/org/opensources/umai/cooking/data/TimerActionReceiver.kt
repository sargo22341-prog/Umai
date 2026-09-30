package org.opensources.umai.cooking.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.opensources.umai.UmaiApplication

/**
 * Receives the wake-up alarm of the next timer and the actions of the timer
 * notifications, and hands them to the timers of the app. It is not exported:
 * only the app's own pending intents reach it.
 *
 * Either may start the app anew, its process having died meanwhile: the
 * broadcast is kept going until the saved timers are read back and acted on.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timers = (context.applicationContext as UmaiApplication).container.cookingTimers
        val id = intent.getIntExtra(TimerIntents.EXTRA_TIMER_ID, NO_TIMER)
        val pending = goAsync()
        timers.whenRestored {
            try {
                act(timers, intent.action, id)
            } finally {
                pending.finish()
            }
        }
    }

    private fun act(timers: CookingTimerController, action: String?, id: Int) {
        if (action == TimerIntents.ACTION_WAKE_UP) {
            timers.refresh()
            return
        }
        when (TimerAction.of(action)) {
            TimerAction.PAUSE -> timers.pause(id)
            TimerAction.RESUME -> timers.resume(id)
            TimerAction.DISMISS -> timers.dismiss(id)
            // Only the app's own intents reach this receiver: none other is expected.
            null -> Unit
        }
    }

    private companion object {
        const val NO_TIMER = -1
    }
}
