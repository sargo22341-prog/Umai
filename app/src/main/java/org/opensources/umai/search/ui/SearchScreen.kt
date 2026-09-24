package org.opensources.umai.search.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.SortField

/** [initialFilters] opens the search on a tag, a category or a tool of a recipe. */
@Composable
fun SearchScreen(
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialFilters: RecipeFilters = RecipeFilters.None,
) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container, initialFilters))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filterOptions by viewModel.filterOptions.collectAsStateWithLifecycle()

    SearchScreen(
        state = state,
        filterOptions = filterOptions,
        onRecipeClick = onRecipeClick,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::clearQuery,
        onApplyFilters = viewModel::applyFilters,
        onResetFilters = viewModel::resetFilters,
        onSelectSort = viewModel::selectSort,
        onLoadFilterOptions = viewModel::loadFilterOptions,
        onFoodQueryChange = viewModel::searchFoods,
        onFoodSelected = viewModel::rememberSelectedFood,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        recipeImageUrl = { recipe -> container.imageUrls.thumbnail(recipe.id, recipe.imageToken) },
        modifier = modifier,
        autoFocus = initialFilters.isEmpty,
    )
}

/** Stateless search, driven by [SearchUiState]. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    filterOptions: FilterOptionsState,
    onRecipeClick: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onApplyFilters: (RecipeFilters) -> Unit,
    onResetFilters: () -> Unit,
    onSelectSort: (SortField) -> Unit,
    onLoadFilterOptions: () -> Unit,
    onFoodQueryChange: (String) -> Unit,
    onFoodSelected: (Food) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    Scaffold(modifier = modifier) { padding ->
        RecipeSearchContent(
            state = state,
            filterOptions = filterOptions,
            actions = RecipeSearchActions(
                onQueryChange = onQueryChange,
                onClearQuery = onClearQuery,
                onApplyFilters = onApplyFilters,
                onResetFilters = onResetFilters,
                onSelectSort = onSelectSort,
                onLoadFilterOptions = onLoadFilterOptions,
                onFoodQueryChange = onFoodQueryChange,
                onFoodSelected = onFoodSelected,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onRefresh = onRefresh,
            ),
            onRecipeClick = { onRecipeClick(it.slug) },
            recipeImageUrl = recipeImageUrl,
            modifier = Modifier.padding(padding),
            autoFocus = autoFocus,
        )
    }
}
