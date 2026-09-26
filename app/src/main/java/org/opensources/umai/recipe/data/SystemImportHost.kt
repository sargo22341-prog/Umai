package org.opensources.umai.recipe.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import org.opensources.umai.UmaiApplication
import org.opensources.umai.core.service.ForegroundKeeper
import org.opensources.umai.recipe.domain.RecipeImportRun

/**
 * Keeps an import alive outside the import screen: a foreground service runs
 * from its start to its end, so the app keeps working when it is left, and
 * its notification shows how far the import is.
 */
class SystemImportHost(
    context: Context,
    private val notifications: ImportNotifications,
) : ImportHost {

    private val keeper = ForegroundKeeper(context, RecipeImportService::class.java)

    /** The last run shown, which the service shows when it starts. */
    @Volatile
    var latest: RecipeImportRun? = null
        private set

    /** Whether an import keeps the app alive now, and shows its progress itself. */
    val keepsAppAlive: Boolean get() = keeper.isKeeping

    override fun running(run: RecipeImportRun) {
        latest = run
        notifications.clearEnded()
        // An import is always started from the import screen, on display: the
        // app is in the foreground and may start the service.
        keeper.keep()
        notifications.showRunning(run)
    }

    override fun ended(run: RecipeImportRun, announce: Boolean) {
        stop()
        if (announce) notifications.showEnded(run)
    }

    override fun clear() {
        stop()
        notifications.clearEnded()
    }

    private fun stop() {
        keeper.release()
        notifications.clearRunning()
    }

    /** Called by the service once in the foreground. */
    internal fun onForeground(service: Service) = keeper.onForeground(service)
}

/** The foreground service [SystemImportHost] starts and stops. */
class RecipeImportService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as UmaiApplication).container
        val host = container.importHost
        // Only started once a run is shown; a service asked to run in the foreground must get there.
        val run = checkNotNull(host.latest) { "The import service started without an import" }
        startForeground(ImportNotifications.RUNNING_ID, container.importNotifications.running(run), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // Stops at once when the import ended before the service started.
        host.onForeground(this)
        // An import does not survive the process: nothing to restart.
        return START_NOT_STICKY
    }
}
