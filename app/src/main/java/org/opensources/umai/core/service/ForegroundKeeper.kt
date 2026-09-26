package org.opensources.umai.core.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Runs a foreground service while some work needs the app kept alive, and
 * stops it after. The system kills an app whose service, started to run in
 * the foreground, stops before it got there: a stop asked for before the
 * service started is left to the service, which reaches the foreground first,
 * then stops itself ([onForeground]).
 */
class ForegroundKeeper(context: Context, service: Class<out Service>) {

    private val context = context.applicationContext
    private val intent = Intent(this.context, service)

    /** Whether the work still needs the app kept alive. */
    @Volatile
    var isKeeping: Boolean = false
        private set

    /** Asked to start, and not stopped since. */
    private var requested = false

    /** The service once in the foreground, until it is stopped. */
    private var running: Service? = null

    /**
     * Keeps the app alive from now on. The system only lets an app start such
     * a service from the foreground: work started in the background goes on
     * without it, as it would have before.
     */
    @Synchronized
    fun keep() {
        isKeeping = true
        if (requested) return
        try {
            context.startForegroundService(intent)
            requested = true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "${intent.component?.shortClassName} not started from the background: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        isKeeping = false
        running?.let {
            it.stopSelf()
            running = null
            requested = false
        }
    }

    /** Called by the service once it runs in the foreground: it stays only while it is needed. */
    @Synchronized
    fun onForeground(service: Service) {
        if (isKeeping) {
            running = service
        } else {
            service.stopSelf()
            requested = false
        }
    }

    private companion object {
        const val TAG = "Umai"
    }
}
