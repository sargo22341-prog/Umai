package org.opensources.umai.youtube.domain

import org.opensources.umai.recipe.domain.VideoChapter
import org.opensources.umai.recipe.domain.VideoManifest

/**
 * One step of a recipe rebuilt from a video. [start] is where it begins in the
 * video, in seconds, `null` when it could not be placed.
 */
data class BlueprintStep(
    val title: String,
    val text: String,
    val start: Double?,
)

/** How the recipe was rebuilt. */
enum class BlueprintOrigin {
    /** By the language model, from the whole transcript. */
    LANGUAGE_MODEL,

    /** From the description and chapters, with plain rules. */
    RULES,
}

/** A recipe rebuilt from a video, before it is written on Mealie. */
data class RecipeBlueprint(
    val name: String,
    val summary: String,
    val servings: Int?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<String>,
    val steps: List<BlueprintStep>,
    /** Where the ingredients are shown in the video, when a chapter is about them. */
    val ingredientsStart: Double?,
    val origin: BlueprintOrigin,
    val video: YouTubeVideo,
) {
    /** A recipe needs something to cook from: steps, or at least ingredients. */
    val isUsable: Boolean get() = steps.isNotEmpty() || ingredients.isNotEmpty()

    /**
     * Where each step is played in the cooking mode: from its start to the
     * next step's. A step that could not be placed has no chapter, and the
     * cooking mode shows it without the video.
     */
    fun manifest(): VideoManifest? {
        val duration = video.durationSeconds.toDouble()
        val placed = steps.mapIndexedNotNull { index, step -> step.start?.let { index to it } }
        if (placed.isEmpty()) return null
        val chapters = placed.mapIndexed { position, (index, start) ->
            val end = placed.getOrNull(position + 1)?.second ?: duration
            VideoChapter(stepIndex = index, start = start, end = end.takeIf { it > start })
        }
        val ingredients = ingredientsStart?.let { start ->
            val end = placed.first().second
            VideoChapter(stepIndex = -1, start = start, end = end.takeIf { it > start })
        }
        return VideoManifest(
            title = name,
            sourceUrl = video.watchUrl,
            videoUrl = video.watchUrl,
            chapters = listOfNotNull(ingredients) + chapters,
        )
    }
}

/**
 * Keeps the step starts inside the video and in order: a start before the
 * previous step's is dropped rather than trusted.
 */
internal fun List<BlueprintStep>.withOrderedStarts(duration: Int): List<BlueprintStep> {
    var previous = -1.0
    return map { step ->
        val start = step.start?.takeIf { it >= 0 && it < duration && it > previous }
        if (start != null) previous = start
        step.copy(start = start)
    }
}
