package org.opensources.umai.cooking.data

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.opensources.umai.MainActivity
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.TimerHost

/**
 * Keeps the timers alive outside the app's screens, as a timer app does:
 *  - a foreground service runs from the first timer to the last one, so the
 *    app keeps running when it is left or swiped away from the recent apps;
 *  - an alarm clock set on the system wakes the device up when the next timer
 *    ends, even in Doze, where the app's own delays stall;
 *  - the notifications show every timer and act on it.
 */
class SystemTimerHost(
    context: Context,
    private val notifications: TimerNotifications,
) : TimerHost {

    private val context = context.applicationContext
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)
    private val serviceIntent = Intent(this.context, CookingTimerService::class.java)
    private var serviceStarted = false

    /** The timers, and when they were read, that the service shows when it starts. */
    var latest: Pair<CookingTimers, Long> = CookingTimers() to 0L
        private set

    override fun update(timers: CookingTimers, now: Long) {
        latest = timers to now
        scheduleWakeUp(timers.nextEnd(now), now)
        if (timers.isEmpty) {
            if (serviceStarted) context.stopService(serviceIntent)
            serviceStarted = false
            notifications.clear()
            return
        }
        // The first timer is always started from the cooking mode, on screen:
        // the app is in the foreground and may start the service.
        if (!serviceStarted) {
            context.startForegroundService(serviceIntent)
            serviceStarted = true
        }
        notifications.show(timers, now)
    }

    /*
     * Lint only knows SCHEDULE_EXACT_ALARM as the permission behind setAlarmClock
     * (MissingPermission). The app declares USE_EXACT_ALARM instead, which grants
     * exact alarms too, and canScheduleExactAlarms() is checked right before the call.
     */
    @SuppressLint("MissingPermission")
    private fun scheduleWakeUp(nextEnd: Long?, now: Long) {
        val wakeUp = TimerIntents.wakeUp(context)
        if (nextEnd == null) {
            alarmManager.cancel(wakeUp)
            return
        }
        val at = System.currentTimeMillis() + (nextEnd - now)
        // USE_EXACT_ALARM is granted at install; the check only guards against
        // a system that would still withhold it.
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp()), wakeUp)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wakeUp)
        }
    }

    /** What the system opens from its "next alarm" display. */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
