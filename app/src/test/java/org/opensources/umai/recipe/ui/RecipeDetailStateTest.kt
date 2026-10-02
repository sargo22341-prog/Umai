package org.opensources.umai.recipe.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeAsset
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.search.domain.OrganizerKind

class RecipeDetailStateTest {

    @Test
    fun `categories, tags and tools are listed with their kind, calorie tags left out`() {
        val state = RecipeDetailUiState(
            recipe = recipe(
                categories = listOf(Organizer("c1", "Plat", "plat")),
                tags = listOf(Organizer("t1", "Poulet", "poulet"), Organizer("t2", "calorie-695", "calorie-695")),
                tools = listOf(Organizer("o1", "Wok", "wok")),
            ),
        )

        assertEquals(
            listOf(OrganizerKind.CATEGORY to "Plat", OrganizerKind.TAG to "Poulet", OrganizerKind.TOOL to "Wok"),
            state.organizers.map { it.kind to it.organizer.name },
        )
    }

    @Test
    fun `no recipe, no organizer`() {
        assertTrue(RecipeDetailUiState().organizers.isEmpty())
    }

    @Test
    fun `a chapters file means the recipe has a video`() {
        val withVideo = RecipeDetailUiState(recipe = recipe(assets = listOf(RecipeAsset("Video", "mdi-file", "jow-chapters.json"))))
        val withoutVideo = RecipeDetailUiState(recipe = recipe(assets = listOf(RecipeAsset("Notes", "mdi-file", "notes.json"))))

        assertTrue(withVideo.hasVideo)
        assertFalse(withoutVideo.hasVideo)
    }

    @Test
    fun `a step photo or an embedded picture counts as step pictures`() {
        val plain = RecipeStep("s1", null, "Cuire.", images = emptyList(), ingredientReferenceIds = emptyList())

        assertFalse(RecipeDetailUiState(recipe = recipe(steps = listOf(plain))).hasStepPictures)
        assertTrue(RecipeDetailUiState(recipe = recipe(steps = listOf(plain.copy(photo = "step-1.jpg")))).hasStepPictures)
        assertTrue(RecipeDetailUiState(recipe = recipe(steps = listOf(plain.copy(images = listOf("a.jpg"))))).hasStepPictures)
        assertFalse(RecipeDetailUiState().hasStepPictures)
    }

    @Test
    fun `a recipe without steps cannot be cooked`() {
        assertFalse(RecipeDetailUiState(recipe = recipe()).canCook)
    }

    private fun recipe(
        categories: List<Organizer> = emptyList(),
        tags: List<Organizer> = emptyList(),
        tools: List<Organizer> = emptyList(),
        steps: List<RecipeStep> = emptyList(),
        assets: List<RecipeAsset> = emptyList(),
    ) = Recipe(
        summary = RecipeSummary(
            id = "r1", slug = "curry", name = "Curry", description = "", imageToken = null, servings = 2.0,
            yieldText = null, totalTime = null, prepTime = null, cookTime = null, performTime = null,
            categories = categories, tags = tags, tools = tools, rating = null, sourceUrl = null,
            dateAdded = null, lastMade = null,
        ),
        ingredients = emptyList(),
        steps = steps,
        nutrition = null,
        notes = emptyList(),
        showNutrition = false,
        showAssets = false,
        assets = assets,
    )
}
