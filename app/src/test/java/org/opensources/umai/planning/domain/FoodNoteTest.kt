package org.opensources.umai.planning.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FoodNoteTest {

    private val french = FoodNoteLabels(
        locale = Locale.FRENCH,
        nutrients = mapOf(
            Nutrient.ENERGY to "Énergie",
            Nutrient.FAT to "Lipides",
            Nutrient.CARBOHYDRATES to "Glucides",
            Nutrient.SUGARS to "Sucres",
            Nutrient.PROTEIN to "Protéines",
            Nutrient.SALT to "Sel",
        ),
    )

    private val cola = NutritionFacts(
        mapOf(
            Nutrient.ENERGY to 42.0,
            Nutrient.FAT to 0.0,
            Nutrient.CARBOHYDRATES to 10.6,
            Nutrient.SUGARS to 10.6,
            Nutrient.PROTEIN to 0.0,
            Nutrient.SALT to 0.0,
        ),
    )

    @Test
    fun `the note gives the calories and quantity first, then the nutrients of the portion`() {
        val text = FoodNote.text(330.0, FoodUnit.MILLILITRE, cola.scaled(3.3), french)

        assertEquals(
            "139 kcal · 330 ml\nLipides 0 g · Glucides 35 g · Sucres 35 g · Protéines 0 g · Sel 0 g",
            text,
        )
    }

    @Test
    fun `the calories written are the ones read back by the plan`() {
        val text = FoodNote.text(250.0, FoodUnit.GRAM, NutritionFacts(mapOf(Nutrient.ENERGY to 294.0)).scaled(2.5), french)

        assertEquals("735 kcal · 250 g", text)
        assertEquals(735, PlanCalories.ofNote(text))
    }

    @Test
    fun `decimals follow the language of the app`() {
        val facts = NutritionFacts(mapOf(Nutrient.FAT to 1.45))
        val english = french.copy(locale = Locale.ENGLISH, nutrients = mapOf(Nutrient.FAT to "Fat"))

        assertEquals("Lipides 1,4 g", FoodNote.text(null, FoodUnit.GRAM, facts, french).substringAfter('\n'))
        assertEquals("Fat 1.4 g", FoodNote.text(null, FoodUnit.GRAM, facts, english))
    }

    @Test
    fun `without nutrition nor quantity the note is empty`() {
        assertEquals("", FoodNote.text(null, FoodUnit.GRAM, NutritionFacts(), french))
    }
}
