package org.opensources.umai.search.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.model.Food
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.suggestionsFor

/**
 * Mobile filter sheet. Every control maps to a filter Mealie really supports;
 * durations are intentionally absent and the reason is spelled out at the
 * bottom of the sheet rather than left unexplained. The order of the results
 * is not a filter and lives on the search screen itself.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filters: RecipeFilters,
    options: FilterOptionsState,
    onDismiss: () -> Unit,
    onApply: (RecipeFilters) -> Unit,
    onReset: () -> Unit,
    onFoodQueryChange: (String) -> Unit,
    onFoodSelected: (Food) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(filters) { mutableStateOf(filters) }
    var categoryQuery by remember { mutableStateOf("") }
    var tagQuery by remember { mutableStateOf("") }
    var foodQuery by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    var focusedSection by remember { mutableStateOf<String?>(null) }
    // With the keyboard up, a field near the bottom of the list could not be
    // scrolled to the top of the sheet: the extra room lets it get there, so
    // the suggestions under it stay in sight.
    val keyboardRoom by animateDpAsState(
        targetValue = if (WindowInsets.isImeVisible) KEYBOARD_ROOM else 0.dp,
        label = "keyboardRoom",
    )
    val roomReady = keyboardRoom == KEYBOARD_ROOM

    // Once the keyboard is up and the room made, the section being typed in
    // moves to the top of the sheet, its suggestions right under it.
    LaunchedEffect(focusedSection, roomReady) {
        val key = focusedSection ?: return@LaunchedEffect
        if (!roomReady) return@LaunchedEffect
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == key }
            ?.let { listState.animateScrollToItem(it.index) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 560.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 12.dp + keyboardRoom,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.filter_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                if (options.loading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                options.error?.let { error ->
                    item { NetworkErrorView(error = error) }
                }

                item {
                    ToggleRow(
                        label = stringResource(R.string.filter_favorites),
                        checked = draft.favoritesOnly,
                        onCheckedChange = { draft = draft.copy(favoritesOnly = it) },
                    )
                }

                item {
                    RatingSection(
                        minRating = draft.minRating,
                        onSelect = { draft = draft.copy(minRating = it) },
                    )
                }

                item {
                    AddedSection(
                        selected = draft.addedWithin,
                        onSelect = { draft = draft.copy(addedWithin = it) },
                    )
                }

                if (options.categories.isNotEmpty()) {
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    item(key = KEY_CATEGORIES) {
                        SearchablePicker(
                            title = stringResource(R.string.filter_categories),
                            fieldLabel = stringResource(R.string.filter_search_categories),
                            query = categoryQuery,
                            onQueryChange = { categoryQuery = it },
                            selected = options.categories
                                .filter { it.id in draft.categoryIds }
                                .map { PickerEntry(it.id, it.name) },
                            suggestions = options.categories
                                .suggestionsFor(categoryQuery, draft.categoryIds)
                                .map { PickerEntry(it.id, it.name) },
                            onAdd = { entry ->
                                draft = draft.copy(categoryIds = draft.categoryIds + entry.id)
                                categoryQuery = ""
                            },
                            onRemove = { entry ->
                                draft = draft.copy(categoryIds = draft.categoryIds - entry.id)
                            },
                            requireAll = draft.requireAllCategories,
                            onRequireAllChange = { draft = draft.copy(requireAllCategories = it) },
                            onFocusChange = { focusedSection = focusedSection.after(KEY_CATEGORIES, it) },
                        )
                    }
                }

                if (options.tags.isNotEmpty()) {
                    item(key = KEY_TAGS) {
                        SearchablePicker(
                            title = stringResource(R.string.filter_tags),
                            fieldLabel = stringResource(R.string.filter_search_tags),
                            query = tagQuery,
                            onQueryChange = { tagQuery = it },
                            selected = options.tags
                                .filter { it.id in draft.tagIds }
                                .map { PickerEntry(it.id, it.name) },
                            suggestions = options.tags
                                .suggestionsFor(tagQuery, draft.tagIds)
                                .map { PickerEntry(it.id, it.name) },
                            onAdd = { entry ->
                                draft = draft.copy(tagIds = draft.tagIds + entry.id)
                                tagQuery = ""
                            },
                            onRemove = { entry -> draft = draft.copy(tagIds = draft.tagIds - entry.id) },
                            requireAll = draft.requireAllTags,
                            onRequireAllChange = { draft = draft.copy(requireAllTags = it) },
                            onFocusChange = { focusedSection = focusedSection.after(KEY_TAGS, it) },
                        )
                    }
                }

                if (options.tools.isNotEmpty()) {
                    item {
                        ToolSection(
                            tools = options.tools,
                            selected = draft.toolIds,
                            requireAll = draft.requireAllTools,
                            onToggle = { id -> draft = draft.copy(toolIds = draft.toolIds.toggle(id)) },
                            onRequireAllChange = { draft = draft.copy(requireAllTools = it) },
                        )
                    }
                }

                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

                item(key = KEY_FOODS) {
                    val selectedFoods = options.selectedFoods.filter { it.id in draft.foodIds }
                    SearchablePicker(
                        title = stringResource(R.string.filter_foods),
                        fieldLabel = stringResource(R.string.filter_search_foods),
                        query = foodQuery,
                        onQueryChange = {
                            foodQuery = it
                            onFoodQueryChange(it)
                        },
                        selected = selectedFoods.map { PickerEntry(it.id, it.name) },
                        suggestions = options.foodResults
                            .filter { it.id !in draft.foodIds }
                            .takeIf { foodQuery.trim().length >= SearchViewModel.MIN_FOOD_QUERY }
                            .orEmpty()
                            .map { PickerEntry(it.id, it.name) },
                        onAdd = { entry ->
                            options.foodResults.firstOrNull { it.id == entry.id }?.let(onFoodSelected)
                            draft = draft.copy(foodIds = draft.foodIds + entry.id)
                            foodQuery = ""
                            onFoodQueryChange("")
                        },
                        onRemove = { entry -> draft = draft.copy(foodIds = draft.foodIds - entry.id) },
                        requireAll = draft.requireAllFoods,
                        onRequireAllChange = { draft = draft.copy(requireAllFoods = it) },
                        onFocusChange = { focusedSection = focusedSection.after(KEY_FOODS, it) },
                        hint = stringResource(R.string.filter_food_hint),
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.filter_time_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.filter_calories_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_reset))
                }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}

/** Tracks which searchable section holds the focus, if any. */
private fun String?.after(section: String, focused: Boolean): String? = when {
    focused -> section
    this == section -> null
    else -> this
}

internal fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

private const val KEY_CATEGORIES = "categories"
private const val KEY_TAGS = "tags"
private const val KEY_FOODS = "foods"

/** Enough for the last section of the sheet to reach its top above the keyboard. */
private val KEYBOARD_ROOM = 320.dp
