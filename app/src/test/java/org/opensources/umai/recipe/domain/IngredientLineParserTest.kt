package org.opensources.umai.recipe.domain

import org.junit.Assert.*
import org.junit.Test

class IngredientLineParserTest {
    @Test fun `metric quantity wins over the parenthesized cup`() {
        val parsed = IngredientLineParser.parse("150 g (1 Tasse) de cacahuètes grillées")!!
        assertEquals(150.0, parsed.quantity)
        assertEquals("g", parsed.unit?.name)
        assertEquals("cacahuètes grillées", parsed.food?.name)
        assertEquals("(1 Tasse)", parsed.note)
        assertTrue(parsed.needsReview)
        assertNull(parsed.unit?.id)
    }

    @Test fun `ranges retain their meaning without inventing an exact quantity`() {
        val parsed = IngredientLineParser.parse("1 à 2 pincées de sel marin (facultatif)")!!
        assertNull(parsed.quantity)
        assertEquals("pincée", parsed.unit?.name)
        assertEquals("sel marin", parsed.food?.name)
        assertTrue(parsed.note.contains("1 à 2 pincées"))
        assertFalse(parsed.isResolved)
    }

    @Test fun `metric abbreviations plurals decimal commas and attached units work`() {
        listOf("0,5kg de farine" to "kg", "2 litres of water" to "l",
            "150 grammes de farine" to "g", "25 ml de lait" to "ml").forEach { (line, unit) ->
            assertEquals(unit, IngredientLineParser.parse(line)?.unit?.name)
        }
        assertEquals(0.5, IngredientLineParser.parse("0,5kg de farine")?.quantity)
        assertNull(IngredientLineParser.parse("2 bananes (200 g)"))
        assertNull(IngredientLineParser.parse("sel marin"))
    }
}
