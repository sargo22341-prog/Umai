package org.opensources.umai.youtube.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.recipe.domain.RecipeLinks

class YouTubeLinksTest {

    @Test
    fun `every form of a video address gives its id`() {
        listOf(
            "https://www.youtube.com/watch?v=0nE7dAlDshk",
            "https://youtube.com/watch?feature=share&v=0nE7dAlDshk&t=42",
            "https://m.youtube.com/watch?v=0nE7dAlDshk",
            "https://youtu.be/0nE7dAlDshk?si=abc",
            "https://www.youtube.com/shorts/0nE7dAlDshk",
            "https://www.youtube.com/embed/0nE7dAlDshk",
            "youtube.com/watch?v=0nE7dAlDshk",
        ).forEach { assertEquals(it, "0nE7dAlDshk", YouTubeLinks.videoId(it)) }
    }

    @Test
    fun `other addresses are not videos`() {
        assertNull(YouTubeLinks.videoId("https://www.youtube.com/@750Grammes"))
        assertNull(YouTubeLinks.videoId("https://www.marmiton.org/recettes/recette_lasagnes.aspx"))
        assertNull(YouTubeLinks.videoId("https://www.youtube.com/watch?v=short"))
        assertFalse(YouTubeLinks.isVideo("not an address"))
        assertTrue(YouTubeLinks.isVideo("https://youtu.be/0nE7dAlDshk"))
    }

    @Test
    fun `two videos are two sources, and one video under two addresses is one`() {
        assertFalse(RecipeLinks.sameSource("https://www.youtube.com/watch?v=0nE7dAlDshk", "https://www.youtube.com/watch?v=Wqbo1EkbWHc"))
        assertTrue(RecipeLinks.sameSource("https://youtu.be/0nE7dAlDshk", "https://www.youtube.com/watch?v=0nE7dAlDshk&t=3"))
        assertEquals("youtube.com/watch?v=0nE7dAlDshk", RecipeLinks.searchFragment("https://youtu.be/0nE7dAlDshk"))
    }
}
