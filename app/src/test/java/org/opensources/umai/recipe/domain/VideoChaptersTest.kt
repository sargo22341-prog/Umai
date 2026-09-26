package org.opensources.umai.recipe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoChaptersTest {

    private val youtube = "https://www.youtube.com/watch?v=y3L14JKSSYI"

    private val draft = RecipeDraft(
        id = "pains",
        name = "Petits pains",
        steps = listOf(DraftStep(text = "Pétrir."), DraftStep(text = "Farcir."), DraftStep(text = "Cuire.")),
    )

    private val saved = VideoManifest(
        title = "Petits pains farcis",
        sourceUrl = youtube,
        videoUrl = youtube,
        chapters = listOf(
            VideoChapter(stepIndex = -1, start = 0.0, end = 20.0),
            VideoChapter(stepIndex = 0, start = 20.0, end = 90.0),
            VideoChapter(stepIndex = 1, start = 90.0, end = 150.0),
            VideoChapter(stepIndex = 2, start = 240.0, end = 285.0),
        ),
    )

    @Test
    fun `each step takes its chapter, an end at the next chapter left implicit`() {
        val edited = VideoChapters.withVideo(draft, saved, sourceUrl = youtube)

        assertEquals(DraftVideo(youtube, ingredients = DraftChapter(0.0)), edited.video)
        assertEquals(
            listOf(DraftChapter(20.0), DraftChapter(90.0, end = 150.0), DraftChapter(240.0, end = 285.0)),
            edited.steps.map { it.chapter },
        )
    }

    @Test
    fun `a recipe imported from YouTube has a video to place, even without a chapters file`() {
        val edited = VideoChapters.withVideo(draft, manifest = null, sourceUrl = youtube)

        assertEquals(DraftVideo(youtube), edited.video)
        assertTrue(edited.steps.all { it.chapter == null })
    }

    @Test
    fun `a recipe from a web page has no video`() {
        assertSame(draft, VideoChapters.withVideo(draft, manifest = null, sourceUrl = "https://example.org/pains"))
    }

    @Test
    fun `a chapters file read and written back unchanged is no change`() {
        val edited = VideoChapters.withVideo(draft, saved, youtube)

        assertFalse(VideoChapters.changed(edited, saved))
        assertEquals(saved.chapters, VideoChapters.manifest(edited, saved)?.chapters)
    }

    @Test
    fun `a chapter follows its step when an earlier step is removed`() {
        val edited = VideoChapters.withVideo(draft, saved, youtube)
        val withoutFirst = edited.copy(steps = edited.steps.drop(1))

        val chapters = requireNotNull(VideoChapters.manifest(withoutFirst, saved)).chapters

        assertTrue(VideoChapters.changed(withoutFirst, saved))
        assertEquals(listOf(-1, 0, 1), chapters.map { it.stepIndex })
        assertEquals(listOf(0.0, 90.0, 240.0), chapters.map { it.start })
        // The ingredients now run until the step that follows them.
        assertEquals(90.0, chapters.first().end)
    }

    @Test
    fun `placing a step, moving it and taking it out of the video`() {
        val placed = VideoChapters.update(VideoChapters.withVideo(draft, null, youtube), 1) { DraftChapter(95.0) }
        assertEquals(DraftChapter(95.0), placed.steps[1].chapter)

        val ended = VideoChapters.update(placed, 1) { it?.copy(end = 130.0) }
        assertEquals(DraftChapter(95.0, 130.0), ended.steps[1].chapter)

        val ingredients = VideoChapters.update(ended, VideoChapters.INGREDIENTS) { DraftChapter(5.0) }
        assertEquals(DraftChapter(5.0), ingredients.video?.ingredients)

        val removed = VideoChapters.update(ingredients, 1) { null }
        assertNull(removed.steps[1].chapter)
    }

    @Test
    fun `an end before the start is not valid`() {
        val invalid = VideoChapters.update(VideoChapters.withVideo(draft, null, youtube), 0) { DraftChapter(60.0, end = 30.0) }

        assertFalse(VideoChapters.areValid(invalid))
        assertTrue(VideoChapters.areValid(VideoChapters.update(invalid, 0) { it?.copy(end = null) }))
    }

    @Test
    fun `a new chapters file keeps the video as its source, and an empty step has no number`() {
        val withEmpty = draft.copy(steps = listOf(DraftStep()) + draft.steps)
        val placed = VideoChapters.update(VideoChapters.withVideo(withEmpty, null, youtube), 3) { DraftChapter(240.0) }

        val manifest = requireNotNull(VideoChapters.manifest(placed, saved = null))

        assertEquals("Petits pains", manifest.title)
        assertEquals(youtube, manifest.sourceUrl)
        assertEquals(listOf(VideoChapter(stepIndex = 2, start = 240.0, end = null)), manifest.chapters)
        assertTrue(VideoChapters.changed(placed, saved = null))
    }
}

class VideoTimeTest {

    @Test
    fun `times are written in minutes and seconds, hours when needed`() {
        assertEquals("0:05", VideoTime.format(5.9))
        assertEquals("1:25", VideoTime.format(85.0))
        assertEquals("1:02:03", VideoTime.format(3_723.0))
    }

    @Test
    fun `times are read as people write them`() {
        assertEquals(85.0, VideoTime.parse("1:25"))
        assertEquals(85.0, VideoTime.parse(" 85 "))
        assertEquals(3_723.0, VideoTime.parse("1:02:03"))
        assertNull(VideoTime.parse("1:75"))
        assertNull(VideoTime.parse("1:"))
        assertNull(VideoTime.parse("-3"))
        assertNull(VideoTime.parse("1.5"))
    }

    @Test
    fun `a player position is kept to the second`() {
        assertEquals(85.0, VideoTime.fromMillis(85_999), 0.0)
        assertEquals(0.0, VideoTime.fromMillis(-10), 0.0)
    }
}
