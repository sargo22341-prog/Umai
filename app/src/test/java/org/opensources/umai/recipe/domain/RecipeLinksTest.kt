package org.opensources.umai.recipe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeLinksTest {

    @Test
    fun `the address is found in the sentence an app shares`() {
        assertEquals(
            "https://jow.fr/recipes/pates-carbonara-7vkmjgzi5t8003pn0drm",
            RecipeLinks.extract("Découvre cette recette sur Jow : https://jow.fr/recipes/pates-carbonara-7vkmjgzi5t8003pn0drm."),
        )
        assertEquals("https://www.marmiton.org/recettes/x.aspx", RecipeLinks.extract("https://www.marmiton.org/recettes/x.aspx"))
    }

    @Test
    fun `a text without address gives nothing`() {
        assertNull(RecipeLinks.extract("Une recette de ma grand-mère"))
        assertNull(RecipeLinks.extract(null))
    }

    @Test
    fun `the same page is recognised whatever its small differences`() {
        val stored = "https://jow.fr/recipes/poulet-au-curry-5e45"

        assertTrue(RecipeLinks.sameSource(stored, "http://www.jow.fr/recipes/poulet-au-curry-5e45/"))
        assertTrue(RecipeLinks.sameSource(stored, "https://jow.fr/recipes/poulet-au-curry-5e45?utm_source=app#step2"))
        assertFalse(RecipeLinks.sameSource(stored, "https://jow.fr/recipes/poulet-au-curry-6f56"))
        assertFalse(RecipeLinks.sameSource(stored, "pas une adresse"))
    }

    @Test
    fun `the fragment looked up leaves out what may differ`() {
        assertEquals("jow.fr/recipes/x", RecipeLinks.searchFragment("https://www.jow.fr/recipes/x/?a=1"))
        assertNull(RecipeLinks.searchFragment("https://example.org/a\"b"))
    }
}
