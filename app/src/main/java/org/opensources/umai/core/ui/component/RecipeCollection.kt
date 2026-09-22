package org.opensources.umai.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.settings.RecipeLayout

val RecipeGridSpacing = 12.dp

/**
 * Cell strategy for the chosen layout: an adaptive grid of cards, or a single
 * column of compact rows.
 */
fun recipeGridCells(layout: RecipeLayout): GridCells = when (layout) {
    RecipeLayout.GRID -> GridCells.Adaptive(minSize = 168.dp)
    RecipeLayout.LIST -> GridCells.Fixed(1)
}

/**
 * Adds recipe items plus a trailing spinner to a lazy grid. Kept here so Home
 * and Search render their results identically.
 */
fun LazyGridScope.recipeCards(
    recipes: List<RecipeSummary>,
    layout: RecipeLayout,
    imageUrlFor: (RecipeSummary) -> String?,
    onRecipeClick: (RecipeSummary) -> Unit,
    loadingMore: Boolean,
) {
    items(
        count = recipes.size,
        key = { index -> recipes[index].id },
    ) { index ->
        val recipe = recipes[index]
        when (layout) {
            RecipeLayout.GRID -> RecipeCard(
                recipe = recipe,
                imageUrl = imageUrlFor(recipe),
                onClick = { onRecipeClick(recipe) },
            )
            RecipeLayout.LIST -> RecipeRow(
                recipe = recipe,
                imageUrl = imageUrlFor(recipe),
                onClick = { onRecipeClick(recipe) },
            )
        }
    }

    if (loadingMore) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/** True when the grid is close enough to its end to fetch the next page. */
@Composable
fun LazyGridState.isNearEnd(threshold: Int = 6): Boolean {
    val state = remember(this) {
        derivedStateOf {
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - threshold
        }
    }
    return state.value
}

/** Same check for a plain lazy list. */
@Composable
fun LazyListState.isNearEnd(threshold: Int = 4): Boolean {
    val state = remember(this) {
        derivedStateOf {
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val total = layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - threshold
        }
    }
    return state.value
}

val RecipeGridPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

val RecipeGridArrangement = Arrangement.spacedBy(RecipeGridSpacing)
