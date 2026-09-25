package org.opensources.umai.planning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.core.ui.component.imageContentDescription
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import org.opensources.umai.planning.domain.MealPlanProposal
import org.opensources.umai.planning.domain.PlannedMeal
import org.opensources.umai.recipe.ui.labelRes
import java.time.LocalDate

/** The callbacks of [AutoPlanSheet], grouped so the signature stays readable. */
class AutoPlanActions(
    val onDismiss: () -> Unit,
    val onScopeChange: (AutoPlanScope) -> Unit,
    val onDayChange: (LocalDate) -> Unit,
    val onPropose: () -> Unit,
    val onRegenerate: () -> Unit,
    val onReplace: (Int) -> Unit,
    val onAccept: () -> Unit,
    val onOpenDishTypes: () -> Unit,
)

/**
 * Plans dishes for the empty lunches and dinners of the week or of one day,
 * and shows the plan before anything is written: each dish can be swapped,
 * or the whole plan drawn again.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoPlanSheet(
    state: AutoPlanUiState,
    actions: AutoPlanActions,
    recipeImageUrl: (RecipeSummary) -> String?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = actions.onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.auto_plan_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.auto_plan_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.scope == AutoPlanScope.WEEK,
                    onClick = { actions.onScopeChange(AutoPlanScope.WEEK) },
                    enabled = !state.working,
                    label = { Text(stringResource(R.string.auto_plan_scope_week)) },
                )
                FilterChip(
                    selected = state.scope == AutoPlanScope.DAY,
                    onClick = { actions.onScopeChange(AutoPlanScope.DAY) },
                    enabled = !state.working,
                    label = { Text(stringResource(R.string.auto_plan_scope_day)) },
                )
            }
            if (state.scope == AutoPlanScope.DAY) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.plannableDays.forEach { day ->
                        FilterChip(
                            selected = day == state.day,
                            onClick = { actions.onDayChange(day) },
                            enabled = !state.working,
                            label = { Text(day.label(today = state.today)) },
                        )
                    }
                }
            }

            val slotCount = state.slots.size
            Text(
                text = if (slotCount == 0) {
                    stringResource(R.string.auto_plan_nothing_to_fill)
                } else {
                    pluralStringResource(R.plurals.auto_plan_meals_to_fill, slotCount, slotCount)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            val phase = state.phase
            val proposal = state.proposal
            val error = state.error
            when {
                phase != null -> Progress(phase)
                proposal != null -> Proposal(state, proposal, actions, recipeImageUrl)
                else -> Button(
                    onClick = actions.onPropose,
                    enabled = slotCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.auto_plan_propose)) }
            }

            if (error != null) {
                Text(
                    text = "${error.title()}\n${error.message()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.noDishes) {
                Text(
                    text = stringResource(R.string.auto_plan_no_dishes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()
            TextButton(onClick = actions.onOpenDishTypes, enabled = !state.working) {
                Text(stringResource(R.string.dish_types_title))
            }
        }
    }
}

@Composable
private fun Progress(phase: AutoPlanPhase) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(
                when (phase) {
                    AutoPlanPhase.READING_RECIPES -> R.string.auto_plan_reading_recipes
                    AutoPlanPhase.READING_DISHES -> R.string.auto_plan_reading_dishes
                    AutoPlanPhase.RECOGNIZING -> R.string.auto_plan_recognizing
                    AutoPlanPhase.COMPOSING -> R.string.auto_plan_composing
                    AutoPlanPhase.SAVING -> R.string.auto_plan_saving
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Proposal(
    state: AutoPlanUiState,
    proposal: MealPlanProposal,
    actions: AutoPlanActions,
    recipeImageUrl: (RecipeSummary) -> String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        proposal.meals.forEachIndexed { index, meal ->
            MealRow(
                meal = meal,
                today = state.today,
                imageUrl = recipeImageUrl(meal.candidate.recipe),
                onReplace = { actions.onReplace(index) },
            )
        }
        if (proposal.unfilled.isNotEmpty()) {
            Text(
                text = pluralStringResource(R.plurals.auto_plan_unfilled, proposal.unfilled.size, proposal.unfilled.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = pluralStringResource(R.plurals.auto_plan_ingredient_count, proposal.ingredientCount, proposal.ingredientCount),
            style = MaterialTheme.typography.titleSmall,
        )
        if (proposal.shared.isNotEmpty()) {
            Text(
                text = stringResource(R.string.auto_plan_shared),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                proposal.shared.take(MAX_SHARED_SHOWN).forEach { shared ->
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.auto_plan_shared_item, shared.name, shared.recipeCount)) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = actions.onRegenerate, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_plan_regenerate))
            }
            Button(
                onClick = actions.onAccept,
                enabled = proposal.meals.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.auto_plan_accept)) }
        }
    }
}

@Composable
private fun MealRow(meal: PlannedMeal, today: LocalDate, imageUrl: String?, onReplace: () -> Unit) {
    val recipe = meal.candidate.recipe
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RemoteImage(
            url = imageUrl,
            contentDescription = imageContentDescription(recipe),
            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
            placeholderIconSize = 20.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${meal.slot.date.label(today = today)} · ${stringResource(meal.slot.type.labelRes())}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(recipe.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onReplace) {
            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.auto_plan_replace, recipe.name))
        }
    }
}

private const val MAX_SHARED_SHOWN = 12
