package org.opensources.umai.recipe.domain

import org.opensources.umai.youtube.domain.YouTubeLinks
import java.util.Locale
import kotlin.math.floor

/**
 * The chapters of a recipe video as the editor sees them. The chapters file
 * ([VideoManifest]) numbers them by step position; in the editor each step
 * carries its own ([DraftStep.chapter]), so a chapter follows its step when a
 * step before it is removed, and the file is numbered again on save.
 */
object VideoChapters {

    /**
     * [draft] with its video: the one of the chapters file when there is one,
     * else the YouTube video the recipe was imported from, not placed yet.
     * A recipe with neither has no video to place.
     */
    fun withVideo(draft: RecipeDraft, manifest: VideoManifest?, sourceUrl: String?): RecipeDraft {
        val url = manifest?.videoUrl ?: sourceUrl?.takeIf(YouTubeLinks::isVideo) ?: return draft
        val chapters = manifest?.chapters.orEmpty()
        fun VideoChapter.toDraft() = DraftChapter(start, end?.takeUnless { it == nextStart(start, chapters.map(VideoChapter::start)) })
        return draft.copy(
            video = DraftVideo(url = url, ingredients = manifest?.chapterFor(INGREDIENTS)?.toDraft()),
            steps = draft.steps.mapIndexed { index, step -> step.copy(chapter = manifest?.chapterFor(index)?.toDraft()) },
        )
    }

    /**
     * The chapters file of [draft], `null` when it has no video. [saved] is the
     * file on Mealie, whose title and source are kept.
     */
    fun manifest(draft: RecipeDraft, saved: VideoManifest?): VideoManifest? {
        val video = draft.video ?: return null
        val placed = listOfNotNull(video.ingredients?.let { INGREDIENTS to it }) +
            draft.writtenSteps.mapIndexedNotNull { index, step -> step.chapter?.let { index to it } }
        val starts = placed.map { (_, chapter) -> chapter.start }
        return VideoManifest(
            title = saved?.title?.takeIf { it.isNotBlank() } ?: draft.name.trim(),
            sourceUrl = saved?.sourceUrl ?: video.url,
            videoUrl = video.url,
            chapters = placed
                .map { (index, chapter) -> VideoChapter(index, chapter.start, chapter.end ?: nextStart(chapter.start, starts)) }
                .sortedBy { it.start },
        )
    }

    /** Whether saving [draft] changes the chapters of [saved], the file on Mealie. */
    fun changed(draft: RecipeDraft, saved: VideoManifest?): Boolean {
        val chapters = manifest(draft, saved)?.chapters ?: return false
        return chapters != saved?.chapters.orEmpty()
    }

    /**
     * [draft] with the chapter of step [stepIndex] — of the ingredients for
     * [INGREDIENTS] — changed by [change]; `null` takes the step out of the video.
     */
    fun update(draft: RecipeDraft, stepIndex: Int, change: (DraftChapter?) -> DraftChapter?): RecipeDraft {
        val video = draft.video ?: return draft
        if (stepIndex == INGREDIENTS) return draft.copy(video = video.copy(ingredients = change(video.ingredients)))
        if (stepIndex !in draft.steps.indices) return draft
        return draft.copy(
            steps = draft.steps.mapIndexed { index, step -> if (index == stepIndex) step.copy(chapter = change(step.chapter)) else step },
        )
    }

    /** Whether every chapter of [draft] plays something. */
    fun areValid(draft: RecipeDraft): Boolean =
        draft.video?.ingredients?.isValid != false && draft.steps.all { it.chapter?.isValid != false }

    /** The index of the ingredients in a chapters file. */
    const val INGREDIENTS = -1

    private fun nextStart(start: Double, starts: List<Double>): Double? = starts.filter { it > start }.minOrNull()
}

/** Times in a video as people write them: `1:25`, `1:02:03`, or seconds alone. */
object VideoTime {

    /** Whole seconds: a chapter is placed to the second. */
    fun format(seconds: Double): String {
        val total = floor(seconds.coerceAtLeast(0.0)).toLong()
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val rest = total % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, rest)
        }
    }

    /** `null` for anything else than `s`, `m:ss` or `h:mm:ss`, where seconds and minutes after the first stay under 60. */
    fun parse(text: String): Double? {
        val parts = text.trim().split(':')
        if (parts.size > 3 || parts.any { it.isEmpty() || it.length > MAX_DIGITS || !it.all(Char::isDigit) }) return null
        val numbers = parts.map { it.toLong() }
        if (numbers.drop(1).any { it >= SECONDS_PER_MINUTE }) return null
        return numbers.fold(0L) { total, part -> total * SECONDS_PER_MINUTE + part }.toDouble()
    }

    /** A player position, to the whole second a chapter is placed to. */
    fun fromMillis(millis: Long): Double = (millis.coerceAtLeast(0L) / MILLIS_PER_SECOND).toDouble()

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val MAX_DIGITS = 6
}
