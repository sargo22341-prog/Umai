package org.opensources.umai.recipe.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import org.opensources.umai.MainActivity
import org.opensources.umai.R
import org.opensources.umai.recipe.domain.ImportNotice
import org.opensources.umai.recipe.domain.ImportOutcome
import org.opensources.umai.recipe.domain.ImportPhase
import org.opensources.umai.recipe.domain.RecipeImportRun

/** What a notification of an import asks the app to open. */
sealed interface ImportRequest {
    /** The import screen, where the import runs or tells how it ended. */
    data object OpenImport : ImportRequest

    /** The recipe the import created. */
    data class OpenRecipe(val slug: String) : ImportRequest
}

/**
 * The import as the system shows it: a silent notification with its progress
 * while it runs, which is also the one of the service keeping the app alive,
 * then, when it ends while no import screen is on display, one that says how
 * it ended and opens the recipe, or the import when it failed.
 */
class ImportNotifications(context: Context) {

    private val context = context.applicationContext
    private val manager = this.context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_RUNNING,
                    this.context.getString(R.string.import_channel_running),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = this@ImportNotifications.context.getString(R.string.import_channel_running_description)
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_ENDED,
                    this.context.getString(R.string.import_channel_ended),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = this@ImportNotifications.context.getString(R.string.import_channel_ended_description)
                },
            ),
        )
    }

    /** The notification of a running import, and of the service keeping the app alive meanwhile. */
    fun running(run: RecipeImportRun): Notification {
        val builder = Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification_import)
            .setContentTitle(context.getString(R.string.import_notification_running))
            .setContentText(context.getString(phaseText(run)))
            .setSubText(run.url.toUri().host)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open(ImportRequest.OpenImport))
        val watch = run.watchProgress?.takeIf { run.phase == ImportPhase.WATCHING && it.total > 0 }
        if (watch != null) builder.setProgress(watch.total, watch.done, false) else builder.setProgress(0, 0, true)
        return builder.build()
    }

    fun showRunning(run: RecipeImportRun) = manager.notify(RUNNING_ID, running(run))

    /** How [run] ended, for a reader who did not see it end on screen. */
    fun showEnded(run: RecipeImportRun) {
        val outcome = run.outcome ?: return
        val builder = Notification.Builder(context, CHANNEL_ENDED)
            .setSmallIcon(R.drawable.ic_notification_import)
            .setSubText(run.url.toUri().host)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
        when (outcome) {
            is ImportOutcome.Imported -> builder
                .setContentTitle(context.getString(R.string.import_notification_done))
                .setContentText(context.getString(outcome.recipe.notice?.let(::noticeText) ?: R.string.import_notification_open))
                .setContentIntent(open(ImportRequest.OpenRecipe(outcome.recipe.slug)))
            is ImportOutcome.Duplicate -> builder
                .setContentTitle(context.getString(R.string.import_duplicate_title))
                .setContentText(context.getString(R.string.import_duplicate_message, outcome.existing.name))
                .setContentIntent(open(ImportRequest.OpenImport))
            is ImportOutcome.Failed, is ImportOutcome.VideoFailed, ImportOutcome.VideoEmpty -> builder
                .setContentTitle(context.getString(R.string.import_notification_failed))
                .setContentText(context.getString(R.string.import_notification_failed_open))
                .setContentIntent(open(ImportRequest.OpenImport))
        }
        manager.notify(ENDED_ID, builder.build())
    }

    fun clearRunning() = manager.cancel(RUNNING_ID)

    fun clearEnded() = manager.cancel(ENDED_ID)

    private fun phaseText(run: RecipeImportRun): Int = when (run.phase) {
        ImportPhase.CHECKING -> R.string.import_checking
        ImportPhase.IMPORTING -> R.string.import_running
        ImportPhase.FETCHING_MEDIA -> R.string.import_fetching_media
        ImportPhase.READING_VIDEO -> R.string.import_reading_video
        ImportPhase.WATCHING -> if (run.watchProgress?.seeing == true) R.string.import_watching_pictures else R.string.import_watching_sound
        ImportPhase.UNDERSTANDING -> R.string.import_understanding
        ImportPhase.SAVING -> R.string.import_saving
    }

    private fun noticeText(notice: ImportNotice): Int = when (notice) {
        ImportNotice.MEDIA_FAILED -> R.string.notice_recipe_imported_without_media
        ImportNotice.VIDEO_WITHOUT_MODEL -> R.string.notice_video_without_model
        ImportNotice.VIDEO_MODEL_FAILED -> R.string.notice_video_model_failed
        ImportNotice.VIDEO_NOT_LINKED -> R.string.notice_video_not_linked
        ImportNotice.VIDEO_CAPTIONS_REFUSED -> R.string.notice_video_captions_refused
    }

    /**
     * Brings the app back on what [request] asks for. Explicit and immutable:
     * nothing outside the app can reach or alter it.
     */
    private fun open(request: ImportRequest): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            // The running activity receives it in onNewIntent rather than a second copy opening.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        when (request) {
            ImportRequest.OpenImport -> intent.setAction(ACTION_OPEN_IMPORT)
            is ImportRequest.OpenRecipe -> intent.setAction(ACTION_OPEN_RECIPE).putExtra(EXTRA_SLUG, request.slug)
        }
        return PendingIntent.getActivity(
            context,
            if (request is ImportRequest.OpenImport) RUNNING_ID else ENDED_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val RUNNING_ID = 3_000
        private const val ENDED_ID = 3_001
        private const val CHANNEL_RUNNING = "import_running"
        private const val CHANNEL_ENDED = "import_ended"
        private const val ACTION_OPEN_IMPORT = "org.opensources.umai.action.OPEN_IMPORT"
        private const val ACTION_OPEN_RECIPE = "org.opensources.umai.action.OPEN_IMPORTED_RECIPE"
        private const val EXTRA_SLUG = "slug"

        /** What [intent] asks for, `null` when it is not an import notification's. */
        fun request(intent: Intent): ImportRequest? = when (intent.action) {
            ACTION_OPEN_IMPORT -> ImportRequest.OpenImport
            ACTION_OPEN_RECIPE -> intent.getStringExtra(EXTRA_SLUG)?.let { ImportRequest.OpenRecipe(it) }
            else -> null
        }
    }
}
