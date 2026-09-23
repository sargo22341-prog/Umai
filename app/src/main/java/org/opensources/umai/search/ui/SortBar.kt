package org.opensources.umai.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.search.domain.RecipeSort
import org.opensources.umai.search.domain.SortField

/**
 * The order of the results, one chip per column. The selected chip carries an
 * arrow showing the direction; tapping it again flips the arrow. The random
 * order has no direction, so it never shows one.
 */
@Composable
internal fun SortBar(
    sort: RecipeSort,
    onSelect: (SortField) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ascending = stringResource(R.string.sort_ascending)
    val descending = stringResource(R.string.sort_descending)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SortField.entries, key = { it.name }) { field ->
            val selected = sort.column == field
            FilterChip(
                selected = selected,
                onClick = { onSelect(field) },
                label = { Text(stringResource(field.labelRes())) },
                leadingIcon = if (field == SortField.RANDOM) {
                    { Icon(Icons.Rounded.Shuffle, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                trailingIcon = if (selected && field.hasDirection) {
                    {
                        Icon(
                            imageVector = if (sort.descending) {
                                Icons.Rounded.ArrowDownward
                            } else {
                                Icons.Rounded.ArrowUpward
                            },
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
                modifier = if (selected && field.hasDirection) {
                    Modifier.semantics { stateDescription = if (sort.descending) descending else ascending }
                } else {
                    Modifier
                },
            )
        }
    }
}

private fun SortField.labelRes(): Int = when (this) {
    SortField.CREATED -> R.string.sort_created
    SortField.NAME -> R.string.sort_name
    SortField.RATING -> R.string.sort_rating
    SortField.LAST_MADE -> R.string.sort_last_made
    SortField.RANDOM -> R.string.sort_random
}
