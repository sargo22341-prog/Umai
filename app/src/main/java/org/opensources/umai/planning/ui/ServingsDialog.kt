package org.opensources.umai.planning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.model.MealPlanEntry

/**
 * Asks how many servings of [entry] were eaten. [servingCalories] are those
 * of one serving, `null` when unknown; the total follows the count.
 */
@Composable
internal fun ServingsDialog(
    entry: MealPlanEntry,
    servingCalories: Int?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var servings by rememberSaveable(entry.id) { mutableIntStateOf(entry.servings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.displayTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { servings-- }, enabled = servings > 1) {
                        Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.servings_decrease))
                    }
                    Text(
                        text = pluralStringResource(R.plurals.plural_servings, servings, servings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { servings++ }, enabled = servings < MAX_SERVINGS) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.servings_increase))
                    }
                }
                servingCalories?.let {
                    Text(
                        text = stringResource(R.string.planning_calories, it * servings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(servings) }, enabled = servings != entry.servings) {
                Text(stringResource(R.string.edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** More than anyone eats of one dish in one meal: it only bounds the stepper. */
internal const val MAX_SERVINGS = 99
