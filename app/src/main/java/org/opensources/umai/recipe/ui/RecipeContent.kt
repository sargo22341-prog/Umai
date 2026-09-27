package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.markdown.MarkdownText
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.search.domain.OrganizerEntry

/**
 * The scrollable body of the recipe page: picture, facts, description,
 * ingredients, instructions with their embedded images, notes, nutrition,
 * categories and tags, source and comments.
 */
@Composable
internal fun RecipeContent(
    recipe: Recipe,
    state: RecipeDetailUiState,
    imageUrl: String?,
    stepImageUrl: (String, String) -> String?,
    stepPhotoUrl: (String) -> String?,
    contentPadding: PaddingValues,
    onServingsChange: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onRate: (Int) -> Unit,
    onPostComment: (String) -> Unit,
    onDeleteComment: (RecipeComment) -> Unit,
    onOpenSource: (String) -> Unit,
    onOrganizerClick: (OrganizerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var commentFieldFocused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    // While a comment is typed, the end of the page — the field — follows the
    // keyboard frame by frame as it slides in, so what is typed stays in sight.
    LaunchedEffect(commentFieldFocused, imeBottom) {
        if (!commentFieldFocused || imeBottom == 0) return@LaunchedEffect
        // The list takes the new keyboard height into account one frame later.
        withFrameNanos { }
        listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    LazyColumn(
        state = listState,
        // The list shrinks with the keyboard, following its animation, so the
        // comment field at the very end can always be scrolled above it.
        modifier = modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding)
            .imePadding(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
    ) {
        headerItems(recipe, state, imageUrl, onToggleFavorite, onRate)
        ingredientItems(recipe, state, stepPhotoUrl, onServingsChange)
        stepItems(recipe, stepImageUrl, stepPhotoUrl)
        referenceItems(recipe, state, onOrganizerClick, onOpenSource)
        if (state.commentsVisible) {
            item { HorizontalDivider(Modifier.padding(horizontal = 20.dp)) }
            recipeComments(
                state = state,
                onPostComment = onPostComment,
                onDeleteComment = onDeleteComment,
                onFieldFocusChange = { commentFieldFocused = it },
            )
        }
    }
}

/** The picture, the name, the rating, the facts and the description. */
private fun LazyListScope.headerItems(
    recipe: Recipe,
    state: RecipeDetailUiState,
    imageUrl: String?,
    onToggleFavorite: () -> Unit,
    onRate: (Int) -> Unit,
) {
    item {
        RemoteImage(
            url = imageUrl,
            contentDescription = if (recipe.summary.hasImage) {
                stringResource(R.string.cd_recipe_image, recipe.name)
            } else {
                stringResource(R.string.cd_recipe_no_image)
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f),
            placeholderIconSize = 48.dp,
        )
    }
    item {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = recipe.name, style = MaterialTheme.typography.headlineSmall)
            RecipeRatingRow(
                isFavorite = state.isFavorite,
                rating = state.shownRating,
                ratingIsOwn = state.ownRating != null,
                onToggleFavorite = onToggleFavorite,
                onRate = onRate,
            )
            RecipeFacts(recipe, showTimes = state.display.showTimes)
            if (recipe.summary.description.isNotBlank()) {
                MarkdownText(markdown = recipe.summary.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun LazyListScope.ingredientItems(
    recipe: Recipe,
    state: RecipeDetailUiState,
    stepPhotoUrl: (String) -> String?,
    onServingsChange: (Int) -> Unit,
) {
    item { HorizontalDivider(Modifier.padding(horizontal = 20.dp)) }
    item { IngredientsHeader(servings = state.servings, canScale = state.canScale, onServingsChange = onServingsChange) }
    recipe.ingredientsPhoto?.let { file ->
        item {
            RemoteImage(
                url = stepPhotoUrl(file),
                contentDescription = stringResource(R.string.cd_ingredients_photo),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
    }
    if (recipe.ingredients.isEmpty()) {
        item { Hint(stringResource(R.string.recipe_no_ingredients)) }
    } else {
        items(count = recipe.ingredients.size) { index -> IngredientRow(recipe.ingredients[index], state.scale) }
    }
}

private fun LazyListScope.stepItems(
    recipe: Recipe,
    stepImageUrl: (String, String) -> String?,
    stepPhotoUrl: (String) -> String?,
) {
    item { Spacer(Modifier.height(8.dp)) }
    item { HorizontalDivider(Modifier.padding(horizontal = 20.dp)) }
    item { SectionTitle(stringResource(R.string.recipe_instructions)) }
    if (recipe.steps.isEmpty()) {
        item { Hint(stringResource(R.string.recipe_no_instructions)) }
    } else {
        items(count = recipe.steps.size) { index ->
            StepBlock(
                index = index,
                step = recipe.steps[index],
                recipeId = recipe.id,
                stepImageUrl = stepImageUrl,
                stepPhotoUrl = stepPhotoUrl,
            )
        }
    }
}

/** Notes, nutrition, categories and tags, and the page the recipe comes from. */
private fun LazyListScope.referenceItems(
    recipe: Recipe,
    state: RecipeDetailUiState,
    onOrganizerClick: (OrganizerEntry) -> Unit,
    onOpenSource: (String) -> Unit,
) {
    if (recipe.notes.isNotEmpty()) {
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp)) }
        item { SectionTitle(stringResource(R.string.recipe_notes)) }
        items(count = recipe.notes.size) { index -> NoteBlock(recipe.notes[index]) }
    }
    recipe.nutrition
        ?.takeIf { state.display.showNutrition && (recipe.showNutrition || !it.isEmpty) }
        ?.let { nutrition ->
            item { HorizontalDivider(Modifier.padding(horizontal = 20.dp)) }
            item { SectionTitle(stringResource(R.string.recipe_nutrition)) }
            item { NutritionTable(nutrition) }
        }
    val organizers = state.organizers
    if (organizers.isNotEmpty()) {
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp)) }
        item { SectionTitle(stringResource(R.string.recipe_organizers)) }
        item { RecipeOrganizers(entries = organizers, onClick = onOrganizerClick) }
    }
    recipe.summary.sourceUrl?.takeIf { state.display.showSource }?.let { url ->
        item { HorizontalDivider(Modifier.padding(horizontal = 20.dp)) }
        item { SourceLink(url = url, onOpen = { onOpenSource(url) }) }
    }
}
