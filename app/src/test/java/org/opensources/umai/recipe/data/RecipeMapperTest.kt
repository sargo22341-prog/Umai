package org.opensources.umai.recipe.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeSummaryDto

/** Payloads below are trimmed copies of real Mealie v3 responses. */
class RecipeMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun `summary with a picture exposes its cache token`() {
        val dto = json.decodeFromString<RecipeSummaryDto>(
            """
            {
              "id": "e1583e04-1e01-4164-8ffd-035ffa755434",
              "name": "Poulet au curry",
              "slug": "poulet-au-curry",
              "image": "73",
              "recipeServings": 1.0,
              "totalTime": "15 minutes",
              "description": "Un curry tout doux",
              "recipeCategory": [{"id":"c1","name":"Plat","slug":"plat","recipeCount":3}],
              "tags": [{"id":"t1","name":"Poulet","slug":"poulet","recipeCount":5}],
              "rating": 4.0
            }
            """.trimIndent(),
        )
        val recipe = dto.toDomain()
        assertNotNull(recipe)
        assertEquals("Poulet au curry", recipe!!.name)
        assertTrue(recipe.hasImage)
        assertEquals("73", recipe.imageToken)
        assertEquals(1, recipe.categories.size)
        assertEquals("Poulet", recipe.tags.single().name)
        assertEquals(4.0, recipe.rating!!, 0.001)
    }

    @Test
    fun `summary without a picture is mapped and flagged as image-less`() {
        val dto = json.decodeFromString<RecipeSummaryDto>(
            """{"id":"abc","name":"Sans photo","slug":"sans-photo","image":null}""",
        )
        val recipe = dto.toDomain()!!
        assertFalse(recipe.hasImage)
        assertNull(recipe.imageToken)
    }

    @Test
    fun `an image sent as a number is still read`() {
        val dto = json.decodeFromString<RecipeSummaryDto>(
            """{"id":"abc","name":"Photo","slug":"photo","image":149}""",
        )
        assertEquals("149", dto.toDomain()!!.imageToken)
    }

    @Test
    fun `a summary without an id is dropped rather than shown broken`() {
        val dto = json.decodeFromString<RecipeSummaryDto>("""{"name":"Orpheline","slug":"orpheline"}""")
        assertNull(dto.toDomain())
    }

    @Test
    fun `a missing name falls back to the slug`() {
        val dto = json.decodeFromString<RecipeSummaryDto>("""{"id":"abc","slug":"mon-plat"}""")
        assertEquals("mon-plat", dto.toDomain()!!.name)
    }

    @Test
    fun `a zero rating is treated as no rating`() {
        val dto = json.decodeFromString<RecipeSummaryDto>(
            """{"id":"abc","name":"x","slug":"x","rating":0}""",
        )
        assertNull(dto.toDomain()!!.rating)
    }

    @Test
    fun `steps carry their extracted images and their ingredient references`() {
        val dto = json.decodeFromString<RecipeDetailDto>(DETAIL)
        val recipe = dto.toDomain()!!

        assertEquals(3, recipe.steps.size)

        val withImage = recipe.steps[0]
        assertEquals("Etape avec image", withImage.title)
        assertEquals("Melangez le tout.", withImage.text)
        assertEquals(listOf("/api/media/recipes/abc/assets/etape1.jpg"), withImage.images)
        assertEquals(listOf("11111111-1111-4111-8111-111111111111"), withImage.ingredientReferenceIds)

        val htmlImage = recipe.steps[1]
        assertEquals(listOf("etape2.png"), htmlImage.images)
        assertEquals("Laissez reposer.", htmlImage.text)

        val withoutImage = recipe.steps[2]
        assertTrue(withoutImage.images.isEmpty())
        assertNull(withoutImage.title)
        assertEquals("Servir aussitot.", withoutImage.text)
    }

    @Test
    fun `ingredients keep the display string rendered by Mealie`() {
        val recipe = json.decodeFromString<RecipeDetailDto>(DETAIL).toDomain()!!
        assertEquals("2 citrons", recipe.ingredients[0].display)
        assertEquals("11111111-1111-4111-8111-111111111111", recipe.ingredients[0].referenceId)
    }

    @Test
    fun `an ingredient without a display string is rebuilt from its parts`() {
        val dto = json.decodeFromString<RecipeDetailDto>(
            """
            {
              "id":"abc","name":"x","slug":"x",
              "recipeIngredient":[
                {"quantity":0.5,"unit":{"id":"u","name":"cuillere","useAbbreviation":false},
                 "food":{"id":"f","name":"sel"},"note":"fin","display":""}
              ]
            }
            """.trimIndent(),
        )
        assertEquals("½ cuillere sel fin", dto.toDomain()!!.ingredients.single().display)
    }

    @Test
    fun `the structured parts of an ingredient survive the mapping`() {
        val dto = json.decodeFromString<RecipeDetailDto>(
            """
            {
              "id":"abc","name":"x","slug":"x",
              "recipeIngredient":[
                {"quantity":1,"unit":{"id":"u","name":"gramme","abbreviation":"g","useAbbreviation":true},
                 "food":{"id":"f","name":"sel","pluralName":"sels"},"display":"1 g sel"}
              ]
            }
            """.trimIndent(),
        )

        // They are what lets the line be re-rendered when the recipe is scaled,
        // and what is echoed back when it is sent to a shopping list.
        val ingredient = dto.toDomain()!!.ingredients.single()
        assertEquals("gramme", ingredient.unit?.name)
        assertEquals("g", ingredient.unit?.abbreviation)
        assertEquals(true, ingredient.unit?.useAbbreviation)
        assertEquals("sel", ingredient.food?.name)
        assertEquals("u", ingredient.unit?.id)
        assertEquals("f", ingredient.food?.id)
    }

    @Test
    fun `an entirely empty nutrition block is dropped`() {
        val dto = json.decodeFromString<RecipeDetailDto>(
            """{"id":"abc","name":"x","slug":"x","nutrition":{"calories":null,"fatContent":null}}""",
        )
        assertNull(dto.toDomain()!!.nutrition)
    }

    @Test
    fun `a populated nutrition block is kept`() {
        val dto = json.decodeFromString<RecipeDetailDto>(
            """{"id":"abc","name":"x","slug":"x","nutrition":{"calories":"390","proteinContent":"12"}}""",
        )
        val nutrition = dto.toDomain()!!.nutrition
        assertNotNull(nutrition)
        assertEquals("390", nutrition!!.calories)
        assertFalse(nutrition.isEmpty)
    }

    @Test
    fun `a recipe with no instruction maps to an empty step list`() {
        val dto = json.decodeFromString<RecipeDetailDto>(
            """{"id":"abc","name":"x","slug":"x","recipeInstructions":null}""",
        )
        assertTrue(dto.toDomain()!!.steps.isEmpty())
    }

    private companion object {
        val DETAIL = """
        {
          "id": "abc",
          "name": "Recette de test",
          "slug": "recette-de-test",
          "image": null,
          "recipeServings": 2.0,
          "totalTime": "PT25M",
          "settings": {"showNutrition": false, "showAssets": false},
          "recipeIngredient": [
            {"quantity":2.0,"unit":null,"food":null,"note":"citrons","display":"2 citrons",
             "referenceId":"11111111-1111-4111-8111-111111111111"},
            {"quantity":0.5,"unit":null,"food":null,"note":"huile","display":"1/2 huile",
             "referenceId":"22222222-2222-4222-8222-222222222222"}
          ],
          "recipeInstructions": [
            {"id":"s1","title":"Etape avec image","text":"Melangez le tout.\n\n![i](/api/media/recipes/abc/assets/etape1.jpg)",
             "ingredientReferences":[{"referenceId":"11111111-1111-4111-8111-111111111111"}]},
            {"id":"s2","title":"","text":"<img src=\"etape2.png\">Laissez reposer.","ingredientReferences":[]},
            {"id":"s3","title":"","text":"Servir aussitot.","ingredientReferences":[]}
          ]
        }
        """.trimIndent()
    }
}
