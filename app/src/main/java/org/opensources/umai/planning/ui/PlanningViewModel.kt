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
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.domain.PlanningWeek
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.RecipeSort
import org.opensources.umai.search.domain.SortField
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

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
) {
    /** The seven days of the week, from [firstDay]. */
    val days: List<LocalDate> get() = PlanningWeek.days(weekStart)

    /** The day the week opens on: today in the current week, its first day in any other. */
    val focusedDay: LocalDate get() = today.takeIf { it in days } ?: weekStart

    val isEmpty: Boolean
        get() = !loading && error == null && entriesByDay.values.all { it.isEmpty() }

    /** Whether a visible day holds a recipe, which could go to a shopping list. */
    val hasRecipes: Boolean
        get() = days.any { day -> entriesByDay[day].orEmpty().any { it.recipe != null } }
}

/**
 * A recipe drawn at random for the meal being added, among all recipes or the
 * recipes of one category ([categoryId], `null` for all).
 */
data class RandomRecipeState(
    val categories: List<Organizer> = emptyList(),
    val categoryId: String? = null,
    val recipe: RecipeSummary? = null,
    val drawing: Boolean = false,
    /** The category holds no recipe: nothing could be drawn. */
    val noMatch: Boolean = false,
    val error: NetworkError? = null,
)

class PlanningViewModel(
    private val mealPlanRepository: MealPlanRepository,
    private val recipeRepository: RecipeRepository,
    private val organizerRepository: OrganizerRepository,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanningUiState(today = clock()))
    val state: StateFlow<PlanningUiState> = _state.asStateFlow()

    private val _random = MutableStateFlow(RandomRecipeState())
    val random: StateFlow<RandomRecipeState> = _random.asStateFlow()

    private var loadJob: Job? = null
    private var drawJob: Job? = null
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
            _state.update { it.copy(today = today, weekStart = PlanningWeek.startOf(today, it.firstDay)) }
            load(refreshing = false, readFirstDay = true)
        } else if (hasLoadedOnce) {
            load(refreshing = true, readFirstDay = true)
        }
    }

    fun load() = load(refreshing = false, readFirstDay = false)

    fun refresh() = load(refreshing = true, readFirstDay = false)

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

    /** A new first day keeps the week around the day in focus, now starting on that day. */
    private fun applyFirstDay(firstDay: DayOfWeek) = _state.update {
        if (it.firstDay == firstDay) it
        else it.copy(firstDay = firstDay, weekStart = PlanningWeek.startOf(it.focusedDay, firstDay))
    }

    fun showPreviousWeek() {
        _state.update { it.copy(weekStart = it.weekStart.minusWeeks(1)) }
        load()
    }

    fun showNextWeek() {
        _state.update { it.copy(weekStart = it.weekStart.plusWeeks(1)) }
        load()
    }

    fun backToToday() {
        val today = clock()
        _state.update { it.copy(today = today, weekStart = PlanningWeek.startOf(today, it.firstDay)) }
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

    fun deleteEntry(entry: MealPlanEntry) = mutate { mealPlanRepository.delete(entry.id) }

    // ---- Random recipe ----------------------------------------------------

    /** Loads the categories the draw can be narrowed to, once. */
    fun loadRandomCategories() {
        if (_random.value.categories.isNotEmpty()) return
        viewModelScope.launch {
            val categories = (organizerRepository.categories() as? ApiResult.Success)?.value.orEmpty()
            _random.update { it.copy(categories = categories.sortedBy { category -> category.name.lowercase() }) }
        }
    }

    fun selectRandomCategory(id: String?) {
        drawJob?.cancel()
        _random.update { it.copy(categoryId = id, recipe = null, drawing = false, noMatch = false, error = null) }
    }

    /**
     * Asks Mealie for recipes in a random order and keeps one, avoiding the
     * recipe drawn just before when the category holds another.
     */
    fun drawRandomRecipe() {
        val current = _random.value
        drawJob?.cancel()
        _random.update { it.copy(drawing = true, noMatch = false, error = null) }
        drawJob = viewModelScope.launch {
            val result = recipeRepository.search(
                query = null,
                filters = current.categoryId?.let { RecipeFilters(categoryIds = setOf(it)) } ?: RecipeFilters.None,
                page = 1,
                sort = RecipeSort(SortField.RANDOM, descending = true),
                perPage = RANDOM_CANDIDATES,
                paginationSeed = Random.nextLong(1, Long.MAX_VALUE).toString(),
            )
            _random.update {
                when (result) {
                    is ApiResult.Failure -> it.copy(drawing = false, error = result.error)
                    is ApiResult.Success -> {
                        val candidates = result.value.items
                        val drawn = candidates.firstOrNull { recipe -> recipe.id != current.recipe?.id }
                            ?: candidates.firstOrNull()
                        it.copy(drawing = false, recipe = drawn, noMatch = drawn == null)
                    }
                }
            }
        }
    }

    /** Forgets the drawn recipe when the sheet closes; the chosen category stays. */
    fun resetRandomRecipe() {
        drawJob?.cancel()
        _random.update { it.copy(recipe = null, drawing = false, noMatch = false, error = null) }
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
        /** Two, so that drawing again can avoid the recipe already shown. */
        private const val RANDOM_CANDIDATES = 2

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                PlanningViewModel(
                    mealPlanRepository = container.mealPlanRepository,
                    recipeRepository = container.recipeRepository,
                    organizerRepository = container.organizerRepository,
                )
            }
        }
    }
}
