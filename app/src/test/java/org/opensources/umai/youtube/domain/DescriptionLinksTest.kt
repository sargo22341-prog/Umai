package org.opensources.umai.youtube.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionLinksTest {

    @Test
    fun `the link announcing the quantities is found among the others`() {
        // The description of Philippe Etchebest's Caesar salad, as YouTube serves it.
        val description = """
            ⚖️ Quantités de la recette : https://tinyurl.com/3xddjkdm
            📖 Livre "Bien cuisiner en bonne compagnie" : https://tinyurl.com/mucumd28
            🔪 Tout le matériel que j'utilise pour cette recette : https://tinyurl.com/vvrkytec

            🛎 Abonne-toi pour ne rien rater : https://www.youtube.com/c/ChefEtchebest/?sub_confirmation=1
            Mon site https://www.philippe-etchebest.com
        """.trimIndent()

        assertEquals(listOf("https://tinyurl.com/3xddjkdm"), DescriptionLinks.recipeLinks(description))
    }

    @Test
    fun `English wordings are understood`() {
        val description = """
            This perfect, fluffy pancake recipe is so easy!

            Full Recipe: https://preppykitchen.com/pancake-recipe/

            Website: http://www.PreppyKitchen.com
        """.trimIndent()

        assertEquals(listOf("https://preppykitchen.com/pancake-recipe/"), DescriptionLinks.recipeLinks(description))
    }

    @Test
    fun `a link alone on its line is announced by the line before`() {
        val description = "La recette :\n\nhttps://example.org/tarte-aux-pommes\n\nInstagram :\nhttps://instagram.com/chef"

        assertEquals(listOf("https://example.org/tarte-aux-pommes"), DescriptionLinks.recipeLinks(description))
    }

    @Test
    fun `the ingredients come before a mere mention of the recipe`() {
        val description = "La recette ici https://a.example/r\nLes ingrédients et quantités : https://b.example/q"

        assertEquals(listOf("https://b.example/q", "https://a.example/r"), DescriptionLinks.recipeLinks(description))
    }

    @Test
    fun `a site of many recipes, a shop or a video is not the recipe`() {
        val description = """
            Toutes les recettes et astuces sur www.750g.com
            La recette de la pâte en vidéo : https://youtu.be/abcdefghijk
            Le livre de recettes : https://shop.example/livre
        """.trimIndent()

        assertTrue(DescriptionLinks.recipeLinks(description).isEmpty())
    }

    @Test
    fun `punctuation after a link is not part of it, and a bare www address gets its scheme`() {
        assertEquals(listOf("https://www.example.org/recette"), DescriptionLinks.recipeLinks("Recette (www.example.org/recette)."))
    }

    @Test
    fun `a description without link gives none`() {
        assertTrue(DescriptionLinks.recipeLinks("Ingrédients :\n200 g de farine").isEmpty())
    }
}
