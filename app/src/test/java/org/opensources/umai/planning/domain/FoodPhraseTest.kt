package org.opensources.umai.planning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodPhraseTest {

    private fun servings(count: Double, food: String) = FoodPhrase(PhraseQuantity.Servings(count), food)

    private fun measured(amount: Double, unit: FoodUnit?, food: String) =
        FoodPhrase(PhraseQuantity.Measured(amount, unit), food)

    @Test
    fun `a number before the food is a number of servings`() {
        assertEquals(servings(2.0, "pommes"), FoodPhrases.parse("2 pommes"))
        assertEquals(servings(3.0, "café sans sucre"), FoodPhrases.parse("3 café sans sucre"))
        assertEquals(servings(2.0, "pommes"), FoodPhrases.parse("2x pommes"))
        assertEquals(servings(1.5, "banane"), FoodPhrases.parse("1,5 banane"))
        assertEquals(servings(0.5, "baguette"), FoodPhrases.parse("1/2 baguette"))
        assertEquals(servings(0.5, "pizza"), FoodPhrases.parse("½ pizza"))
    }

    @Test
    fun `numbers may be written in words, in French or English`() {
        assertEquals(servings(1.0, "thé"), FoodPhrases.parse("un thé"))
        assertEquals(servings(2.0, "oeufs"), FoodPhrases.parse("deux oeufs"))
        assertEquals(servings(0.5, "baguette"), FoodPhrases.parse("une demi baguette"))
        assertEquals(servings(3.0, "eggs"), FoodPhrases.parse("three eggs"))
        assertEquals(servings(1.0, "apple"), FoodPhrases.parse("an apple"))
    }

    @Test
    fun `without a number it is one serving`() {
        assertEquals(servings(1.0, "Pizza saumon raviole"), FoodPhrases.parse("Pizza saumon raviole"))
    }

    @Test
    fun `a weight or a volume is the amount eaten`() {
        assertEquals(measured(200.0, FoodUnit.GRAM, "riz"), FoodPhrases.parse("200 g de riz"))
        assertEquals(measured(200.0, FoodUnit.GRAM, "riz"), FoodPhrases.parse("200g riz"))
        assertEquals(measured(330.0, FoodUnit.MILLILITRE, "coca"), FoodPhrases.parse("33 cl de coca"))
        assertEquals(measured(1500.0, FoodUnit.GRAM, "pommes"), FoodPhrases.parse("1,5 kg de pommes"))
        assertEquals(measured(500.0, FoodUnit.MILLILITRE, "eau"), FoodPhrases.parse("0.5 l d'eau"))
    }

    @Test
    fun `a household measure counts in the unit of the food`() {
        assertEquals(measured(400.0, null, "lait"), FoodPhrases.parse("2 verres de lait"))
        assertEquals(measured(200.0, null, "eau"), FoodPhrases.parse("un verre d’eau"))
        assertEquals(measured(15.0, null, "miel"), FoodPhrases.parse("1 cuillère à soupe de miel"))
        assertEquals(measured(200.0, null, "milk"), FoodPhrases.parse("a glass of milk"))
    }

    @Test
    fun `a measure is a whole word, not the start of a food`() {
        assertEquals(servings(2.0, "gaufres"), FoodPhrases.parse("2 gaufres"))
        assertEquals(servings(1.0, "lait"), FoodPhrases.parse("1 lait"))
        assertEquals(servings(1.0, "cassoulet"), FoodPhrases.parse("cassoulet"))
    }

    @Test
    fun `a phrase without a food is none`() {
        assertNull(FoodPhrases.parse("2"))
        assertNull(FoodPhrases.parse("200 g"))
        assertNull(FoodPhrases.parse("  "))
    }

    @Test
    fun `foods are parted by commas, and, plus and new lines, not by a decimal comma`() {
        assertEquals(
            listOf("2 pommes", "1 café sans sucre", "1 thé", "1,5 kg de riz", "yaourt"),
            FoodPhrases.split("2 pommes, 1 café sans sucre et 1 thé\n1,5 kg de riz + yaourt"),
        )
        assertEquals(listOf("an apple", "a tea"), FoodPhrases.split("an apple and a tea"))
        assertEquals(emptyList<String>(), FoodPhrases.split(" , "))
    }

    @Test
    fun `the amount eaten is the servings times the usual portion, or the measure`() {
        val apple = FoodTableFixtures.table.search("pomme", limit = 1).single()

        assertEquals(300.0, FoodPhrases.parse("2 pommes")?.amountOf(apple))
        assertEquals(FoodUnit.GRAM, FoodPhrases.parse("2 pommes")?.unitOf(apple))
        assertEquals(80.0, FoodPhrases.parse("80 g de pomme")?.amountOf(apple))
        val listed = FoodChoice(FoodTableFixtures.apple, usual = null)
        assertNull(FoodPhrases.parse("2 pommes")?.amountOf(listed))
        assertEquals(400.0, FoodPhrases.parse("2 verres de pomme")?.amountOf(listed))
    }
}
