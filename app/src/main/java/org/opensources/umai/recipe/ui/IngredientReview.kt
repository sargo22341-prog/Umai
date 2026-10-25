package org.opensources.umai.recipe.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.data.IngredientAnalysisRepository
import org.opensources.umai.recipe.domain.IngredientChoices
import org.opensources.umai.recipe.domain.DraftFood
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.IngredientAnalysis
import org.opensources.umai.recipe.domain.RecipeDraft

data class IngredientReviewState(
    val busy: Boolean = false,
    val error: NetworkError? = null,
    val reviewing: DraftIngredient? = null,
    val choices: IngredientChoices = IngredientChoices(emptyList(), emptyList()),
    val searching: Boolean = false,
    val searched: Boolean = false,
)

/** Owns one bounded analysis run; results never overwrite edits made while it ran. */
class IngredientReview(
    private val scope: CoroutineScope,
    private val repository: IngredientAnalysisRepository,
    private val draft: () -> RecipeDraft,
    private val edit: ((RecipeDraft) -> RecipeDraft) -> Unit,
) {
    private val mutable = MutableStateFlow(IngredientReviewState())
    val state = mutable.asStateFlow()
    private var searchJob: Job? = null

    fun analyze() {
        if (mutable.value.busy) return
        scope.launch {
            // The panel shows errors and unresolved lines directly.
            val _ = analyzeNow()
        }
    }

    suspend fun analyzeNow(): Boolean {
        if (mutable.value.busy) return false
        val before = draft().ingredients
        mutable.update { it.copy(busy = true, error = null) }
        when (val result = repository.analyze(before)) {
            is ApiResult.Failure -> mutable.update { it.copy(busy = false, error = result.error) }
            is ApiResult.Success -> {
                edit { current -> if (current.ingredients == before) current.copy(ingredients = result.value) else current }
                mutable.update { it.copy(busy = false) }
                next()
            }
        }
        return mutable.value.error == null && draft().ingredients.all { it.text.isBlank() || it.analysis?.isResolved == true }
    }

    fun review(line: DraftIngredient) {
        searchJob?.cancel()
        mutable.update { it.copy(reviewing = line, error = null, choices = IngredientChoices(emptyList(), emptyList()), searching = false, searched = false) }
    }

    fun search(food: String, unit: String) {
        if (mutable.value.busy) return
        searchJob?.cancel()
        val line = mutable.value.reviewing
        mutable.update { it.copy(searching = true, searched = false, error = null, choices = IngredientChoices(emptyList(), emptyList())) }
        searchJob = scope.launch {
            delay(350)
            when (val result = repository.choices(food, unit)) {
                is ApiResult.Failure -> mutable.update { if (it.reviewing == line) it.copy(searching = false, searched = true, error = result.error) else it }
                is ApiResult.Success -> mutable.update { if (it.reviewing == line) it.copy(searching = false, searched = true, choices = result.value) else it }
            }
        }
    }

    fun confirm(analysis: IngredientAnalysis) {
        if (!analysis.isResolved || mutable.value.busy) return
        val line = mutable.value.reviewing ?: return
        edit { current -> current.copy(ingredients = current.ingredients.map {
            if (it == line) it.copy(analysis = analysis, food = analysis.food?.let { food -> DraftFood(food.name) }) else it
        }) }
        next()
    }

    fun dismiss() {
        searchJob?.cancel()
        mutable.update { IngredientReviewState(busy = it.busy) }
    }

    private fun next() {
        searchJob?.cancel()
        mutable.update { it.copy(reviewing = draft().ingredientsNeedingReview.firstOrNull(),
            choices = IngredientChoices(emptyList(), emptyList()), searching = false, searched = false) }
    }
}
