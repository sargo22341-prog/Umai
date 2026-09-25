package org.opensources.umai.llm.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import org.opensources.umai.MainActivity
import org.opensources.umai.R
import org.opensources.umai.UmaiApplication
import org.opensources.umai.llm.domain.LlmProgress

/**
 * Keeps the app running while the language model works. Reading a video
 * transcript takes minutes on a phone: without a foreground service, leaving
 * the app would freeze it half way. The service runs from the first token to
 * the last, and its notification shows how far the model is.
 */
class LocalAiWork(context: Context) {

    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private val serviceIntent = Intent(this.context, LocalAiService::class.java)
    private var running = false
    private var lastPercent = -1

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                this.context.getString(R.string.local_ai_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = this@LocalAiWork.context.getString(R.string.local_ai_channel_description)
                setShowBadge(false)
            },
        )
    }

    /** Called from a screen on display: the app is in the foreground and may start the service. */
    @Synchronized
    fun begin() {
        lastPercent = -1
        if (!running) {
            context.startForegroundService(serviceIntent)
            running = true
        }
    }

    @Synchronized
    fun progress(progress: LlmProgress) {
        if (!running) return
        val percent = if (progress.readingPrompt) (progress.promptFraction * 100).toInt() else 100
        if (percent == lastPercent && !(progress.generated > 0 && progress.generated % TOKEN_STEP == 0)) return
        lastPercent = percent
        manager.notify(NOTIFICATION_ID, notification(progress))
    }

    @Synchronized
    fun end() {
        if (!running) return
        context.stopService(serviceIntent)
        running = false
    }

    fun notification(progress: LlmProgress? = null): Notification {
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle(context.getString(R.string.local_ai_working))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        when {
            progress == null -> builder.setProgress(0, 0, true)
            progress.readingPrompt -> builder
                .setContentText(context.getString(R.string.local_ai_reading))
                .setProgress(progress.promptTotal, progress.promptRead, false)
            else -> builder
                .setContentText(
                    context.resources.getQuantityString(R.plurals.local_ai_writing, progress.generated, progress.generated),
                )
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        const val NOTIFICATION_ID = 2_000
        private const val CHANNEL = "local_ai"

        /** How often, in tokens written, the notification is refreshed. */
        private const val TOKEN_STEP = 16
    }
}

/** The foreground service [LocalAiWork] starts and stops. */
class LocalAiService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val work = (application as UmaiApplication).container.localAiWork
        startForeground(LocalAiWork.NOTIFICATION_ID, work.notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // A generation does not survive the process: nothing to restart.
        return START_NOT_STICKY
    }
}
