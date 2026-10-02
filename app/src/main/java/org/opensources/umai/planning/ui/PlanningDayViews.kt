package org.opensources.umai.planning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.currentLocale
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.core.ui.motion.RollingContent
import org.opensources.umai.core.ui.motion.animatePressScale
import org.opensources.umai.core.ui.motion.scaledBy
import org.opensources.umai.planning.domain.DayCalories
import org.opensources.umai.recipe.ui.labelRes
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.FormatStyle

/** What a card shows besides its entry: its calories and its picture, when it has them. */
internal class EntryDetails(val calories: Int?, val imageUrl: String?)

/** One day of the week: its name and date, its calories, its meals, and the button that adds one. */
@Composable
internal fun DayColumn(
    date: LocalDate,
    label: String,
    isToday: Boolean,
    entries: List<MealPlanEntry>,
    calories: DayCalories,
    loadingCalories: Boolean,
    onAdd: () -> Unit,
    onRecipeClick: (String) -> Unit,
    onDelete: (MealPlanEntry) -> Unit,
    onEdit: (MealPlanEntry) -> Unit,
    entryDetails: (MealPlanEntry) -> EntryDetails,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(entries) {
        entries.sortedBy { MealType.displayOrder.indexOf(it.type).takeIf { i -> i >= 0 } ?: MealType.displayOrder.size }
    }
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DayHeader(date, label, isToday, calories, loadingCalories)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sorted.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.planning_empty_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(count = sorted.size, key = { sorted[it].id }) { index ->
                    MealEntryCard(
                        entry = sorted[index],
                        details = entryDetails(sorted[index]),
                        onClick = onRecipeClick,
                        onDelete = { onDelete(sorted[index]) },
                        onEdit = { onEdit(sorted[index]) },
                        // A meal added or removed makes room, or closes the gap, rather than jumping.
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(text = stringResource(R.string.planning_add_meal), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, label: String, isToday: Boolean, calories: DayCalories, loadingCalories: Boolean) {
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = date.format(dateFormatter),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!calories.isEmpty) DayCaloriesLine(calories = calories, loading = loadingCalories, isToday = isToday)
    }
}

/**
 * The total of the day. The entries without calories are counted apart, once
 * the calories of the recipes are known.
 */
@Composable
private fun DayCaloriesLine(calories: DayCalories, loading: Boolean, isToday: Boolean) {
    val locale = currentLocale()
    val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val unknown = if (calories.unknown > 0 && !loading) {
        " " + pluralStringResource(R.plurals.planning_calories_unknown, calories.unknown, calories.unknown)
    } else {
        ""
    }
    // The total rolls up or down as meals come and go, or their servings change.
    RollingContent(value = calories.total, modifier = Modifier.padding(top = 2.dp)) { total ->
        Text(
            text = stringResource(R.string.planning_calories, number.format(total)) + unknown,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
        )
    }
}

/** The meal of an entry, and its edit and delete buttons. */
@Composable
private fun EntryHeader(entry: MealPlanEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(entry.type.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row {
            IconButton(onClick = onEdit, modifier = Modifier.heightIn(max = 28.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.planning_edit_servings),
                    modifier = Modifier.height(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.heightIn(max = 28.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.planning_delete_entry),
                    modifier = Modifier.height(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MealEntryCard(
    entry: MealPlanEntry,
    details: EntryDetails,
    onClick: (String) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = entry.recipe
    val interactions = remember { MutableInteractionSource() }
    val pressScale = animatePressScale(interactions)
    Card(
        onClick = { recipe?.slug?.let(onClick) },
        modifier = modifier.fillMaxWidth().scaledBy(pressScale),
        interactionSource = interactions,
        enabled = recipe != null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EntryHeader(entry, onEdit, onDelete)
            if (recipe != null || details.imageUrl != null) EntryPicture(entry, details.imageUrl)
            val title = entry.displayTitle.ifBlank { stringResource(R.string.planning_empty_day) }
            Text(
                text = if (entry.servings == 1) title else stringResource(R.string.planning_entry_servings, entry.servings, title),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            details.calories?.let { calories ->
                Text(
                    text = stringResource(R.string.planning_calories, calories),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EntryPicture(entry: MealPlanEntry, imageUrl: String?) {
    val isRecipe = entry.recipe != null
    RemoteImage(
        url = imageUrl,
        // A recipe is named below its picture; the photo of a product is described.
        contentDescription = if (isRecipe) null else stringResource(R.string.food_photo_description, entry.displayTitle),
        // A product is shown whole: its package is often tall.
        contentScale = if (isRecipe) ContentScale.Crop else ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(MaterialTheme.shapes.small),
        placeholderIconSize = 24.dp,
    )
}
