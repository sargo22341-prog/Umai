package org.opensources.umai.youtube.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDescriptionTest {

    private val description = """
        Hervé Cuisine un poulet curry facile et rapide (en 25 minutes).

        Si vous souhaitez refaire le poulet curry à la maison, voici les ingrédients :

        Pour 2 personnes :
        - 2 hauts de cuisse de poulet
        • 1 oignon
        1 poignée de raisins secs
        150 ml d'eau ou de bouillon de volaille
        1 cuil. à soupe de curry en poudre

        Préparation :
        1. Émincer l'oignon et le faire revenir.
        2. Ajouter le poulet coupé en dés et le curry.

        ---------------
        Retrouvez-moi sur https://www.instagram.com/herve
        #poulet #curry
    """.trimIndent()

    @Test
    fun `the list under an ingredient heading is read, bullets removed`() {
        assertEquals(
            listOf(
                "2 hauts de cuisse de poulet",
                "1 oignon",
                "1 poignée de raisins secs",
                "150 ml d'eau ou de bouillon de volaille",
                "1 cuil. à soupe de curry en poudre",
            ),
            VideoDescription.ingredients(description),
        )
    }

    @Test
    fun `without a heading, the longest run of quantities is the list`() {
        val text = """
            Une recette de grand-mère.
            200 g de farine
            3 œufs
            50 cl de lait
            Bon appétit !
        """.trimIndent()

        assertEquals(listOf("200 g de farine", "3 œufs", "50 cl de lait"), VideoDescription.ingredients(text))
    }

    @Test
    fun `a description without any list gives no ingredient`() {
        assertTrue(VideoDescription.ingredients("Abonnez-vous ! https://example.com").isEmpty())
    }

    @Test
    fun `written steps are read without their numbers`() {
        assertEquals(
            listOf("Émincer l'oignon et le faire revenir.", "Ajouter le poulet coupé en dés et le curry."),
            VideoDescription.steps(description),
        )
    }

    @Test
    fun `servings are read in French and English`() {
        assertEquals(2, VideoDescription.servings(description))
        assertEquals(4, VideoDescription.servings("Serves 4, ready in 20 minutes"))
        assertEquals(6, VideoDescription.servings("Ingrédients pour 6 parts"))
        assertNull(VideoDescription.servings("Une recette facile"))
    }

    @Test
    fun `timestamps become chapters when they start at zero`() {
        val text = """
            0:00 Intro
            1:25 - La sauce
            12:05 Dressage
            1:02:03 Bonus
        """.trimIndent()

        assertEquals(
            listOf(
                ChapterMark("Intro", 0.0),
                ChapterMark("La sauce", 85.0),
                ChapterMark("Dressage", 725.0),
                ChapterMark("Bonus", 3723.0),
            ),
            VideoDescription.timestamps(text),
        )
    }

    @Test
    fun `timestamps not starting at zero are not chapters`() {
        assertTrue(VideoDescription.timestamps("1:25 La sauce\n3:00 Dressage").isEmpty())
    }
}
