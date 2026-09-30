package org.opensources.umai

import android.content.Context
import android.content.Intent

/**
 * The way into the app the notifications take: an alias of [MainActivity]
 * that is not exported (AndroidManifest.xml). MainActivity itself is exported,
 * as the launcher and the target of a share, so any app can send it an intent:
 * a screen is opened on request only when the intent came through this entry,
 * which no other app can reach.
 */
object NotificationEntry {

    /** Resolved against the namespace of the app, as the manifest's `.NotificationEntry`. */
    private const val CLASS_NAME = "org.opensources.umai.NotificationEntry"

    /** An intent to the app, to put in the pending intent of a notification. */
    fun intent(context: Context): Intent = Intent().setClassName(context, CLASS_NAME)

    /** Whether [intent] came from one of the app's notifications. */
    fun isFrom(intent: Intent): Boolean = intent.component?.className == CLASS_NAME
}
