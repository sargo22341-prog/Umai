package org.opensources.umai.recipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.DurationText
import org.opensources.umai.core.format.IngredientText
import org.opensources.umai.core.markdown.MarkdownText
import org.opensources.umai.core.model.Nutrition
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeNote
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.ui.component.RemoteImage

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * "Ingredients for N servings", with the control that scales them.
 *
 * The number is deliberately shown next to the list it changes rather than in
 * the row of facts above: it is not a fact about the recipe any more, it is
 * what the reader is cooking today.
 */
@Composable
internal fun IngredientsHeader(
    servings: Int,
    canScale: Boolean,
    onServingsChange: (Int) -> Unit,
) {
    // Mealie leaves the serving count at zero on plenty of recipes; there is
    // nothing to scale then, and the plain heading is shown instead.
    val scalable = canScale && servings > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (scalable) {
                stringResource(
                    R.string.recipe_ingredients_for,
                    pluralStringResource(R.plurals.plural_servings, servings, servings),
                )
            } else {
                stringResource(R.string.recipe_ingredients)
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )

        if (scalable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onServingsChange(servings - 1) },
                    enabled = servings > 1,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Remove,
                        contentDescription = stringResource(R.string.servings_decrease),
                    )
                }
                Text(
                    text = servings.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { onServingsChange(servings + 1) }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.servings_increase),
                    )
                }
            }
        }
    }
}

/**
 * Mealie's own editor labels `performTime` "cook time" and never fills the
 * `cookTime` column, which only a scraped recipe carries; the cooking time is
 * therefore read from `performTime` first. [showTimes] follows the reader's
 * settings; the yield is not a duration and stays.
 */
@Composable
internal fun RecipeFacts(recipe: Recipe, showTimes: Boolean) {
    val hourUnit = stringResource(R.string.unit_hour_short)
    val minuteUnit = stringResource(R.string.unit_minute_short)
    val cookTime = recipe.summary.performTime ?: recipe.summary.cookTime
    val facts = buildList {
        if (showTimes) {
            DurationText.format(recipe.summary.totalTime, hourUnit, minuteUnit)?.let {
                add(stringResource(R.string.recipe_time_total) to it)
            }
            DurationText.format(recipe.summary.prepTime, hourUnit, minuteUnit)?.let {
                add(stringResource(R.string.recipe_time_prep) to it)
            }
            DurationText.format(cookTime, hourUnit, minuteUnit)?.let {
                add(stringResource(R.string.recipe_time_cook) to it)
            }
        }
        recipe.summary.yieldText?.let {
            add(stringResource(R.string.recipe_yield) to it)
        }
    }
    if (facts.isEmpty()) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(count = facts.size) { index ->
            val (label, value) = facts[index]
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun IngredientRow(ingredient: RecipeIngredient, scale: Double) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        ingredient.sectionTitle?.let { title ->
            Text(
                text = title,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Row(
            modifier = Modifier.padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(5.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )
            MarkdownText(
                markdown = IngredientText.format(ingredient, scale),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
internal fun StepBlock(
    index: Int,
    step: RecipeStep,
    recipeId: String,
    stepImageUrl: (String, String) -> String?,
    stepPhotoUrl: (String) -> String?,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                step.title?.let {
                    Text(text = it, style = MaterialTheme.typography.titleSmall)
                }
                if (step.text.isNotBlank()) {
                    MarkdownText(markdown = step.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // The photo of the step comes first, then the pictures of its text.
        val urls = listOfNotNull(step.photo?.let(stepPhotoUrl)) + step.images.map { stepImageUrl(recipeId, it) }
        urls.forEachIndexed { imageIndex, url ->
            RemoteImage(
                url = url,
                contentDescription = stringResource(R.string.cd_step_image, index + 1),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium),
            )
            if (imageIndex < urls.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun NutritionTable(nutrition: Nutrition) {
    val rows = listOfNotNull(
        nutrition.calories?.let { stringResource(R.string.nutrition_calories) to it },
        nutrition.protein?.let { stringResource(R.string.nutrition_protein) to it },
        nutrition.carbohydrates?.let { stringResource(R.string.nutrition_carbohydrate) to it },
        nutrition.sugar?.let { stringResource(R.string.nutrition_sugar) to it },
        nutrition.fat?.let { stringResource(R.string.nutrition_fat) to it },
        nutrition.saturatedFat?.let { stringResource(R.string.nutrition_saturated_fat) to it },
        nutrition.unsaturatedFat?.let { stringResource(R.string.nutrition_unsaturated_fat) to it },
        nutrition.transFat?.let { stringResource(R.string.nutrition_trans_fat) to it },
        nutrition.fiber?.let { stringResource(R.string.nutrition_fiber) to it },
        nutrition.sodium?.let { stringResource(R.string.nutrition_sodium) to it },
        nutrition.cholesterol?.let { stringResource(R.string.nutrition_cholesterol) to it },
    ).filter { it.second.isNotBlank() }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun NoteBlock(note: RecipeNote) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (note.title.isNotBlank()) Text(note.title, style = MaterialTheme.typography.titleSmall)
        MarkdownText(note.text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The page the recipe was imported from, opened in the browser. */
@Composable
internal fun SourceLink(url: String, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.recipe_open_source), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
