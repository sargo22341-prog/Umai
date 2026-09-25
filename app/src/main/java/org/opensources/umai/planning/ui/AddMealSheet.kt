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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealType
import org.opensources.umai.recipe.ui.labelRes
import java.time.LocalDate
import java.time.format.FormatStyle

/** The callbacks of [AddMealSheet], grouped so the signature stays readable. */
class AddMealActions(
    /**
     * Opens the full-screen recipe search for the day and the meal; the last
     * argument is the on-screen centre of the field tapped, where the search
     * field of that screen starts its way up.
     */
    val onSearchRecipe: (LocalDate, MealType, Float) -> Unit,
    val onAddNote: (LocalDate, MealType, String) -> Unit,
)

/**
 * Adds an entry to one day of the meal plan: a recipe searched for on its own
 * screen or, as Mealie also allows, a free-text note for a meal that is not a
 * recipe.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMealSheet(
    date: LocalDate,
    actions: AddMealActions,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = rememberDateFormatter(FormatStyle.FULL)
    var mealType by remember { mutableStateOf(MealType.DINNER) }
    var note by remember { mutableStateOf("") }
    var noteFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Left for the full-screen search, the sheet is not dismissed: it stays up
    // while that screen composes behind it and goes with the week once the
    // navigation is over, so its field hands over to the search field at the
    // same place, with nothing shown in between.
    var leftForSearch by remember { mutableStateOf(false) }

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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                    onClick = { centerY ->
                        if (!leftForSearch) {
                            leftForSearch = true
                            actions.onSearchRecipe(date, mealType, centerY)
                        }
                    },
                )
            }

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
                        onDismiss()
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
 * search, filters included, on a screen of its own. [onClick] receives the
 * vertical centre of the launcher on screen: the sheet is a window of its own,
 * so only screen coordinates are shared with the screen it opens.
 */
@Composable
private fun RecipeSearchLauncher(onClick: (Float) -> Unit, modifier: Modifier = Modifier) {
    var centerY by remember { mutableFloatStateOf(0f) }
    Surface(
        onClick = { onClick(centerY) },
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { centerY = it.positionOnScreen().y + it.size.height / 2f }
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

/** Enough for the note to reach the top of the sheet above the keyboard. */
private val KEYBOARD_ROOM = 320.dp
