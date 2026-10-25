package org.opensources.umai.recipe.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.NetworkError

class IngredientCatalogWriterTest {
    private val fake = FakeMealieServer()
    private val writer = IngredientCatalogWriter(fake.api())
    private val json = MealieClientFactory.json

    @After fun close() = fake.shutdown()

    @Test fun `names receive existing catalog ids before the recipe is written`() = runBlocking {
        fake.enqueueJson("""{"items":[{"id":"f1","name":"sel marin"}]}""")
        fake.enqueueJson("""{"items":[{"id":"u1","name":"g"}]}""")
        val document = json.parseToJsonElement("""{"recipeIngredient":[
            {"food":{"name":"sel marin"},"unit":{"name":"g"},"quantity":150}]}""").jsonObject
        val result = withTimeout(5000) { writer.resolve(document) } as ApiResult.Success
        val line = result.value.getValue("recipeIngredient").jsonArray.single().jsonObject
        assertEquals("f1", line.getValue("food").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("u1", line.getValue("unit").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(2, fake.server.requestCount)
    }

    @Test fun `new names are created once and existing identifiers stay untouched`() = runBlocking {
        fake.enqueueJson("""{"items":[]}""")
        fake.enqueueJson("""{"id":"f2","name":"farine"}""")
        val document = json.parseToJsonElement("""{"recipeIngredient":[
            {"food":{"name":"farine"},"unit":{"id":"u1","name":"g"}},
            {"food":{"name":"farine"}}]}""").jsonObject
        val result = withTimeout(5000) { writer.resolve(document) }
        assertTrue(result is ApiResult.Success)
        assertEquals("GET", fake.takeRequest().method)
        assertEquals("POST", fake.takeRequest().method)
        assertEquals(2, fake.server.requestCount)
    }

    @Test fun `catalog failure is surfaced before any recipe update`() = runBlocking {
        fake.enqueueError(403)
        val document = json.parseToJsonElement("""{"recipeIngredient":[{"food":{"name":"salt"}}]}""").jsonObject
        assertEquals(ApiResult.Failure(NetworkError.Forbidden), withTimeout(5000) { writer.resolve(document) })
        assertEquals(1, fake.server.requestCount)
    }

    @Test fun `a partial catalog never causes an accidental duplicate creation`() = runBlocking {
        fake.enqueueJson("""{"total":201,"items":[]}""")
        val document = json.parseToJsonElement("""{"recipeIngredient":[{"unit":{"name":"g"}}]}""").jsonObject
        assertEquals(ApiResult.Failure(NetworkError.InvalidResponse), withTimeout(5000) { writer.resolve(document) })
        assertEquals(1, fake.server.requestCount)
    }
}
