package org.opensources.umai.planning.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.planning.domain.EstimatedFood
import org.opensources.umai.planning.domain.FoodSource
import org.opensources.umai.planning.domain.FoodSuggestion
import org.opensources.umai.planning.domain.Nutrient
import kotlin.math.roundToInt

/**
 * What was eaten, typed as it is said: "2 pommes, 1 café sans sucre". The
 * foods of the table it may be are offered while it is typed; searched, it is
 * looked up in full, and estimated by the local AI when the table lacks it.
 */
@Composable
internal fun DescriptionSection(state: FoodEntryUiState, actions: FoodEntryActions) {
    HelperText(stringResource(R.string.food_describe_intro))

    OutlinedTextField(
        value = state.description,
        onValueChange = actions.onDescriptionChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.food_describe_field)) },
        placeholder = { Text(stringResource(R.string.food_describe_placeholder)) },
        singleLine = true,
        enabled = !state.estimating,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { actions.onEstimate() }),
    )

    if (state.estimating) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                text = stringResource(R.string.food_describe_estimating),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onCancelEstimate) { Text(stringResource(R.string.action_cancel)) }
        }
    } else {
        Button(onClick = actions.onEstimate, enabled = state.canEstimate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Search, contentDescription = null)
            Text(stringResource(R.string.food_describe_search), modifier = Modifier.padding(start = 8.dp))
        }
    }

    state.suggestions.forEach { suggestion ->
        SuggestionRow(suggestion, onClick = { actions.onSuggestionChosen(suggestion) })
    }

    HelperText(
        stringResource(if (state.canAskModel) R.string.food_describe_model_hint else R.string.food_describe_no_model_hint),
    )
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SuggestionRow(suggestion: FoodSuggestion, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text = suggestion.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = itemDetails(suggestion.item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "300 g · 162 kcal", or the calories for 100 g while the amount is not known. */
@Composable
private fun itemDetails(item: EstimatedFood): String {
    val amount = item.amount
    val calories = item.calories
    return if (amount != null && calories != null) {
        stringResource(R.string.food_estimate_amount, amount.roundToInt(), item.unit.symbol, calories)
    } else {
        val per100 = item.per100[Nutrient.ENERGY]?.roundToInt() ?: 0
        stringResource(R.string.food_found_energy, per100, item.unit.symbol)
    }
}

/** Why what was typed gave no food. */
@Composable
internal fun DescriptionIssueBanner(issue: DescriptionIssue) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = stringResource(issue.messageRes()),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * The foods the description gave, with where their values come from, as the
 * licence of the Ciqual table asks; a guess of the model says it is one.
 */
@Composable
internal fun EstimateCard(state: FoodEntryUiState, actions: FoodEntryActions) {
    val estimate = state.estimate ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val color = MaterialTheme.colorScheme.onSecondaryContainer
            Text(
                text = stringResource(
                    if (estimate.byModel) R.string.food_estimate_model_title else R.string.food_estimate_table_title,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
            estimate.items.forEach { item ->
                val details = itemDetails(item)
                val line = if (item.source == FoodSource.MODEL) {
                    stringResource(R.string.food_estimate_item_guessed, item.name, details)
                } else {
                    stringResource(R.string.food_estimate_item, item.name, details)
                }
                Text(text = line, style = MaterialTheme.typography.bodyMedium, color = color)
            }
            if (estimate.items.size > 1) {
                state.calories?.let { total ->
                    Text(
                        text = stringResource(R.string.food_estimate_total, total),
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                }
            }
            if (estimate.byModel) {
                Text(
                    text = stringResource(R.string.food_estimate_model_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
            Text(text = stringResource(R.string.food_estimate_source), style = MaterialTheme.typography.bodySmall, color = color)
            TextButton(onClick = actions.onClearEstimate) { Text(stringResource(R.string.food_estimate_change)) }
        }
    }
}

@StringRes
private fun DescriptionIssue.messageRes(): Int = when (this) {
    DescriptionIssue.NOT_FOUND -> R.string.food_describe_not_found
    DescriptionIssue.MODEL_FOUND_NOTHING -> R.string.food_describe_model_nothing
    DescriptionIssue.MODEL_FAILED -> R.string.food_describe_model_failed
    DescriptionIssue.TABLE_UNREADABLE -> R.string.food_describe_table_unreadable
}
