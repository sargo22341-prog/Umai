package org.opensources.umai.planning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.RecipeRow
import org.opensources.umai.recipe.ui.labelRes
import java.time.LocalDate
import java.time.format.FormatStyle

/**
 * Adds an entry to one day of the meal plan: either a recipe from the instance
 * or, as Mealie also allows, a free-text note for a meal that is not a recipe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMealSheet(
    date: LocalDate,
    picker: RecipePickerState,
    onQueryChange: (String) -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
    onDismiss: () -> Unit,
    onAddRecipe: (MealType, RecipeSummary) -> Unit,
    onAddNote: (MealType, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    var mealType by remember { mutableStateOf(MealType.DINNER) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.planning_add_meal),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = date.format(dateFormatter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.displayOrder.forEach { type ->
                    FilterChip(
                        selected = type == mealType,
                        onClick = { mealType = type },
                        label = { Text(stringResource(type.labelRes())) },
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.planning_choose_recipe),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = picker.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.planning_search_recipe)) },
                singleLine = true,
            )

            if (picker.loading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }

            if (picker.results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = picker.results.size, key = { picker.results[it].id }) { index ->
                        val recipe = picker.results[index]
                        RecipeRow(
                            recipe = recipe,
                            imageUrl = recipeImageUrl(recipe),
                            onClick = { onAddRecipe(mealType, recipe) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.planning_or_note),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.planning_note_placeholder)) },
                singleLine = true,
            )
            Button(
                onClick = { onAddNote(mealType, note.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = note.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_add))
            }
        }
    }
}
