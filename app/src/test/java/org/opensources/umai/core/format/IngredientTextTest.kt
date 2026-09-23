package org.opensources.umai.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import org.opensources.umai.core.model.IngredientFood
import org.opensources.umai.core.model.IngredientUnit
import org.opensources.umai.core.model.RecipeIngredient

/** Scaling an ingredient line, the way the recipe page and the cooking mode do. */
class IngredientTextTest {

    private fun unit(
        name: String = "gramme",
        pluralName: String? = "grammes",
        abbreviation: String = "g",
        useAbbreviation: Boolean = false,
    ) = IngredientUnit(
        id = "u",
        name = name,
        pluralName = pluralName,
        abbreviation = abbreviation,
        pluralAbbreviation = null,
        useAbbreviation = useAbbreviation,
        fraction = true,
    )

    private fun ingredient(
        display: String = "70 grammes de riz",
        quantity: Double? = 70.0,
        unit: IngredientUnit? = unit(),
        food: IngredientFood? = IngredientFood("f", "riz", null),
        note: String? = null,
        originalText: String? = null,
    ) = RecipeIngredient(
        referenceId = "ref",
        display = display,
        quantity = quantity,
        unit = unit,
        food = food,
        note = note,
        sectionTitle = null,
        originalText = originalText,
    )

    @Test
    fun `at the recipe's own servings Mealie's own wording is kept`() {
        assertEquals("70 grammes de riz", IngredientText.format(ingredient(), scale = 1.0))
    }

    @Test
    fun `doubling the servings doubles the quantity`() {
        assertEquals("140 grammes riz", IngredientText.format(ingredient(), scale = 2.0))
    }

    @Test
    fun `halving a quantity falls back to the typographic fraction`() {
        val single = ingredient(quantity = 1.0, food = IngredientFood("f", "citron", "citrons"))
        assertEquals("½ gramme citron", IngredientText.format(single, scale = 0.5))
    }

    @Test
    fun `above one the plural forms of the unit and the food are used`() {
        val single = ingredient(
            quantity = 1.0,
            food = IngredientFood("f", "citron", "citrons"),
        )
        assertEquals("3 grammes citrons", IngredientText.format(single, scale = 3.0))
    }

    @Test
    fun `a unit flagged for abbreviation keeps its short form once scaled`() {
        val abbreviated = ingredient(unit = unit(useAbbreviation = true))
        assertEquals("140 g riz", IngredientText.format(abbreviated, scale = 2.0))
    }

    @Test
    fun `a note is kept at the end of a scaled line`() {
        val noted = ingredient(note = "bien rince")
        assertEquals("140 grammes riz bien rince", IngredientText.format(noted, scale = 2.0))
    }

    @Test
    fun `a line without quantity is never rewritten`() {
        val free = ingredient(display = "Sel et poivre", quantity = null, unit = null, food = null)
        assertEquals("Sel et poivre", IngredientText.format(free, scale = 4.0))
    }

    @Test
    fun `a quantity of zero is treated as no quantity at all`() {
        val zero = ingredient(display = "Un filet d'huile", quantity = 0.0, unit = null, food = null)
        assertEquals("Un filet d'huile", IngredientText.format(zero, scale = 2.0))
    }

    @Test
    fun `an unparsed line falls back to the text the user typed`() {
        val unparsed = RecipeIngredient(
            referenceId = "ref",
            display = "",
            quantity = null,
            unit = null,
            food = null,
            note = null,
            sectionTitle = null,
            originalText = "une pincee de sel",
        )
        assertEquals("une pincee de sel", IngredientText.format(unparsed))
    }
}
