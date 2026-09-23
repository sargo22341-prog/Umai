package org.opensources.umai.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.PagedItems
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.RecipeSort
import kotlin.random.Random

data class SearchUiState(
    val query: String = "",
    val filters: RecipeFilters = RecipeFilters.None,
    val results: PagedItems<RecipeSummary> = PagedItems(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: NetworkError? = null,
    val hasQueried: Boolean = false,
    val layout: RecipeLayout = RecipeLayout.GRID,
) {
    /** Nothing typed and no filter set: show the invitation instead of a blank list. */
    val isIdle: Boolean get() = query.isBlank() && filters.isEmpty
    val isEmptyResult: Boolean
        get() = hasQueried && !loading && error == null && results.items.isEmpty()
}

data class FilterOptionsState(
    val categories: List<Organizer> = emptyList(),
    val tags: List<Organizer> = emptyList(),
    val tools: List<Organizer> = emptyList(),
    val foodResults: List<Food> = emptyList(),
    val selectedFoods: List<Food> = emptyList(),
    val loading: Boolean = false,
    val error: NetworkError? = null,
)

class SearchViewModel(
    private val recipeRepository: RecipeRepository,
    private val organizerRepository: OrganizerRepository,
    layout: Flow<RecipeLayout>,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val filtersFlow = MutableStateFlow(RecipeFilters.None)

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _filterOptions = MutableStateFlow(FilterOptionsState())
    val filterOptions: StateFlow<FilterOptionsState> = _filterOptions.asStateFlow()

    private var searchJob: Job? = null
    private var foodJob: Job? = null

    /** Regenerated per search so a random ordering stays stable while paging. */
    private var paginationSeed: String = newSeed()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeQuery() {
        viewModelScope.launch {
            combine(queryFlow.debounce(DEBOUNCE_MS), filtersFlow) { query, filters -> query to filters }
                .distinctUntilChanged()
                .collect { (query, filters) -> runSearch(query, filters) }
        }
    }

    init {
        observeQuery()
        viewModelScope.launch {
            layout.collect { value -> _state.update { it.copy(layout = value) } }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        queryFlow.value = value
    }

    fun clearQuery() = onQueryChange("")

    fun applyFilters(filters: RecipeFilters) {
        _state.update { it.copy(filters = filters) }
        filtersFlow.value = filters
    }

    fun resetFilters() = applyFilters(RecipeFilters.None)

    fun retry() = runSearch(_state.value.query, _state.value.filters)

    /** Runs the same query again, for the pull-to-refresh gesture. */
    fun refresh() = runSearch(_state.value.query, _state.value.filters, refreshing = true)

    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.loading || !current.results.canLoadMore) return

        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val result = recipeRepository.search(
                query = current.query,
                filters = current.filters,
                page = current.results.page + 1,
                paginationSeed = paginationSeed,
            )
            when (result) {
                is ApiResult.Failure ->
                    _state.update { it.copy(loadingMore = false, error = result.error) }
                is ApiResult.Success ->
                    _state.update { it.copy(results = it.results.append(result.value), loadingMore = false) }
            }
        }
    }

    // ---- Filter sheet -----------------------------------------------------

    fun loadFilterOptions() {
        if (_filterOptions.value.categories.isNotEmpty() || _filterOptions.value.loading) return
        _filterOptions.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val categories = organizerRepository.categories()
            val tags = organizerRepository.tags()
            val tools = organizerRepository.tools()
            val failure = listOf(categories, tags, tools).firstNotNullOfOrNull {
                (it as? ApiResult.Failure)?.error
            }
            _filterOptions.update {
                it.copy(
                    categories = (categories as? ApiResult.Success)?.value.orEmpty(),
                    tags = (tags as? ApiResult.Success)?.value.orEmpty(),
                    tools = (tools as? ApiResult.Success)?.value.orEmpty(),
                    loading = false,
                    error = failure,
                )
            }
        }
    }

    fun searchFoods(query: String) {
        foodJob?.cancel()
        if (query.trim().length < MIN_FOOD_QUERY) {
            _filterOptions.update { it.copy(foodResults = emptyList()) }
            return
        }
        foodJob = viewModelScope.launch {
            when (val result = organizerRepository.searchFoods(query)) {
                is ApiResult.Failure -> _filterOptions.update { it.copy(foodResults = emptyList()) }
                is ApiResult.Success -> _filterOptions.update { it.copy(foodResults = result.value) }
            }
        }
    }

    /** Keeps the labels of the selected ingredients visible in the sheet. */
    fun rememberSelectedFood(food: Food) {
        _filterOptions.update {
            if (it.selectedFoods.any { existing -> existing.id == food.id }) it
            else it.copy(selectedFoods = it.selectedFoods + food)
        }
    }

    private fun runSearch(query: String, filters: RecipeFilters, refreshing: Boolean = false) {
        searchJob?.cancel()

        if (query.isBlank() && filters.isEmpty) {
            _state.update {
                it.copy(
                    results = PagedItems(),
                    loading = false,
                    refreshing = false,
                    loadingMore = false,
                    error = null,
                    hasQueried = false,
                )
            }
            return
        }

        if (filters.sort == RecipeSort.RANDOM) paginationSeed = newSeed()
        _state.update { it.copy(loading = !refreshing, refreshing = refreshing, error = null) }

        searchJob = viewModelScope.launch {
            val result = recipeRepository.search(
                query = query,
                filters = filters,
                page = 1,
                paginationSeed = paginationSeed,
            )
            when (result) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, refreshing = false, error = result.error, hasQueried = true)
                }
                is ApiResult.Success -> _state.update {
                    it.copy(
                        results = PagedItems<RecipeSummary>().append(result.value),
                        loading = false,
                        refreshing = false,
                        error = null,
                        hasQueried = true,
                    )
                }
            }
        }
    }

    private fun newSeed(): String = Random.nextLong(1, Long.MAX_VALUE).toString()

    companion object {
        private const val DEBOUNCE_MS = 350L
        const val MIN_FOOD_QUERY = 2

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                SearchViewModel(
                    recipeRepository = container.recipeRepository,
                    organizerRepository = container.organizerRepository,
                    layout = container.preferencesRepository.preferences.map { it.recipeLayout },
                )
            }
        }
    }
}
