package org.opensources.umai

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Pictures of real products and labels (`app/src/sharedTest/pictures`), which
 * the test APK carries as assets. The app reads pictures by address, as the
 * photo picker hands them over: each is copied to a file of the app first.
 */
object TestPictures {

    fun uriOf(name: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, name)
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use(input::copyTo) }
        return Uri.fromFile(file).toString()
    }
}
