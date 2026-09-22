package org.opensources.umai.cooking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.data.RecipeRepository

data class CookingUiState(
    val recipe: Recipe? = null,
    val currentStep: Int = 0,
    val loading: Boolean = true,
    val error: NetworkError? = null,
    val keepScreenOn: Boolean = true,
) {
    val steps: List<RecipeStep> get() = recipe?.steps.orEmpty()
    val stepCount: Int get() = steps.size
    val step: RecipeStep? get() = steps.getOrNull(currentStep)
    val hasPrevious: Boolean get() = currentStep > 0
    val hasNext: Boolean get() = currentStep < stepCount - 1
    val isLastStep: Boolean get() = stepCount > 0 && currentStep == stepCount - 1

    /**
     * Mealie links steps to ingredients through `ingredientReferences`; when a
     * step declares none, the whole ingredient list stays available instead.
     */
    val ingredientsForStep: List<RecipeIngredient>
        get() {
            val references = step?.ingredientReferenceIds.orEmpty()
            val all = recipe?.ingredients.orEmpty()
            if (references.isEmpty()) return emptyList()
            return all.filter { it.referenceId != null && it.referenceId in references }
        }
}

class CookingViewModel(
    private val slug: String,
    private val recipeRepository: RecipeRepository,
    keepScreenOn: Flow<Boolean>,
) : ViewModel() {

    private val _state = MutableStateFlow(CookingUiState())
    val state: StateFlow<CookingUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            val enabled = keepScreenOn.first()
            _state.update { it.copy(keepScreenOn = enabled) }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = recipeRepository.recipe(slug)) {
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
                is ApiResult.Success -> _state.update {
                    it.copy(recipe = result.value, loading = false, error = null, currentStep = 0)
                }
            }
        }
    }

    fun next() = _state.update {
        if (it.hasNext) it.copy(currentStep = it.currentStep + 1) else it
    }

    fun previous() = _state.update {
        if (it.hasPrevious) it.copy(currentStep = it.currentStep - 1) else it
    }

    fun goToStep(index: Int) = _state.update {
        if (index in it.steps.indices) it.copy(currentStep = index) else it
    }

    companion object {
        fun factory(container: AppContainer, slug: String) = viewModelFactory {
            initializer {
                CookingViewModel(
                    slug = slug,
                    recipeRepository = container.recipeRepository,
                    keepScreenOn = container.preferencesRepository.preferences
                        .map { it.keepScreenOnWhileCooking },
                )
            }
        }
    }
}
