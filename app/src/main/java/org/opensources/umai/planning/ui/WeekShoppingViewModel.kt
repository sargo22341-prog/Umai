package org.opensources.umai.planning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.shopping.data.ShoppingRepository
import java.time.LocalDate

/** The three steps of sending the week's meals to a shopping list. */
enum class WeekShoppingStep { RECIPES, SERVINGS, INGREDIENTS }

data class WeekShoppingUiState(
    val visible: Boolean = false,
    val step: WeekShoppingStep = WeekShoppingStep.RECIPES,
    /** The planned meals that are recipes, in the order of the plan. */
    val entries: List<MealPlanEntry> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val lists: List<ShoppingListSummary> = emptyList(),
    val listId: String? = null,
    val loadingLists: Boolean = false,
    /** Full recipes, by slug, read once the meals are chosen. */
    val recipes: Map<String, Recipe> = emptyMap(),
    val loadingRecipes: Boolean = false,
    /** Servings per planned meal, by plan entry id. */
    val servings: Map<Int, Int> = emptyMap(),
    /** Ingredient lines left out, by plan entry id. */
    val excluded: Map<Int, Set<Int>> = emptyMap(),
    val adding: Boolean = false,
    val error: NetworkError? = null,
    /** Meals already on the list, so a retry after a failure does not add them twice. */
    val sent: Set<Int> = emptySet(),
    /** Set once every meal is on the list: how many were added. */
    val added: Int? = null,
) {
    val chosen: List<MealPlanEntry> get() = entries.filter { it.id in selected }

    val listName: String? get() = lists.firstOrNull { it.id == listId }?.name

    fun recipeOf(entry: MealPlanEntry): Recipe? = entry.recipe?.slug?.let(recipes::get)

    /** The lines of [entry] that will be sent. */
    fun ingredientCount(entry: MealPlanEntry): Int =
        (recipeOf(entry)?.ingredients?.size ?: 0) - excluded[entry.id].orEmpty().size

    val canContinue: Boolean
        get() = when (step) {
            WeekShoppingStep.RECIPES -> selected.isNotEmpty() && listId != null
            WeekShoppingStep.SERVINGS -> !loadingRecipes && chosen.all { recipeOf(it) != null }
            WeekShoppingStep.INGREDIENTS -> !adding && chosen.any { ingredientCount(it) > 0 }
        }
}

/**
 * Sends the recipes of the planned meals to a shopping list, the way a single
 * recipe is: the user picks the meals, the servings of each, and the lines
 * they need. Mealie's own "add recipe to list" does the rest, scaling the
 * quantities and merging identical items.
 */
class WeekShoppingViewModel(
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WeekShoppingUiState())
    val state: StateFlow<WeekShoppingUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Opens on the recipes of [entries]; the meals from [today] on are ticked. */
    fun open(entries: List<MealPlanEntry>, today: LocalDate = LocalDate.now()) {
        val recipes = entries.filter { it.recipe != null }.sortedWith(compareBy({ it.date }, { it.type.ordinal }))
        _state.value = WeekShoppingUiState(
            visible = true,
            entries = recipes,
            selected = recipes.filter { !it.date.isBefore(today) }.map { it.id }.toSet(),
            lists = _state.value.lists,
            listId = _state.value.listId,
        )
        loadLists()
    }

    fun close() {
        job?.cancel()
        _state.update { it.copy(visible = false) }
    }

    fun toggle(entry: MealPlanEntry) = _state.update {
        it.copy(selected = if (entry.id in it.selected) it.selected - entry.id else it.selected + entry.id)
    }

    fun selectList(list: ShoppingListSummary) = _state.update { it.copy(listId = list.id) }

    fun setServings(entry: MealPlanEntry, value: Int) = _state.update {
        it.copy(servings = it.servings + (entry.id to value.coerceIn(1, MAX_SERVINGS)))
    }

    fun toggleIngredient(entry: MealPlanEntry, index: Int) = _state.update {
        val current = it.excluded[entry.id].orEmpty()
        it.copy(excluded = it.excluded + (entry.id to if (index in current) current - index else current + index))
    }

    fun back() = _state.update {
        when (it.step) {
            WeekShoppingStep.RECIPES -> it
            WeekShoppingStep.SERVINGS -> it.copy(step = WeekShoppingStep.RECIPES, error = null)
            WeekShoppingStep.INGREDIENTS -> it.copy(step = WeekShoppingStep.SERVINGS, error = null)
        }
    }

    fun next() {
        val current = _state.value
        if (!current.canContinue) return
        when (current.step) {
            WeekShoppingStep.RECIPES -> {
                _state.update { it.copy(step = WeekShoppingStep.SERVINGS, error = null) }
                loadRecipes()
            }
            WeekShoppingStep.SERVINGS -> _state.update { it.copy(step = WeekShoppingStep.INGREDIENTS) }
            WeekShoppingStep.INGREDIENTS -> add()
        }
    }

    fun retry() = loadRecipes()

    private fun loadLists() {
        if (_state.value.lists.isNotEmpty()) return
        _state.update { it.copy(loadingLists = true) }
        viewModelScope.launch {
            when (val result = shoppingRepository.lists()) {
                is ApiResult.Failure -> _state.update { it.copy(loadingLists = false, error = result.error) }
                is ApiResult.Success -> _state.update {
                    it.copy(loadingLists = false, lists = result.value, listId = it.listId ?: result.value.firstOrNull()?.id)
                }
            }
        }
    }

    /** Reads the recipes not read yet, each once however many times it is planned. */
    private fun loadRecipes() {
        val missing = _state.value.chosen.mapNotNull { it.recipe?.slug }.distinct().filter { it !in _state.value.recipes }
        if (missing.isEmpty()) {
            _state.update { it.withDefaultServings() }
            return
        }
        _state.update { it.copy(loadingRecipes = true, error = null) }
        job?.cancel()
        job = viewModelScope.launch {
            val results = missing.map { slug -> async { slug to recipeRepository.recipe(slug) } }.awaitAll()
            val failure = results.firstNotNullOfOrNull { (_, result) -> (result as? ApiResult.Failure)?.error }
            val loaded = results.mapNotNull { (slug, result) -> (result as? ApiResult.Success)?.value?.let { slug to it } }
            // One update, so the recipes are never seen without their servings.
            _state.update {
                it.copy(loadingRecipes = false, recipes = it.recipes + loaded, error = failure).withDefaultServings()
            }
        }
    }

    private fun WeekShoppingUiState.withDefaultServings(): WeekShoppingUiState {
        val defaults = chosen
            .filter { it.id !in servings }
            .associate { entry -> entry.id to (recipeOf(entry)?.baseServings ?: 1) }
        return copy(servings = servings + defaults)
    }

    /** One call per meal; a failure stops there, and a retry goes on from it. */
    private fun add() {
        val current = _state.value
        val listId = current.listId ?: return
        _state.update { it.copy(adding = true, error = null) }
        job = viewModelScope.launch {
            for (entry in current.chosen.filter { it.id !in current.sent }) {
                val recipe = current.recipeOf(entry) ?: continue
                val excluded = current.excluded[entry.id].orEmpty()
                val lines = recipe.ingredients.filterIndexed { index, _ -> index !in excluded }
                if (lines.isEmpty()) continue
                val base = recipe.baseServings
                val servings = current.servings[entry.id] ?: base ?: 1
                val multiplier = if (base != null && base > 0) servings.toDouble() / base else 1.0
                val result = shoppingRepository.addRecipe(
                    listId = listId,
                    recipeId = recipe.id,
                    multiplier = multiplier,
                    ingredients = lines.takeIf { excluded.isNotEmpty() },
                )
                if (result is ApiResult.Failure) {
                    _state.update { it.copy(adding = false, error = result.error) }
                    return@launch
                }
                _state.update { it.copy(sent = it.sent + entry.id) }
            }
            _state.update { it.copy(adding = false, added = it.sent.size) }
        }
    }

    companion object {
        private const val MAX_SERVINGS = 99

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { WeekShoppingViewModel(container.recipeRepository, container.shoppingRepository) }
        }
    }
}
