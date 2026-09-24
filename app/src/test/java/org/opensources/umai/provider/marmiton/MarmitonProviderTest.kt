package org.opensources.umai.provider.marmiton

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarmitonProviderTest {

    private val source = "https://www.marmiton.org/recettes/recette_brownies_16951.aspx"

    @Test
    fun `only the recipe pages of Marmiton are handled`() {
        assertTrue(MarmitonProvider.handles(source))
        assertTrue(MarmitonProvider.handles("https://marmiton.org/recettes/recette_pavlova_16007.aspx"))
        assertFalse(MarmitonProvider.handles("https://www.marmiton.org/recettes/recherche.aspx?aqt=gateau"))
        assertFalse(MarmitonProvider.handles("https://www.notmarmiton.org/recettes/recette_x_1.aspx"))
        assertFalse(MarmitonProvider.handles("https://jow.fr/recipes/carbonara-xyz"))
    }

    @Test
    fun `step photos are asked for in a size fit for a step`() {
        assertEquals(
            "https://assets.afcdn.com/recipe/20180413/78574_w1024.webp",
            MarmitonProvider.stepSize("https://assets.afcdn.com/recipe/20180413/78574_w225h225c1cx1728cy2592.webp"),
        )
        // The original can weigh megabytes.
        assertEquals(
            "https://assets.afcdn.com/recipe/20180413/78574_w1024.jpg",
            MarmitonProvider.stepSize("https://assets.afcdn.com/recipe/20180413/78574_origin.jpg"),
        )
    }

    @Test
    fun `an address outside the picture host of Marmiton is kept as is`() {
        val elsewhere = "https://example.org/recipe/20180413/78574_w40h40c1.webp"
        assertEquals(elsewhere, MarmitonProvider.stepSize(elsewhere))
        val unknownShape = "https://assets.afcdn.com/video/20180413/78574.mp4"
        assertEquals(unknownShape, MarmitonProvider.stepSize(unknownShape))
    }

    @Test
    fun `a step with a photo gets it, a picture of the whole recipe is not a step photo`() {
        val media = MarmitonProvider.media(Json.parseToJsonElement(RECIPE), source)!!

        assertNull(media.video)
        assertEquals(mapOf(2 to "https://assets.afcdn.com/recipe/20200101/1234_w1024.jpg"), media.stepPhotos)
    }

    @Test
    fun `the usual Marmiton recipe, without step photos, brings none`() {
        val media = MarmitonProvider.media(Json.parseToJsonElement(WITHOUT_PHOTOS), source)!!

        assertTrue(media.stepPhotos.isEmpty())
    }

    private companion object {
        val RECIPE = """
        {"@type":"Recipe","name":"Brownies",
         "image":["https://assets.afcdn.com/recipe/20180413/78574_w1024h768c1cx1728cy2592.webp"],
         "recipeInstructions":[
           {"@type":"HowToStep","text":"Faites fondre le chocolat.",
            "image":"https://assets.afcdn.com/recipe/20180413/78574_w600h600c1.webp"},
           {"@type":"HowToStep","text":"Battez les oeufs avec le sucre.",
            "image":"https://assets.afcdn.com/recipe/20200101/1234_origin.jpg"},
           {"@type":"HowToStep","text":"Enfournez 15 min."}
         ]}
        """.trimIndent()

        // What Marmiton pages publish today: text-only steps.
        val WITHOUT_PHOTOS = """
        {"@type":"Recipe","name":"Brownies","recipeInstructions":[
          {"@type":"HowToStep","text":"Faites fondre le chocolat."},
          {"@type":"HowToStep","text":"Enfournez 15 min."}
        ]}
        """.trimIndent()
    }
}
