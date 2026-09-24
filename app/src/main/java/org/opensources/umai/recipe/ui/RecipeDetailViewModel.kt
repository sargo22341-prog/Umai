package org.opensources.umai.recipe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.MAX_RATING_STARS
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.session.AuthMode
import org.opensources.umai.core.session.SessionManager
import org.opensources.umai.core.settings.RecipeDisplayOptions
import org.opensources.umai.home.data.RecentRecipesStore
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.domain.PlanningWeek
import org.opensources.umai.recipe.data.RecipeCommentRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.recipe.domain.withoutCalorieTags
import org.opensources.umai.search.domain.OrganizerEntry
import org.opensources.umai.search.domain.OrganizerKind
import org.opensources.umai.shopping.data.ShoppingRepository
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/** One-shot messages shown as a snackbar. */
sealed interface RecipeEvent {
    data class AddedToList(val listName: String) : RecipeEvent
    data object AddedToPlan : RecipeEvent
    data class Failed(val error: NetworkError) : RecipeEvent
    data object FavoritesNeedAccount : RecipeEvent
    data object RatingNeedsAccount : RecipeEvent
}

data class RecipeDetailUiState(
    val recipe: Recipe? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: NetworkError? = null,
    val isFavorite: Boolean = false,
    /** Favourites and ratings both need a user account on the Mealie side. */
    val favoritesSupported: Boolean = true,
    /** The stars the signed-in user gave; `null` until they rate the recipe. */
    val ownRating: Int? = null,
    /** Which optional sections the reader chose to see, from the app settings. */
    val display: RecipeDisplayOptions = RecipeDisplayOptions(),
    val shoppingLists: List<ShoppingListSummary> = emptyList(),
    val loadingShoppingLists: Boolean = false,
    /** Where the weeks offered by the meal plan picker start, as on the meal plan. */
    val planFirstDay: DayOfWeek = PlanningWeek.DEFAULT_FIRST_DAY,
    /**
     * Servings the reader asked for. It starts at the recipe's own value and is
     * deliberately never persisted: reopening the app shows the recipe as its
     * author wrote it.
     */
    val servings: Int = 0,
    val comments: List<RecipeComment> = emptyList(),
    val commentsLoading: Boolean = false,
    val postingComment: Boolean = false,
    val currentUserId: String? = null,
    val currentUserIsAdmin: Boolean = false,
    val commentsSupported: Boolean = true,
    val event: RecipeEvent? = null,
) {
    /** Servings the recipe was written for, or `null` when Mealie holds none. */
    val baseServings: Int? get() = recipe?.baseServings

    /** 1.0 as long as the reader did not change the number of servings. */
    val scale: Double
        get() {
            val base = baseServings ?: return 1.0
            if (servings <= 0 || base <= 0) return 1.0
            return servings.toDouble() / base
        }

    val canScale: Boolean get() = baseServings != null

    /**
     * The stars shown on the page: the reader's own rating, or the average of
     * the household until they rate it themselves.
     */
    val shownRating: Int
        get() = ownRating ?: recipe?.summary?.rating?.roundToInt()?.coerceIn(0, MAX_RATING_STARS) ?: 0

    val commentsVisible: Boolean
        get() = recipe != null && display.showComments && commentsSupported && !recipe.commentsDisabled

    /** The categories, tags and tools of the recipe, each opening a search on it. */
    val organizers: List<OrganizerEntry>
        get() {
            val summary = recipe?.summary ?: return emptyList()
            return summary.categories.map { OrganizerEntry(OrganizerKind.CATEGORY, it) } +
                summary.tags.withoutCalorieTags().map { OrganizerEntry(OrganizerKind.TAG, it) } +
                summary.tools.map { OrganizerEntry(OrganizerKind.TOOL, it) }
        }

    fun canDelete(comment: RecipeComment): Boolean =
        currentUserIsAdmin || (currentUserId != null && comment.authorId == currentUserId)
}

class RecipeDetailViewModel(
    private val slug: String,
    private val recipeRepository: RecipeRepository,
    private val commentRepository: RecipeCommentRepository,
    private val shoppingRepository: ShoppingRepository,
    private val mealPlanRepository: MealPlanRepository,
    private val recentRecipesStore: RecentRecipesStore,
    sessionManager: SessionManager,
    displayOptions: Flow<RecipeDisplayOptions>,
) : ViewModel() {

    private val _state = MutableStateFlow(
        RecipeDetailUiState(
            // Favourites and comments are per-user; a bare API token has no
            // user context, so Mealie has nobody to attribute them to.
            favoritesSupported = sessionManager.activeSession()?.let {
                it.authMode == AuthMode.PASSWORD || it.userId != null
            } ?: false,
            commentsSupported = sessionManager.activeSession()?.userId != null,
            currentUserId = sessionManager.activeSession()?.userId,
            currentUserIsAdmin = sessionManager.activeSession()?.isAdmin == true,
        ),
    )
    val state: StateFlow<RecipeDetailUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            displayOptions.collect { options -> _state.update { it.copy(display = options) } }
        }
    }

    fun load() = fetch(initial = true)

    fun refresh() = fetch(initial = false)

    /** Only a recipe Mealie gave a serving count for can be scaled. */
    fun setServings(value: Int) {
        if (_state.value.baseServings == null) return
        _state.update { it.copy(servings = value.coerceIn(1, MAX_SERVINGS)) }
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

    /** Optimistic, like the favourite: the stars move at once and come back on failure. */
    fun setRating(stars: Int) {
        val recipe = _state.value.recipe ?: return
        if (!_state.value.favoritesSupported) {
            _state.update { it.copy(event = RecipeEvent.RatingNeedsAccount) }
            return
        }
        val previous = _state.value.ownRating
        val target = stars.coerceIn(1, MAX_RATING_STARS)
        if (target == previous) return
        _state.update { it.copy(ownRating = target) }
        viewModelScope.launch {
            val result = recipeRepository.setRating(recipe.slug, target, _state.value.isFavorite)
            if (result is ApiResult.Failure) {
                _state.update { it.copy(ownRating = previous, event = RecipeEvent.Failed(result.error)) }
            }
        }
    }

    fun loadShoppingLists() {
        if (_state.value.shoppingLists.isNotEmpty() || _state.value.loadingShoppingLists) return
        _state.update { it.copy(loadingShoppingLists = true) }
        viewModelScope.launch {
            when (val result = shoppingRepository.lists()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loadingShoppingLists = false, event = RecipeEvent.Failed(result.error))
                }
                is ApiResult.Success -> _state.update {
                    it.copy(shoppingLists = result.value, loadingShoppingLists = false)
                }
            }
        }
    }

    /**
     * [ingredients] are the lines the user kept ticked, and [servings] how many
     * portions they want; Mealie scales the quantities itself from the ratio.
     */
    fun addToShoppingList(
        list: ShoppingListSummary,
        servings: Int,
        ingredients: List<RecipeIngredient>,
    ) {
        val recipe = _state.value.recipe ?: return
        val base = recipe.baseServings
        val multiplier = if (base != null && base > 0 && servings > 0) {
            servings.toDouble() / base
        } else {
            1.0
        }
        val selection = ingredients.takeIf { it.size != recipe.ingredients.size }

        viewModelScope.launch {
            val result = shoppingRepository.addRecipe(
                listId = list.id,
                recipeId = recipe.id,
                multiplier = multiplier,
                ingredients = selection,
            )
            _state.update {
                when (result) {
                    is ApiResult.Failure -> it.copy(event = RecipeEvent.Failed(result.error))
                    is ApiResult.Success -> it.copy(event = RecipeEvent.AddedToList(list.name))
                }
            }
        }
    }

    /** Reads the household's first day of the week before the meal plan picker shows its weeks. */
    fun loadPlanFirstDay() {
        viewModelScope.launch {
            val result = mealPlanRepository.firstDayOfWeek()
            if (result is ApiResult.Success) _state.update { it.copy(planFirstDay = result.value) }
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

    fun postComment(text: String) {
        val recipe = _state.value.recipe ?: return
        if (text.isBlank() || _state.value.postingComment) return
        _state.update { it.copy(postingComment = true) }
        viewModelScope.launch {
            when (val result = commentRepository.add(recipe.id, text)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(postingComment = false, event = RecipeEvent.Failed(result.error))
                }
                // Comments read oldest first, so the new one lands at the end,
                // right above the field it was typed in.
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(postingComment = false, comments = it.comments + result.value)
                    }
                }
            }
        }
    }

    fun deleteComment(comment: RecipeComment) {
        val previous = _state.value.comments
        _state.update { it.copy(comments = previous.filterNot { item -> item.id == comment.id }) }
        viewModelScope.launch {
            when (val result = commentRepository.delete(comment.id)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(comments = previous, event = RecipeEvent.Failed(result.error))
                }
                is ApiResult.Success -> Unit
            }
        }
    }

    fun consumeEvent() = _state.update { it.copy(event = null) }

    private fun fetch(initial: Boolean) {
        _state.update { it.copy(loading = initial && it.recipe == null, refreshing = !initial, error = null) }
        viewModelScope.launch {
            when (val result = recipeRepository.recipe(slug)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.error)
                }
                is ApiResult.Success -> {
                    val recipe = result.value
                    _state.update {
                        it.copy(
                            recipe = recipe,
                            loading = false,
                            refreshing = false,
                            error = null,
                            // A reload keeps the servings the reader picked, as
                            // long as the recipe still declares a base count.
                            servings = it.servings.takeIf { value -> value > 0 }
                                ?: recipe.baseServings ?: 0,
                        )
                    }
                    recentRecipesStore.remember(slug)
                    refreshFavorite(recipe.id)
                    refreshOwnRating(recipe.id)
                    loadComments()
                }
            }
        }
    }

    private suspend fun refreshFavorite(recipeId: String) {
        if (!_state.value.favoritesSupported) return
        when (val result = recipeRepository.favoriteIds()) {
            is ApiResult.Failure -> Unit
            is ApiResult.Success -> _state.update { it.copy(isFavorite = recipeId in result.value) }
        }
    }

    private suspend fun refreshOwnRating(recipeId: String) {
        if (!_state.value.favoritesSupported) return
        when (val result = recipeRepository.ownRating(recipeId)) {
            // The average stays on screen: a missing personal rating is no error.
            is ApiResult.Failure -> Unit
            is ApiResult.Success -> _state.update { it.copy(ownRating = result.value) }
        }
    }

    private suspend fun loadComments() {
        if (_state.value.recipe?.commentsDisabled == true) return
        _state.update { it.copy(commentsLoading = true) }
        when (val result = commentRepository.comments(slug)) {
            // Comments are a side section: a failure must not hide the recipe.
            is ApiResult.Failure -> _state.update { it.copy(commentsLoading = false) }
            is ApiResult.Success -> _state.update {
                it.copy(comments = result.value, commentsLoading = false)
            }
        }
    }

    companion object {
        private const val MAX_SERVINGS = 99

        fun factory(container: AppContainer, slug: String) = viewModelFactory {
            initializer {
                RecipeDetailViewModel(
                    slug = slug,
                    recipeRepository = container.recipeRepository,
                    commentRepository = container.recipeCommentRepository,
                    shoppingRepository = container.shoppingRepository,
                    mealPlanRepository = container.mealPlanRepository,
                    recentRecipesStore = container.recentRecipesStore,
                    sessionManager = container.sessionManager,
                    displayOptions = container.preferencesRepository.preferences.map { it.recipeDisplay },
                )
            }
        }
    }
}
