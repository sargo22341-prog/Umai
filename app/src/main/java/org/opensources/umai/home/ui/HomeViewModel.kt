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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.PagedItems
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.recipe.data.RecipeRepository

data class HomeUiState(
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
    }

    /**
     * Called every time the tab comes back into view: the recipe just read has
     * to appear among the recently viewed ones, and the instance may have
     * gained recipes meanwhile.
     */
    fun onScreenShown() {
        if (hasLoadedOnce) refresh()
    }

    fun refresh(initial: Boolean = false) {
        loadJob?.cancel()
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        loadJob = viewModelScope.launch {
            val latest = recipeRepository.latest(page = 1)
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

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                HomeViewModel(
                    recipeRepository = container.recipeRepository,
                    recentSlugs = container.recentRecipesStore.slugs,
                    layout = container.preferencesRepository.preferences.map { it.recipeLayout },
                )
            }
        }
    }
}
