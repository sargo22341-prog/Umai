package org.opensources.umai.planning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodTableTest {

    private val table = FoodTableFixtures.table

    @Test
    fun `words are compared without case, accents, plurals or little words`() {
        assertEquals(FoodWords.of("café sans sucre"), FoodWords.of("Cafés sans sucres"))
        assertEquals(listOf("cafe", "pomme"), FoodWords.of("Café, pommes"))
        assertEquals(listOf("jus", "orange"), FoodWords.of("jus d’orange"))
        assertEquals(listOf("oeuf"), FoodWords.of("Œufs"))
        assertEquals(listOf("the", "vert"), FoodWords.of("Thé vert"))
    }

    @Test
    fun `a usual name comes before the names of the table`() {
        val found = table.search("pommes")

        assertEquals(FoodTableFixtures.apple, found.first().food)
        assertEquals(150.0, found.first().serving)
        assertEquals("Pomme", found.first().name("fr"))
        assertEquals("Apple", found.first().name("en"))
    }

    @Test
    fun `both languages are searched, whatever the language of the app`() {
        assertEquals(FoodTableFixtures.apple, table.search("apple").first().food)
        assertEquals(FoodTableFixtures.smokedSalmon, table.search("smoked salmon").first().food)
        assertEquals("Saumon fumé, à l'aneth et au citron", table.search("saumon fumé").first().name("fr"))
    }

    @Test
    fun `every word typed must be in the name, the last one may be cut short`() {
        assertEquals(FoodTableFixtures.coffee, table.search("café sans sucre").first().food)
        assertEquals(FoodTableFixtures.apple, table.search("pom").first().food)
        assertTrue(table.search("pizza saumon raviole").isEmpty())
        assertTrue(table.search("").isEmpty())
    }

    @Test
    fun `the tables shipped with the app are read whole`() {
        val shipped = FoodTableFixtures.shippedTable()
        val ciqualLines = FoodTableFixtures.asset("ciqual.tsv").readLines().size - 1
        val usualLines = FoodTableFixtures.asset("basic_foods.tsv").readLines().size - 1

        assertEquals(ciqualLines + usualLines, shipped.size)
        assertTrue(ciqualLines > 3000)
    }

    @Test
    fun `the everyday foods are found by their usual name, with their portion`() {
        val shipped = FoodTableFixtures.shippedTable()
        val expected = mapOf(
            "pomme" to "Pomme, chair et peau, crue",
            "café sans sucre" to "Café, non instantané, sans sucres ajoutés, prêt à boire",
            "thé" to "Thé infusé, sans sucres ajoutés",
            "oeuf" to "Oeuf cru",
            "banana" to "Banane, chair sans peau, crue",
            "pizza saumon" to "Pizza au saumon, préemballée",
            // A whole word before the start of a longer one: not "laitue".
            "lait" to "Lait demi-écrémé (aliment moyen)",
        )

        expected.forEach { (typed, name) ->
            val choice = shipped.search(typed).first()
            assertEquals(typed, name, choice.food.nameFr)
            assertNotNull(typed, choice.serving)
            assertNotNull(typed, choice.food.per100[Nutrient.ENERGY])
        }
        assertEquals(FoodUnit.MILLILITRE, shipped.search("thé").first().unit)
    }

    @Test
    fun `a food without a usual name is found in the table`() {
        val shipped = FoodTableFixtures.shippedTable()

        val found = shipped.search("ravioli").first()

        assertTrue(found.food.nameFr, FoodWords.of(found.food.nameFr).contains("ravioli"))
        assertEquals(null, found.serving)
    }
}
