package org.opensources.umai.planning.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.image.CameraCapture
import org.opensources.umai.planning.domain.Nutrient
import kotlin.math.roundToInt

/** Automatic, from the barcode, or manual: automatic unless the user or a product not found says otherwise. */
@Composable
internal fun ModeSelector(state: FoodEntryUiState, actions: FoodEntryActions) {
    val modes = FoodEntryMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = state.mode == mode,
                onClick = { actions.onModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                enabled = !state.searching,
            ) {
                Text(stringResource(if (mode == FoodEntryMode.AUTO) R.string.food_mode_auto else R.string.food_mode_manual))
            }
        }
    }
}

/**
 * The barcode photographed with the camera app, picked, or typed, then looked
 * up in Open Food Facts. The photo is only read, then deleted.
 */
@Composable
internal fun BarcodeSection(state: FoodEntryUiState, actions: FoodEntryActions) {
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }
    val takePhoto = rememberPhotoTaker(onTaken = actions.onScanBarcode)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { actions.onScanBarcode(it.toString()) }
    }

    HelperText(stringResource(R.string.food_barcode_intro))

    if (state.searching) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                text = stringResource(R.string.food_barcode_searching),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onCancelSearch) { Text(stringResource(R.string.action_cancel)) }
        }
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { cameraUnavailable = !takePhoto() },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Text(stringResource(R.string.food_barcode_scan), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = stringResource(R.string.food_photo_pick))
        }
    }
    if (cameraUnavailable) ErrorText(stringResource(R.string.image_camera_unavailable))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.barcode,
            onValueChange = actions.onBarcodeChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.food_barcode_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.onSearchBarcode() }),
        )
        OutlinedButton(onClick = actions.onSearchBarcode, enabled = state.barcode.isNotBlank()) {
            Text(stringResource(R.string.food_barcode_search))
        }
    }
}

/** Why the barcode gave no product, or not all of it. */
@Composable
internal fun LookupIssueBanner(issue: LookupIssue) {
    Surface(
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
}

/** The product the barcode gave, with the source of its data, as the Open Database License asks. */
@Composable
internal fun FoundProductCard(state: FoodEntryUiState) {
    val product = state.found ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.food_found_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (product.name.isNotBlank()) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            product.per100[Nutrient.ENERGY]?.let { kcal ->
                Text(
                    text = stringResource(R.string.food_found_energy, kcal.roundToInt(), product.unit.symbol),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.food_found_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/**
 * Takes one photo with the camera app, into a file Umai shares with it, for a
 * picture that is read at once. The function returned answers false when the
 * phone has no camera app.
 */
@Composable
internal fun rememberPhotoTaker(onTaken: (String) -> Unit): () -> Boolean {
    val context = LocalContext.current
    val taken by rememberUpdatedState(onTaken)
    var output by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val photo = output
        output = null
        if (saved && photo != null) taken(photo)
    }
    return {
        val target = CameraCapture.newPhotoUri(context)
        output = target.toString()
        try {
            camera.launch(target)
            true
        } catch (_: ActivityNotFoundException) {
            output = null
            false
        }
    }
}

@StringRes
private fun LookupIssue.messageRes(): Int = when (this) {
    LookupIssue.UNREADABLE -> R.string.food_lookup_unreadable
    LookupIssue.INVALID_CODE -> R.string.food_lookup_invalid
    LookupIssue.NOT_FOUND -> R.string.food_lookup_not_found
    LookupIssue.NO_NUTRITION -> R.string.food_lookup_no_nutrition
    LookupIssue.FAILED -> R.string.food_lookup_failed
}
