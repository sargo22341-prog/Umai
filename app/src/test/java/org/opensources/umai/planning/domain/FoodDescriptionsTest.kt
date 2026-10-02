package org.opensources.umai.planning.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest

class FoodDescriptionsTest {

    private class Model(private val ready: Boolean, private val outcome: LlmOutcome) : LanguageModel {
        val asked = mutableListOf<LlmRequest>()
        override suspend fun isReady() = ready
        override val contextSize = 4_096
        override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
            asked += request
            return outcome
        }
    }

    private val noModel = Model(ready = false, outcome = LlmOutcome.Failure(LlmFailure.NOT_READY))

    private fun descriptions(model: Model = noModel, table: FoodTable? = FoodTableFixtures.table) = FoodDescriptions(
        table = { table },
        model = ModelFoodEstimator(model),
        language = "fr",
        decimalSeparator = ',',
    )

    @Test
    fun `the foods typed are offered with the amount and calories they make`() = runBlocking {
        val suggestions = descriptions().suggestions("2 pommes").orEmpty()

        val first = suggestions.first()
        assertEquals("2 × Pomme", first.title)
        assertEquals(300.0, first.item.amount)
        assertEquals(162, first.item.calories)
        // The same food by its name in the table, whose portion is not known.
        assertEquals("2 × Pomme, chair et peau, crue", suggestions[1].title)
        assertNull(suggestions[1].item.amount)
    }

    @Test
    fun `nothing is offered for several foods, nor without the table`() = runBlocking {
        assertTrue(descriptions().suggestions("2 pommes, 1 café").orEmpty().isEmpty())
        assertNull(descriptions(table = null).suggestions("pomme"))
    }

    @Test
    fun `basic foods are found in the table, without asking the model`() = runBlocking {
        val model = Model(ready = true, outcome = LlmOutcome.Success("{}"))

        val outcome = descriptions(model).describe("2 pommes, 3 cafés sans sucre")

        val estimate = (outcome as DescriptionOutcome.Estimated).estimate
        assertFalse(estimate.byModel)
        assertEquals(listOf(300.0, 450.0), estimate.items.map { it.amount })
        assertEquals(750.0, estimate.amount)
        assertTrue(model.asked.isEmpty())
    }

    @Test
    fun `a meal the table lacks is weighed by the model, each food looked up in the table`() = runBlocking {
        val model = Model(
            ready = true,
            outcome = LlmOutcome.Success(
                """{"foods":[{"name":"saumon fumé","grams":60,"kcal":100},{"name":"pâte à pizza","grams":200,"kcal":540}]}""",
            ),
        )

        val outcome = descriptions(model).describe(" 1 pizza saumon raviole ")

        val estimate = (outcome as DescriptionOutcome.Estimated).estimate
        assertTrue(estimate.byModel)
        assertEquals("1 pizza saumon raviole", model.asked.single().user)
        val (salmon, dough) = estimate.items
        // The table's values win over the model's guess.
        assertEquals(FoodSource.TABLE, salmon.source)
        assertEquals(121, salmon.calories)
        assertEquals(FoodSource.MODEL, dough.source)
        assertEquals("Pâte à pizza", dough.name)
        assertEquals(540, dough.calories)
        assertEquals(260.0, estimate.amount)
    }

    @Test
    fun `without a model a food the table lacks is not found`() = runBlocking {
        assertEquals(DescriptionOutcome.NotFound, descriptions().describe("pizza saumon raviole"))
    }

    @Test
    fun `the model's failures are told apart`() = runBlocking {
        val failing = Model(ready = true, outcome = LlmOutcome.Failure(LlmFailure.LOAD_FAILED))
        val empty = Model(ready = true, outcome = LlmOutcome.Success("""{"foods":[]}"""))

        assertEquals(
            DescriptionOutcome.ModelFailed(LlmFailure.LOAD_FAILED),
            descriptions(failing).describe("pizza saumon raviole"),
        )
        assertEquals(DescriptionOutcome.ModelFoundNothing, descriptions(empty).describe("pizza saumon raviole"))
        assertEquals(DescriptionOutcome.TableUnreadable, descriptions(table = null).describe("pomme"))
    }

    @Test
    fun `the model is told what to answer, in a short answer`() {
        val request = ModelFoodEstimator.REQUEST

        assertTrue(request.jsonSchema.contains("\"grams\""))
        assertEquals(0f, request.temperature)
        assertNull(request.media)
    }

    @Test
    fun `the model's answer keeps only the foods it could weigh`() {
        val answer = """{"foods":[
            {"name":"raviolis","grams":150,"kcal":250.0},
            {"name":"","grams":10,"kcal":5},
            {"name":"sauce","grams":0,"kcal":40},
            {"name":"fromage","grams":"30","kcal":-1}
        ]}"""

        assertEquals(listOf(ModelIngredient("raviolis", 150.0, 250.0)), ModelFoodEstimator.parse(answer))
        assertTrue(ModelFoodEstimator.parse("not json").isEmpty())
    }
}
