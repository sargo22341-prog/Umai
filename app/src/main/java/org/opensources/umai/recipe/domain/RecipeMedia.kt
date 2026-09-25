package org.opensources.umai.recipe.domain

import org.opensources.umai.core.model.RecipeAsset

/**
 * Mealie has no field for the video of a recipe nor for the photo of a step.
 * Both are kept as recipe assets, following conventions already present in
 * existing Mealie data:
 *
 * - a step photo is an image named `step-<n>` — `step-0` for the ingredients,
 *   `step-1` for the first step, and so on;
 * - a video is described by a `<name>-chapters.json` file ([VideoManifest]):
 *   where the video is and where each step starts in it. The video itself
 *   stays with its publisher.
 */
object RecipeMediaFiles {

    private val stepPhoto = Regex("""^step-(\d+)\.(jpe?g|png|webp|gif|avif|bmp)$""", RegexOption.IGNORE_CASE)
    private val videoExtensions = setOf("mp4", "webm", "mov", "m4v", "ogg", "ogv")
    const val CHAPTERS_SUFFIX = "-chapters"

    /** The asset name of the photo of step [stepNumber]; `0` is the ingredients. */
    fun stepPhotoName(stepNumber: Int): String = "step-$stepNumber"

    /** The step number a photo asset belongs to, `null` for any other file. */
    fun stepNumberOf(fileName: String?): Int? =
        fileName?.trim()?.let(stepPhoto::matchEntire)?.groupValues?.get(1)?.toIntOrNull()

    /** Step number to file name, the first file winning when a number appears twice. */
    fun stepPhotos(assets: List<RecipeAsset>): Map<Int, String> = buildMap {
        assets.forEach { asset ->
            val fileName = asset.fileName ?: return@forEach
            stepNumberOf(fileName)?.let { if (it !in this) put(it, fileName) }
        }
    }

    fun isVideo(fileName: String?): Boolean =
        fileName?.substringAfterLast('.', "")?.lowercase() in videoExtensions

    /** The chapters file of the recipe: the JSON asset ending in `-chapters.json`. */
    fun chaptersFile(assets: List<RecipeAsset>): String? = assets.mapNotNull { it.fileName }.firstOrNull {
        it.endsWith(".json", ignoreCase = true) && it.substringBeforeLast('.').endsWith(CHAPTERS_SUFFIX, ignoreCase = true)
    }
}

/**
 * Where one step is shown in the video. [stepIndex] counts from 0, and `-1`
 * stands for the presentation of the ingredients.
 */
data class VideoChapter(val stepIndex: Int, val start: Double, val end: Double?)

/** The content of a chapters file. */
data class VideoManifest(
    val title: String,
    /** The page the recipe comes from. */
    val sourceUrl: String?,
    /** The video at its publisher. */
    val videoUrl: String?,
    val chapters: List<VideoChapter>,
) {
    fun chapterFor(stepIndex: Int): VideoChapter? = chapters.firstOrNull { it.stepIndex == stepIndex }
}

/**
 * The part of a video to play for one step. [isHls] tells the player the
 * address is an HLS playlist.
 */
data class StepClip(
    val videoUrl: String,
    val start: Double,
    val end: Double?,
    val isHls: Boolean = false,
)

/** Where a video is read from: an address the player reads, and whether it is an HLS playlist. */
data class VideoStream(val url: String, val isHls: Boolean)
