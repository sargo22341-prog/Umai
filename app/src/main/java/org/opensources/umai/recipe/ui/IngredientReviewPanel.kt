package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.IngredientAnalysis
import org.opensources.umai.recipe.domain.IngredientChoice
import org.opensources.umai.recipe.domain.IngredientLineParser
import org.opensources.umai.recipe.domain.RecipeDraft

class IngredientReviewActions(
    val analyze: () -> Unit = {},
    val review: (DraftIngredient) -> Unit = {},
    val search: (String, String) -> Unit = { _, _ -> },
    val confirm: (IngredientAnalysis) -> Unit = {},
    val dismiss: () -> Unit = {},
) {
    constructor(review: IngredientReview) : this(review::analyze, review::review, review::search, review::confirm, review::dismiss)
}

@Composable
internal fun IngredientReviewPanel(
    draft: RecipeDraft,
    state: IngredientReviewState,
    actions: IngredientReviewActions,
    enabled: Boolean,
) {
    val unresolved = draft.ingredientsNeedingReview
    Text(stringResource(R.string.ingredients_analysis_help), style = MaterialTheme.typography.bodySmall)
    if (draft.servings <= 0) Text(stringResource(R.string.ingredients_servings_needed))
    if (state.busy) CircularProgressIndicator()
    state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    if (unresolved.isEmpty()) {
        Text(stringResource(R.string.ingredients_analyzed, draft.quantifiedIngredientCount))
        if (draft.unscaledIngredientCount > 0) {
            Text(stringResource(R.string.ingredients_unscaled, draft.unscaledIngredientCount))
        }
    } else {
        OutlinedButton(onClick = actions.analyze, enabled = enabled && !state.busy) {
            Text(stringResource(R.string.ingredients_analyze))
        }
    }
    draft.writtenIngredients.forEach { line ->
        TextButton(onClick = { actions.review(line) }, enabled = enabled && !state.busy) {
            val label = if (line.analysis?.isResolved == true) R.string.ingredients_edit_line else R.string.ingredients_review_line
            Text(stringResource(label, line.text))
        }
    }
    state.reviewing?.let { line -> IngredientReviewDialog(line, state, actions) }
}

@Composable
private fun IngredientReviewDialog(line: DraftIngredient, state: IngredientReviewState, actions: IngredientReviewActions) {
    val initial = line.analysis?.takeIf { it.isScalable || it.keptAsText }
        ?: IngredientLineParser.parse(line.text) ?: line.analysis
    var quantity by rememberSaveable(line) { mutableStateOf(initial?.quantity?.takeIf { it > 0 }?.toString().orEmpty()) }
    var food by rememberSaveable(line) { mutableStateOf(initial?.food?.name.orEmpty()) }
    var unit by rememberSaveable(line) { mutableStateOf(initial?.unit?.name.orEmpty()) }
    var note by rememberSaveable(line) { mutableStateOf(initial?.note.orEmpty()) }
    var selectedFood by androidx.compose.runtime.remember(line) { mutableStateOf(initial?.food) }
    var selectedUnit by androidx.compose.runtime.remember(line) { mutableStateOf(initial?.unit) }
    LaunchedEffect(line, food, unit) { actions.search(food, unit) }
    val number = quantity.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val chosenFood = selectedFood?.takeIf { it.name == food } ?: state.choices.foods.firstOrNull { it.name == food }
        ?: initial?.food?.takeIf { it.name == food } ?: IngredientChoice(food.trim())
    val chosenUnit = selectedUnit?.takeIf { it.name == unit } ?: state.choices.units.firstOrNull { it.name == unit }
        ?: initial?.unit?.takeIf { it.name == unit } ?: unit.trim().takeIf { it.isNotBlank() }?.let { IngredientChoice(it) }
    AlertDialog(
        onDismissRequest = { if (!state.busy) actions.dismiss() },
        title = { Text(stringResource(R.string.ingredients_review_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewIntroduction(line, initial, state.busy, actions.confirm)
                ReviewField(quantity, { quantity = it }, R.string.ingredients_quantity)
                ReviewField(food, { food = it }, R.string.ingredients_food)
                Text(stringResource(if (chosenFood.id != null) R.string.ingredients_linked else R.string.ingredients_unlinked))
                ReviewField(unit, { unit = it }, R.string.ingredients_unit)
                if (chosenUnit != null) Text(stringResource(if (chosenUnit.id != null) R.string.ingredients_linked else R.string.ingredients_unlinked))
                ReviewField(note, { note = it }, R.string.ingredients_note)
                TextButton(onClick = { actions.search(food, unit) }, enabled = !state.busy) {
                    Text(stringResource(R.string.ingredients_search_choices))
                }
                SearchFeedback(state)
                ChoiceButtons(state.choices.foods, { selectedFood = it; food = it.name })
                ChoiceButtons(state.choices.units, { selectedUnit = it; unit = it.name })
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.confirm(IngredientAnalysis(number, chosenFood, chosenUnit, note.trim())) },
                enabled = number != null && food.isNotBlank() && !state.busy) {
                Text(stringResource(R.string.ingredients_confirm))
            }
        },
        dismissButton = { TextButton(onClick = actions.dismiss, enabled = !state.busy) {
            Text(stringResource(R.string.action_cancel))
        } },
    )
}

@Composable
private fun ReviewIntroduction(line: DraftIngredient, initial: IngredientAnalysis?, busy: Boolean, confirm: (IngredientAnalysis) -> Unit) {
    Text(line.text)
    if (initial?.modelSuggestion == true) Text(stringResource(R.string.ingredients_ai_suggestion))
    initial?.confidence?.let { Text(stringResource(R.string.ingredients_confidence, it.toString())) }
    Text(stringResource(R.string.ingredients_review_help))
    TextButton(onClick = { confirm(IngredientAnalysis(null, null, note = line.text, keptAsText = true)) }, enabled = !busy) {
        Text(stringResource(R.string.ingredients_keep_text))
    }
}

@Composable
private fun SearchFeedback(state: IngredientReviewState) {
    if (state.busy || state.searching) CircularProgressIndicator()
    state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
    if (state.searched && state.error == null && state.choices.foods.isEmpty() && state.choices.units.isEmpty()) {
        Text(stringResource(R.string.ingredients_no_choices))
    }
}

@Composable
private fun ReviewField(value: String, change: (String) -> Unit, @StringRes label: Int) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(label)) })
}

@Composable
private fun ChoiceButtons(choices: List<IngredientChoice>, select: (IngredientChoice) -> Unit) {
    choices.forEach { choice -> TextButton(onClick = { select(choice) }) {
        Text(stringResource(if (choice.id != null) R.string.ingredients_mealie_choice else R.string.ingredients_reference_choice, choice.name))
    } }
}
