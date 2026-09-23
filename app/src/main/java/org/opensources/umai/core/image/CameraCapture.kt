package org.opensources.umai.core.image

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where the camera app writes the photo it takes for Umai.
 *
 * The file is shared through a [FileProvider] rather than a permission: the
 * camera app is handed write access to this one file only, and Umai needs no
 * camera permission of its own. Each capture gets a new name, so an image
 * loader never shows a previous photo from its cache; older captures are
 * removed at the same time.
 */
object CameraCapture {

    fun newPhotoUri(context: Context): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, "photo-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    }

    /** Must match `res/xml/image_paths.xml`. */
    private const val DIRECTORY = "camera"

    /** Must match the provider declared in the manifest. */
    private const val AUTHORITY_SUFFIX = ".images"
}
