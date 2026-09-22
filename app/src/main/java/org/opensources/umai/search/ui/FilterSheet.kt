package org.opensources.umai.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import org.opensources.umai.R
import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.search.domain.AddedWithin
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.RecipeSort

/**
 * Mobile filter sheet. Every control maps to a filter Mealie really supports;
 * durations are intentionally absent and the reason is spelled out at the
 * bottom of the sheet rather than left unexplained.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var foodQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 560.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 12.dp,
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
                    SortSection(
                        selected = draft.sort,
                        onSelect = { draft = draft.copy(sort = it) },
                    )
                }

                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

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
                    ServingsSection(
                        min = draft.minServings,
                        max = draft.maxServings,
                        onMinChange = { draft = draft.copy(minServings = it) },
                        onMaxChange = { draft = draft.copy(maxServings = it) },
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
                    item {
                        OrganizerSection(
                            title = stringResource(R.string.filter_categories),
                            options = options.categories,
                            selected = draft.categoryIds,
                            requireAll = draft.requireAllCategories,
                            onToggle = { id ->
                                draft = draft.copy(categoryIds = draft.categoryIds.toggle(id))
                            },
                            onRequireAllChange = { draft = draft.copy(requireAllCategories = it) },
                        )
                    }
                }

                if (options.tags.isNotEmpty()) {
                    item {
                        OrganizerSection(
                            title = stringResource(R.string.filter_tags),
                            options = options.tags,
                            selected = draft.tagIds,
                            requireAll = draft.requireAllTags,
                            onToggle = { id -> draft = draft.copy(tagIds = draft.tagIds.toggle(id)) },
                            onRequireAllChange = { draft = draft.copy(requireAllTags = it) },
                            searchable = true,
                        )
                    }
                }

                if (options.tools.isNotEmpty()) {
                    item {
                        OrganizerSection(
                            title = stringResource(R.string.filter_tools),
                            options = options.tools,
                            selected = draft.toolIds,
                            requireAll = draft.requireAllTools,
                            onToggle = { id -> draft = draft.copy(toolIds = draft.toolIds.toggle(id)) },
                            onRequireAllChange = { draft = draft.copy(requireAllTools = it) },
                        )
                    }
                }

                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

                item {
                    FoodSection(
                        query = foodQuery,
                        results = options.foodResults,
                        selectedFoods = options.selectedFoods,
                        selectedIds = draft.foodIds,
                        requireAll = draft.requireAllFoods,
                        onQueryChange = {
                            foodQuery = it
                            onFoodQueryChange(it)
                        },
                        onToggle = { food ->
                            onFoodSelected(food)
                            draft = draft.copy(foodIds = draft.foodIds.toggle(food.id))
                        },
                        onRequireAllChange = { draft = draft.copy(requireAllFoods = it) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortSection(selected: RecipeSort, onSelect: (RecipeSort) -> Unit) {
    SectionHeader(stringResource(R.string.filter_sort))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecipeSort.entries.forEach { sort ->
            FilterChip(
                selected = selected == sort,
                onClick = { onSelect(sort) },
                label = { Text(stringResource(sort.labelRes())) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingSection(minRating: Int?, onSelect: (Int?) -> Unit) {
    SectionHeader(stringResource(R.string.filter_rating))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = minRating == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_rating_any)) },
        )
        (5 downTo 1).forEach { value ->
            FilterChip(
                selected = minRating == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(R.string.filter_rating_value, value)) },
            )
        }
    }
}

@Composable
private fun ServingsSection(
    min: Int?,
    max: Int?,
    onMinChange: (Int?) -> Unit,
    onMaxChange: (Int?) -> Unit,
) {
    SectionHeader(stringResource(R.string.filter_servings))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            value = min,
            label = stringResource(R.string.filter_servings_min),
            onValueChange = onMinChange,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = max,
            label = stringResource(R.string.filter_servings_max),
            onValueChange = onMaxChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumberField(
    value: Int?,
    label: String,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text ->
            onValueChange(text.filter { it.isDigit() }.take(3).toIntOrNull())
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddedSection(selected: AddedWithin, onSelect: (AddedWithin) -> Unit) {
    SectionHeader(stringResource(R.string.filter_added))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AddedWithin.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.labelRes())) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizerSection(
    title: String,
    options: List<Organizer>,
    selected: Set<String>,
    requireAll: Boolean,
    onToggle: (String) -> Unit,
    onRequireAllChange: (Boolean) -> Unit,
    searchable: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(options, query, selected) {
        val filtered = if (query.isBlank()) {
            options
        } else {
            options.filter { it.name.contains(query, ignoreCase = true) }
        }
        // Selected entries stay visible even when they fall outside the filter.
        (options.filter { it.id in selected } + filtered).distinctBy { it.id }.take(MAX_VISIBLE)
    }

    SectionHeader(title)

    if (searchable && options.size > MAX_VISIBLE) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            label = { Text(stringResource(R.string.filter_search_list)) },
            singleLine = true,
        )
    }

    if (visible.isEmpty()) {
        Text(
            text = stringResource(R.string.filter_no_match),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { organizer ->
            FilterChip(
                selected = organizer.id in selected,
                onClick = { onToggle(organizer.id) },
                label = { Text(organizer.name) },
            )
        }
    }

    if (selected.size > 1) {
        ToggleRow(
            label = stringResource(R.string.filter_require_all),
            checked = requireAll,
            onCheckedChange = onRequireAllChange,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodSection(
    query: String,
    results: List<Food>,
    selectedFoods: List<Food>,
    selectedIds: Set<String>,
    requireAll: Boolean,
    onQueryChange: (String) -> Unit,
    onToggle: (Food) -> Unit,
    onRequireAllChange: (Boolean) -> Unit,
) {
    SectionHeader(stringResource(R.string.filter_foods))

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        label = { Text(stringResource(R.string.filter_foods)) },
        supportingText = { Text(stringResource(R.string.filter_food_hint)) },
        singleLine = true,
    )

    val chips = remember(results, selectedFoods, selectedIds) {
        (selectedFoods.filter { it.id in selectedIds } + results).distinctBy { it.id }
    }

    if (chips.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { food ->
                FilterChip(
                    selected = food.id in selectedIds,
                    onClick = { onToggle(food) },
                    label = { Text(food.name) },
                )
            }
        }
    }

    if (selectedIds.size > 1) {
        ToggleRow(
            label = stringResource(R.string.filter_require_all),
            checked = requireAll,
            onCheckedChange = onRequireAllChange,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

private const val MAX_VISIBLE = 40

private fun RecipeSort.labelRes(): Int = when (this) {
    RecipeSort.RECENT -> R.string.sort_recent
    RecipeSort.OLDEST -> R.string.sort_oldest
    RecipeSort.NAME_ASC -> R.string.sort_name_asc
    RecipeSort.NAME_DESC -> R.string.sort_name_desc
    RecipeSort.RATING -> R.string.sort_rating
    RecipeSort.LAST_MADE -> R.string.sort_last_made
    RecipeSort.RANDOM -> R.string.sort_random
}

private fun AddedWithin.labelRes(): Int = when (this) {
    AddedWithin.ANY -> R.string.filter_added_any
    AddedWithin.WEEK -> R.string.filter_added_week
    AddedWithin.MONTH -> R.string.filter_added_month
    AddedWithin.YEAR -> R.string.filter_added_year
}
