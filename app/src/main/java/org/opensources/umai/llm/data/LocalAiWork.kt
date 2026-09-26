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
import org.opensources.umai.core.service.ForegroundKeeper
import org.opensources.umai.llm.domain.LlmProgress

/**
 * Keeps the app running while the language model works. Reading a video
 * transcript takes minutes on a phone: without a foreground service, leaving
 * the app would freeze it half way. The service runs from the first token to
 * the last, and its notification shows how far the model is.
 *
 * Work may overlap (Whisper and the language model, the planning during an
 * import): the service runs until the last one ends. None runs while
 * [heldElsewhere], when an import keeps the app alive with a notification of
 * its own that already says what the model does.
 */
class LocalAiWork(context: Context, private val heldElsewhere: () -> Boolean) : ModelWork {

    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)
    private val keeper = ForegroundKeeper(this.context, LocalAiService::class.java)
    private var working = 0
    private var lastWritten = -1

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

    @Synchronized
    override fun begin() {
        lastWritten = -1
        if (working++ == 0 && !heldElsewhere()) keeper.keep()
    }

    @Synchronized
    override fun progress(progress: LlmProgress) {
        if (!keeper.isKeeping) return
        // Once when the answer starts, then every few pieces written.
        val step = progress.generated / TOKEN_STEP
        if (step == lastWritten && progress.generated != 1) return
        lastWritten = step
        manager.notify(NOTIFICATION_ID, notification(progress))
    }

    @Synchronized
    override fun end() {
        working = (working - 1).coerceAtLeast(0)
        if (working == 0) keeper.release()
    }

    /** Called by the service once in the foreground. */
    internal fun onForeground(service: Service) = keeper.onForeground(service)

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
                .setProgress(0, 0, true)
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

        /** How often, in pieces written, the notification is refreshed. */
        private const val TOKEN_STEP = 16
    }
}

/** The foreground service [LocalAiWork] starts and stops. */
class LocalAiService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val work = (application as UmaiApplication).container.localAiWork
        startForeground(LocalAiWork.NOTIFICATION_ID, work.notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // Stops at once when the work ended before the service started.
        work.onForeground(this)
        // A generation does not survive the process: nothing to restart.
        return START_NOT_STICKY
    }
}
