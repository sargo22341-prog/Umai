package org.opensources.umai.cooking.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.opensources.umai.UmaiApplication

/**
 * Receives the wake-up alarm of the next timer and the actions of the timer
 * notifications, and hands them to the timers of the app. It is not exported:
 * only the app's own pending intents reach it.
 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val timers = (context.applicationContext as UmaiApplication).container.cookingTimers
        val id = intent.getIntExtra(TimerIntents.EXTRA_TIMER_ID, NO_TIMER)
        when (intent.action) {
            TimerIntents.ACTION_WAKE_UP -> timers.refresh()
            TimerIntents.ACTION_PAUSE -> timers.pause(id)
            TimerIntents.ACTION_RESUME -> timers.resume(id)
            TimerIntents.ACTION_DISMISS -> timers.dismiss(id)
        }
    }

    private companion object {
        const val NO_TIMER = -1
    }
}
