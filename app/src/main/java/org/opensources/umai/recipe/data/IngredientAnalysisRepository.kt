package org.opensources.umai.recipe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.dto.IngredientsRequestDto
import org.opensources.umai.core.network.valueOr
import org.opensources.umai.recipe.domain.DraftFood
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.IngredientAnalysis
import org.opensources.umai.recipe.domain.IngredientLineParser
import org.opensources.umai.recipe.domain.IngredientChoices
import org.opensources.umai.recipe.domain.IngredientChoice
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.planning.data.FoodTableRepository

/** Mealie always runs first. Model suggestions require a human confirmation. */
class IngredientAnalysisRepository(
    private val apiProvider: () -> MealieApi?,
    private val model: (() -> LanguageModel)? = null,
    private val referenceFoods: FoodTableRepository? = null,
    private val language: () -> String = { "en" },
) {
    suspend fun analyze(lines: List<DraftIngredient>): ApiResult<List<DraftIngredient>> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val provider = { api }
        val pending = lines.filter { it.text.isNotBlank() && it.analysis?.isResolved != true }
        if (pending.isEmpty()) return ApiResult.Success(lines)
        if (pending.size > MAX_LINES || pending.any { it.text.length > MAX_LINE_LENGTH }) {
            return ApiResult.Failure(NetworkError.InvalidResponse)
        }
        val parsed = provider.call { parseIngredients(IngredientsRequestDto(pending.map { it.text })) }
            .valueOr { return it }
        if (parsed.size != pending.size || parsed.zip(pending).any { (answer, line) ->
                answer.input != null && answer.input != line.text
            }) return ApiResult.Failure(NetworkError.InvalidResponse)
        val answers = parsed.zip(pending).associate { (answer, line) ->
            val local = IngredientLineParser.parse(line.text)
            val scores = answer.confidence
            val confidences = listOfNotNull(scores.average, scores.quantity, scores.food, scores.name, scores.unit)
            val uncertain = confidences.isEmpty() || answer.ingredient.food?.id == null || confidences.any { !it.isFinite() || it < MIN_CONFIDENCE || it > 1.0 }
            val server = answer.ingredient.toAnalysis().copy(needsReview = uncertain,
                confidence = confidences.filter { it in 0.0..1.0 }.minOrNull())
            val analysis = reconcile(local, server)
            line.referenceId to line.copy(analysis = analysis, food = analysis.food?.let { DraftFood(it.name) })
        }
        val result = lines.map { answers[it.referenceId] ?: it }
        if (result.all { it.text.isBlank() || it.analysis?.isResolved == true }) return ApiResult.Success(result)
        val languageModel = model?.invoke() ?: return ApiResult.Success(result)
        if (!languageModel.isReady()) return ApiResult.Success(result)
        val suggested = withTimeoutOrNull(MODEL_TIMEOUT_MS) {
            result.map { line ->
                if (line.text.isBlank() || line.analysis?.isResolved == true || IngredientLineParser.parse(line.text) != null) line
                else suggest(line, languageModel, api)
            }
        }
        // Timeout leaves the original text and Mealie result available for manual review.
        return ApiResult.Success(suggested ?: result)
    }

    private fun reconcile(local: IngredientAnalysis?,
        server: IngredientAnalysis,
    ): IngredientAnalysis {
        if (local == null) return server
        val sameUnit = IngredientLineParser.canonicalUnit(server.unit?.name.orEmpty()) == local.unit?.name
        val sameFood = IngredientLineParser.normalize(server.food?.name.orEmpty()) == IngredientLineParser.normalize(local.food?.name.orEmpty())
        if (sameUnit && sameFood && server.quantity == local.quantity && local.quantity != null) return server
        return local.copy(food = if (sameFood) server.food else local.food, unit = if (sameUnit) server.unit else local.unit)
    }

    private suspend fun suggest(line: DraftIngredient, model: LanguageModel, api: MealieApi): DraftIngredient {
        val provider = { api }
        val query = line.analysis?.food?.name?.takeIf { it.isNotBlank() } ?: line.text.take(80)
        val foods = provider.call { foods(search = query, perPage = 20).items }
            .valueOr { return line.copy(analysis = line.analysis?.copy(needsReview = true)) }
        val units = provider.call { units(perPage = 40).items }
            .valueOr { return line.copy(analysis = line.analysis?.copy(needsReview = true)) }
        val suggestion = IngredientModelParser.suggest(
            model, line.text, (foods.filter { it.id.isNotBlank() && it.name.isNotBlank() }.map { IngredientChoice(it.name, it.id) } + referenceChoices(query)).distinctBy { it.name },
            units.filter { !it.id.isNullOrBlank() && it.name.isNotBlank() }.map { IngredientChoice(it.name, it.id) },
        ) ?: return line
        return line.copy(analysis = suggestion.copy(needsReview = true, modelSuggestion = true), food = suggestion.food?.let { DraftFood(it.name) })
    }

    suspend fun choices(food: String, unit: String): ApiResult<IngredientChoices> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val provider = { api }
        val foods = provider.call { foods(search = food.take(80), perPage = 20).items }.valueOr { return it }
        val canonical = IngredientLineParser.canonicalUnit(unit)
        val units = provider.call { units(search = if (canonical == null) unit.take(80) else null, perPage = 200).items }.valueOr { return it }
        return ApiResult.Success(IngredientChoices(
            (foods.filter { it.id.isNotBlank() && it.name.isNotBlank() }.map { IngredientChoice(it.name, it.id) } + referenceChoices(food))
                .distinctBy { it.name }.sortedWith(compareBy<IngredientChoice> {
                    if (foodKey(it.name) == foodKey(food)) 0 else 1
                }.thenByDescending { choice -> foodKey(choice.name).split(' ').count { it in foodKey(food).split(' ') } }),
            units.filter { !it.id.isNullOrBlank() && it.name.isNotBlank() }
                .filter { canonical == null || listOfNotNull(it.name, it.pluralName, it.abbreviation, it.pluralAbbreviation)
                    .any { name -> IngredientLineParser.canonicalUnit(name) == canonical } }
                .map { IngredientChoice(it.name, it.id) },
        ))
    }

    private fun foodKey(value: String): String = IngredientLineParser.normalize(value).split(' ')
        .joinToString(" ") { it.removeSuffix("s") }

    private suspend fun referenceChoices(query: String): List<IngredientChoice> {
        val table = referenceFoods?.table() ?: return emptyList()
        return withContext(Dispatchers.Default) {
            table.search(query, limit = 8).map { IngredientChoice(it.name(language())) }
        }
    }

    private companion object {
        const val MAX_LINES = 100
        const val MAX_LINE_LENGTH = 2000
        const val MIN_CONFIDENCE = 0.8
        const val MODEL_TIMEOUT_MS = 120_000L
    }
}
