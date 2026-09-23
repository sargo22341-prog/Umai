package org.opensources.umai.planning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.search.domain.RecipeFilters
import java.time.LocalDate

data class PlanningUiState(
    /** First visible day; the window always starts the day before [anchor]. */
    val anchor: LocalDate = LocalDate.now(),
    val entriesByDay: Map<LocalDate, List<MealPlanEntry>> = emptyMap(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: NetworkError? = null,
    val mutating: Boolean = false,
) {
    val today: LocalDate get() = LocalDate.now()

    /**
     * Yesterday first, then today, then the rest of the window: today is the
     * second column and stays visible when the screen opens.
     */
    val days: List<LocalDate> get() = (-1 until DAYS_AHEAD).map { anchor.plusDays(it.toLong()) }

    val isEmpty: Boolean
        get() = !loading && error == null && entriesByDay.values.all { it.isEmpty() }

    /** Whether a visible day holds a recipe, which could go to a shopping list. */
    val hasRecipes: Boolean
        get() = days.any { day -> entriesByDay[day].orEmpty().any { it.recipe != null } }

    companion object {
        const val DAYS_AHEAD = 7
    }
}

data class RecipePickerState(
    val query: String = "",
    val results: List<RecipeSummary> = emptyList(),
    val loading: Boolean = false,
)

class PlanningViewModel(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanningUiState())
    val state: StateFlow<PlanningUiState> = _state.asStateFlow()

    private val _picker = MutableStateFlow(RecipePickerState())
    val picker: StateFlow<RecipePickerState> = _picker.asStateFlow()

    private var loadJob: Job? = null
    private var pickerJob: Job? = null
    private var hasLoadedOnce = false

    init {
        load()
    }

    /**
     * Called every time the tab comes back into view.
     *
     * A meal added from a recipe page lands on the server while this ViewModel
     * is still alive, so returning to the tab has to ask Mealie again instead of
     * showing what was loaded before.
     */
    fun onScreenShown() {
        if (hasLoadedOnce) refresh()
    }

    fun load() = load(refreshing = false)

    fun refresh() = load(refreshing = true)

    private fun load(refreshing: Boolean) {
        val window = _state.value
        loadJob?.cancel()
        _state.update {
            it.copy(loading = !refreshing, refreshing = refreshing, error = null)
        }
        loadJob = viewModelScope.launch {
            val start = window.days.first()
            val end = window.days.last()
            when (val result = mealPlanRepository.entries(start, end)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.error)
                }
                is ApiResult.Success -> {
                    hasLoadedOnce = true
                    _state.update {
                        it.copy(
                            entriesByDay = result.value.groupBy { entry -> entry.date },
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun showPreviousWeek() {
        _state.update { it.copy(anchor = it.anchor.minusWeeks(1)) }
        load()
    }

    fun showNextWeek() {
        _state.update { it.copy(anchor = it.anchor.plusWeeks(1)) }
        load()
    }

    fun backToToday() {
        _state.update { it.copy(anchor = LocalDate.now()) }
        load()
    }

    fun addRecipe(date: LocalDate, type: MealType, recipe: RecipeSummary) =
        mutate { mealPlanRepository.add(date = date, type = type, recipeId = recipe.id) }

    fun addNote(date: LocalDate, type: MealType, note: String) =
        mutate {
            mealPlanRepository.add(
                date = date,
                type = type,
                recipeId = null,
                title = note,
            )
        }

    fun moveEntry(entry: MealPlanEntry, date: LocalDate, type: MealType) =
        mutate { mealPlanRepository.update(entry.copy(date = date, type = type)) }

    fun deleteEntry(entry: MealPlanEntry) = mutate { mealPlanRepository.delete(entry.id) }

    fun onPickerQueryChange(query: String) {
        _picker.update { it.copy(query = query) }
        pickerJob?.cancel()
        if (query.isBlank()) {
            _picker.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        _picker.update { it.copy(loading = true) }
        pickerJob = viewModelScope.launch {
            val result = recipeRepository.search(
                query = query,
                filters = RecipeFilters.None,
                page = 1,
                perPage = 20,
            )
            _picker.update {
                it.copy(
                    results = (result as? ApiResult.Success)?.value?.items.orEmpty(),
                    loading = false,
                )
            }
        }
    }

    fun resetPicker() {
        pickerJob?.cancel()
        _picker.value = RecipePickerState()
    }

    private fun mutate(block: suspend () -> ApiResult<*>) {
        _state.update { it.copy(mutating = true) }
        viewModelScope.launch {
            when (val result = block()) {
                is ApiResult.Failure -> _state.update { it.copy(mutating = false, error = result.error) }
                is ApiResult.Success -> {
                    _state.update { it.copy(mutating = false) }
                    load()
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                PlanningViewModel(container.mealPlanRepository, container.recipeRepository)
            }
        }
    }
}
