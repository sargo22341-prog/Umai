package org.opensources.umai.youtube.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.opensources.umai.recipe.domain.VideoStream
import org.opensources.umai.youtube.domain.VideoDescription
import org.opensources.umai.youtube.domain.VideoSource
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeResult
import org.opensources.umai.youtube.domain.YouTubeVideo
import java.io.IOException

/**
 * Reads YouTube the way yt-dlp does (public domain), with no account and no
 * API key: the watch page for the chapters and a session identifier, then
 * YouTube's own player API, asked as the clients yt-dlp currently uses
 * without a proof-of-origin token:
 *
 * - `ANDROID_VR` answers with caption tracks that can be downloaded as they
 *   are, and a progressive MP4 stream;
 * - `VISIONOS` answers with an HLS stream, in every quality, which the player
 *   reads without any extra header.
 *
 * YouTube changes what these clients get every few months: when it does, the
 * client names and versions below are what to update, from yt-dlp's
 * `yt_dlp/extractor/youtube/_base.py`.
 */
class YouTubeClient(
    private val http: OkHttpClient,
    /** "fr" or "en": the language of the page, and the one captions are preferred in. */
    private val language: () -> String,
    /** Only a test points it elsewhere. */
    private val baseUrl: String = "https://www.youtube.com",
) : VideoSource {

    /** The video with its description, chapters and transcript. */
    override suspend fun video(id: String): YouTubeResult<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            val page = get(watchUrl(id), WEB_USER_AGENT)
                ?: return@withContext YouTubeResult.Failure(YouTubeFailure.NETWORK)
            val initialPlayer = YouTubePage.embeddedObject(page, "ytInitialPlayerResponse = ")
            val visitor = YouTubePage.visitorData(page)
            val player = player(id, ANDROID_VR, visitor) ?: initialPlayer
                ?: return@withContext YouTubeResult.Failure(YouTubeFailure.UNREADABLE)
            val details = YouTubePage.player(player)
            val pageDetails = initialPlayer?.let(YouTubePage::player)

            when (details.status) {
                "OK" -> Unit
                "LOGIN_REQUIRED" -> return@withContext YouTubeResult.Failure(YouTubeFailure.BLOCKED)
                else -> return@withContext YouTubeResult.Failure(YouTubeFailure.UNAVAILABLE)
            }

            val description = details.description.ifBlank { pageDetails?.description.orEmpty() }
            val chapters = YouTubePage.chapters(YouTubePage.embeddedObject(page, "ytInitialData = "))
                .ifEmpty { VideoDescription.timestamps(description) }
            val tracks = details.captions.ifEmpty { pageDetails?.captions.orEmpty() }
            val track = YouTubePage.preferredTrack(tracks)
            val transcript = track?.let { get("${it.baseUrl}&fmt=json3", ANDROID_VR.userAgent) }
                ?.let(YouTubePage::transcript)
                .orEmpty()

            YouTubeResult.Success(
                YouTubeVideo(
                    id = id,
                    title = details.title.ifBlank { pageDetails?.title.orEmpty() },
                    author = details.author,
                    description = description,
                    durationSeconds = details.durationSeconds,
                    thumbnailUrl = details.thumbnailUrl ?: pageDetails?.thumbnailUrl,
                    chapters = chapters,
                    transcript = transcript,
                    transcriptAutomatic = track?.automatic == true,
                ),
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: IOException) {
            YouTubeResult.Failure(YouTubeFailure.NETWORK)
        }
    }

    /**
     * A stream of the video the player can read, looked up right before it is
     * played: the addresses YouTube gives expire after a few hours.
     */
    suspend fun stream(id: String): VideoStream? = withContext(Dispatchers.IO) {
        try {
            val visitor = get(watchUrl(id), WEB_USER_AGENT)?.let(YouTubePage::visitorData)
            player(id, VISIONOS, visitor)?.let(YouTubePage::player)?.hlsUrl
                ?.let { return@withContext VideoStream(it, isHls = true, headers = emptyMap()) }
            player(id, ANDROID_VR, visitor)?.let(YouTubePage::player)?.progressiveUrls?.firstOrNull()
                ?.let { VideoStream(it, isHls = false, headers = mapOf(USER_AGENT to ANDROID_VR.userAgent)) }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: IOException) {
            null
        }
    }

    private fun player(id: String, client: InnerTubeClient, visitor: String?): JsonObject? {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    client.context.forEach { (key, value) ->
                        when (value) {
                            is Int -> put(key, value)
                            else -> put(key, value.toString())
                        }
                    }
                    put("hl", language())
                    visitor?.let { put("visitorData", it) }
                }
            }
            put("videoId", id)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val request = Request.Builder()
            .url("$baseUrl/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON))
            .header(USER_AGENT, client.userAgent)
            .header("X-YouTube-Client-Name", client.number.toString())
            .header("X-YouTube-Client-Version", client.version)
            .header("Origin", ORIGIN)
            .apply { visitor?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            YouTubePage.parseObject(response.body.string())
        }
    }

    private fun get(url: String, userAgent: String): String? {
        val request = Request.Builder()
            .url(url)
            .header(USER_AGENT, userAgent)
            .header("Accept-Language", "${language()};q=1.0, en;q=0.5")
            // Skips the cookie consent page YouTube shows in Europe.
            .header("Cookie", "SOCS=CAI")
            .build()
        return http.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    }

    private fun watchUrl(id: String) = "$baseUrl/watch?v=$id&hl=${language()}&bpctr=9999999999&has_verified=1"

    private class InnerTubeClient(
        val number: Int,
        val version: String,
        val userAgent: String,
        val context: Map<String, Any>,
    )

    private companion object {
        const val ORIGIN = "https://www.youtube.com"
        const val USER_AGENT = "User-Agent"
        val JSON = "application/json".toMediaType()

        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

        val ANDROID_VR = InnerTubeClient(
            number = 28,
            version = "1.65.10",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            context = mapOf(
                "clientName" to "ANDROID_VR",
                "clientVersion" to "1.65.10",
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
                "androidSdkVersion" to 32,
                "osName" to "Android",
                "osVersion" to "12L",
            ),
        )

        val VISIONOS = InnerTubeClient(
            number = 101,
            version = "1.02",
            userAgent = WEB_USER_AGENT,
            context = mapOf(
                "clientName" to "VISIONOS",
                "clientVersion" to "1.02",
                "deviceMake" to "Apple",
                "deviceModel" to "RealityDevice17,1",
                "osName" to "visionOS",
                "osVersion" to "26.5.23O471",
            ),
        )
    }
}
