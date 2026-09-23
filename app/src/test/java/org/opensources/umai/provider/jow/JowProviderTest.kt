package org.opensources.umai.provider.jow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.recipe.domain.VideoChapter

class JowProviderTest {

    private val source = "https://jow.fr/recipes/pates-carbonara-7vkmjgzi5t8003pn0drm"

    @Test
    fun `only the recipe pages of Jow are handled`() {
        assertTrue(JowProvider.handles(source))
        assertTrue(JowProvider.handles("https://www.jow.fr/en/recipes/carbonara-xyz"))
        assertFalse(JowProvider.handles("https://jow.fr/cooking/recipes"))
        assertFalse(JowProvider.handles("https://notjow.fr/recipes/x"))
        assertFalse(JowProvider.handles("https://www.marmiton.org/recettes/x"))
        assertFalse(JowProvider.handles("n'importe quoi"))
    }

    @Test
    fun `the video and the clip of each step become chapters`() {
        val media = JowProvider.media(Json.parseToJsonElement(CARBONARA), source)!!

        val video = media.video!!
        assertEquals("https://static.jow.fr/recipes/U72kt0L06oMyAw_transcoded.mp4", video.videoUrl)
        assertEquals("Pâtes carbonara", video.title)
        assertEquals(source, video.sourceUrl)
        assertEquals(
            listOf(
                VideoChapter(-1, 0.0, 8.8),
                VideoChapter(0, 9.1, 16.3),
                VideoChapter(1, 16.3, 27.0),
                // The last clip has no end: it runs to the end of the video.
                VideoChapter(2, 27.0, null),
            ),
            video.chapters,
        )
    }

    @Test
    fun `a step without its own photo gets none, whatever the video shows`() {
        val media = JowProvider.media(Json.parseToJsonElement(CARBONARA), source)!!

        // Step 2 points at a moment of the video and step 3 at the recipe picture:
        // neither is a photo of the step. Only step 1 has one.
        assertEquals(mapOf(1 to "https://static.jow.fr/steps/pancetta.jpg"), media.stepPhotos)
    }

    @Test
    fun `older pages give the chapters through the time of their step images`() {
        val media = JowProvider.media(Json.parseToJsonElement(OLD_PAGE), "https://jow.fr/recipes/old")!!

        assertEquals(
            listOf(VideoChapter(-1, 0.0, 4.7), VideoChapter(0, 5.0, 11.7), VideoChapter(1, 12.0, null)),
            media.video!!.chapters,
        )
        assertTrue(media.stepPhotos.isEmpty())
    }

    @Test
    fun `a recipe without video has no chapters`() {
        val media = JowProvider.media(Json.parseToJsonElement(NO_VIDEO), "https://jow.fr/recipes/salade")!!

        assertNull(media.video)
    }

    @Test
    fun `a page without recipe gives nothing`() {
        assertNull(JowProvider.media(JsonPrimitive("recipe_scrapers was unable to scrape this URL"), source))
    }

    private companion object {
        val CARBONARA = """
            {
              "@context": "https://schema.org", "@type": "Recipe", "name": "Pâtes carbonara",
              "image": ["https://static.jow.fr/1024x1024/recipes/VZIbcxb3qnBmxw.jpg"],
              "recipeInstructions": [
                {"@type": "HowToStep", "text": "Coupez la pancetta.",
                 "image": "https://static.jow.fr/steps/pancetta.jpg",
                 "video": {"@type": "Clip", "startOffset": 9.1, "endOffset": 16.3,
                           "url": "https://jow.fr/recipes/pates-carbonara?t=9"}},
                {"@type": "HowToStep", "text": "Faites revenir la pancetta.",
                 "image": "https://static.jow.fr/recipes/U72kt0L06oMyAw_transcoded.mp4#t=16",
                 "video": {"@type": "Clip", "startOffset": 16.3, "endOffset": 27}},
                {"@type": "HowToStep", "text": "Servez.",
                 "image": "https://static.jow.fr/1024x1024/recipes/VZIbcxb3qnBmxw.jpg",
                 "video": {"@type": "Clip", "startOffset": 27, "url": "https://jow.fr/recipes/pates-carbonara?t=27"}}
              ],
              "video": [{"@type": "VideoObject", "name": "Pâtes carbonara",
                         "thumbnailUrl": ["https://static.jow.fr/1024x1024/recipes/VZIbcxb3qnBmxw.jpg"],
                         "contentUrl": "https://static.jow.fr/recipes/U72kt0L06oMyAw_transcoded.mp4"}]
            }
        """.trimIndent()

        val OLD_PAGE = """
            {"@graph": [{"@type": "WebPage"}, {"@type": ["Recipe"], "name": "Ancienne",
              "video": {"@type": "VideoObject", "contentUrl": "https://static.jow.fr/recipes/old.mp4"},
              "recipeInstructions": [
                {"@type": "HowToSection", "itemListElement": [
                  {"@type": "HowToStep", "text": "Un", "image": "https://static.jow.fr/recipes/old.mp4#t=5"},
                  {"@type": "HowToStep", "text": "Deux", "image": {"url": "https://static.jow.fr/recipes/old.mp4#t=12"}}
                ]}
              ]}]}
        """.trimIndent()

        val NO_VIDEO = """
            {"@type": "Recipe", "name": "Salade",
             "recipeInstructions": [{"@type": "HowToStep", "text": "Mélangez."}]}
        """.trimIndent()
    }
}
