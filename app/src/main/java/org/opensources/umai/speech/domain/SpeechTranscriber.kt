package org.opensources.umai.speech.domain

/** What is said from [start] to [end], in seconds from the start of the sound given. */
data class SpokenText(val start: Double, val end: Double, val text: String)

/** Writes down speech on the phone. */
interface SpeechTranscriber {

    /** Whether a speech model is installed. */
    suspend fun isReady(): Boolean

    /**
     * What is said in [samples], 16 kHz mono sound, in [language] ("fr"), or
     * in the language it detects when `null`. Empty when nothing is said;
     * `null` when the model could not run.
     */
    suspend fun transcribe(samples: ShortArray, language: String?): List<SpokenText>?
}
