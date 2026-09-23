package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.ui.component.CropFrame
import org.opensources.umai.core.ui.component.ImagePicker
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.core.ui.component.rememberImagePickerState
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.RecipeDraft

/** What the steps section shows beside the draft itself. */
data class StepsFormState(
    /** The outcome of the last automatic linking, until the section is left. */
    val linkResult: IngredientLinkResult? = null,
    /** The step whose new photo is being framed. */
    val processingPhoto: Int? = null,
    val photoFailed: Boolean = false,
)

/**
 * The instructions, each with an optional heading, the ingredients it uses and
 * an optional photo. [photoUrl] gives the photo to show for a step: the one
 * framed on the device, or else the one it has on Mealie.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstructionsSection(
    draft: RecipeDraft,
    state: StepsFormState,
    photoUrl: (DraftStep) -> String?,
    actions: RecipeFormActions,
) {
    val picker = rememberImagePickerState()
    var pickingFor by rememberSaveable { mutableStateOf<Int?>(null) }
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }

    ImagePicker(
        state = picker,
        frame = CropFrame.RECIPE,
        onImageReady = { source, region ->
            cameraUnavailable = false
            pickingFor?.let { actions.onStepPhotoPicked(it, source, region) }
        },
        onCameraUnavailable = { cameraUnavailable = true },
    )

    LinkPanel(draft = draft, result = state.linkResult, onLink = actions.onLinkIngredients)

    if (state.photoFailed || cameraUnavailable) {
        Text(
            text = stringResource(if (cameraUnavailable) R.string.image_camera_unavailable else R.string.create_image_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    val ingredients = draft.ingredients.associateBy { it.referenceId }
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
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                ),
            )

            val linked = step.ingredientReferences.mapNotNull { ingredients[it] }.filter { it.text.isNotBlank() }
            if (linked.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.links_step_ingredients),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    linked.forEach { ingredient -> LinkedChip(ingredient) { actions.onUnlinkIngredient(index, ingredient.referenceId) } }
                }
            }

            StepPhoto(
                stepNumber = index + 1,
                url = photoUrl(step),
                processing = state.processingPhoto == index,
                canRemove = step.photoPath != null,
                onPick = {
                    pickingFor = index
                    picker.open()
                },
                onRemove = { actions.onRemoveStepPhoto(index) },
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

@Composable
private fun LinkPanel(draft: RecipeDraft, result: IngredientLinkResult?, onLink: () -> Unit) {
    val canLink = draft.ingredients.any { it.text.isNotBlank() } && draft.steps.any { it.text.isNotBlank() }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.links_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onLink, enabled = canLink, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Text(
                    text = stringResource(R.string.links_action),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            result?.let {
                Text(
                    text = pluralStringResource(R.plurals.links_found, it.added, it.added) + " " +
                        pluralStringResource(R.plurals.links_total, it.total, it.total),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LinkedChip(ingredient: DraftIngredient, onRemove: () -> Unit) {
    val label = ingredient.text.trim()
    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) },
        trailingIcon = {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.links_remove, label),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

@Composable
private fun StepPhoto(
    stepNumber: Int,
    url: String?,
    processing: Boolean,
    canRemove: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    if (url != null || processing) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(CropFrame.RECIPE.aspectRatio)
                .clip(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = url,
                contentDescription = stringResource(R.string.cd_step_image, stepNumber),
                modifier = Modifier.fillMaxSize(),
                placeholderIconSize = 32.dp,
            )
            if (processing) {
                Surface(color = Color.Black.copy(alpha = 0.4f), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onPick, enabled = !processing) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Text(
                text = stringResource(if (url == null) R.string.step_photo_add else R.string.step_photo_change),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (canRemove) {
            TextButton(onClick = onRemove, enabled = !processing) {
                Text(stringResource(R.string.create_image_remove))
            }
        }
    }
}
