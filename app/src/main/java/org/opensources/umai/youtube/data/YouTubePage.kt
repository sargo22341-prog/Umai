package org.opensources.umai.youtube.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.opensources.umai.youtube.domain.ChapterMark
import org.opensources.umai.youtube.domain.TranscriptCue

/** A caption track offered for a video. */
internal data class CaptionTrack(val baseUrl: String, val languageCode: String, val automatic: Boolean)

/** What the player of a video says about it. */
internal data class PlayerDetails(
    val status: String,
    val title: String,
    val author: String,
    val description: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val captions: List<CaptionTrack>,
    val hlsUrl: String?,
    /** Progressive MP4 formats with a direct address, video and sound together. */
    val progressiveUrls: List<String>,
)

/**
 * Reads the JSON YouTube's web page and its player answer carry. Only the
 * fields the app uses are read, and each one leniently: YouTube changes these
 * documents often, and a missing field loses that field, not the video.
 */
internal object YouTubePage {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject

    /** The JSON object assigned after [marker] in a page script, such as `var ytInitialData = `. */
    fun embeddedObject(html: String, marker: String): JsonObject? {
        val start = html.indexOf(marker).takeIf { it >= 0 }?.let { html.indexOf('{', it) } ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until html.length) {
            val char = html[index]
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) {
                        return parseObject(html.substring(start, index + 1))
                    }
                }
            }
        }
        return null
    }

    private val visitorData = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")

    fun visitorData(html: String): String? = visitorData.find(html)?.groupValues?.get(1)

    /** The chapters of the video's progress bar, found wherever the page places them. */
    fun chapters(initialData: JsonObject?): List<ChapterMark> {
        val found = mutableListOf<ChapterMark>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    if (key == "chapterRenderer" && value is JsonObject) {
                        val title = value.obj("title")?.let { it.text("simpleText") ?: it.runs() }
                        val start = value.number("timeRangeStartMillis")
                        if (title != null && start != null) found += ChapterMark(title.trim(), start / 1000.0)
                    } else {
                        walk(value)
                    }
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        initialData?.let(::walk)
        return found.distinctBy { it.start }.sortedBy { it.start }
    }

    fun player(response: JsonObject): PlayerDetails {
        val details = response.obj("videoDetails")
        val streaming = response.obj("streamingData")
        val tracks = response.obj("captions")?.obj("playerCaptionsTracklistRenderer")?.array("captionTracks")
            .orEmpty()
            .mapNotNull { element ->
                val track = element as? JsonObject ?: return@mapNotNull null
                val url = track.text("baseUrl") ?: return@mapNotNull null
                CaptionTrack(
                    baseUrl = url,
                    languageCode = track.text("languageCode").orEmpty(),
                    automatic = track.text("kind") == "asr",
                )
            }
        val thumbnails = details?.obj("thumbnail")?.array("thumbnails").orEmpty().mapNotNull { it as? JsonObject }
        return PlayerDetails(
            status = response.obj("playabilityStatus")?.text("status").orEmpty(),
            title = details?.text("title").orEmpty(),
            author = details?.text("author").orEmpty(),
            description = details?.text("shortDescription").orEmpty(),
            durationSeconds = details?.text("lengthSeconds")?.toIntOrNull() ?: 0,
            thumbnailUrl = thumbnails.maxByOrNull { it.number("width") ?: 0.0 }?.text("url"),
            captions = tracks,
            hlsUrl = streaming?.text("hlsManifestUrl"),
            progressiveUrls = streaming?.array("formats").orEmpty()
                .mapNotNull { it as? JsonObject }
                .sortedByDescending { it.number("height") ?: 0.0 }
                .mapNotNull { it.text("url") },
        )
    }

    /**
     * The best transcript: one written by a person in the language the video
     * is spoken in, else the automatic one, else any.
     */
    fun preferredTrack(tracks: List<CaptionTrack>): CaptionTrack? {
        val spoken = tracks.firstOrNull { it.automatic }?.languageCode
        return tracks.firstOrNull { !it.automatic && spoken != null && it.languageCode.substringBefore('-') == spoken.substringBefore('-') }
            ?: tracks.firstOrNull { it.automatic }
            ?: tracks.firstOrNull()
    }

    /** Captions in YouTube's `json3` format. */
    fun transcript(text: String): List<TranscriptCue> {
        val root = parseObject(text) ?: return emptyList()
        return root.array("events").mapNotNull { element ->
            val event = element as? JsonObject ?: return@mapNotNull null
            val start = event.number("tStartMs") ?: return@mapNotNull null
            val duration = event.number("dDurationMs") ?: 0.0
            val words = event.array("segs").mapNotNull { (it as? JsonObject)?.text("utf8", trim = false) }
                .joinToString("")
                .replace('\n', ' ')
                .trim()
            words.takeIf { it.isNotEmpty() }?.let { TranscriptCue(start / 1000.0, (start + duration) / 1000.0, it) }
        }
    }

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()

    private fun JsonObject.text(key: String, trim: Boolean = true): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull?.let { if (trim) it.trim() else it }?.takeIf { it.isNotEmpty() }

    private fun JsonObject.number(key: String): Double? =
        (get(key) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.intOrNull?.toDouble() ?: it.contentOrNull?.toDoubleOrNull() }

    private fun JsonObject.runs(): String? =
        array("runs").mapNotNull { (it as? JsonObject)?.text("text", trim = false) }.joinToString("").takeIf { it.isNotBlank() }
}
