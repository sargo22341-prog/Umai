package org.opensources.umai.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.PagedItems
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.valueOrNull
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.recipe.data.RecipeRepository
import kotlin.random.Random

data class HomeUiState(
    /** A few recipes drawn at random, shown large at the top of the screen. */
    val discovery: List<RecipeSummary> = emptyList(),
    val latest: PagedItems<RecipeSummary> = PagedItems(),
    val recentlyViewed: List<RecipeSummary> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: NetworkError? = null,
    val layout: RecipeLayout = RecipeLayout.GRID,
) {
    val isEmpty: Boolean get() = !loading && error == null && latest.items.isEmpty()
}

class HomeViewModel(
    private val recipeRepository: RecipeRepository,
    private val recentSlugs: Flow<List<String>>,
    layout: Flow<RecipeLayout>,
    private val newSeed: () -> String = { Random.nextLong(1, Long.MAX_VALUE).toString() },
    /** Slugs of recipes deleted from the app, dropped from every list on screen. */
    deletedRecipes: Flow<String> = emptyFlow(),
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var hasLoadedOnce = false

    init {
        refresh(initial = true)
        viewModelScope.launch {
            layout.collect { value -> _state.update { it.copy(layout = value) } }
        }
        viewModelScope.launch {
            deletedRecipes.collect { slug ->
                val deleted = { recipe: RecipeSummary -> recipe.slug == slug }
                _state.update {
                    it.copy(
                        discovery = it.discovery.filterNot(deleted),
                        latest = it.latest.without(deleted),
                        recentlyViewed = it.recentlyViewed.filterNot(deleted),
                    )
                }
            }
        }
    }

    /**
     * Called every time the tab comes back into view: the recipe just read has
     * to appear among the recently viewed ones, and the instance may have
     * gained recipes meanwhile.
     */
    fun onScreenShown() {
        if (hasLoadedOnce) load(initial = false, reshuffle = false)
    }

    /** An explicit refresh also draws new recipes to discover. */
    fun refresh(initial: Boolean = false) = load(initial, reshuffle = true)

    /**
     * Coming back to the tab keeps the discovery draw: recipes moving under
     * the reader's eyes for no reason would feel like a glitch.
     */
    private fun load(initial: Boolean, reshuffle: Boolean) {
        loadJob?.cancel()
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        loadJob = viewModelScope.launch {
            val discovery = if (reshuffle || _state.value.discovery.isEmpty()) {
                async { loadDiscovery() }
            } else {
                null
            }
            val latest = recipeRepository.latest(page = 1)
            // Both requests run together, and the screen shows once both are
            // back, so the carousel does not push the list down as it arrives.
            discovery?.await()
            when (latest) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = latest.error)
                }
                is ApiResult.Success -> {
                    hasLoadedOnce = true
                    _state.update {
                        it.copy(
                            latest = PagedItems<RecipeSummary>().append(latest.value),
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                }
            }
            loadRecentlyViewed()
        }
    }

    /**
     * The carousel is an invitation, not content the screen depends on: a
     * failed draw keeps the previous one, or no carousel at all, and the
     * latest recipes carry the error.
     */
    private suspend fun loadDiscovery() {
        val drawn = recipeRepository.discover(count = DISCOVERY_COUNT, seed = newSeed()).valueOrNull() ?: return
        _state.update { it.copy(discovery = drawn) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.loading || !current.latest.canLoadMore) return

        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val next = recipeRepository.latest(page = current.latest.page + 1)) {
                is ApiResult.Failure -> _state.update { it.copy(loadingMore = false, error = next.error) }
                is ApiResult.Success -> _state.update {
                    it.copy(latest = it.latest.append(next.value), loadingMore = false)
                }
            }
        }
    }

    /**
     * Mealie exposes no "recently viewed" collection, so the slugs kept on the
     * device are resolved one by one; entries that no longer exist are dropped.
     */
    private suspend fun loadRecentlyViewed() {
        val slugs = recentSlugs.first().take(MAX_RECENT)
        if (slugs.isEmpty()) {
            _state.update { it.copy(recentlyViewed = emptyList()) }
            return
        }
        val recipes = coroutineScope {
            slugs.map { slug -> async { recipeRepository.recipe(slug) } }
                .awaitAll()
                .mapNotNull { (it as? ApiResult.Success)?.value?.summary }
        }
        _state.update { it.copy(recentlyViewed = recipes) }
    }

    companion object {
        private const val MAX_RECENT = 8
        private const val DISCOVERY_COUNT = 5

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                HomeViewModel(
                    recipeRepository = container.recipeRepository,
                    recentSlugs = container.recentRecipesStore.slugs,
                    layout = container.preferencesRepository.preferences.map { it.recipeLayout },
                    deletedRecipes = container.recipeEditRepository.deletedRecipes,
                )
            }
        }
    }
}
