package org.opensources.umai.provider.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaOrgRecipeTest {

    @Test
    fun `steps come in the order of the page, sections opened where they stand`() {
        val recipe = Json.parseToJsonElement(
            """{"@type":"Recipe","recipeInstructions":[
                "Préchauffez.",
                {"@type":"HowToSection","itemListElement":[
                  {"@type":"HowToStep","text":"Coupez."},{"@type":"HowToStep","text":"Mélangez."}]},
                {"@type":"HowToStep","text":"Servez."}]}""",
        ).jsonObject

        val steps = SchemaOrgRecipe.steps(recipe).map { step ->
            (step as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: step.jsonPrimitive.content
        }

        assertEquals(listOf("Préchauffez.", "Coupez.", "Mélangez.", "Servez."), steps)
    }

    @Test
    fun `addresses are found in the order of the page, relative ones resolved`() {
        val value = Json.parseToJsonElement("""[{"contentUrl":"/a.jpg"},"https://cdn.example/b.jpg"]""")

        assertEquals("https://site.example/a.jpg", SchemaOrgRecipe.url(value, BASE))
        assertEquals(listOf("https://site.example/a.jpg", "https://cdn.example/b.jpg"), SchemaOrgRecipe.urls(value, BASE))
    }

    @Test
    fun `deeply nested instructions are read without running out of stack`() {
        var nested: JsonElement = JsonPrimitive("Servez.")
        repeat(DEPTH) { nested = JsonArray(listOf(nested)) }

        val steps = SchemaOrgRecipe.steps(JsonObject(mapOf("recipeInstructions" to nested)))

        assertEquals(listOf(JsonPrimitive("Servez.")), steps)
    }

    private companion object {
        const val BASE = "https://site.example/recette"

        /** Far beyond what a recursive reading survives on the stack of a test thread. */
        const val DEPTH = 100_000
    }
}
