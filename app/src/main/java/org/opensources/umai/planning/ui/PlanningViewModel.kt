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
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.data.PlanPhotos
import org.opensources.umai.planning.data.RecipeCaloriesRepository
import org.opensources.umai.planning.domain.DayCalories
import org.opensources.umai.planning.domain.PlanCalories
import org.opensources.umai.planning.domain.PlanningWeek
import java.time.DayOfWeek
import java.time.LocalDate

data class PlanningUiState(
    val today: LocalDate = LocalDate.now(),
    /** The first day of the week in Mealie's household preferences. */
    val firstDay: DayOfWeek = PlanningWeek.DEFAULT_FIRST_DAY,
    /** The first day of the week on screen. */
    val weekStart: LocalDate = PlanningWeek.startOf(today, firstDay),
    val entriesByDay: Map<LocalDate, List<MealPlanEntry>> = emptyMap(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: NetworkError? = null,
    val mutating: Boolean = false,
    /** The calories of one serving of each recipe of the week, by recipe id; `null` when it has none. */
    val recipeCalories: Map<String, Int?> = emptyMap(),
    /** While the calories of some recipes are asked of Mealie. */
    val loadingCalories: Boolean = false,
    /** The photos of the foods added to the week, by entry id: files on this phone. */
    val photos: Map<Int, String> = emptyMap(),
    /** Counts the requests to bring [focusedDay] into view: each new value scrolls the week to it. */
    val focusRequests: Int = 0,
) {
    /** The seven days of the week, from [firstDay]. */
    val days: List<LocalDate> get() = PlanningWeek.days(weekStart)

    /** The day the week opens on: today in the current week, its first day in any other. */
    val focusedDay: LocalDate get() = today.takeIf { it in days } ?: weekStart

    val isEmpty: Boolean
        get() = !loading && error == null && entriesByDay.values.all { it.isEmpty() }

    /** The calories of [entry], `null` when it has none. */
    fun caloriesOf(entry: MealPlanEntry): Int? = PlanCalories.ofEntry(entry, recipeCalories)

    fun calories(day: LocalDate): DayCalories = PlanCalories.ofDay(entriesByDay[day].orEmpty(), recipeCalories)

    /** Whether a visible day holds a recipe, which could go to a shopping list. */
    val hasRecipes: Boolean
        get() = days.any { day -> entriesByDay[day].orEmpty().any { it.recipe != null } }
}

class PlanningViewModel(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeCalories: RecipeCaloriesRepository,
    private val photos: PlanPhotos,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanningUiState(today = clock()))
    val state: StateFlow<PlanningUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var hasLoadedOnce = false

    init {
        load(refreshing = false, readFirstDay = true)
    }

    /**
     * Called every time the tab comes back into view.
     *
     * A meal added from a recipe page lands on the server while this ViewModel
     * is still alive, so returning to the tab has to ask Mealie again instead of
     * showing what was loaded before. The day may have changed meanwhile too,
     * and so may the first day of the week, set in the Mealie settings.
     */
    fun onScreenShown() {
        val today = clock()
        if (today != _state.value.today) {
            _state.update {
            it.copy(
                today = today,
                weekStart = PlanningWeek.startOf(today, it.firstDay),
                focusRequests = it.focusRequests + 1,
            )
        }
            load(refreshing = false, readFirstDay = true)
        } else if (hasLoadedOnce) {
            load(refreshing = true, readFirstDay = true)
        }
    }

    fun load() = load(refreshing = false, readFirstDay = false)

    /** Pulled by the user, who may have changed a recipe's nutrition in Mealie meanwhile. */
    fun refresh() {
        recipeCalories.forget()
        load(refreshing = true, readFirstDay = false)
    }

    private fun load(refreshing: Boolean, readFirstDay: Boolean) {
        loadJob?.cancel()
        _state.update {
            it.copy(loading = !refreshing, refreshing = refreshing, error = null)
        }
        loadJob = viewModelScope.launch {
            // Without the preference, the week keeps the start it has: the
            // entries below report the failure if the server is really down.
            if (readFirstDay) {
                (mealPlanRepository.firstDayOfWeek() as? ApiResult.Success)?.let { applyFirstDay(it.value) }
            }
            val days = _state.value.days
            when (val result = mealPlanRepository.entries(days.first(), days.last())) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.error)
                }
                is ApiResult.Success -> {
                    hasLoadedOnce = true
                    val entries = result.value
                    val recipes = entries.mapNotNull { it.recipe }
                    val entryPhotos = photos.sync(days.first(), days.last(), entries)
                    _state.update {
                        it.copy(
                            entriesByDay = entries.groupBy { entry -> entry.date },
                            loading = false,
                            refreshing = false,
                            error = null,
                            recipeCalories = recipes.associate { recipe -> recipe.id to PlanCalories.ofTags(recipe) },
                            loadingCalories = recipes.isNotEmpty(),
                            photos = entryPhotos,
                        )
                    }
                    if (recipes.isNotEmpty()) {
                        val calories = recipeCalories.calories(recipes)
                        _state.update { it.copy(recipeCalories = calories, loadingCalories = false) }
                    }
                }
            }
        }
    }

    /** A new first day keeps the week around the day in focus, now starting on that day. */
    private fun applyFirstDay(firstDay: DayOfWeek) = _state.update {
        if (it.firstDay == firstDay) it
        else it.copy(
            firstDay = firstDay,
            weekStart = PlanningWeek.startOf(it.focusedDay, firstDay),
            // The days moved along the week: the one in focus is at another place.
            focusRequests = it.focusRequests + 1,
        )
    }

    fun showPreviousWeek() {
        _state.update { it.copy(weekStart = it.weekStart.minusWeeks(1), focusRequests = it.focusRequests + 1) }
        load()
    }

    fun showNextWeek() {
        _state.update { it.copy(weekStart = it.weekStart.plusWeeks(1), focusRequests = it.focusRequests + 1) }
        load()
    }

    fun backToToday() {
        val today = clock()
        _state.update {
            it.copy(
                today = today,
                weekStart = PlanningWeek.startOf(today, it.firstDay),
                focusRequests = it.focusRequests + 1,
            )
        }
        load()
    }

    fun addNote(date: LocalDate, type: MealType, note: String) =
        mutate {
            mealPlanRepository.add(
                date = date,
                type = type,
                recipeId = null,
                title = note,
            )
        }

    fun deleteEntry(entry: MealPlanEntry) = mutate {
        mealPlanRepository.delete(entry.id).also { if (it is ApiResult.Success) photos.delete(entry) }
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
                PlanningViewModel(
                    mealPlanRepository = container.mealPlanRepository,
                    recipeCalories = container.recipeCaloriesRepository,
                    photos = container.planPhotos,
                )
            }
        }
    }
}
