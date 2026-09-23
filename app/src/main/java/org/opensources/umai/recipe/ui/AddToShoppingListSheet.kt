package org.opensources.umai.recipe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.IngredientText
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.ShoppingListSummary

/**
 * Sends the ingredients of a recipe to one of the Mealie shopping lists.
 *
 * The reader picks the list, how many servings they are shopping for, and which
 * lines they actually need — the ones already in the cupboard are simply
 * unticked. Mealie's own "add recipe to list" endpoint does the rest: quantities
 * are scaled server-side and the list keeps its link to the recipe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddToShoppingListSheet(
    recipe: Recipe,
    lists: List<ShoppingListSummary>,
    loadingLists: Boolean,
    initialServings: Int,
    onDismiss: () -> Unit,
    onConfirm: (ShoppingListSummary, Int, List<RecipeIngredient>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val baseServings = recipe.baseServings

    var selectedListId by remember(lists) { mutableStateOf(lists.firstOrNull()?.id) }
    var servings by remember {
        mutableIntStateOf(initialServings.takeIf { it > 0 } ?: baseServings ?: 1)
    }
    var excluded by remember { mutableStateOf(emptySet<Int>()) }

    val selected = remember(excluded, recipe) {
        recipe.ingredients.filterIndexed { index, _ -> index !in excluded }
    }
    val scale = if (baseServings != null && baseServings > 0) {
        servings.toDouble() / baseServings
    } else {
        1.0
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 520.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.recipe_add_to_list),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                item { SectionLabel(stringResource(R.string.shopping_choose_list)) }

                if (loadingLists && lists.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                } else if (lists.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.shopping_no_lists_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            lists.forEach { list ->
                                FilterChip(
                                    selected = list.id == selectedListId,
                                    onClick = { selectedListId = list.id },
                                    label = { Text(list.name) },
                                )
                            }
                        }
                    }
                }

                if (baseServings != null) {
                    item { SectionLabel(stringResource(R.string.filter_servings)) }
                    item {
                        ServingsRow(
                            servings = servings,
                            baseServings = baseServings,
                            onChange = { servings = it.coerceIn(1, MAX_SERVINGS) },
                        )
                    }
                }

                if (recipe.ingredients.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            SectionLabel(stringResource(R.string.recipe_ingredients))
                            TextButton(
                                onClick = {
                                    excluded = if (excluded.isEmpty()) {
                                        recipe.ingredients.indices.toSet()
                                    } else {
                                        emptySet()
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (excluded.isEmpty()) {
                                            R.string.action_select_none
                                        } else {
                                            R.string.action_select_all
                                        },
                                    ),
                                )
                            }
                        }
                    }

                    items(count = recipe.ingredients.size) { index ->
                        val ingredient = recipe.ingredients[index]
                        IngredientCheckRow(
                            label = IngredientText.format(ingredient, scale),
                            checked = index !in excluded,
                            onCheckedChange = { checked ->
                                excluded = if (checked) excluded - index else excluded + index
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            Button(
                onClick = {
                    val list = lists.firstOrNull { it.id == selectedListId } ?: return@Button
                    onConfirm(list, servings, selected)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                enabled = selectedListId != null && selected.isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_add))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ServingsRow(servings: Int, baseServings: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pluralStringResource(R.plurals.plural_servings, servings, servings),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(servings - 1) }, enabled = servings > 1) {
                Icon(
                    Icons.Outlined.Remove,
                    contentDescription = stringResource(R.string.servings_decrease),
                )
            }
            Text(
                text = servings.toString(),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { onChange(servings + 1) }) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.servings_increase),
                )
            }
        }
    }

    if (servings != baseServings) {
        Text(
            text = stringResource(R.string.shopping_servings_scaled, baseServings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IngredientCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val MAX_SERVINGS = 99
