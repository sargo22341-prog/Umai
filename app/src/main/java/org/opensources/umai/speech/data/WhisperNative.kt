package org.opensources.umai.speech.data

/**
 * whisper.cpp, built with the app (src/main/cpp). A context is the handle of
 * a loaded model; its segments are those of its last transcription. One
 * context is used by one thread at a time.
 */
internal object WhisperNative {

    init {
        System.loadLibrary("umai_whisper")
    }

    /** 0 when the file is not a model whisper.cpp reads. */
    external fun load(path: String): Long

    /** The number of segments written down, or -1 when the model failed. */
    external fun transcribe(context: Long, samples: FloatArray, language: String, threads: Int): Int

    /** The text of a segment, in UTF-8. */
    external fun segmentText(context: Long, index: Int): ByteArray

    /** In hundredths of a second from the start of the sound. */
    external fun segmentStart(context: Long, index: Int): Long

    external fun segmentEnd(context: Long, index: Int): Long

    /** How likely the segment is to be noise or music rather than speech, from 0 to 1. */
    external fun segmentNoSpeech(context: Long, index: Int): Float

    external fun free(context: Long)
}
