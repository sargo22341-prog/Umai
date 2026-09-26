package org.opensources.umai.speech

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.llm.data.ModelWork
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.speech.data.WhisperTranscriber
import org.opensources.umai.speech.domain.SpeechModelCatalog
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Runs whisper.cpp, as built with the app, on the phone: every size of the
 * catalog found in the app's models folder (downloaded from the Local AI
 * screen, or pushed with adb) hears 30 seconds of sound, and the time it takes
 * is logged (`adb logcat -s UmaiAiTest`). Skipped without any model file.
 */
@RunWith(AndroidJUnit4::class)
class WhisperTranscriberTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val noWork = object : ModelWork {
        override fun begin() = Unit

        override fun progress(progress: LlmProgress) = Unit

        override fun end() = Unit
    }

    private fun present(): List<Pair<String, File>> = SpeechModelCatalog.models.mapNotNull { model ->
        context.getExternalFilesDir("models")?.resolve(model.fileName)?.takeIf { it.isFile }?.let { model.name to it }
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun everyWhisperSizeOnThePhoneRunsAndSaysNothingOfATone() = runBlocking {
        val models = present()
        assumeTrue("No Whisper model on the phone", models.isNotEmpty())
        // A 440 Hz tone: sound, but no speech.
        val tone = ShortArray(RATE * SECONDS) { (sin(2 * PI * 440 * it / RATE) * 8_000).toInt().toShort() }
        for ((name, file) in models) {
            val whisper = WhisperTranscriber(modelPath = { file.path }, work = noWork, scope = scope)
            val started = SystemClock.elapsedRealtime()
            val heard = whisper.transcribe(tone, "fr")
            Log.i(TAG, "$name: $SECONDS s heard in ${SystemClock.elapsedRealtime() - started} ms, load included | $heard")
            assertNotNull(heard)
            assertTrue(heard.orEmpty().joinToString(" ") { it.text }.length < MAX_NOISE_CHARS)
        }
    }

    @Test
    fun aFileThatIsNotAModelIsNotRun() = runBlocking {
        val file = File(context.cacheDir, "not-a-model.bin").apply { writeText("nothing") }
        val whisper = WhisperTranscriber(modelPath = { file.path }, work = noWork, scope = scope)

        assertNull(whisper.transcribe(ShortArray(RATE), "fr"))
    }

    private companion object {
        const val TAG = "UmaiAiTest"
        const val RATE = 16_000
        const val SECONDS = 30

        /** A word or two that a small model may still read into a tone. */
        const val MAX_NOISE_CHARS = 40
    }
}
