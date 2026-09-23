package org.opensources.umai.recipe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientLinkerTest {

    private fun line(text: String, ref: String, food: DraftFood? = null) = DraftIngredient(text, ref, food)

    @Test
    fun `a step mentioning the food of a line is linked to it`() {
        val ingredients = listOf(
            line("200 g farine", "farine", DraftFood("farine")),
            line("2 oeufs", "oeufs", DraftFood("oeuf", "oeufs")),
        )
        val steps = listOf(DraftStep(text = "Versez la farine dans un saladier."), DraftStep(text = "Ajoutez les œufs un à un."))

        val result = IngredientLinker.link(ingredients, steps)

        assertEquals(listOf("farine"), result.steps[0].ingredientReferences)
        assertEquals(listOf("oeufs"), result.steps[1].ingredientReferences)
        assertEquals(2, result.added)
        assertEquals(2, result.total)
    }

    @Test
    fun `free text lines are known by their words once quantities and units are set aside`() {
        val ingredients = listOf(
            line("2 gousses d'ail", "ail"),
            line("20 cl de crème liquide", "creme"),
            line("1 oignon rouge", "oignon"),
            line("Sel, poivre", "sel"),
        )
        val steps = listOf(
            DraftStep(text = "Émincez les oignons et l'ail."),
            DraftStep(text = "Versez la crème, salez selon votre goût."),
            DraftStep(text = "Ajoutez une pincée de sel."),
        )

        val result = IngredientLinker.link(ingredients, steps)

        assertEquals(listOf("ail", "oignon"), result.steps[0].ingredientReferences.sorted())
        // "selon" is not "sel": only whole words count.
        assertEquals(listOf("creme"), result.steps[1].ingredientReferences)
        assertEquals(listOf("sel"), result.steps[2].ingredientReferences)
    }

    @Test
    fun `English lines are understood too`() {
        val ingredients = listOf(line("2 tbsp olive oil", "oil"), line("1 red onion, chopped", "onion"))
        val steps = listOf(DraftStep(text = "Fry the onion in the oil."))

        val result = IngredientLinker.link(ingredients, steps)

        assertEquals(listOf("oil", "onion"), result.steps[0].ingredientReferences.sorted())
    }

    @Test
    fun `existing links are kept and not counted again`() {
        val ingredients = listOf(line("Farine", "farine"), line("Beurre", "beurre"))
        val steps = listOf(DraftStep(text = "Mélangez la farine.", ingredientReferences = listOf("beurre")))

        val result = IngredientLinker.link(ingredients, steps)

        assertEquals(listOf("beurre", "farine"), result.steps[0].ingredientReferences)
        assertEquals(1, result.added)
        assertEquals(2, result.total)
    }

    @Test
    fun `running it twice finds nothing more`() {
        val ingredients = listOf(line("Farine", "farine"))
        val once = IngredientLinker.link(ingredients, listOf(DraftStep(text = "Tamisez la farine.")))

        val twice = IngredientLinker.link(ingredients, once.steps)

        assertEquals(0, twice.added)
        assertEquals(once.steps, twice.steps)
    }

    @Test
    fun `a link to a line that no longer exists is dropped`() {
        val steps = listOf(DraftStep(text = "Rien.", ingredientReferences = listOf("gone")))

        val result = IngredientLinker.link(listOf(line("Farine", "farine")), steps)

        assertTrue(result.steps[0].ingredientReferences.isEmpty())
    }

    @Test
    fun `the step title counts as much as its text`() {
        val result = IngredientLinker.link(
            listOf(line("Chocolat noir", "choco")),
            listOf(DraftStep(title = "Le chocolat", text = "Faites fondre au bain-marie.")),
        )

        assertEquals(listOf("choco"), result.steps[0].ingredientReferences)
    }
}
