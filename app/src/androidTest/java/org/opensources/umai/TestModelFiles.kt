package org.opensources.umai

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Models staged with run-as stay readable when instrumentation cannot open external storage. */
internal object TestModelFiles {
    fun pathFor(fileName: String): File? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.filesDir.resolve("models/$fileName").takeIf { it.isFile }
            ?: context.getExternalFilesDir("models")?.resolve(fileName)
    }
}
