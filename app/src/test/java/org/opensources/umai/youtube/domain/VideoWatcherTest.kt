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
import org.opensources.umai.llm.domain.LlmMedia
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.speech.domain.SpeechTranscriber
import org.opensources.umai.speech.domain.SpokenText
import java.io.IOException

/** Sound and pictures made up by the test: [sounds] pieces of the length asked, and pictures wherever asked. */
class FakeVideoMedia(
    private val sounds: Int = 0,
    /** The pieces after which reading the sound fails, as a dropped connection would. */
    private val soundBreaksAfter: Int? = null,
) : VideoMedia {
    val picturesAsked = mutableListOf<Double>()

    override fun sound(url: String, pieceSeconds: Int, maxSeconds: Int): Flow<SoundPiece> = flow {
        repeat(sounds) { index ->
            if (index == soundBreaksAfter) throw IOException("connection lost")
            emit(SoundPiece(index * pieceSeconds.toDouble(), (index + 1) * pieceSeconds.toDouble(), ShortArray(16)))
        }
    }

    override fun pictures(url: String, seconds: List<Double>): Flow<VideoPicture> {
        picturesAsked += seconds
        return seconds.map { VideoPicture(it, ByteArray(8)) }.asFlow()
    }
}

/** A Whisper that hears what the test says, piece after piece; a `null` piece is a failure. */
class FakeTranscriber(private val pieces: List<List<SpokenText>?> = emptyList(), private val ready: Boolean = true) : SpeechTranscriber {
    val languages = mutableListOf<String?>()

    override suspend fun isReady() = ready

    override suspend fun transcribe(samples: ShortArray, language: String?): List<SpokenText>? {
        languages += language
        return pieces.getOrElse(languages.size - 1) { emptyList() }
    }
}

class VideoWatcherTest {

    private fun silent(duration: Int = 90, captions: List<TranscriptCue> = emptyList(), chapters: List<ChapterMark> = emptyList()) =
        video(transcript = captions, chapters = chapters, duration = duration)
            .copy(soundUrl = "https://sound", pictureUrl = "https://pictures", spokenLanguage = "fr-FR")

    private fun shown(text: String) = LlmOutcome.Success("""{"shown": "$text"}""")

    private val noModel = ScriptedModel(emptyList(), ready = false)

    @Test
    fun `a video without captions is heard by Whisper, sentence by sentence, in its spoken language`() = runBlocking {
        val whisper = FakeTranscriber(
            listOf(
                listOf(SpokenText(1.5, 6.0, "On verse la farine."), SpokenText(6.0, 12.0, "Puis le lait.")),
                emptyList(),
                listOf(SpokenText(0.0, 4.0, "[Musique]"), SpokenText(4.0, 31.0, "On cuit à la poêle.")),
            ),
        )
        val progress = mutableListOf<WatchProgress>()

        val watched = VideoWatcher(noModel, whisper, FakeVideoMedia(sounds = 3))
            .complete(silent(duration = 360), "fr", ingredientsKnown = false) { progress += it }

        assertEquals(TranscriptSource.HEARD, watched.transcriptSource)
        assertEquals(
            listOf(
                TranscriptCue(1.5, 6.0, "On verse la farine."),
                TranscriptCue(6.0, 12.0, "Puis le lait."),
                // Times are those of the video, and never beyond the end of the piece.
                TranscriptCue(244.0, 271.0, "On cuit à la poêle."),
            ),
            watched.transcript,
        )
        assertEquals(listOf("fr", "fr", "fr"), whisper.languages)
        assertEquals(WatchProgress(seeing = false, done = 3, total = 3), progress.last())
    }

    @Test
    fun `captions are trusted, the video is not listened to`() = runBlocking {
        val whisper = FakeTranscriber()
        val video = silent(captions = listOf(TranscriptCue(0.0, 5.0, "on verse la farine")))

        val watched = VideoWatcher(noModel, whisper, FakeVideoMedia(sounds = 3)).complete(video, "fr", ingredientsKnown = false) {}

        assertSame(video, watched)
        assertTrue(whisper.languages.isEmpty())
    }

    @Test
    fun `without a Whisper model the video is not listened to`() = runBlocking {
        val whisper = FakeTranscriber(ready = false)
        val video = silent().copy(pictureUrl = null)

        val watched = VideoWatcher(noModel, whisper, FakeVideoMedia(sounds = 3)).complete(video, "fr", ingredientsKnown = false) {}

        assertEquals(video, watched)
        assertTrue(whisper.languages.isEmpty())
    }

    @Test
    fun `a Whisper that fails stops the listening and leaves the video as it was`() = runBlocking {
        val whisper = FakeTranscriber(listOf(null))
        val video = silent().copy(pictureUrl = null)

        val watched = VideoWatcher(noModel, whisper, FakeVideoMedia(sounds = 3)).complete(video, "fr", ingredientsKnown = false) {}

        assertEquals(video, watched)
        assertEquals(1, whisper.languages.size)
    }

    @Test
    fun `a sound that stops being readable keeps what was heard`() = runBlocking {
        val whisper = FakeTranscriber(listOf(listOf(SpokenText(0.0, 30.0, "On verse la farine."))))

        val watched = VideoWatcher(noModel, whisper, FakeVideoMedia(sounds = 3, soundBreaksAfter = 1))
            .complete(silent().copy(pictureUrl = null), "fr", ingredientsKnown = false) {}

        assertEquals(listOf(TranscriptCue(0.0, 30.0, "On verse la farine.")), watched.transcript)
    }

    @Test
    fun `a video where nothing is said is looked at, one picture a stretch`() = runBlocking {
        val model = ScriptedModel(listOf(shown("On pétrit la pâte."), shown(""), shown("La galette cuit.")))
        val media = FakeVideoMedia(sounds = 2)

        val watched = VideoWatcher(model, FakeTranscriber(), media).complete(silent(duration = 60), "fr", ingredientsKnown = false) {}

        assertEquals(TranscriptSource.SEEN, watched.transcriptSource)
        // A picture in the middle of each 20-second stretch.
        assertEquals(listOf(10.0, 30.0, 50.0), media.picturesAsked)
        assertEquals(
            listOf(TranscriptCue(0.0, 20.0, "On pétrit la pâte."), TranscriptCue(40.0, 60.0, "La galette cuit.")),
            watched.transcript,
        )
        assertTrue(model.requests.all { it.media is LlmMedia.Picture })
    }

    @Test
    fun `without a language model the video is not looked at`() = runBlocking {
        val media = FakeVideoMedia(sounds = 1)

        VideoWatcher(noModel, FakeTranscriber(), media).complete(silent(), "fr", ingredientsKnown = false) {}

        assertTrue(media.picturesAsked.isEmpty())
    }

    @Test
    fun `chapters already place the steps, the video is not looked at`() = runBlocking {
        val media = FakeVideoMedia(sounds = 1)

        VideoWatcher(ScriptedModel(listOf(shown("On coupe."))), FakeTranscriber(), media)
            .complete(silent(chapters = listOf(ChapterMark("La pâte", 0.0))), "fr", ingredientsKnown = false) {}

        assertTrue(media.picturesAsked.isEmpty())
    }

    @Test
    fun `listed ingredients spare looking at the pictures`() = runBlocking {
        val media = FakeVideoMedia(sounds = 1)

        val watched = VideoWatcher(ScriptedModel(listOf(shown("On coupe."))), FakeTranscriber(), media)
            .complete(silent(), "fr", ingredientsKnown = true) {}

        assertTrue(media.picturesAsked.isEmpty())
        assertTrue(watched.transcript.isEmpty())
    }

    @Test
    fun `a long video is looked at more sparsely`() = runBlocking {
        val media = FakeVideoMedia()

        VideoWatcher(ScriptedModel(listOf(shown("On coupe."))), FakeTranscriber(), media)
            .complete(silent(duration = 1_800).copy(soundUrl = null), "fr", ingredientsKnown = false) {}

        assertEquals(VideoWatcher.MAX_PICTURES, media.picturesAsked.size)
    }

    @Test
    fun `an answer that is not the expected field is no text`() {
        assertEquals("On coupe.", VideoWatcher.field("""{"shown": "On coupe."}""", "shown"))
        assertNull(VideoWatcher.field("""{"shown": "[Musique]"}""", "shown"))
        assertNull(VideoWatcher.field("not json", "shown"))
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
}
