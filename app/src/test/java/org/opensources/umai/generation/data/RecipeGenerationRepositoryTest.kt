package org.opensources.umai.generation.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.llm.domain.*
import org.opensources.umai.planning.data.FoodTableRepository
import org.opensources.umai.recipe.domain.ImportOutcome

class RecipeGenerationRepositoryTest {
    private val server = FakeMealieServer()
    private var key = "first"
    private var ready = true
    private var answer = VALID
    private var beforeAnswer: () -> Unit = {}
    private val model = object : LanguageModel {
        override val contextSize = 4096
        override suspend fun isReady() = ready
        override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
            beforeAnswer()
            return LlmOutcome.Success(answer)
        }
    }
    private val api = server.api()
    private val repository = RecipeGenerationRepository(
        { api }, { key }, model, FoodTableRepository { throw IOException("Not available in this test") }, { "en" },
        { "Estimated calories per serving" },
    )

    @After
    fun close() = server.shutdown()

    @Test
    fun savesCompleteRecipeWithEstimatedCaloriesPerServingAndNoImage() = runBlocking {
        withTimeout(5000) {
            server.enqueueJson("""{"items":[]}""")
            server.enqueueJson("\"soup\"")
            server.enqueueJson("""{"slug":"soup","tags":[]}""")
            val result = repository.generate("Vegan soup") as ImportOutcome.Imported
            assertEquals("soup", result.recipe.slug)
            server.takeRequest()
            val request = server.takeRequest()
            assertEquals("/api/recipes/create/html-or-json", request.url.encodedPath)
            val body = Json.parseToJsonElement(checkNotNull(request.body).utf8()).jsonObject
            val schema = Json.parseToJsonElement(body.getValue("data").jsonPrimitive.content).jsonObject
            assertEquals("100 kcal", schema.getValue("nutrition").jsonObject.getValue("calories").jsonPrimitive.content)
            assertFalse(schema.containsKey("image"))
            assertTrue(schema.getValue("description").jsonPrimitive.content.contains("Estimated calories"))
            assertTrue(schema.containsKey("recipeIngredient"))
            assertTrue(schema.containsKey("recipeInstructions"))
            assertEquals(listOf("200 g lentils"), schema.getValue("recipeIngredient").jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test
    fun noModelDoesNotReadOrWriteMealie() = runBlocking {
        ready = false
        assertEquals(ImportOutcome.GenerationFailed, repository.generate("Soup"))
        assertEquals(0, server.server.requestCount)
    }

    @Test
    fun incompleteAnswerIsNeverSaved() = runBlocking {
        withTimeout(5000) {
            server.enqueueJson("""{"items":[]}""")
            answer = "{}"
            assertEquals(ImportOutcome.GenerationFailed, repository.generate("Soup"))
            assertEquals(1, server.server.requestCount)
        }
    }

    @Test
    fun changingInstanceDuringGenerationPreventsSaving() = runBlocking {
        withTimeout(5000) {
            server.enqueueJson("""{"items":[]}""")
            beforeAnswer = { key = "second" }
            assertEquals(ImportOutcome.Failed(NetworkError.Unauthorized), repository.generate("Soup"))
            assertEquals(1, server.server.requestCount)
        }
    }

    @Test
    fun serverFailureIsVisible() = runBlocking {
        withTimeout(5000) {
            server.enqueueError(403)
            assertEquals(ImportOutcome.Failed(NetworkError.Forbidden), repository.generate("Soup"))
        }
    }

    @Test
    fun referenceFoodCaloriesOverrideTheModelsEstimate() = runBlocking {
        withTimeout(5000) {
            server.enqueueJson("""{"items":[]}""")
            server.enqueueJson("\"apples\"")
            server.enqueueJson("""{"slug":"apples","tags":[]}""")
            answer = VALID.replace("lentils", "apple")
            val foodTable = FoodTableRepository { name ->
                org.opensources.umai.planning.domain.FoodTableFixtures.asset(name).inputStream()
            }
            val generator = RecipeGenerationRepository({ api }, { key }, model, foodTable, { "en" }, { "Estimated calories" })
            assertTrue(generator.generate("Apples for two") is ImportOutcome.Imported)
            server.takeRequest()
            val body = Json.parseToJsonElement(checkNotNull(server.takeRequest().body).utf8()).jsonObject
            val schema = Json.parseToJsonElement(body.getValue("data").jsonPrimitive.content).jsonObject
            assertEquals("54 kcal", schema.getValue("nutrition").jsonObject.getValue("calories").jsonPrimitive.content)
        }
    }

    @Test
    fun rejectsInvalidAmountsAndEmptyInstructions() {
        assertNull(GeneratedRecipe.parse(VALID.replace("200.0", "-1.0")))
        assertNull(GeneratedRecipe.parse(VALID.replace("Heat and serve.", "")))
        assertNull(GeneratedRecipe.parse(VALID.replace("\"servings\":2", "\"servings\":0")))
    }

    @Test
    fun everyIngredientLineUsesItsValidatedWeightWithoutRoundingItAway() {
        assertEquals("200 g lentils", GeneratedIngredient("lentils", 200.0, 200.0).line)
        assertEquals("2.5 g olive oil", GeneratedIngredient("olive oil", 2.5, 22.0).line)
        assertEquals("0.01 g pepper", GeneratedIngredient("pepper", 0.01, 0.0).line)
        assertNull(GeneratedRecipe.parse(VALID.replace("\"grams\":200.0", "\"grams\":0.001")))
    }

    @Test
    fun aNewCalorieTagRefreshesTheSearchFilters() = runBlocking {
        withTimeout(5000) {
            val tags = org.opensources.umai.recipe.data.CalorieTagRepository({ api }, { key })
            server.enqueueJson("""{"items":[]}""")
            assertTrue((tags.calorieTags() as org.opensources.umai.core.network.ApiResult.Success).value.isEmpty())
            server.enqueueJson("""{"items":[]}""")
            server.enqueueJson("\"soup\"")
            server.enqueueJson("""{"slug":"soup","tags":[],"nutrition":{"calories":"100 kcal"}}""")
            server.enqueueJson("""{"items":[]}""")
            server.enqueueJson("""{"id":"t1","name":"calorie-100","slug":"calorie-100"}""")
            server.enqueueJson("""{"slug":"soup"}""")
            val generator = RecipeGenerationRepository(
                { api }, { key }, model, FoodTableRepository { throw IOException("Test fallback") },
                { "en" }, { "Estimated calories" }, tags,
            )
            assertTrue(generator.generate("Soup") is ImportOutcome.Imported)
            server.enqueueJson("""{"items":[{"id":"t1","name":"calorie-100","slug":"calorie-100"}]}""")
            assertEquals(1, (tags.calorieTags() as org.opensources.umai.core.network.ApiResult.Success).value.size)
            assertEquals(8, server.server.requestCount)
        }
    }

    private companion object {
        const val VALID = """{"name":"Soup","description":"Vegan soup","servings":2,"prepMinutes":5,"cookMinutes":10,"ingredients":[{"food":"lentils","grams":200.0,"kcal":200.0}],"steps":["Heat and serve."]}"""
    }
}
