package org.opensources.umai.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.core.ui.component.EmptyView
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.RecipeGridArrangement
import org.opensources.umai.core.ui.component.recipeGridCells
import org.opensources.umai.core.ui.component.isNearEnd
import org.opensources.umai.core.ui.component.recipeCards

@Composable
fun SearchScreen(
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
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
        onLoadFilterOptions = viewModel::loadFilterOptions,
        onFoodQueryChange = viewModel::searchFoods,
        onFoodSelected = viewModel::rememberSelectedFood,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        recipeImageUrl = { recipe -> container.imageUrls.thumbnail(recipe.id, recipe.imageToken) },
        modifier = modifier,
    )
}

/** Stateless search, driven by [SearchUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    filterOptions: FilterOptionsState,
    onRecipeClick: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onApplyFilters: (RecipeFilters) -> Unit,
    onResetFilters: () -> Unit,
    onLoadFilterOptions: () -> Unit,
    onFoodQueryChange: (String) -> Unit,
    onFoodSelected: (Food) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
    modifier: Modifier = Modifier,
) {
    var filtersVisible by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val nearEnd = gridState.isNearEnd()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(nearEnd, state.results.page) {
        if (nearEnd) onLoadMore()
    }

    if (filtersVisible) {
        FilterSheet(
            filters = state.filters,
            options = filterOptions,
            onDismiss = { filtersVisible = false },
            onApply = {
                onApplyFilters(it)
                filtersVisible = false
            },
            onReset = {
                onResetFilters()
                filtersVisible = false
            },
            onFoodQueryChange = onFoodQueryChange,
            onFoodSelected = onFoodSelected,
        )
    }

    Scaffold(modifier = modifier) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                SearchField(
                    query = state.query,
                    activeFilterCount = state.filters.activeCount,
                    focusRequester = focusRequester,
                    onQueryChange = onQueryChange,
                    onClear = onClearQuery,
                    onOpenFilters = {
                        onLoadFilterOptions()
                        filtersVisible = true
                    },
                )

                if (state.filters.activeCount > 0) {
                    ActiveFiltersRow(
                        count = state.filters.activeCount,
                        onClear = onResetFilters,
                    )
                }

                val error = state.error
        when {
                    state.loading && state.results.items.isEmpty() -> LoadingView()

                    error != null && state.results.items.isEmpty() ->
                        NetworkErrorView(
                            error = error,
                            modifier = Modifier.fillMaxSize(),
                            onRetry = onRetry,
                        )

                    state.isIdle -> EmptyView(
                        title = stringResource(R.string.search_start_title),
                        message = stringResource(R.string.search_start_message),
                        icon = Icons.Outlined.Search,
                        modifier = Modifier.fillMaxSize(),
                    )

                    state.isEmptyResult -> EmptyView(
                        title = stringResource(R.string.search_empty_title),
                        message = stringResource(R.string.search_empty_message),
                        icon = Icons.Outlined.Search,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> LazyVerticalGrid(
                        columns = recipeGridCells(state.layout),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = RecipeGridArrangement,
                        verticalArrangement = RecipeGridArrangement,
                    ) {
                        recipeCards(
                            recipes = state.results.items,
                            layout = state.layout,
                            imageUrlFor = recipeImageUrl,
                            onRecipeClick = { onRecipeClick(it.slug) },
                            loadingMore = state.loadingMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    activeFilterCount: Int,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.cd_search),
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        BadgedBox(
            badge = {
                if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) }
            },
        ) {
            IconButton(onClick = onOpenFilters) {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = stringResource(R.string.cd_open_filters),
                )
            }
        }
    }
}

@Composable
private fun ActiveFiltersRow(count: Int, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pluralStringResource(R.plurals.plural_active_filters, count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.search_clear_filters))
        }
    }
}
