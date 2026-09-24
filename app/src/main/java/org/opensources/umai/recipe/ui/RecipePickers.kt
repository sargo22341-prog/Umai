package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.model.MealType
import org.opensources.umai.planning.ui.label
import org.opensources.umai.planning.domain.PlanningWeek
import java.time.LocalDate

/**
 * Picks a day and a meal slot for the recipe: a day of this week or of the
 * next one, Monday to Sunday as the meal plan shows them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MealPlanPicker(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, MealType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now() }
    val weeks = remember(today) {
        val thisWeek = PlanningWeek.startOf(today)
        listOf(
            R.string.planning_this_week to PlanningWeek.days(thisWeek),
            R.string.planning_next_week to PlanningWeek.days(thisWeek.plusWeeks(1)),
        )
    }
    var selectedDate by remember { mutableStateOf(today) }
    var selectedType by remember { mutableStateOf(MealType.DINNER) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recipe_add_to_plan),
                style = MaterialTheme.typography.titleMedium,
            )

            weeks.forEach { (titleRes, days) ->
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEach { day ->
                        FilterChip(
                            selected = day == selectedDate,
                            onClick = { selectedDate = day },
                            label = { Text(day.label(today)) },
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.planning_meal_type),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.displayOrder.forEach { type ->
                    FilterChip(
                        selected = type == selectedType,
                        onClick = { selectedType = type },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }

            Button(
                onClick = { onConfirm(selectedDate, selectedType) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_add))
            }
        }
    }
}

internal fun MealType.labelRes(): Int = when (this) {
    MealType.BREAKFAST -> R.string.meal_breakfast
    MealType.LUNCH -> R.string.meal_lunch
    MealType.DINNER -> R.string.meal_dinner
    MealType.SIDE -> R.string.meal_side
    MealType.SNACK -> R.string.meal_snack
    MealType.DRINK -> R.string.meal_drink
    MealType.DESSERT -> R.string.meal_dessert
}
