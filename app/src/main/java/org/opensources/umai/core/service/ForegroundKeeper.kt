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
 *
 * [start] asks the system to start the service, and throws
 * [ForegroundServiceStartNotAllowedException] when it may not; [name] names
 * it in the log.
 */
class ForegroundKeeper(private val start: () -> Unit, private val name: String) {

    /** Keeps the app alive with [service]. */
    constructor(context: Context, service: Class<out Service>) : this(
        start = {
            // The component it answers is the service itself: nothing more to learn from it.
            val _ = context.applicationContext.startForegroundService(Intent(context.applicationContext, service))
        },
        name = service.simpleName,
    )

    /** Whether the work still needs the app kept alive. */
    @Volatile
    var isKeeping: Boolean = false
        private set

    /** Asked to start, and not stopped since. */
    private var requested = false

    /** Stops the service once it is in the foreground, until it is stopped. */
    private var running: (() -> Unit)? = null

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
            start()
            requested = true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "$name not started from the background: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        isKeeping = false
        running?.let { stop ->
            stop()
            running = null
            requested = false
        }
    }

    /** Called by the service once it runs in the foreground: it stays only while it is needed. */
    fun onForeground(service: Service) = onForeground { service.stopSelf() }

    /** [onForeground], with what stops the service. */
    @Synchronized
    internal fun onForeground(stop: () -> Unit) {
        if (isKeeping) {
            running = stop
        } else {
            stop()
            requested = false
        }
    }

    private companion object {
        const val TAG = "Umai"
    }
}
