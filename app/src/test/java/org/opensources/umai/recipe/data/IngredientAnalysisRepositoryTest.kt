package org.opensources.umai.recipe.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.IngredientAnalysis
import org.opensources.umai.recipe.domain.IngredientChoice

class IngredientAnalysisRepositoryTest {
    private val fake = FakeMealieServer()
    private val repository = IngredientAnalysisRepository({ fake.api() })

    @After fun close() = fake.shutdown()

    @Test fun `batch parsing preserves text order references and fractional quantities`() = runBlocking {
        fake.enqueueJson("""[{"input":"half an apple","confidence":{"quantity":0.98,"food":1,"unit":1},
            "ingredient":{"quantity":0.5,"food":{"id":"f1","name":"apple"},"note":"peeled"}}]""")
        val line = DraftIngredient("half an apple", referenceId = "keep-me")
        val result = withTimeout(5000) { repository.analyze(listOf(line)) } as ApiResult.Success
        val parsed = result.value.single()
        assertEquals("keep-me", parsed.referenceId)
        assertEquals(line.text, parsed.text)
        assertEquals(0.5, parsed.analysis?.quantity)
        assertEquals("peeled", parsed.analysis?.note)
        assertTrue(parsed.analysis?.isResolved == true)
        val request = fake.takeRequest()
        assertEquals("/api/parser/ingredients", request.url.encodedPath)
        assertTrue(request.body?.utf8().orEmpty().contains("half an apple"))
    }

    @Test fun `resolved ingredients are not parsed again`() = runBlocking {
        val line = DraftIngredient("2 apples", analysis = IngredientAnalysis(2.0, IngredientChoice("apple")))
        assertEquals(ApiResult.Success(listOf(line)), repository.analyze(listOf(line)))
        assertEquals(0, fake.server.requestCount)
    }

    @Test fun `a missing quantity a missing food and low confidence require review`() = runBlocking {
        fake.enqueueJson("""[
            {"ingredient":{"quantity":0,"food":{"id":"f1","name":"salt"}}},
            {"ingredient":{"quantity":2}},
            {"confidence":{"quantity":0.3},"ingredient":{"quantity":2,"food":{"id":"f1","name":"apple"}}}
        ]""")
        val lines = listOf("salt", "2 unknown", "2 apples").map { DraftIngredient(it) }
        val result = withTimeout(5000) { repository.analyze(lines) } as ApiResult.Success
        assertTrue(result.value.all { it.analysis?.isResolved == false })
        assertEquals(0.3, result.value.last().analysis?.confidence)
    }

    @Test fun `partial and reordered responses are rejected instead of assigning the wrong quantities`() = runBlocking {
        fake.enqueueJson("[]")
        assertEquals(ApiResult.Failure(NetworkError.InvalidResponse), repository.analyze(listOf(DraftIngredient("2 apples"))))
        fake.enqueueJson("""[{"input":"other line","ingredient":{"quantity":2}}]""")
        assertEquals(ApiResult.Failure(NetworkError.InvalidResponse), repository.analyze(listOf(DraftIngredient("2 apples"))))
    }

    @Test fun `a server failure stays distinct from an empty list`() = runBlocking {
        fake.enqueueError(403)
        assertEquals(ApiResult.Failure(NetworkError.Forbidden), repository.analyze(listOf(DraftIngredient("2 apples"))))
        assertEquals(ApiResult.Failure(NetworkError.Unauthorized), IngredientAnalysisRepository({ null }).analyze(emptyList()))
    }

    @Test fun `local AI runs only after Mealie and suggestions still need confirmation`() = runBlocking {
        fake.enqueueJson("""[{"ingredient":{"quantity":0,"food":{"name":"apple"}}}]""")
        fake.enqueueJson("""{"items":[{"id":"f1","name":"apple"}]}""")
        fake.enqueueJson("""{"items":[{"id":"u1","name":"gram"}]}""")
        val model = Model("""{"quantity":200,"food":0,"unit":0,"note":"peeled"}""")
        val assisted = IngredientAnalysisRepository({ fake.api() }, { model })
        val result = withTimeout(5000) { assisted.analyze(listOf(DraftIngredient("some apple"))) } as ApiResult.Success
        assertTrue(result.value.single().analysis?.modelSuggestion == true)
        assertFalse(result.value.single().analysis?.isResolved == true)
        assertEquals("f1", result.value.single().analysis?.food?.id)
        assertEquals("/api/parser/ingredients", fake.takeRequest().url.encodedPath)
        assertEquals("/api/foods", fake.takeRequest().url.encodedPath)
        assertEquals("/api/units", fake.takeRequest().url.encodedPath)
        assertTrue(model.request?.system.orEmpty().contains("Never guess weights"))
    }

    @Test fun `AI cannot invent a food identifier`() = runBlocking {
        assertNull(IngredientModelParser.suggest(Model("""{"quantity":2,"food":99,"unit":-1,"note":""}"""),
            "2 apples", listOf(IngredientChoice("apple", "f1")), emptyList()))
    }

    @Test fun `explicit grams and pinches replace inconsistent server slices without AI`() = runBlocking {
        fake.enqueueJson("""[
            {"ingredient":{"quantity":150,"food":{"id":"f1","name":"peanuts"},"unit":{"id":"u1","name":"tranche"}}},
            {"ingredient":{"quantity":1,"food":{"id":"f2","name":"fleur de sel"},"unit":{"id":"u1","name":"tranche"}}}
        ]""")
        val lines = listOf("150 g (1 Tasse) de cacahuètes grillées", "1 à 2 pincées de sel marin (facultatif)").map { DraftIngredient(it) }
        val result = withTimeout(5000) { repository.analyze(lines) } as ApiResult.Success
        assertEquals(150.0, result.value.first().analysis?.quantity)
        assertEquals("g", result.value.first().analysis?.unit?.name)
        assertEquals("pincée", result.value.last().analysis?.unit?.name)
        assertEquals("sel marin", result.value.last().analysis?.food?.name)
        assertNull(result.value.last().analysis?.quantity)
        assertTrue(result.value.all { it.analysis?.needsReview == true })
    }

    @Test fun `unit abbreviations find catalog names and exact foods precede references`() = runBlocking {
        fake.enqueueJson("""{"items":[{"id":"f1","name":"fleur de sel"},{"id":"f2","name":"sel marin"}]}""")
        fake.enqueueJson("""{"items":[{"id":"u1","name":"tranche"},{"id":"u2","name":"gramme","abbreviation":"g"}]}""")
        val result = withTimeout(5000) { repository.choices("sel marin", "g") } as ApiResult.Success
        assertEquals("f2", result.value.foods.first().id)
        assertEquals("u2", result.value.units.single().id)
    }

    @Test fun `singular food names rank above unrelated foods sharing an adjective`() = runBlocking {
        fake.enqueueJson("""{"items":[{"id":"f1","name":"algue grillée"},{"id":"f2","name":"cacahuète grillée"}]}""")
        fake.enqueueJson("""{"items":[]}""")
        val result = withTimeout(5000) { repository.choices("cacahuètes grillées", "g") } as ApiResult.Success
        assertEquals("f2", result.value.foods.first().id)
    }

    @Test fun `an empty catalog can receive a named suggestion requiring confirmation`() = runBlocking {
        val suggested = IngredientModelParser.suggest(Model("""{"quantity":200,"food":-1,"unit":-1,
            "foodName":"flour","unitName":"g","note":"sifted"}"""), "200 g flour, sifted", emptyList(), emptyList())
        assertEquals("flour", suggested?.food?.name)
        assertNull(suggested?.food?.id)
        assertTrue(suggested?.needsReview == true)
    }

    private class Model(private val answer: String) : LanguageModel {
        var request: LlmRequest? = null
        override val contextSize = 4096
        override suspend fun isReady() = true
        override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
            this.request = request
            return LlmOutcome.Success(answer)
        }
    }
}
