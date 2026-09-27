package org.opensources.umai.youtube.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.takeWhile
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmMedia
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.llm.domain.ModelAnswer
import org.opensources.umai.speech.domain.SpeechTranscriber
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max

/** A piece of the sound track, from [start] to [end] seconds, as 16 kHz mono samples. */
class SoundPiece(val start: Double, val end: Double, val samples: ShortArray)

/** A picture of the video at [second], as a JPEG. */
class VideoPicture(val second: Double, val jpeg: ByteArray)

/** Reads the sound and the pictures of a video, on the phone. */
interface VideoMedia {

    /**
     * The sound at [url] in pieces of [pieceSeconds], up to [maxSeconds]; fails
     * with an [IOException] once the file cannot be read or decoded.
     */
    fun sound(url: String, pieceSeconds: Int, maxSeconds: Int): Flow<SoundPiece>

    /**
     * Pictures of the video at [url] at each of [seconds]; one that cannot be
     * read is skipped, and a file that cannot be read fails with an [IOException].
     */
    fun pictures(url: String, seconds: List<Double>): Flow<VideoPicture>
}

/** How far watching a video is: [done] of [total] pieces heard, or pictures seen when [seeing]. */
data class WatchProgress(val seeing: Boolean, val done: Int, val total: Int)

/**
 * Gives the language model the times it places the steps with when YouTube
 * gives none, as yt-transcript and pick-a-recipe do with Whisper and a
 * vision model — here on the phone:
 *
 * - a video without captions, or whose captions YouTube refused, is listened
 *   to by Whisper ([transcriber]): its original sound track, in pieces of
 *   [PIECE_SECONDS], written down with the times of each sentence;
 * - a video where nothing is said, that has no chapters and whose ingredients
 *   nothing lists is looked at: one picture every [MIN_PICTURE_GAP] seconds, or
 *   fewer on a long video, each described.
 *
 * What is heard or seen becomes timed lines of the transcript, which the
 * recipe is then rebuilt from as from captions. Without a speech model, a
 * language model with a vision part, or a readable sound or picture file, the
 * video is left as it was.
 */
class VideoWatcher(
    private val model: LanguageModel,
    private val transcriber: SpeechTranscriber,
    private val media: VideoMedia,
) {

    /**
     * [language] is the one the pictures are described in: "fr" or "en".
     * [ingredientsKnown] when the description or the recipe page lists them:
     * looking at the pictures, minutes of work, then only places the steps,
     * and is left out.
     */
    suspend fun complete(
        video: YouTubeVideo,
        language: String,
        ingredientsKnown: Boolean,
        onProgress: (WatchProgress) -> Unit,
    ): YouTubeVideo {
        var watched = video
        val soundUrl = video.soundUrl
        if (video.transcript.isEmpty() && soundUrl != null && transcriber.isReady()) {
            val heard = hear(video, soundUrl, onProgress)
            if (heard.isNotEmpty()) watched = watched.copy(transcript = heard, transcriptSource = TranscriptSource.HEARD)
        }
        val pictureUrl = video.pictureUrl
        if (!watched.hasTimes && !ingredientsKnown && pictureUrl != null && model.isReady()) {
            val seen = see(video, pictureUrl, language, onProgress)
            if (seen.isNotEmpty()) watched = watched.copy(transcript = seen, transcriptSource = TranscriptSource.SEEN)
        }
        return watched
    }

    private suspend fun hear(video: YouTubeVideo, url: String, onProgress: (WatchProgress) -> Unit): List<TranscriptCue> {
        val seconds = minOf(video.durationSeconds, MAX_HEARD_SECONDS)
        val total = ceil(seconds.toDouble() / PIECE_SECONDS).toInt()
        val cues = mutableListOf<TranscriptCue>()
        var done = 0
        var stopped = false
        onProgress(WatchProgress(seeing = false, done = 0, total = total))
        media.sound(url, PIECE_SECONDS, seconds)
            .takeWhile { !stopped }
            // A sound file that stops being readable leaves what was heard so far.
            .catch { if (it !is IOException) throw it }
            .collect { piece ->
                val said = transcriber.transcribe(piece.samples, video.spokenLanguage?.substringBefore('-'))
                if (said == null) {
                    stopped = true
                } else {
                    said.forEach { spoken ->
                        val text = Transcript.clean(spoken.text)
                        if (text.isNotEmpty()) {
                            cues += TranscriptCue(piece.start + spoken.start, minOf(piece.start + spoken.end, piece.end), text)
                        }
                    }
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
            // Likewise for the pictures: those seen so far are kept.
            .catch { if (it !is IOException) throw it }
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
        /**
         * The sound handed to Whisper at once. It hears it in windows of 30
         * seconds and ends each piece with a window for the last sentence: two
         * minutes cost that window once where pieces of 30 seconds doubled the work.
         */
        const val PIECE_SECONDS = 120

        /** Longer videos are heard up to there. */
        const val MAX_HEARD_SECONDS = 20 * 60

        /** About twelve seconds a picture on a Pixel 10: a long video is looked at more sparsely. */
        const val MIN_PICTURE_GAP = 20.0
        const val MAX_PICTURES = 30

        private const val SHOWN = "shown"
        private const val MAX_SHOWN_TOKENS = 120

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
            val root = ModelAnswer.objectOrNull(answer) ?: return null
            return (root[key] as? JsonPrimitive)?.contentOrNull?.let(Transcript::clean)?.takeIf { it.isNotEmpty() }
        }
    }
}

/** Sound as Whisper hears it: 16 kHz, one channel, 16-bit samples. */
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
}
