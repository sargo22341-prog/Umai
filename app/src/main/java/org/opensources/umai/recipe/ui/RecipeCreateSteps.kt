package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.recipe.domain.RecipeDraft

/** Stage one: what the recipe is, how many it serves and how long it takes. */
@Composable
internal fun BasicsStep(draft: RecipeDraft, actions: RecipeCreateActions) {
    // An untouched field is not a mistake: the requirement is stated in the
    // helper text, and the last step keeps its button disabled until it is met.
    OutlinedTextField(
        value = draft.name,
        onValueChange = actions.onNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.create_name_label)) },
        supportingText = { Text(stringResource(R.string.create_name_helper)) },
        singleLine = true,
    )

    OutlinedTextField(
        value = draft.description,
        onValueChange = actions.onDescriptionChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.create_description_label)) },
        minLines = 3,
        maxLines = 6,
    )

    ServingsStepper(
        servings = draft.servings,
        onChange = actions.onServingsChange,
    )

    OutlinedTextField(
        value = draft.prepTime,
        onValueChange = actions.onPrepTimeChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.recipe_time_prep)) },
        placeholder = { Text(stringResource(R.string.create_time_placeholder)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.cookTime,
        onValueChange = actions.onCookTimeChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.recipe_time_cook)) },
        placeholder = { Text(stringResource(R.string.create_time_placeholder)) },
        singleLine = true,
    )
    OutlinedTextField(
        value = draft.totalTime,
        onValueChange = actions.onTotalTimeChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.recipe_time_total)) },
        placeholder = { Text(stringResource(R.string.create_time_placeholder)) },
        singleLine = true,
    )

    Text(
        text = stringResource(R.string.create_time_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ServingsStepper(servings: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.filter_servings),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(servings - 1) }, enabled = servings > 0) {
                Icon(
                    Icons.Outlined.Remove,
                    contentDescription = stringResource(R.string.servings_decrease),
                )
            }
            Text(
                text = servings.toString(),
                modifier = Modifier.padding(horizontal = 8.dp),
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
}

/** Stage two: one free-text line per ingredient, as Mealie's own editor does. */
@Composable
internal fun IngredientsStep(draft: RecipeDraft, actions: RecipeCreateActions) {
    Text(
        text = stringResource(R.string.create_ingredients_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    draft.ingredients.forEachIndexed { index, line ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = line,
                onValueChange = { actions.onIngredientChange(index, it) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.create_ingredient_label, index + 1)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )
            IconButton(onClick = { actions.onRemoveIngredient(index) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.create_remove_ingredient),
                )
            }
        }
    }

    OutlinedButton(onClick = actions.onAddIngredient, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Text(
            text = stringResource(R.string.create_add_ingredient),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Stage three: the instructions, each with an optional heading. */
@Composable
internal fun InstructionsStep(draft: RecipeDraft, actions: RecipeCreateActions) {
    draft.steps.forEachIndexed { index, step ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.create_step_label, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = { actions.onRemoveStep(index) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.create_remove_step),
                    )
                }
            }
            OutlinedTextField(
                value = step.title,
                onValueChange = { actions.onStepTitleChange(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.create_step_title_label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = step.text,
                onValueChange = { actions.onStepTextChange(index, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.create_step_text_label)) },
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                ),
            )
        }
    }

    OutlinedButton(onClick = actions.onAddStep, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Text(
            text = stringResource(R.string.create_add_step),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Stage four: the categories and tags that already exist on the instance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrganizersStep(state: RecipeCreateUiState, actions: RecipeCreateActions) {
    if (state.loadingOrganizers) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Text(
        text = stringResource(R.string.create_organizers_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OrganizerPicker(
        title = stringResource(R.string.filter_categories),
        options = state.categories,
        selectedIds = state.draft.categories.map { it.id }.toSet(),
        onToggle = actions.onToggleCategory,
    )

    OrganizerPicker(
        title = stringResource(R.string.filter_tags),
        options = state.tags,
        selectedIds = state.draft.tags.map { it.id }.toSet(),
        onToggle = actions.onToggleTag,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizerPicker(
    title: String,
    options: List<Organizer>,
    selectedIds: Set<String>,
    onToggle: (Organizer) -> Unit,
) {
    Text(text = title, style = MaterialTheme.typography.titleSmall)

    if (options.isEmpty()) {
        Text(
            text = stringResource(R.string.create_no_organizer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Selected entries first, so a long list never hides what was picked.
        (options.filter { it.id in selectedIds } + options)
            .distinctBy { it.id }
            .take(MAX_VISIBLE_ORGANIZERS)
            .forEach { organizer ->
                FilterChip(
                    selected = organizer.id in selectedIds,
                    onClick = { onToggle(organizer) },
                    label = { Text(organizer.name) },
                )
            }
    }
}

private const val MAX_VISIBLE_ORGANIZERS = 60
