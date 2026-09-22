package org.opensources.umai.recipe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.planning.ui.label
import org.opensources.umai.planning.ui.rememberPlanningWeek
import java.time.LocalDate
import java.time.format.FormatStyle

/**
 * Sends the ingredients of a recipe to one of the Mealie shopping lists, using
 * Mealie's own "add recipe to list" endpoint rather than copying items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListPicker(
    lists: List<ShoppingListSummary>,
    onDismiss: () -> Unit,
    onSelect: (ShoppingListSummary) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.shopping_choose_list),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )

            if (lists.isEmpty()) {
                Text(
                    text = stringResource(R.string.shopping_no_lists_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                lists.forEach { list ->
                    ListItem(
                        headlineContent = { Text(list.name) },
                        leadingContent = {
                            Icon(Icons.Outlined.ShoppingCart, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(list) },
                    )
                }
            }
        }
    }
}

/** Picks a day of the visible week and a meal slot for the recipe. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MealPlanPicker(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, MealType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val week = rememberPlanningWeek()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedType by remember { mutableStateOf(MealType.DINNER) }
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.recipe_add_to_plan),
                style = MaterialTheme.typography.titleMedium,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.forEach { day ->
                    FilterChip(
                        selected = day == selectedDate,
                        onClick = { selectedDate = day },
                        label = { Text(day.label(dateFormatter)) },
                    )
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
