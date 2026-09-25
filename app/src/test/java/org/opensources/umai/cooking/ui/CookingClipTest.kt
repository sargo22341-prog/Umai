package org.opensources.umai.cooking.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.planning.domain.summary
import org.opensources.umai.recipe.domain.StepClip
import org.opensources.umai.recipe.domain.VideoChapter
import org.opensources.umai.recipe.domain.VideoManifest
import org.opensources.umai.recipe.domain.VideoStream

class CookingClipTest {

    private val recipe = Recipe(
        summary = summary("lasagnes"),
        ingredients = emptyList(),
        steps = listOf(
            RecipeStep("s1", "Sauce", "Faire la sauce.", emptyList(), emptyList()),
            RecipeStep("s2", "Montage", "Monter.", emptyList(), emptyList()),
        ),
        nutrition = null,
        notes = emptyList(),
        showNutrition = false,
        showAssets = false,
        assets = emptyList(),
    )

    private val manifest = VideoManifest(
        title = "Lasagnes",
        sourceUrl = "https://www.youtube.com/watch?v=0nE7dAlDshk",
        videoUrl = "https://www.youtube.com/watch?v=0nE7dAlDshk",
        chapters = listOf(VideoChapter(0, 10.0, 90.0), VideoChapter(1, 90.0, null)),
    )

    @Test
    fun `the step loops on its chapter of the stream found for the video`() {
        val stream = VideoStream("https://manifest.googlevideo.com/master.m3u8", isHls = true)
        val state = CookingUiState(recipe = recipe, video = manifest, stream = stream, currentStep = 1)

        assertEquals(StepClip("https://manifest.googlevideo.com/master.m3u8", 90.0, null, isHls = true), state.clip)
    }

    @Test
    fun `without a readable stream the step shows no video`() {
        assertNull(CookingUiState(recipe = recipe, video = manifest, stream = null).clip)
    }
}
