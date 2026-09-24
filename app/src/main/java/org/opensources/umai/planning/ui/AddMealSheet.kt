package org.opensources.umai.planning.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.RecipeRow
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import org.opensources.umai.recipe.ui.labelRes
import java.time.LocalDate
import java.time.format.FormatStyle

/** The callbacks of [AddMealSheet], grouped so the signature stays readable. */
class AddMealActions(
    /** Opens the full-screen recipe search for the day and the meal. */
    val onSearchRecipe: (LocalDate, MealType) -> Unit,
    val onAddRecipe: (LocalDate, MealType, RecipeSummary) -> Unit,
    val onAddNote: (LocalDate, MealType, String) -> Unit,
    /** The sheet opened: the categories of the random draw are needed. */
    val onOpen: () -> Unit,
    /** The sheet closed: the drawn recipe is forgotten. */
    val onClose: () -> Unit,
    val onSelectRandomCategory: (String?) -> Unit,
    val onDrawRandom: () -> Unit,
)

/**
 * Adds an entry to one day of the meal plan: a recipe searched for on its own
 * screen, a recipe drawn at random, or, as Mealie also allows, a free-text note
 * for a meal that is not a recipe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMealSheet(
    date: LocalDate,
    random: RandomRecipeState,
    actions: AddMealActions,
    recipeImageUrl: (RecipeSummary) -> String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    var mealType by remember { mutableStateOf(MealType.DINNER) }
    var note by remember { mutableStateOf("") }
    var noteFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) { actions.onOpen() }

    val close = {
        actions.onClose()
        onDismiss()
    }

    // With the keyboard up, the note at the bottom of the sheet would sit
    // behind it: the extra room lets the sheet scroll the note up to its top,
    // so what is typed stays in sight, as in the filters of the search.
    val keyboardRoom by animateDpAsState(
        targetValue = if (noteFocused && WindowInsets.isImeVisible) KEYBOARD_ROOM else 0.dp,
        label = "keyboardRoom",
    )
    val roomReady = keyboardRoom == KEYBOARD_ROOM
    LaunchedEffect(noteFocused, roomReady) {
        if (noteFocused && roomReady) scrollState.animateScrollTo(scrollState.maxValue)
    }

    ModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .heightIn(max = 640.dp)
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp + keyboardRoom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.planning_add_meal),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = date.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.planning_choose_recipe))
                RecipeSearchLauncher(
                    onClick = {
                        actions.onSearchRecipe(date, mealType)
                        close()
                    },
                )
            }

            HorizontalDivider()

            RandomRecipeSection(
                random = random,
                recipeImageUrl = recipeImageUrl,
                onSelectCategory = actions.onSelectRandomCategory,
                onDraw = actions.onDrawRandom,
                onAdd = { recipe ->
                    actions.onAddRecipe(date, mealType, recipe)
                    close()
                },
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(stringResource(R.string.planning_or_note))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { noteFocused = it.isFocused },
                    placeholder = { Text(stringResource(R.string.planning_note_placeholder)) },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        actions.onAddNote(date, mealType, note.trim())
                        close()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = note.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

/**
 * Looks like the search field it opens: tapping it brings the whole recipe
 * search, filters included, on a screen of its own.
 */
@Composable
private fun RecipeSearchLauncher(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.planning_search_recipe),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RandomRecipeSection(
    random: RandomRecipeState,
    recipeImageUrl: (RecipeSummary) -> String?,
    onSelectCategory: (String?) -> Unit,
    onDraw: () -> Unit,
    onAdd: (RecipeSummary) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.planning_random_all_categories)
    val selectedName = random.categories.firstOrNull { it.id == random.categoryId }?.name ?: allLabel

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.planning_random_title))

        // The category and the draw sit side by side: picking one then drawing
        // is a single gesture, and drawing again stays where the first draw was.
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.planning_random_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(allLabel) },
                        onClick = {
                            onSelectCategory(null)
                            menuExpanded = false
                        },
                    )
                    random.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onSelectCategory(category.id)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = onDraw,
                enabled = !random.drawing,
                modifier = Modifier.heightIn(min = OutlinedTextFieldDefaults.MinHeight),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Casino,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(
                        if (random.recipe != null) R.string.planning_random_again else R.string.planning_random_draw,
                    ),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }

        val drawn = random.recipe
        when {
            random.drawing -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }

            drawn != null -> {
                RecipeRow(recipe = drawn, imageUrl = recipeImageUrl(drawn), onClick = { onAdd(drawn) })
                Button(onClick = { onAdd(drawn) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_add))
                }
            }

            else -> {
                if (random.noMatch) {
                    Hint(stringResource(R.string.planning_random_no_match))
                }
                random.error?.let { error ->
                    Text(
                        text = "${error.title()}\n${error.message()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Enough for the note to reach the top of the sheet above the keyboard. */
private val KEYBOARD_ROOM = 320.dp
