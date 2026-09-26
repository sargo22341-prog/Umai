package org.opensources.umai.planning.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.image.CameraCapture
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.ui.component.CropFrame
import org.opensources.umai.core.ui.component.ImagePicker
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.core.ui.component.rememberImagePickerState
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.Nutrient
import org.opensources.umai.recipe.ui.labelRes
import java.io.File

/** The name of the product, the meal it is part of, and its photo if the user wants one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductSection(state: FoodEntryUiState, actions: FoodEntryActions) {
    OutlinedTextField(
        value = state.name,
        onValueChange = actions.onNameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.food_name)) },
        placeholder = { Text(stringResource(R.string.food_name_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )

    SectionTitle(stringResource(R.string.planning_meal_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.displayOrder.forEach { type ->
            FilterChip(
                selected = type == state.mealType,
                onClick = { actions.onMealTypeChange(type) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }

    SectionTitle(stringResource(R.string.food_photo))
    ProductPhoto(state, actions)
}

@Composable
private fun ProductPhoto(state: FoodEntryUiState, actions: FoodEntryActions) {
    val picker = rememberImagePickerState()
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }

    ImagePicker(
        state = picker,
        frame = CropFrame.RECIPE,
        onImageReady = { source, region ->
            cameraUnavailable = false
            actions.onPhotoPicked(source, region)
        },
        onCameraUnavailable = { cameraUnavailable = true },
    )

    val photo = state.photoPath
    if (photo != null || state.processingPhoto) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CropFrame.RECIPE.aspectRatio)
                .clip(MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = photo?.let { Uri.fromFile(File(it)).toString() },
                contentDescription = stringResource(R.string.food_photo_description, state.name),
                modifier = Modifier.fillMaxSize(),
                placeholderIconSize = 48.dp,
            )
            if (state.processingPhoto) {
                Surface(color = Color.Black.copy(alpha = 0.4f), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }

    if (state.photoFailed || cameraUnavailable) {
        ErrorText(stringResource(if (cameraUnavailable) R.string.image_camera_unavailable else R.string.food_photo_failed))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = picker::openCamera, enabled = !state.processingPhoto, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
            Text(stringResource(R.string.image_source_camera), modifier = Modifier.padding(start = 8.dp))
        }
        if (photo != null) {
            OutlinedButton(onClick = actions.onRemovePhoto, enabled = !state.processingPhoto) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(stringResource(R.string.food_photo_remove), modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            OutlinedButton(onClick = picker::open, enabled = !state.processingPhoto) {
                Text(stringResource(R.string.food_photo_pick))
            }
        }
    }

    HelperText(stringResource(R.string.food_photo_local))
}

/**
 * The label read from a photo, or typed. The photo is taken with the camera
 * app, or picked, then handed to the model and deleted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NutritionSection(state: FoodEntryUiState, actions: FoodEntryActions) {
    if (state.canReadLabel) {
        LabelCapture(state, actions)
    } else {
        HelperText(stringResource(R.string.food_label_no_model))
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SectionTitle(stringResource(R.string.food_values_for))
        FoodUnit.entries.forEach { unit ->
            FilterChip(
                selected = unit == state.unit,
                onClick = { actions.onUnitChange(unit) },
                label = { Text(unit.symbol) },
                enabled = !state.readingLabel,
            )
        }
    }

    Nutrient.entries.forEach { nutrient ->
        NumberField(
            value = state.values[nutrient].orEmpty(),
            onValueChange = { actions.onValueChange(nutrient, it) },
            label = stringResource(nutrient.labelRes()),
            suffix = if (nutrient == Nutrient.ENERGY) "kcal" else "g",
            enabled = !state.readingLabel,
        )
    }
}

@Composable
private fun LabelCapture(state: FoodEntryUiState, actions: FoodEntryActions) {
    val context = LocalContext.current
    var cameraOutput by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val output = cameraOutput
        cameraOutput = null
        if (saved && output != null) actions.onReadLabel(output)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { actions.onReadLabel(it.toString()) }
    }

    HelperText(stringResource(R.string.food_label_intro))

    if (state.readingLabel) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                text = stringResource(R.string.food_label_reading),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onCancelReading) { Text(stringResource(R.string.action_cancel)) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    cameraUnavailable = false
                    val target = CameraCapture.newPhotoUri(context)
                    cameraOutput = target.toString()
                    try {
                        camera.launch(target)
                    } catch (_: ActivityNotFoundException) {
                        cameraOutput = null
                        cameraUnavailable = true
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.DocumentScanner, contentDescription = null)
                Text(stringResource(R.string.food_label_take), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = stringResource(R.string.food_photo_pick),
                )
            }
        }
    }

    if (cameraUnavailable) ErrorText(stringResource(R.string.image_camera_unavailable))

    val issue = state.labelIssue
    when {
        issue != null -> Surface(
            onClick = actions.onDismissLabelIssue,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Text(
                text = stringResource(issue.messageRes()),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        state.labelRead -> Text(
            text = stringResource(R.string.food_label_read),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The quantity eaten, and the calories it makes. */
@Composable
internal fun PortionSection(state: FoodEntryUiState, actions: FoodEntryActions) {
    NumberField(
        value = state.quantity,
        onValueChange = actions.onQuantityChange,
        label = stringResource(R.string.food_quantity),
        suffix = state.unit.symbol,
        enabled = true,
    )
    HelperText(stringResource(R.string.food_quantity_hint))

    val calories = state.calories
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (calories != null) {
                Text(
                    text = stringResource(R.string.food_calories_total, calories),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Text(
                    text = stringResource(R.string.food_calories_missing, state.unit.symbol),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun HelperText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@StringRes
internal fun Nutrient.labelRes(): Int = when (this) {
    Nutrient.ENERGY -> R.string.nutrition_energy
    Nutrient.FAT -> R.string.nutrition_fat
    Nutrient.SATURATED_FAT -> R.string.nutrition_saturated_fat
    Nutrient.CARBOHYDRATES -> R.string.nutrition_carbohydrate
    Nutrient.SUGARS -> R.string.nutrition_sugar
    Nutrient.FIBER -> R.string.nutrition_fiber
    Nutrient.PROTEIN -> R.string.nutrition_protein
    Nutrient.SALT -> R.string.nutrition_salt
}

@StringRes
private fun LabelIssue.messageRes(): Int = when (this) {
    LabelIssue.NO_MODEL -> R.string.food_label_no_model
    LabelIssue.NO_VISION -> R.string.food_label_no_vision
    LabelIssue.PICTURE_UNREADABLE -> R.string.food_label_picture_unreadable
    LabelIssue.NOTHING_FOUND -> R.string.food_label_nothing
    LabelIssue.FAILED -> R.string.food_label_failed
}
