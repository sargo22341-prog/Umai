package org.opensources.umai.youtube.domain

/** A chapter of a video, as its author cut it. [start] is in seconds. */
data class ChapterMark(val title: String, val start: Double)

/** One caption of the transcript, in seconds. */
data class TranscriptCue(val start: Double, val end: Double, val text: String)

/** What a YouTube page tells about a video: the material a recipe is rebuilt from. */
data class YouTubeVideo(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    /** The author's chapters, or the timestamps listed in the description; sorted by start. */
    val chapters: List<ChapterMark>,
    /** Empty when the video has no captions at all. */
    val transcript: List<TranscriptCue>,
    /** Where [transcript] comes from; `null` when it is empty. */
    val transcriptSource: TranscriptSource?,
    /**
     * The video has captions, but YouTube refused to hand them over — it
     * limits how often one address may read them — so [transcript] is empty.
     */
    val transcriptRefused: Boolean = false,
    /** The original sound track, which the phone can listen to when there are no captions. */
    val soundUrl: String? = null,
    /** A file of the pictures the phone can look at, when nothing is said. */
    val pictureUrl: String? = null,
    /** The language spoken in the video, such as "fr", when YouTube tells it. */
    val spokenLanguage: String? = null,
) {
    val watchUrl: String get() = YouTubeLinks.watchUrl(id)

    /**
     * Whether anything tells where things happen in the video: chapters, or
     * words spoken. Captions of a video with only music hold nothing but "[Musique]".
     */
    val hasTimes: Boolean
        get() = chapters.isNotEmpty() || transcript.any { Transcript.clean(it.text).isNotEmpty() }

    /** Where chapter [index] ends: the next one's start, or the end of the video. */
    fun chapterEnd(index: Int): Double =
        chapters.getOrNull(index + 1)?.start ?: durationSeconds.toDouble()
}

/** Where the transcript of a video comes from, from the most to the least reliable. */
enum class TranscriptSource {
    /** Captions written by a person. */
    WRITTEN,

    /** Captions of YouTube's speech recognition. */
    AUTOMATIC,

    /** The sound track, transcribed on the phone by the language model. */
    HEARD,

    /** Nothing is said: what pictures of the video show, described by the language model. */
    SEEN,
}

/** Why a video could not be read. */
enum class YouTubeFailure {
    /** The address is not a YouTube video. */
    NOT_A_VIDEO,

    /** Private, removed, age-restricted or blocked in this country. */
    UNAVAILABLE,

    /** YouTube asked to sign in to prove this is not a robot; it usually passes after a while. */
    BLOCKED,

    NETWORK,

    /** The page did not have the shape the app reads: YouTube changed it. */
    UNREADABLE,
}

/** Where videos are read from: YouTube itself, or a test double. */
interface VideoSource {
    suspend fun video(id: String): YouTubeResult<YouTubeVideo>
}

sealed interface YouTubeResult<out T> {
    data class Success<T>(val value: T) : YouTubeResult<T>

    data class Failure(val failure: YouTubeFailure) : YouTubeResult<Nothing>
}

/** Recognizes YouTube addresses, whatever their form. */
object YouTubeLinks {

    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val hosts = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com")

    /** The id of the video at [url], `null` when it is not a YouTube video. */
    fun videoId(url: String): String? {
        val address = url.trim()
        val withScheme = if (address.contains("://")) address else "https://$address"
        val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val candidate = when {
            host == "youtu.be" -> segments.firstOrNull()
            host in hosts && segments.firstOrNull() == "watch" -> query(uri.rawQuery, "v")
            host in hosts && segments.firstOrNull() in setOf("shorts", "embed", "live", "v") -> segments.getOrNull(1)
            else -> null
        }
        return candidate?.takeIf { idPattern.matches(it) }
    }

    fun isVideo(url: String): Boolean = videoId(url) != null

    /** Any address on YouTube: a video, a channel, a playlist. */
    fun isYouTube(url: String): Boolean {
        val withScheme = if (url.contains("://")) url else "https://$url"
        val host = runCatching { java.net.URI(withScheme).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return false
        return host == "youtu.be" || host in hosts || host.endsWith(".youtube.com")
    }

    fun watchUrl(id: String): String = "https://www.youtube.com/watch?v=$id"

    private fun query(raw: String?, name: String): String? = raw?.split('&')
        ?.map { it.split('=', limit = 2) }
        ?.firstOrNull { it.first() == name }
        ?.getOrNull(1)
}
