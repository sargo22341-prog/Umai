package org.opensources.umai.recipe.ui

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
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.session.AuthMode
import org.opensources.umai.core.session.SessionManager
import org.opensources.umai.home.data.RecentRecipesStore
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.shopping.data.ShoppingRepository
import java.time.LocalDate

/** One-shot messages shown as a snackbar. */
sealed interface RecipeEvent {
    data class AddedToList(val listName: String) : RecipeEvent
    data object AddedToPlan : RecipeEvent
    data class Failed(val error: NetworkError) : RecipeEvent
    data object FavoritesNeedAccount : RecipeEvent
}

data class RecipeDetailUiState(
    val recipe: Recipe? = null,
    val loading: Boolean = true,
    val error: NetworkError? = null,
    val isFavorite: Boolean = false,
    val favoritesSupported: Boolean = true,
    val shoppingLists: List<ShoppingListSummary> = emptyList(),
    val event: RecipeEvent? = null,
)

class RecipeDetailViewModel(
    private val slug: String,
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val recentRecipesStore: RecentRecipesStore,
    sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecipeDetailUiState(
            // Favourites are per-user; a bare API token has no user context.
            favoritesSupported = sessionManager.activeSession()?.let {
                it.authMode == AuthMode.PASSWORD || it.userId != null
            } ?: false,
        ),
    )
    val state: StateFlow<RecipeDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = recipeRepository.recipe(slug)) {
                is ApiResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
                is ApiResult.Success -> {
                    _state.update { it.copy(recipe = result.value, loading = false, error = null) }
                    recentRecipesStore.remember(slug)
                    refreshFavorite(result.value.id)
                }
            }
        }
    }

    fun toggleFavorite() {
        val recipe = _state.value.recipe ?: return
        if (!_state.value.favoritesSupported) {
            _state.update { it.copy(event = RecipeEvent.FavoritesNeedAccount) }
            return
        }
        val target = !_state.value.isFavorite
        _state.update { it.copy(isFavorite = target) }
        viewModelScope.launch {
            when (val result = recipeRepository.setFavorite(recipe.slug, target)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(isFavorite = !target, event = RecipeEvent.Failed(result.error))
                }
                is ApiResult.Success -> Unit
            }
        }
    }

    fun loadShoppingLists() {
        if (_state.value.shoppingLists.isNotEmpty()) return
        viewModelScope.launch {
            when (val result = shoppingRepository.lists()) {
                is ApiResult.Failure -> _state.update { it.copy(event = RecipeEvent.Failed(result.error)) }
                is ApiResult.Success -> _state.update { it.copy(shoppingLists = result.value) }
            }
        }
    }

    fun addToShoppingList(list: ShoppingListSummary) {
        val recipe = _state.value.recipe ?: return
        viewModelScope.launch {
            val result = shoppingRepository.addRecipe(
                listId = list.id,
                recipeId = recipe.id,
                servings = 1.0,
            )
            _state.update {
                when (result) {
                    is ApiResult.Failure -> it.copy(event = RecipeEvent.Failed(result.error))
                    is ApiResult.Success -> it.copy(event = RecipeEvent.AddedToList(list.name))
                }
            }
        }
    }

    fun addToMealPlan(date: LocalDate, type: MealType) {
        val recipe = _state.value.recipe ?: return
        viewModelScope.launch {
            val result = mealPlanRepository.add(date = date, type = type, recipeId = recipe.id)
            _state.update {
                when (result) {
                    is ApiResult.Failure -> it.copy(event = RecipeEvent.Failed(result.error))
                    is ApiResult.Success -> it.copy(event = RecipeEvent.AddedToPlan)
                }
            }
        }
    }

    fun consumeEvent() = _state.update { it.copy(event = null) }

    private suspend fun refreshFavorite(recipeId: String) {
        if (!_state.value.favoritesSupported) return
        when (val result = recipeRepository.favoriteIds()) {
            is ApiResult.Failure -> Unit
            is ApiResult.Success -> _state.update { it.copy(isFavorite = recipeId in result.value) }
        }
    }

    companion object {
        fun factory(container: AppContainer, slug: String) = viewModelFactory {
            initializer {
                RecipeDetailViewModel(
                    slug = slug,
                    recipeRepository = container.recipeRepository,
                    shoppingRepository = container.shoppingRepository,
                    mealPlanRepository = container.mealPlanRepository,
                    recentRecipesStore = container.recentRecipesStore,
                    sessionManager = container.sessionManager,
                )
            }
        }
    }
}
