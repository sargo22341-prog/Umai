package org.opensources.umai.youtube.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmMedia
import org.opensources.umai.llm.domain.LlmOutcome
import java.io.IOException

/** Sound and pictures made up by the test: [sounds] pieces of 30 seconds, and pictures wherever asked. */
class FakeVideoMedia(
    private val sounds: Int = 0,
    /** The pieces after which reading the sound fails, as a dropped connection would. */
    private val soundBreaksAfter: Int? = null,
) : VideoMedia {
    val picturesAsked = mutableListOf<Double>()

    override fun sound(url: String, pieceSeconds: Int, maxSeconds: Int): Flow<SoundPiece> = flow {
        repeat(sounds) { index ->
            if (index == soundBreaksAfter) throw IOException("connection lost")
            emit(SoundPiece(index * 30.0, (index + 1) * 30.0, ByteArray(44)))
        }
    }

    override fun pictures(url: String, seconds: List<Double>): Flow<VideoPicture> {
        picturesAsked += seconds
        return seconds.map { VideoPicture(it, ByteArray(8)) }.asFlow()
    }
}

class VideoWatcherTest {

    private fun silent(duration: Int = 90, captions: List<TranscriptCue> = emptyList(), chapters: List<ChapterMark> = emptyList()) =
        video(transcript = captions, chapters = chapters, duration = duration)
            .copy(soundUrl = "https://sound", pictureUrl = "https://pictures", spokenLanguage = "fr")

    private fun said(text: String) = LlmOutcome.Success("""{"speech": "$text"}""")

    private fun shown(text: String) = LlmOutcome.Success("""{"shown": "$text"}""")

    @Test
    fun `a video without captions is listened to, piece by piece, in its spoken language`() = runBlocking {
        val model = ScriptedModel(listOf(said("On verse la farine."), said(""), said("[Musique] On cuit à la poêle.")))
        val progress = mutableListOf<WatchProgress>()

        val watched = VideoWatcher(model, FakeVideoMedia(sounds = 3)).complete(silent(), "fr", ingredientsKnown = false) { progress += it }

        assertEquals(TranscriptSource.HEARD, watched.transcriptSource)
        assertEquals(
            listOf(TranscriptCue(0.0, 30.0, "On verse la farine."), TranscriptCue(60.0, 90.0, "On cuit à la poêle.")),
            watched.transcript,
        )
        assertTrue(model.requests.all { it.media is LlmMedia.Sound })
        assertTrue(model.requests.first().user.contains("French"))
        assertEquals(WatchProgress(seeing = false, done = 3, total = 3), progress.last())
    }

    @Test
    fun `captions are trusted, the video is not listened to`() = runBlocking {
        val model = ScriptedModel(listOf(said("Autre chose.")))
        val video = silent(captions = listOf(TranscriptCue(0.0, 5.0, "on verse la farine")))

        val watched = VideoWatcher(model, FakeVideoMedia(sounds = 3)).complete(video, "fr", ingredientsKnown = false) {}

        assertSame(video, watched)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `a model that cannot hear stops the listening and leaves the video as it was`() = runBlocking {
        val model = ScriptedModel(listOf(LlmOutcome.Failure(LlmFailure.MEDIA_UNSUPPORTED)))
        val video = silent().copy(pictureUrl = null)

        val watched = VideoWatcher(model, FakeVideoMedia(sounds = 3)).complete(video, "fr", ingredientsKnown = false) {}

        assertEquals(video, watched)
        assertEquals(1, model.requests.size)
    }

    @Test
    fun `a sound that stops being readable keeps what was heard`() = runBlocking {
        val model = ScriptedModel(listOf(said("On verse la farine.")))

        val watched = VideoWatcher(model, FakeVideoMedia(sounds = 3, soundBreaksAfter = 1))
            .complete(silent().copy(pictureUrl = null), "fr", ingredientsKnown = false) {}

        assertEquals(listOf(TranscriptCue(0.0, 30.0, "On verse la farine.")), watched.transcript)
    }

    @Test
    fun `a video where nothing is said is looked at, one picture a stretch`() = runBlocking {
        val model = ScriptedModel(listOf(said(""), said(""), shown("On pétrit la pâte."), shown(""), shown("La galette cuit.")))
        val media = FakeVideoMedia(sounds = 2)

        val watched = VideoWatcher(model, media).complete(silent(duration = 60), "fr", ingredientsKnown = false) {}

        assertEquals(TranscriptSource.SEEN, watched.transcriptSource)
        // A picture in the middle of each 20-second stretch.
        assertEquals(listOf(10.0, 30.0, 50.0), media.picturesAsked)
        assertEquals(
            listOf(TranscriptCue(0.0, 20.0, "On pétrit la pâte."), TranscriptCue(40.0, 60.0, "La galette cuit.")),
            watched.transcript,
        )
        assertTrue(model.requests.drop(2).all { it.media is LlmMedia.Picture })
    }

    @Test
    fun `chapters already place the steps, the video is not looked at`() = runBlocking {
        val model = ScriptedModel(listOf(said("")))
        val media = FakeVideoMedia(sounds = 1)

        VideoWatcher(model, media).complete(silent(chapters = listOf(ChapterMark("La pâte", 0.0))), "fr", ingredientsKnown = false) {}

        assertTrue(media.picturesAsked.isEmpty())
    }

    @Test
    fun `listed ingredients spare looking at the pictures`() = runBlocking {
        val model = ScriptedModel(listOf(said("")))
        val media = FakeVideoMedia(sounds = 1)

        val watched = VideoWatcher(model, media).complete(silent(), "fr", ingredientsKnown = true) {}

        assertTrue(media.picturesAsked.isEmpty())
        assertTrue(watched.transcript.isEmpty())
    }

    @Test
    fun `a long video is looked at more sparsely`() = runBlocking {
        val media = FakeVideoMedia()

        VideoWatcher(ScriptedModel(listOf(shown("On coupe."))), media).complete(silent(duration = 1_800).copy(soundUrl = null), "fr", ingredientsKnown = false) {}

        assertEquals(VideoWatcher.MAX_PICTURES, media.picturesAsked.size)
    }

    @Test
    fun `an answer that is not the expected field is no text`() {
        assertEquals("On verse.", VideoWatcher.field("""{"speech": "On verse."}""", "speech"))
        assertNull(VideoWatcher.field("""{"speech": "[Musique]"}""", "speech"))
        assertNull(VideoWatcher.field("not json", "speech"))
    }
}

class SpeechSoundTest {

    @Test
    fun `sound at 48 kHz is averaged down to 16 kHz`() {
        val samples = shortArrayOf(0, 3, 6, 30, 30, 30, -3, -3, -3)

        assertArrayEquals(shortArrayOf(3, 30, -3), SpeechSound.resample(samples, fromRate = 48_000))
    }

    @Test
    fun `sound already at 16 kHz is kept as it is`() {
        val samples = shortArrayOf(1, 2, 3)

        assertSame(samples, SpeechSound.resample(samples, fromRate = SpeechSound.RATE))
    }

    @Test
    fun `a WAV file describes 16 kHz mono 16-bit sound, little-endian`() {
        val wav = SpeechSound.wav(shortArrayOf(1, -2))

        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(48, wav.size)
        // Sample rate, then the two samples.
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x3E, 0, 0), wav.copyOfRange(24, 28))
        assertArrayEquals(byteArrayOf(1, 0, 0xFE.toByte(), 0xFF.toByte()), wav.copyOfRange(44, 48))
    }
}
