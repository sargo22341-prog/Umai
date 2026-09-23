package org.opensources.umai.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.recipe.domain.CalorieFilter
import org.opensources.umai.core.model.MAX_RATING_STARS
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.search.domain.AddedWithin

/** One entry of a [SearchablePicker]: a category, a tag or an ingredient. */
data class PickerEntry(val id: String, val name: String)

/**
 * Minimum rating as a row of five stars: tapping the third star keeps recipes
 * rated three and more. Tapping the selected star again clears the filter.
 */
@Composable
internal fun RatingSection(minRating: Int?, onSelect: (Int?) -> Unit) {
    SectionHeader(stringResource(R.string.filter_rating))
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..MAX_RATING_STARS).forEach { value ->
            val filled = minRating != null && value <= minRating
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .selectable(
                        selected = minRating == value,
                        role = Role.RadioButton,
                        onClick = { onSelect(if (minRating == value) null else value) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = pluralStringResource(R.plurals.filter_rating_stars, value, value),
                    tint = if (filled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            text = if (minRating == null) {
                stringResource(R.string.filter_rating_any)
            } else {
                pluralStringResource(R.plurals.filter_rating_at_least, minRating, minRating)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddedSection(selected: AddedWithin, onSelect: (AddedWithin) -> Unit) {
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

/**
 * One range at a time; picking the selected one again clears it. The ranges
 * rely on the `calorie-<value>` tags, as explained under the chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaloriesSection(selected: CalorieFilter, onSelect: (CalorieFilter) -> Unit) {
    SectionHeader(stringResource(R.string.filter_calories))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CalorieFilter.entries.filter { it != CalorieFilter.ANY }.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) CalorieFilter.ANY else option) },
                label = {
                    Text(
                        option.maxCalories?.let { stringResource(R.string.filter_calories_up_to, it) }
                            ?: stringResource(R.string.filter_calories_unknown),
                    )
                },
            )
        }
    }
    Text(
        text = stringResource(R.string.filter_calories_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Instances hold few tools, so they stay a plain list of chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolSection(
    tools: List<Organizer>,
    selected: Set<String>,
    requireAll: Boolean,
    onToggle: (String) -> Unit,
    onRequireAllChange: (Boolean) -> Unit,
) {
    SectionHeader(stringResource(R.string.filter_tools))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.forEach { tool ->
            FilterChip(
                selected = tool.id in selected,
                onClick = { onToggle(tool.id) },
                label = { Text(tool.name) },
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

/**
 * A field to type in, the matches under it, and what was already picked.
 *
 * Nothing is listed before the user types: the picked entries are shown as
 * removable chips, and the suggestions appear — animated — as soon as the
 * query matches something.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchablePicker(
    title: String,
    fieldLabel: String,
    query: String,
    onQueryChange: (String) -> Unit,
    selected: List<PickerEntry>,
    suggestions: List<PickerEntry>,
    onAdd: (PickerEntry) -> Unit,
    onRemove: (PickerEntry) -> Unit,
    requireAll: Boolean,
    onRequireAllChange: (Boolean) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    Column {
        SectionHeader(title)

        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.forEach { entry ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(entry) },
                        label = { Text(entry.name) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.filter_remove_entry, entry.name),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .onFocusChanged { onFocusChange(it.isFocused) },
            label = { Text(fieldLabel) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_clear),
                        )
                    }
                }
            },
            supportingText = hint?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        AnimatedVisibility(
            visible = query.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (suggestions.isEmpty()) {
                Text(
                    text = stringResource(R.string.filter_no_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { entry ->
                        SuggestionChip(
                            onClick = { onAdd(entry) },
                            label = { Text(entry.name) },
                        )
                    }
                }
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
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun AddedWithin.labelRes(): Int = when (this) {
    AddedWithin.ANY -> R.string.filter_added_any
    AddedWithin.WEEK -> R.string.filter_added_week
    AddedWithin.MONTH -> R.string.filter_added_month
    AddedWithin.YEAR -> R.string.filter_added_year
}
