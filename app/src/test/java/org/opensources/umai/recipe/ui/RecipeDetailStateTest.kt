package org.opensources.umai.recipe.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.Recipe
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

    private fun recipe(categories: List<Organizer>, tags: List<Organizer>, tools: List<Organizer>) = Recipe(
        summary = RecipeSummary(
            id = "r1", slug = "curry", name = "Curry", description = "", imageToken = null, servings = 2.0,
            yieldText = null, totalTime = null, prepTime = null, cookTime = null, performTime = null,
            categories = categories, tags = tags, tools = tools, rating = null, sourceUrl = null,
            dateAdded = null, lastMade = null,
        ),
        ingredients = emptyList(),
        steps = emptyList(),
        nutrition = null,
        notes = emptyList(),
        showNutrition = false,
        showAssets = false,
        assets = emptyList(),
    )
}
