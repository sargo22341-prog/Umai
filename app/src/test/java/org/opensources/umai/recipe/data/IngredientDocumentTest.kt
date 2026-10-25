package org.opensources.umai.recipe.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.opensources.umai.core.format.IngredientText
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.IngredientAnalysis
import org.opensources.umai.recipe.domain.IngredientChoice

class IngredientDocumentTest {
    @Test fun `confirmed quantities persist with references and scale portions`() {
        val json = MealieClientFactory.json
        val document = json.parseToJsonElement("""{"id":"r1","slug":"test","name":"Test","recipeServings":2}""").jsonObject
        val original = json.decodeFromJsonElement(RecipeDetailDto.serializer(), document).toEditableDraft()
        val edited = original.copy(ingredients = listOf(DraftIngredient("200 g flour", "ref1", analysis =
            IngredientAnalysis(200.0, IngredientChoice("flour", "f1"), IngredientChoice("g", "u1")))))
        val written = document.withEdits(original, edited, json)
        val line = written.getValue("recipeIngredient").jsonArray.single().jsonObject
        assertEquals("ref1", line.getValue("referenceId").jsonPrimitive.content)
        assertEquals("200.0", line.getValue("quantity").jsonPrimitive.content)
        val recipe = json.decodeFromJsonElement(RecipeDetailDto.serializer(), written).toDomain()!!
        assertEquals("400 g flour", IngredientText.format(recipe.ingredients.single(), scale = 2.0))
        assertEquals("200 g flour", line.getValue("originalText").jsonPrimitive.content)
    }

    @Test fun `editing free text invalidates the old quantity`() {
        val json = MealieClientFactory.json
        val document = json.parseToJsonElement("""{"id":"r1","slug":"test","name":"Test",
            "recipeIngredient":[{"title":"Dough","quantity":200,"food":{"id":"f1","name":"flour"},"referenceId":"ref1","display":"200 g flour"}]}""").jsonObject
        val original = json.decodeFromJsonElement(RecipeDetailDto.serializer(), document).toEditableDraft()
        val changed = original.copy(ingredients = original.ingredients.map { it.copy(text = "300 g flour") })
        val written: JsonObject = document.withEdits(original, changed, json)
        val line = written.getValue("recipeIngredient").jsonArray.single().jsonObject
        assertEquals("0.0", line.getValue("quantity").jsonPrimitive.content)
        assertFalse(line.containsKey("food"))
        assertEquals("Dough", line.getValue("title").jsonPrimitive.content)
    }
}
