package org.opensources.umai.planning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.planning.data.MealPlanRepository
import java.time.LocalDate

data class PlanRecipePickerUiState(
    val date: LocalDate,
    val mealType: MealType,
    val adding: Boolean = false,
    /** Set once Mealie holds the new entry; the screen then closes. */
    val added: Boolean = false,
    val error: NetworkError? = null,
)

/** Puts the recipe picked in the search on the day and the meal chosen before. */
class PlanRecipePickerViewModel(
    date: LocalDate,
    mealType: MealType,
    private val mealPlanRepository: MealPlanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanRecipePickerUiState(date, mealType))
    val state: StateFlow<PlanRecipePickerUiState> = _state.asStateFlow()

    fun add(recipe: RecipeSummary) {
        val current = _state.value
        if (current.adding || current.added) return
        _state.update { it.copy(adding = true, error = null) }
        viewModelScope.launch {
            val result = mealPlanRepository.add(date = current.date, type = current.mealType, recipeId = recipe.id)
            _state.update {
                when (result) {
                    is ApiResult.Failure -> it.copy(adding = false, error = result.error)
                    is ApiResult.Success -> it.copy(adding = false, added = true)
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        fun factory(container: AppContainer, date: LocalDate, mealType: MealType) = viewModelFactory {
            initializer { PlanRecipePickerViewModel(date, mealType, container.mealPlanRepository) }
        }
    }
}
