package org.opensources.umai.speech.data

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.opensources.umai.llm.data.ModelWork
import org.opensources.umai.speech.domain.SpeechTranscriber
import org.opensources.umai.speech.domain.SpokenText
import java.util.Locale

/**
 * Writes down speech with whisper.cpp on the CPU. The model stays loaded
 * while pieces of a video come in, and is freed a minute after the last one;
 * while it writes, the app is kept running by [work], as for the language model.
 */
class WhisperTranscriber(
    private val modelPath: suspend () -> String?,
    private val work: ModelWork,
    private val scope: CoroutineScope,
) : SpeechTranscriber {

    private val mutex = Mutex()
    private var context = 0L
    private var loadedPath: String? = null
    private var unloadJob: Job? = null

    override suspend fun isReady(): Boolean = modelPath() != null

    override suspend fun transcribe(samples: ShortArray, language: String?): List<SpokenText>? = mutex.withLock {
        val path = modelPath() ?: return@withLock null
        unloadJob?.cancel()
        work.begin()
        try {
            val handle = withContext(Dispatchers.Default) { load(path) } ?: return@withLock null
            transcribeCancellably(handle, samples, language)
        } finally {
            work.end()
            scheduleUnload()
        }
    }

    /**
     * whisper.cpp blocks the thread it runs on until the piece is written
     * down: it runs on a worker while this coroutine waits. Cancelling the
     * wait raises the abort flag of [handle]. whisper.cpp reads it between two
     * steps — once the 30-second window is encoded, then at every token — so
     * the encoding under way ends first, and the decoding is skipped. The
     * model is used again only once it has returned.
     */
    private suspend fun transcribeCancellably(handle: Long, samples: ShortArray, language: String?): List<SpokenText>? =
        coroutineScope {
            WhisperNative.setAborted(handle, false)
            val transcription = async(Dispatchers.Default) { run(handle, samples, language) }
            try {
                transcription.await()
            } catch (cause: CancellationException) {
                WhisperNative.setAborted(handle, true)
                throw cause
            }
        }

    private fun run(handle: Long, samples: ShortArray, language: String?): List<SpokenText>? {
        val started = SystemClock.elapsedRealtime()
        val sound = FloatArray(samples.size) { samples[it] / SAMPLE_SCALE }
        val count = WhisperNative.transcribe(handle, sound, language ?: AUTO_LANGUAGE, THREADS)
        if (Log.isLoggable(TAG, Log.INFO)) {
            val elapsed = SystemClock.elapsedRealtime() - started
            Log.i(TAG, "Whisper: %.1f s heard in %d ms, %d segments".format(Locale.ROOT, samples.size / RATE, elapsed, count))
        }
        if (count < 0) return null
        return (0 until count).mapNotNull { index ->
            if (WhisperNative.segmentNoSpeech(handle, index) > MAX_NO_SPEECH) return@mapNotNull null
            val text = String(WhisperNative.segmentText(handle, index), Charsets.UTF_8).trim()
            if (text.isEmpty()) return@mapNotNull null
            SpokenText(
                start = WhisperNative.segmentStart(handle, index) / CENTISECONDS,
                end = WhisperNative.segmentEnd(handle, index) / CENTISECONDS,
                text = text,
            )
        }
    }

    /** The loaded model for [path], loading it if needed; null when the file cannot be read. */
    private fun load(path: String): Long? {
        if (context != 0L && loadedPath == path) return context
        unload()
        val handle = WhisperNative.load(path)
        if (handle == 0L) {
            Log.w(TAG, "Whisper could not load $path")
            return null
        }
        context = handle
        loadedPath = path
        return handle
    }

    private fun unload() {
        if (context == 0L) return
        WhisperNative.free(context)
        context = 0L
        loadedPath = null
    }

    private fun scheduleUnload() {
        unloadJob?.cancel()
        unloadJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock { withContext(Dispatchers.Default) { unload() } }
        }
    }

    private companion object {
        const val TAG = "UmaiAi"
        const val IDLE_UNLOAD_MS = 60_000L
        const val AUTO_LANGUAGE = "auto"

        /**
         * Measured on a Pixel 10, on a minute of speech: Small took 43 s with
         * two threads, 62 to 68 s with four, 71 to 104 s with six. ggml's
         * threads wait for one another at every step, and the more there are,
         * the more land on slower cores. Two is also the big cores of a Tensor G1:
         * on a Pixel 6 Pro, 109 s of speech took 117 s with two, 130 s with four.
         */
        const val THREADS = 2

        /** Above this, whisper.cpp itself takes a segment for music or noise. */
        const val MAX_NO_SPEECH = 0.6f

        const val SAMPLE_SCALE = 32_768f
        const val RATE = 16_000.0
        const val CENTISECONDS = 100.0
    }
}
