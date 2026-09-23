package org.opensources.umai.core.ui.component

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.image.CameraCapture
import org.opensources.umai.core.image.CropRegion

/** Opens the image picker; kept by the screen that shows the [ImagePicker]. */
@Stable
class ImagePickerState internal constructor(
    choosing: MutableState<Boolean>,
    pending: MutableState<String?>,
    camera: MutableState<String?>,
) {
    internal var choosingSource by choosing
    internal var pendingCrop by pending
    internal var cameraOutput by camera

    fun open() {
        choosingSource = true
    }
}

@Composable
fun rememberImagePickerState(): ImagePickerState {
    // The crop editor and the camera's target survive a rotation, and the app
    // being trimmed while the camera app is in front.
    val choosing = rememberSaveable { mutableStateOf(false) }
    val pending = rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(choosing, pending, camera) { ImagePickerState(choosing, pending, camera) }
}

/**
 * The whole path from "change the picture" to a picture ready to upload: the
 * user picks where it comes from — the photo gallery, the camera or any file —
 * then frames it in the [ImageCropEditor]. [onImageReady] receives the picked
 * picture and the region to keep; nothing is read or uploaded here.
 *
 * No permission is needed: the system photo picker and the document picker
 * grant access to the one file chosen, and the camera app writes into a file
 * Umai shares with it for that single photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePicker(
    state: ImagePickerState,
    frame: CropFrame,
    onImageReady: (sourceUri: String, region: CropRegion) -> Unit,
    onCameraUnavailable: () -> Unit,
) {
    val context = LocalContext.current

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { state.pendingCrop = it.toString() }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { state.pendingCrop = it.toString() }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val output = state.cameraOutput
        state.cameraOutput = null
        if (saved && output != null) state.pendingCrop = output
    }

    if (state.choosingSource) {
        ModalBottomSheet(onDismissRequest = { state.choosingSource = false }) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(
                    text = stringResource(R.string.image_source_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                SourceRow(Icons.Outlined.PhotoLibrary, stringResource(R.string.image_source_gallery)) {
                    state.choosingSource = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SourceRow(Icons.Outlined.PhotoCamera, stringResource(R.string.image_source_camera)) {
                    state.choosingSource = false
                    val target: Uri = CameraCapture.newPhotoUri(context)
                    state.cameraOutput = target.toString()
                    try {
                        camera.launch(target)
                    } catch (_: ActivityNotFoundException) {
                        state.cameraOutput = null
                        onCameraUnavailable()
                    }
                }
                SourceRow(Icons.Outlined.Folder, stringResource(R.string.image_source_files)) {
                    state.choosingSource = false
                    files.launch(arrayOf("image/*"))
                }
            }
        }
    }

    state.pendingCrop?.let { source ->
        ImageCropEditor(
            sourceUri = source,
            frame = frame,
            onCancel = { state.pendingCrop = null },
            onConfirm = { region ->
                state.pendingCrop = null
                onImageReady(source, region)
            },
        )
    }
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
        // The sheet's own colour, rather than a band of another surface.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
