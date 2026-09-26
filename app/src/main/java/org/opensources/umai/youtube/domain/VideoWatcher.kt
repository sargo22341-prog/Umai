package org.opensources.umai.youtube.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmMedia
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmRequest
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** A piece of the sound track, from [start] to [end] seconds, as a WAV file of 16 kHz mono sound. */
class SoundPiece(val start: Double, val end: Double, val wav: ByteArray)

/** A picture of the video at [second], as a JPEG. */
class VideoPicture(val second: Double, val jpeg: ByteArray)

/** Reads the sound and the pictures of a video, on the phone. */
interface VideoMedia {

    /** The sound at [url] in pieces of [pieceSeconds], up to [maxSeconds]. */
    fun sound(url: String, pieceSeconds: Int, maxSeconds: Int): Flow<SoundPiece>

    /** Pictures of the video at [url] at each of [seconds]; one that cannot be read is skipped. */
    fun pictures(url: String, seconds: List<Double>): Flow<VideoPicture>
}

/** How far watching a video is: [done] of [total] pieces heard, or pictures seen when [seeing]. */
data class WatchProgress(val seeing: Boolean, val done: Int, val total: Int)

/**
 * Gives the language model the times it places the steps with when YouTube
 * gives none, as yt-transcript and pick-a-recipe do with Whisper and a
 * vision model — here on the phone, with the model already installed:
 *
 * - a video without captions, or whose captions YouTube refused, is listened
 *   to: its original sound track, in pieces of [PIECE_SECONDS], written down;
 * - a video where nothing is said and that has no chapters is looked at: one
 *   picture every [MIN_PICTURE_GAP] seconds, or fewer on a long video, each described.
 *
 * Each piece becomes a timed line of the transcript, which the recipe is then
 * rebuilt from as from captions. A model without audio or vision part, or a
 * sound or a picture file that cannot be read, leaves the video as it was.
 */
class VideoWatcher(private val model: LanguageModel, private val media: VideoMedia) {

    /** [language] is the one the pictures are described in: "fr" or "en". */
    suspend fun complete(video: YouTubeVideo, language: String, onProgress: (WatchProgress) -> Unit): YouTubeVideo {
        var watched = video
        val soundUrl = video.soundUrl
        if (video.transcript.isEmpty() && soundUrl != null) {
            val heard = hear(video, soundUrl, onProgress)
            if (heard.isNotEmpty()) watched = watched.copy(transcript = heard, transcriptSource = TranscriptSource.HEARD)
        }
        val pictureUrl = video.pictureUrl
        if (!watched.hasTimes && pictureUrl != null) {
            val seen = see(video, pictureUrl, language, onProgress)
            if (seen.isNotEmpty()) watched = watched.copy(transcript = seen, transcriptSource = TranscriptSource.SEEN)
        }
        return watched
    }

    private suspend fun hear(video: YouTubeVideo, url: String, onProgress: (WatchProgress) -> Unit): List<TranscriptCue> {
        val seconds = minOf(video.durationSeconds, MAX_HEARD_SECONDS)
        val total = ceil(seconds.toDouble() / PIECE_SECONDS).toInt()
        val request = hearing(video.spokenLanguage)
        val cues = mutableListOf<TranscriptCue>()
        var done = 0
        var stopped = false
        onProgress(WatchProgress(seeing = false, done = 0, total = total))
        media.sound(url, PIECE_SECONDS, seconds)
            .takeWhile { !stopped }
            // A sound file that stops being readable leaves what was heard so far.
            .catch { }
            .collect { piece ->
                when (val outcome = model.generate(request.copy(media = LlmMedia.Sound(piece.wav)))) {
                    is LlmOutcome.Success -> field(outcome.text, SPEECH)?.let { cues += TranscriptCue(piece.start, piece.end, it) }
                    is LlmOutcome.Failure -> stopped = true
                }
                onProgress(WatchProgress(seeing = false, done = ++done, total = total))
            }
        return cues
    }

    private suspend fun see(
        video: YouTubeVideo,
        url: String,
        language: String,
        onProgress: (WatchProgress) -> Unit,
    ): List<TranscriptCue> {
        val duration = video.durationSeconds.toDouble()
        if (duration <= 0) return emptyList()
        // Each picture stands for the stretch of video around it.
        val gap = max(MIN_PICTURE_GAP, duration / MAX_PICTURES)
        val windows = generateSequence(0.0) { it + gap }.takeWhile { it < duration }.toList()
        val request = seeing(language)
        val cues = mutableListOf<TranscriptCue>()
        var done = 0
        var stopped = false
        onProgress(WatchProgress(seeing = true, done = 0, total = windows.size))
        media.pictures(url, windows.map { minOf(it + gap / 2, duration - 1) })
            .takeWhile { !stopped }
            .catch { }
            .collect { picture ->
                when (val outcome = model.generate(request.copy(media = LlmMedia.Picture(picture.jpeg)))) {
                    is LlmOutcome.Success -> field(outcome.text, SHOWN)?.let { shown ->
                        val start = windows.lastOrNull { it <= picture.second } ?: 0.0
                        cues += TranscriptCue(start, minOf(start + gap, duration), shown)
                    }
                    is LlmOutcome.Failure -> stopped = true
                }
                onProgress(WatchProgress(seeing = true, done = ++done, total = windows.size))
            }
        return cues
    }

    companion object {
        /** The longest the model hears at once. */
        const val PIECE_SECONDS = 30

        /** About seven minutes of listening on a Pixel 10: longer videos are heard up to there. */
        const val MAX_HEARD_SECONDS = 20 * 60

        /** About twelve seconds a picture on a Pixel 10: a long video is looked at more sparsely. */
        const val MIN_PICTURE_GAP = 20.0
        const val MAX_PICTURES = 30

        private const val SPEECH = "speech"
        private const val SHOWN = "shown"
        private const val MAX_SPEECH_TOKENS = 400
        private const val MAX_SHOWN_TOKENS = 120

        private val json = Json { ignoreUnknownKeys = true }

        internal fun hearing(spokenLanguage: String?): LlmRequest {
            val spoken = spokenLanguage?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }?.takeIf { it.isNotBlank() }
            return LlmRequest(
                system = """
                    You write down what is said in a piece of the sound track of a cooking video, word for word, in the language spoken. Write only what is said: music and noises are not speech, and nothing is added. When nobody speaks, the speech is empty.
                """.trimIndent(),
                user = if (spoken != null) "Write down what is said. The video is in $spoken." else "Write down what is said.",
                jsonSchema = schema(SPEECH, maxLength = 1_500),
                maxTokens = MAX_SPEECH_TOKENS,
            )
        }

        internal fun seeing(language: String): LlmRequest {
            val writeIn = if (language == "fr") "French" else "English"
            return LlmRequest(
                system = """
                    You describe pictures of a cooking video, to find where each step of the recipe is done. In one short sentence, say the cooking action the picture shows, naming the ingredients and utensils you see. Copy any recipe text written on the picture: ingredients, quantities, temperatures, times; leave out logos, channel names and calls to subscribe. When the picture shows no cooking, the action is empty. Write in $writeIn.
                """.trimIndent(),
                user = "Describe this picture.",
                jsonSchema = schema(SHOWN, maxLength = 400),
                maxTokens = MAX_SHOWN_TOKENS,
            )
        }

        private fun schema(field: String, maxLength: Int) = """
            {"type": "object", "properties": {"$field": {"type": "string", "maxLength": $maxLength}}, "required": ["$field"], "additionalProperties": false}
        """.trimIndent()

        /** The text of [key] in the answer, `null` when empty. */
        internal fun field(answer: String, key: String): String? {
            val root = runCatching { json.parseToJsonElement(answer) }.getOrNull() as? JsonObject ?: return null
            return (root[key] as? JsonPrimitive)?.contentOrNull?.let(Transcript::clean)?.takeIf { it.isNotEmpty() }
        }
    }
}

/** Sound as the model hears it: 16 kHz, one channel, 16-bit samples. */
object SpeechSound {

    const val RATE = 16_000

    /**
     * [samples] of one channel at [fromRate], brought to [RATE]. Each output
     * sample is the mean of the input ones it covers, which keeps the sounds
     * too high for 16 kHz from folding back into speech as noise.
     */
    fun resample(samples: ShortArray, fromRate: Int): ShortArray {
        if (fromRate == RATE) return samples
        val step = fromRate.toDouble() / RATE
        val count = (samples.size / step).toInt()
        return ShortArray(count) { index ->
            val from = (index * step).toInt()
            val to = minOf(samples.size, ((index + 1) * step).toInt()).coerceAtLeast(from + 1)
            var sum = 0L
            for (k in from until to) sum += samples[k]
            (sum / (to - from)).toInt().toShort()
        }
    }

    /** A WAV file of [samples] at [RATE]. */
    fun wav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * BYTES_PER_SAMPLE
        val bytes = ByteArray(HEADER_SIZE + dataSize)
        fun text(at: Int, value: String) = value.forEachIndexed { i, c -> bytes[at + i] = c.code.toByte() }
        fun int(at: Int, value: Int) = repeat(4) { bytes[at + it] = (value shr (8 * it)).toByte() }
        fun short(at: Int, value: Int) = repeat(2) { bytes[at + it] = (value shr (8 * it)).toByte() }
        text(0, "RIFF")
        int(4, HEADER_SIZE - 8 + dataSize)
        text(8, "WAVE")
        text(12, "fmt ")
        int(16, 16)
        short(20, 1)
        short(22, 1)
        int(24, RATE)
        int(28, RATE * BYTES_PER_SAMPLE)
        short(32, BYTES_PER_SAMPLE)
        short(34, 16)
        text(36, "data")
        int(40, dataSize)
        samples.forEachIndexed { i, sample -> short(HEADER_SIZE + i * BYTES_PER_SAMPLE, sample.toInt()) }
        return bytes
    }

    private const val HEADER_SIZE = 44
    private const val BYTES_PER_SAMPLE = 2
}
