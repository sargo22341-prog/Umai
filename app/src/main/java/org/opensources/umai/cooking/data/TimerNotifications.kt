package org.opensources.umai.cooking.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import org.opensources.umai.R
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.TimerFormat

/**
 * The timers as the system shows them, as a timer app would:
 *  - one silent notification per timer counting down or paused, with a live
 *    countdown and its pause, resume and cancel actions;
 *  - one alarm notification per timer that reached zero, popping up with its
 *    stop action while the alarm rings (the sound is played by the app itself);
 *  - a summary grouping the first ones, which is the notification of the
 *    foreground service keeping the app alive while timers run.
 * They show in full on the lock screen, as an alarm clock does, so a timer
 * that rings can be stopped without unlocking the phone.
 */
class TimerNotifications(context: Context) {

    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    /** What is on screen, so what is no longer there can be removed. */
    private var posted = emptySet<Posted>()

    private data class Posted(val tag: String, val timerId: Int)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_RUNNING,
                    this.context.getString(R.string.cooking_timer_channel_running),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = this@TimerNotifications.context.getString(
                        R.string.cooking_timer_channel_running_description,
                    )
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_ALARM,
                    this.context.getString(R.string.cooking_timer_channel_alarm),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = this@TimerNotifications.context.getString(
                        R.string.cooking_timer_channel_alarm_description,
                    )
                    // The alarm already rings and vibrates on its own, as the reader chose.
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            ),
        )
    }

    /** The notification of the foreground service. */
    fun summary(timers: CookingTimers, now: Long): Notification {
        val first = timers.pending(now).firstOrNull() ?: timers.timers.firstOrNull()
        return Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(
                context.resources.getQuantityString(R.plurals.cooking_timers_running, timers.timers.size, timers.timers.size),
            )
            .setContentText(first?.let { describe(it, now) })
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { first?.let { setContentIntent(TimerIntents.openCooking(context, it)) } }
            .build()
    }

    fun show(timers: CookingTimers, now: Long) {
        val finished = timers.finished(now)
        val pending = timers.pending(now)
        val shown = pending.map { Posted(TAG_TIMER, it.id) } + finished.map { Posted(TAG_ALARM, it.id) }

        (posted - shown.toSet()).forEach { manager.cancel(it.tag, it.timerId) }
        manager.notify(SUMMARY_ID, summary(timers, now))
        pending.forEach { manager.notify(TAG_TIMER, it.id, running(it, now)) }
        finished.forEach { manager.notify(TAG_ALARM, it.id, ringing(it)) }
        posted = shown.toSet()
    }

    fun clear() {
        posted.forEach { manager.cancel(it.tag, it.timerId) }
        posted = emptySet()
        manager.cancel(SUMMARY_ID)
    }

    private fun running(timer: CookingTimer, now: Long): Notification {
        val builder = Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(timer.recipe.name)
            .setContentText(describe(timer, now))
            .setGroup(GROUP)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(TimerIntents.openCooking(context, timer))
        if (timer.isRunning) {
            // The system draws the countdown itself, to the second, with no update from the app.
            builder
                .setWhen(System.currentTimeMillis() + timer.remainingMillis(now))
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(action(R.string.cooking_timer_action_pause, TimerIntents.ACTION_PAUSE, timer))
        } else {
            builder
                .setShowWhen(false)
                .addAction(action(R.string.cooking_timer_action_resume, TimerIntents.ACTION_RESUME, timer))
        }
        return builder
            .addAction(action(R.string.action_cancel, TimerIntents.ACTION_DISMISS, timer))
            .build()
    }

    private fun ringing(timer: CookingTimer): Notification =
        Notification.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(context.getString(R.string.cooking_timer_done))
            .setContentText(context.getString(R.string.cooking_timer_of_recipe, timer.recipe.name, label(timer)))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(TimerIntents.openCooking(context, timer))
            // Swiping the alarm away stops it, like its button.
            .setDeleteIntent(TimerIntents.action(context, TimerIntents.ACTION_DISMISS, timer.id))
            .addAction(action(R.string.cooking_timer_stop, TimerIntents.ACTION_DISMISS, timer))
            .build()

    /** "Step 2 · 15 min", or "Step 2 · 15 min · paused, 12:03 left". */
    private fun describe(timer: CookingTimer, now: Long): String = when {
        timer.isRunning && timer.isFinished(now) -> context.getString(R.string.cooking_timer_done)
        timer.isRunning -> label(timer)
        else -> context.getString(R.string.cooking_timer_paused, label(timer), TimerFormat.countdown(timer.remainingMillis(now)))
    }

    private fun label(timer: CookingTimer): String {
        val units = TimerFormat.Units(
            hour = context.getString(R.string.unit_hour_short),
            minute = context.getString(R.string.unit_minute_short),
            second = context.getString(R.string.unit_second_short),
        )
        return context.getString(R.string.cooking_timer_label, timer.stepIndex + 1, TimerFormat.duration(timer.duration, units))
    }

    private fun action(titleRes: Int, action: String, timer: CookingTimer): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification_timer),
            context.getString(titleRes),
            TimerIntents.action(context, action, timer.id),
        ).build()

    companion object {
        const val SUMMARY_ID = 1

        private const val CHANNEL_RUNNING = "cooking_timers"
        private const val CHANNEL_ALARM = "cooking_timer_alarm"
        private const val GROUP = "org.opensources.umai.COOKING_TIMERS"
        private const val TAG_TIMER = "timer"
        private const val TAG_ALARM = "timer_alarm"
    }
}
