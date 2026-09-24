package org.opensources.umai.cooking.data

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import org.opensources.umai.UmaiApplication

/**
 * Runs while at least one cooking timer exists, so the app is not stopped
 * once it is left: the timers keep counting and still ring. [SystemTimerHost]
 * starts and stops it; its notification is the summary of the timers.
 */
class CookingTimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as UmaiApplication).container
        val (timers, now) = container.timerHost.latest
        startForeground(
            TimerNotifications.SUMMARY_ID,
            container.timerNotifications.summary(timers, now),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        // In-memory timers do not survive the process: nothing to restart.
        return START_NOT_STICKY
    }
}
