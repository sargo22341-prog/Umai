package org.opensources.umai.provider.site750g

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Site750gProviderTest {

    private val source = "https://www.750g.com/pavlova-aux-fruits-rouges-et-coulis-au-balsamique-r204378.htm"

    @Test
    fun `only the recipe pages of 750g are handled`() {
        assertTrue(Site750gProvider.handles(source))
        assertTrue(Site750gProvider.handles("https://750g.com/gateau-au-chocolat-r41427.htm"))
        assertFalse(Site750gProvider.handles("https://www.750g.com/recherche/?q=gateau"))
        assertFalse(Site750gProvider.handles("https://www.not750g.com/gateau-r41427.htm"))
        assertFalse(Site750gProvider.handles("https://www.marmiton.org/recettes/recette_brownies_16951.aspx"))
        assertFalse(Site750gProvider.handles("pas une adresse"))
    }

    @Test
    fun `the photo of each step is attached to its step`() {
        val media = Site750gProvider.media(Json.parseToJsonElement(PAVLOVA), source)!!

        assertNull(media.video)
        assertEquals(
            mapOf(
                1 to "https://static.750g.com/images/1200-675/b1adf7b0/pavlova-ingredients.jpg",
                2 to "https://static.750g.com/images/1200-675/ed927c43/pavlova-pap-1.jpg",
            ),
            media.stepPhotos,
        )
    }

    @Test
    fun `a recipe whose steps have no photo brings none`() {
        val media = Site750gProvider.media(Json.parseToJsonElement(WITHOUT_PHOTOS), source)!!

        assertTrue(media.stepPhotos.isEmpty())
    }

    @Test
    fun `a page without recipe gives nothing`() {
        assertNull(Site750gProvider.media(Json.parseToJsonElement("""{"@type":"WebPage"}"""), source))
    }

    private companion object {
        // Shaped like the schema of a 750g "pas à pas" page: the third step
        // shows the picture of the whole recipe, which is not a step photo.
        val PAVLOVA = """
        {
          "@context":"https://schema.org","@type":"Recipe","name":"Pavlova aux fruits rouges",
          "image":["https://static.750g.com/images/1200-675/aa11/pavlova.jpg"],
          "recipeInstructions":[
            {"@type":"HowToStep","text":"Disposez tous les ingrédients.",
             "image":"https://static.750g.com/images/1200-675/b1adf7b0/pavlova-ingredients.jpg"},
            {"@type":"HowToStep","text":"Préchauffez le four à 120 degrés.",
             "image":"https://static.750g.com/images/1200-675/ed927c43/pavlova-pap-1.jpg"},
            {"@type":"HowToStep","text":"Servez.",
             "image":"https://static.750g.com/images/1200-675/aa11/pavlova.jpg"},
            {"@type":"HowToStep","text":"Régalez-vous."}
          ]
        }
        """.trimIndent()

        val WITHOUT_PHOTOS = """
        {"@type":"Recipe","name":"Gâteau","recipeInstructions":[
          {"@type":"HowToStep","text":"Préchauffer le four."},"Mélanger."
        ]}
        """.trimIndent()
    }
}
