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
import org.opensources.umai.planning.data.DishPool
import org.opensources.umai.planning.data.DishPoolPhase
import org.opensources.umai.planning.data.DishPoolRepository
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.domain.MealPlanProposal
import org.opensources.umai.planning.domain.MealPlanner
import org.opensources.umai.planning.domain.MealSlot
import org.opensources.umai.planning.domain.allow
import java.time.LocalDate
import kotlin.random.Random

/** Plan the whole week shown, or one day of it. */
enum class AutoPlanScope { WEEK, DAY }

/** Where the automatic planning stands while it works. */
enum class AutoPlanPhase { READING_RECIPES, READING_DISHES, RECOGNIZING, COMPOSING, SAVING }

data class AutoPlanUiState(
    val visible: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val days: List<LocalDate> = emptyList(),
    val entriesByDay: Map<LocalDate, List<MealPlanEntry>> = emptyMap(),
    val scope: AutoPlanScope = AutoPlanScope.WEEK,
    val day: LocalDate = today,
    val phase: AutoPlanPhase? = null,
    val proposal: MealPlanProposal? = null,
    val error: NetworkError? = null,
    /** The instance holds no dish to plan: every recipe is a dessert, a drink or a side. */
    val noDishes: Boolean = false,
    /** Set once the meals are on Mealie; the sheet then closes. */
    val saved: Boolean = false,
) {
    val working: Boolean get() = phase != null

    /** The days that can still be planned: past days of the week are left alone. */
    val plannableDays: List<LocalDate> get() = days.filter { !it.isBefore(today) }

    /**
     * The lunches and dinners to fill: a meal that already has an entry in
     * Mealie, recipe or note, is kept as it is.
     */
    val slots: List<MealSlot>
        get() {
            val chosen = if (scope == AutoPlanScope.WEEK) plannableDays else listOf(day)
            return chosen.flatMap { date ->
                MEALS.filter { type -> entriesByDay[date].orEmpty().none { it.type == type } }
                    .map { MealSlot(date, it) }
            }
        }

    companion object {
        val MEALS = listOf(MealType.LUNCH, MealType.DINNER)
    }
}

/**
 * Proposes dishes for the empty lunches and dinners of a day or of the week
 * ([MealPlanner]), lets the user swap any of them or ask for another plan,
 * then writes the plan on Mealie. Nothing is written before the user accepts.
 */
class AutoPlanViewModel(
    private val dishes: DishPoolRepository,
    private val mealPlans: MealPlanRepository,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(AutoPlanUiState())
    val state: StateFlow<AutoPlanUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var pool: DishPool? = null
    private var alreadyNeeded: Set<String> = emptySet()
    private var rejected: Set<String> = emptySet()

    /** Dishes of the plans the user asked to redo. */
    private var avoid: Set<String> = emptySet()
    private var planner = MealPlanner(random)

    fun open(today: LocalDate, days: List<LocalDate>, entriesByDay: Map<LocalDate, List<MealPlanEntry>>, focusedDay: LocalDate) {
        job?.cancel()
        pool = null
        rejected = emptySet()
        avoid = emptySet()
        _state.value = AutoPlanUiState(
            visible = true,
            today = today,
            days = days,
            entriesByDay = entriesByDay,
            day = focusedDay.takeIf { !it.isBefore(today) } ?: days.firstOrNull { !it.isBefore(today) } ?: focusedDay,
        )
    }

    fun close() {
        job?.cancel()
        _state.update { it.copy(visible = false, phase = null) }
    }

    fun setScope(scope: AutoPlanScope) = _state.update { it.copy(scope = scope, proposal = null, noDishes = false) }

    fun setDay(day: LocalDate) = _state.update { it.copy(day = day, proposal = null, noDishes = false) }

    /** Gathers the dishes once, then composes a plan from them. */
    fun propose() {
        if (_state.value.working) return
        job = viewModelScope.launch {
            _state.update { it.copy(error = null, noDishes = false, proposal = null) }
            val gathered = pool ?: gather() ?: return@launch
            compose(gathered)
        }
    }

    /** Another plan from the same dishes, avoiding those of the plan shown. */
    fun regenerate() {
        if (_state.value.working) return
        val gathered = pool ?: return propose()
        avoid = avoid + _state.value.proposal?.meals.orEmpty().map { it.candidate.recipe.id }
        planner = MealPlanner(Random(random.nextLong()))
        rejected = emptySet()
        job = viewModelScope.launch { compose(gathered) }
    }

    /** Swaps the dish of one meal for the next best, the others staying. */
    fun replace(index: Int) {
        val current = _state.value
        val proposal = current.proposal ?: return
        val gathered = pool ?: return
        val meal = proposal.meals.getOrNull(index) ?: return
        rejected = rejected + meal.candidate.recipe.id
        val replaced = planner.replace(
            proposal = proposal,
            index = index,
            candidates = gathered.candidates,
            rejected = rejected,
            alreadyNeeded = alreadyNeeded,
            allowed = { slot, candidate -> gathered.rules.allow(slot, candidate.recipe.id) },
        )
        _state.update { it.copy(proposal = replaced) }
    }

    /** Writes the proposed meals on Mealie, one entry each. */
    fun accept() {
        val proposal = _state.value.proposal ?: return
        if (_state.value.working) return
        job = viewModelScope.launch {
            _state.update { it.copy(phase = AutoPlanPhase.SAVING, error = null) }
            proposal.meals.forEach { meal ->
                val result = mealPlans.add(meal.slot.date, meal.slot.type, meal.candidate.recipe.id)
                if (result is ApiResult.Failure) {
                    _state.update { it.copy(phase = null, error = result.error) }
                    return@launch
                }
            }
            _state.update { it.copy(phase = null, saved = true, visible = false) }
        }
    }

    fun consumeSaved() = _state.update { it.copy(saved = false) }

    private suspend fun gather(): DishPool? {
        val state = _state.value
        val result = dishes.dishPool(state.today, random) { phase ->
            _state.update {
                it.copy(
                    phase = when (phase) {
                        DishPoolPhase.READING_RECIPES -> AutoPlanPhase.READING_RECIPES
                        DishPoolPhase.READING_DISHES -> AutoPlanPhase.READING_DISHES
                        DishPoolPhase.RECOGNIZING -> AutoPlanPhase.RECOGNIZING
                    },
                )
            }
        }
        return when (result) {
            is ApiResult.Failure -> {
                _state.update { it.copy(phase = null, error = result.error) }
                null
            }
            is ApiResult.Success -> {
                val planned = state.plannableDays.flatMap { day ->
                    state.entriesByDay[day].orEmpty().filter { it.type in AutoPlanUiState.MEALS }.mapNotNull { it.recipe }
                }
                alreadyNeeded = dishes.ingredientsOf(planned)
                // A dish already on the week is not proposed a second time.
                val plannedIds = planned.map { it.id }.toSet()
                result.value.copy(candidates = result.value.candidates.filter { it.recipe.id !in plannedIds })
                    .also { pool = it }
            }
        }
    }

    private fun compose(pool: DishPool) {
        _state.update { it.copy(phase = AutoPlanPhase.COMPOSING) }
        val slots = _state.value.slots
        if (pool.candidates.isEmpty()) {
            _state.update { it.copy(phase = null, noDishes = true) }
            return
        }
        val proposal = planner.plan(
            slots = slots,
            candidates = pool.candidates,
            alreadyNeeded = alreadyNeeded,
            avoid = avoid,
            allowed = { slot, candidate -> pool.rules.allow(slot, candidate.recipe.id) },
        )
        _state.update { it.copy(phase = null, proposal = proposal) }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                AutoPlanViewModel(
                    dishes = container.dishPoolRepository,
                    mealPlans = container.mealPlanRepository,
                )
            }
        }
    }
}
