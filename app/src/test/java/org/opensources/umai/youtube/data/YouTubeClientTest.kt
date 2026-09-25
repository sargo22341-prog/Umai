package org.opensources.umai.youtube.data

import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.youtube.domain.ChapterMark
import org.opensources.umai.youtube.domain.YouTubeFailure
import org.opensources.umai.youtube.domain.YouTubeResult

class YouTubeClientTest {

    private val server = MockWebServer()
    private var playerStatus = "OK"
    private val playerBodies = mutableListOf<String>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path == "/watch" -> ok(watchPage())
                    path == "/youtubei/v1/player" -> {
                        val body = request.body?.utf8().orEmpty()
                        playerBodies += body
                        ok(if (body.contains("VISIONOS")) visionPlayer() else vrPlayer())
                    }
                    path == "/api/timedtext" -> ok(CAPTIONS)
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private fun client() = YouTubeClient(OkHttpClient(), language = { "fr" }, baseUrl = server.url("/").toString().trimEnd('/'))

    @Test
    fun `a video is read with its chapters, description and transcript`() = runBlocking {
        val result = client().video("0nE7dAlDshk")

        val video = (result as YouTubeResult.Success).value
        assertEquals("Lasagnes", video.title)
        assertEquals("750g", video.author)
        assertEquals(480, video.durationSeconds)
        assertEquals("Ingrédients :\n250 g de pâtes", video.description)
        assertEquals("https://i.ytimg.com/big.jpg", video.thumbnailUrl)
        assertEquals(listOf(ChapterMark("Présentation", 0.0), ChapterMark("La sauce", 86.0)), video.chapters)
        assertEquals(2, video.transcript.size)
        assertEquals("on commence", video.transcript[0].text)
        assertEquals(1.5, video.transcript[0].start, 0.001)
        assertTrue(video.transcriptAutomatic)
        // The player is asked as the ANDROID_VR client, with the session of the page.
        assertTrue(playerBodies.single().contains("ANDROID_VR"))
        assertTrue(playerBodies.single().contains("visitor-123"))
    }

    @Test
    fun `a video YouTube wants a sign-in for is reported as blocked`() = runBlocking {
        playerStatus = "LOGIN_REQUIRED"
        assertEquals(YouTubeResult.Failure(YouTubeFailure.BLOCKED), client().video("0nE7dAlDshk"))
    }

    @Test
    fun `a private video is unavailable`() = runBlocking {
        playerStatus = "ERROR"
        assertEquals(YouTubeResult.Failure(YouTubeFailure.UNAVAILABLE), client().video("0nE7dAlDshk"))
    }

    @Test
    fun `the stream played is the HLS playlist, read without extra headers`() = runBlocking {
        val stream = requireNotNull(client().stream("0nE7dAlDshk"))
        assertEquals("https://manifest.googlevideo.com/master.m3u8", stream.url)
        assertTrue(stream.isHls)
        assertTrue(stream.headers.isEmpty())
    }

    @Test
    fun `an unreachable YouTube is a network failure`() = runBlocking {
        server.close()
        assertEquals(YouTubeResult.Failure(YouTubeFailure.NETWORK), client().video("0nE7dAlDshk"))
    }

    private fun watchPage() = """
        <html><script>var ytcfg = {"VISITOR_DATA":"visitor-123"};</script>
        <script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"$playerStatus"},"videoDetails":{"title":"Lasagnes","shortDescription":"Ingrédients :\n250 g de pâtes"}};</script>
        <script>var ytInitialData = {"playerOverlays":{"markersMap":[{"value":{"chapters":[
          {"chapterRenderer":{"title":{"simpleText":"Présentation"},"timeRangeStartMillis":0}},
          {"chapterRenderer":{"title":{"simpleText":"La sauce"},"timeRangeStartMillis":86000}}
        ]}}]},"text":"a } in a \" string"};</script></html>
    """.trimIndent()

    private fun vrPlayer() = """
        {"playabilityStatus":{"status":"$playerStatus"},
         "videoDetails":{"title":"Lasagnes","author":"750g","lengthSeconds":"480",
           "shortDescription":"Ingrédients :\n250 g de pâtes",
           "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/small.jpg","width":120},{"url":"https://i.ytimg.com/big.jpg","width":1280}]}},
         "captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[
           {"baseUrl":"${server.url("/api/timedtext")}?v=0nE7dAlDshk&lang=fr&kind=asr","languageCode":"fr","kind":"asr"}]}},
         "streamingData":{"formats":[{"itag":18,"url":"https://rr.googlevideo.com/18.mp4","height":360}]}}
    """.trimIndent()

    private fun visionPlayer() = """
        {"playabilityStatus":{"status":"OK"},"streamingData":{"hlsManifestUrl":"https://manifest.googlevideo.com/master.m3u8"}}
    """.trimIndent()

    private companion object {
        const val CAPTIONS = """
            {"events":[
              {"tStartMs":0,"dDurationMs":1000},
              {"tStartMs":1500,"dDurationMs":2000,"segs":[{"utf8":"on "},{"utf8":"commence"}]},
              {"tStartMs":4000,"dDurationMs":2000,"segs":[{"utf8":"la\nsauce"}]}
            ]}
        """
    }
}
