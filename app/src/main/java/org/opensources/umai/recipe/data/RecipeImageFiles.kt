package org.opensources.umai.recipe.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.image.ImageCropper
import java.io.File
import java.util.UUID

/**
 * Recipe pictures framed on the device and not uploaded yet: the picture of a
 * draft, or the new picture of a recipe being edited.
 *
 * The picked picture is cropped at once and kept as a file of its own, because
 * the access the photo picker grants to the original ends with the app.
 */
interface RecipeImageFiles {
    /** Crops [sourceUri] to [region] and returns the path of the new file, `null` on failure. */
    suspend fun save(sourceUri: String, region: CropRegion): String?

    suspend fun read(path: String): EncodedImage?

    fun delete(path: String)
}

class DeviceRecipeImageFiles(
    context: Context,
    private val cropper: ImageCropper,
) : RecipeImageFiles {

    private val directory = File(context.applicationContext.filesDir, DIRECTORY)

    override suspend fun save(sourceUri: String, region: CropRegion): String? {
        val image = cropper.crop(sourceUri, region, MAX_SIDE) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                directory.mkdirs()
                // A new name each time: an image loader never shows a stale picture.
                val file = File(directory, "${UUID.randomUUID()}.${image.extension}")
                file.writeBytes(image.bytes)
                file.absolutePath
            }.getOrNull()
        }
    }

    override suspend fun read(path: String): EncodedImage? = withContext(Dispatchers.IO) {
        val file = File(path).takeIf { it.isOwned() && it.isFile } ?: return@withContext null
        runCatching { EncodedImage(file.readBytes(), mediaType = "image/jpeg", extension = file.extension) }
            .getOrNull()
    }

    override fun delete(path: String) {
        File(path).takeIf { it.isOwned() }?.delete()
    }

    /** Only files this class wrote are ever read or deleted. */
    private fun File.isOwned(): Boolean = canonicalFile.parentFile == directory.canonicalFile

    private companion object {
        const val DIRECTORY = "recipe_images"

        /** Plenty for the recipe page; Mealie builds its own smaller sizes. */
        const val MAX_SIDE = 1600
    }
}
