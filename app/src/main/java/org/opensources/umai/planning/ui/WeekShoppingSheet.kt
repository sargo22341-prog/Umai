package org.opensources.umai.planning.ui

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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.IngredientText
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import org.opensources.umai.recipe.ui.labelRes
import java.time.format.FormatStyle

/** The callbacks of [WeekShoppingSheet], grouped so the signature stays readable. */
class WeekShoppingActions(
    val onDismiss: () -> Unit,
    val onToggle: (MealPlanEntry) -> Unit,
    val onSelectList: (ShoppingListSummary) -> Unit,
    val onServingsChange: (MealPlanEntry, Int) -> Unit,
    val onToggleIngredient: (MealPlanEntry, Int) -> Unit,
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * Sends the recipes planned on the visible days to a shopping list, in three
 * steps: which meals, how many servings each, which lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekShoppingSheet(state: WeekShoppingUiState, actions: WeekShoppingActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = actions.onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            val added = state.added
            if (added != null) {
                Done(added = added, listName = state.listName.orEmpty(), onClose = actions.onDismiss)
                return@Column
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 560.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.week_shopping_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(
                            R.string.week_shopping_step,
                            state.step.ordinal + 1,
                            WeekShoppingStep.entries.size,
                            stringResource(state.step.labelRes()),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                state.error?.let { error ->
                    item {
                        Text(
                            text = "${error.title()}\n${error.message()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (state.step == WeekShoppingStep.SERVINGS) {
                            TextButton(onClick = actions.onRetry) { Text(stringResource(R.string.action_retry)) }
                        }
                    }
                }
                when (state.step) {
                    WeekShoppingStep.RECIPES -> recipesStep(state, actions)
                    WeekShoppingStep.SERVINGS -> servingsStep(state, actions)
                    WeekShoppingStep.INGREDIENTS -> ingredientsStep(state, actions)
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.step != WeekShoppingStep.RECIPES) {
                    OutlinedButton(onClick = actions.onBack, enabled = !state.adding, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.create_previous))
                    }
                }
                Button(onClick = actions.onNext, enabled = state.canContinue, modifier = Modifier.weight(1f)) {
                    if (state.adding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            stringResource(
                                if (state.step == WeekShoppingStep.INGREDIENTS) R.string.action_add else R.string.create_next,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.recipesStep(state: WeekShoppingUiState, actions: WeekShoppingActions) {
    item { SectionLabel(stringResource(R.string.shopping_choose_list)) }
    item {
        when {
            state.loadingLists && state.lists.isEmpty() -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
            state.lists.isEmpty() -> Hint(stringResource(R.string.shopping_no_lists_message))
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.lists.forEach { list ->
                    FilterChip(
                        selected = list.id == state.listId,
                        onClick = { actions.onSelectList(list) },
                        label = { Text(list.name) },
                    )
                }
            }
        }
    }
    item { SectionLabel(stringResource(R.string.week_shopping_meals)) }
    if (state.entries.isEmpty()) {
        item { Hint(stringResource(R.string.week_shopping_no_recipe)) }
    }
    state.entries.forEach { entry ->
        item(key = "meal-${entry.id}") {
            CheckRow(
                label = entry.recipe?.name.orEmpty(),
                supporting = mealLabel(entry),
                checked = entry.id in state.selected,
                onCheckedChange = { actions.onToggle(entry) },
            )
        }
    }
}

private fun LazyListScope.servingsStep(state: WeekShoppingUiState, actions: WeekShoppingActions) {
    if (state.loadingRecipes) {
        item { CircularProgressIndicator(modifier = Modifier.padding(16.dp).size(28.dp)) }
        return
    }
    state.chosen.forEach { entry ->
        item(key = "servings-${entry.id}") {
            val recipe = state.recipeOf(entry)
            val servings = state.servings[entry.id] ?: recipe?.baseServings ?: 1
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.recipe?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (recipe?.baseServings == null) {
                            stringResource(R.string.week_shopping_no_servings)
                        } else {
                            mealLabel(entry)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (recipe?.baseServings != null) {
                    IconButton(onClick = { actions.onServingsChange(entry, servings - 1) }, enabled = servings > 1) {
                        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.servings_decrease))
                    }
                    Text(
                        text = pluralStringResource(R.plurals.plural_servings, servings, servings),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    IconButton(onClick = { actions.onServingsChange(entry, servings + 1) }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.servings_increase))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.ingredientsStep(state: WeekShoppingUiState, actions: WeekShoppingActions) {
    state.chosen.forEach { entry ->
        val recipe = state.recipeOf(entry) ?: return@forEach
        val base = recipe.baseServings
        val servings = state.servings[entry.id] ?: base ?: 1
        val scale = if (base != null && base > 0) servings.toDouble() / base else 1.0
        val excluded = state.excluded[entry.id].orEmpty()
        item(key = "recipe-${entry.id}") { SectionLabel(recipe.name) }
        if (recipe.ingredients.isEmpty()) {
            item(key = "empty-${entry.id}") { Hint(stringResource(R.string.recipe_no_ingredients)) }
        }
        recipe.ingredients.forEachIndexed { index, ingredient ->
            item(key = "line-${entry.id}-$index") {
                CheckRow(
                    label = IngredientText.format(ingredient, scale),
                    supporting = null,
                    checked = index !in excluded,
                    onCheckedChange = { actions.onToggleIngredient(entry, index) },
                )
            }
        }
    }
}

@Composable
private fun mealLabel(entry: MealPlanEntry): String {
    val formatter = rememberDateFormatter(FormatStyle.MEDIUM)
    return stringResource(R.string.planning_entry_for, entry.date.label(formatter), stringResource(entry.type.labelRes()))
}

@Composable
private fun Done(added: Int, listName: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.week_shopping_done, added, added, listName),
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_close)) }
    }
}

@Composable
private fun CheckRow(label: String, supporting: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            supporting?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun WeekShoppingStep.labelRes(): Int = when (this) {
    WeekShoppingStep.RECIPES -> R.string.week_shopping_step_recipes
    WeekShoppingStep.SERVINGS -> R.string.week_shopping_step_servings
    WeekShoppingStep.INGREDIENTS -> R.string.week_shopping_step_ingredients
}
