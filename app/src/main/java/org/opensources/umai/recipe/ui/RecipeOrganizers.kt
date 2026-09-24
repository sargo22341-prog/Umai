package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.search.domain.OrganizerEntry

/**
 * The categories, tags and tools of a recipe. Each opens a search on it. A
 * recipe with many of them shows [COLLAPSED_LINES] lines first, the rest behind
 * a "show more" button.
 */
@Composable
internal fun RecipeOrganizers(
    entries: List<OrganizerEntry>,
    onClick: (OrganizerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var hiddenCount by remember(entries) { mutableIntStateOf(0) }

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        LineLimitedFlow(
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            spacing = 8.dp,
            onHiddenCountChange = { hiddenCount = it },
        ) {
            entries.forEach { entry ->
                SuggestionChip(
                    onClick = { onClick(entry) },
                    label = { Text(entry.organizer.name) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    border = null,
                )
            }
        }
        if (expanded || hiddenCount > 0) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    if (expanded) {
                        stringResource(R.string.recipe_organizers_show_less)
                    } else {
                        stringResource(R.string.recipe_organizers_show_more, hiddenCount)
                    },
                )
            }
        }
    }
}

/**
 * Lays its children out in rows, like a flow row, but places no more than
 * [maxLines] rows and reports how many children it had to leave out.
 */
@Composable
private fun LineLimitedFlow(
    maxLines: Int,
    spacing: Dp,
    onHiddenCountChange: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }

        val rows = mutableListOf<MutableList<Int>>()
        var rowWidth = 0
        placeables.forEachIndexed { index, placeable ->
            val row = rows.lastOrNull()
            if (row == null || rowWidth + gap + placeable.width > constraints.maxWidth) {
                rows += mutableListOf(index)
                rowWidth = placeable.width
            } else {
                row += index
                rowWidth += gap + placeable.width
            }
        }

        val shown = rows.take(maxLines)
        onHiddenCountChange(placeables.size - shown.sumOf { it.size })
        val rowHeights = shown.map { row -> row.maxOf { placeables[it].height } }
        val height = rowHeights.sum()

        layout(constraints.maxWidth, height) {
            var y = 0
            shown.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEach { index ->
                    placeables[index].placeRelative(x, y)
                    x += placeables[index].width + gap
                }
                y += rowHeights[rowIndex]
            }
        }
    }
}

private const val COLLAPSED_LINES = 3
