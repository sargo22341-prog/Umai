package org.opensources.umai.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.EmptyView
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.RecipeCard
import org.opensources.umai.core.ui.component.RecipeGridArrangement
import org.opensources.umai.core.ui.component.recipeGridCells
import org.opensources.umai.core.ui.component.isNearEnd
import org.opensources.umai.core.ui.component.recipeCards

@Composable
fun HomeScreen(
    onRecipeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onRecipeClick = onRecipeClick,
        onSearchClick = onSearchClick,
        onRefresh = { viewModel.refresh() },
        onRetry = { viewModel.refresh(initial = true) },
        onLoadMore = viewModel::loadMore,
        recipeImageUrl = { recipe -> container.imageUrls.thumbnail(recipe.id, recipe.imageToken) },
        modifier = modifier,
    )
}

/** Stateless Home, driven by [HomeUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRecipeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val nearEnd = gridState.isNearEnd()

    LaunchedEffect(nearEnd, state.latest.page) {
        if (nearEnd) onLoadMore()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val error = state.error
        when {
                state.loading -> LoadingView()

                error != null && state.latest.items.isEmpty() ->
                    NetworkErrorView(
                        error = error,
                        modifier = Modifier.fillMaxSize(),
                        onRetry = onRetry,
                    )

                state.isEmpty -> EmptyView(
                    title = stringResource(R.string.home_empty_title),
                    message = stringResource(R.string.home_empty_message),
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchShortcut(onClick = onSearchClick)
                    }

                    if (state.recentlyViewed.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionTitle(stringResource(R.string.home_section_recent))
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            RecentRow(
                                recipes = state.recentlyViewed,
                                onRecipeClick = { onRecipeClick(it.slug) },
                                recipeImageUrl = recipeImageUrl,
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(stringResource(R.string.home_section_latest))
                    }

                    recipeCards(
                        recipes = state.latest.items,
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

@Composable
private fun SearchShortcut(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun RecentRow(
    recipes: List<RecipeSummary>,
    onRecipeClick: (RecipeSummary) -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(count = recipes.size, key = { recipes[it].id }) { index ->
            val recipe = recipes[index]
            Box(modifier = Modifier.width(168.dp).clip(MaterialTheme.shapes.medium)) {
                RecipeCard(
                    recipe = recipe,
                    imageUrl = recipeImageUrl(recipe),
                    onClick = { onRecipeClick(recipe) },
                )
            }
        }
    }
}
