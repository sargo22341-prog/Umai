package org.opensources.umai.planning.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.image.ImageCropper
import org.opensources.umai.core.model.MealPlanEntry
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * The photos of the foods added to the plan.
 *
 * Mealie has no picture on a plan entry, so the photo stays on this phone,
 * named after the day and the id of its entry. An entry removed, from Umai or
 * from Mealie, takes its photo with it the next time its week is read.
 */
interface PlanPhotos {

    /** Crops [sourceUri] to [region] into a photo not yet tied to an entry; its path, `null` on failure. */
    suspend fun frame(sourceUri: String, region: CropRegion): String?

    /** Keeps [image], already framed, as a photo not yet tied to an entry; its path, `null` on failure. */
    suspend fun keep(image: EncodedImage): String?

    /** Ties the framed photo at [path] to [entry]; false when it could not be kept. */
    suspend fun attach(path: String, entry: MealPlanEntry): Boolean

    /** Removes a framed photo that will not be tied to any entry. */
    fun discard(path: String)

    /**
     * The photos of [entries], the whole plan from [start] to [end], by entry
     * id. The photos of those days whose entry is gone are removed.
     */
    suspend fun sync(start: LocalDate, end: LocalDate, entries: List<MealPlanEntry>): Map<Int, String>

    fun delete(entry: MealPlanEntry)
}

class DevicePlanPhotos(
    context: Context,
    private val cropper: ImageCropper,
) : PlanPhotos {

    private val directory = File(context.applicationContext.filesDir, DIRECTORY)

    override suspend fun frame(sourceUri: String, region: CropRegion): String? =
        cropper.crop(sourceUri, region, MAX_SIDE)?.let { keep(it) }

    override suspend fun keep(image: EncodedImage): String? = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            // One food is added at a time: a photo framed before and never kept is left over.
            directory.listFiles { file -> file.name.startsWith(PENDING) }?.forEach { it.delete() }
            val file = File(directory, "$PENDING${UUID.randomUUID()}.${image.extension}")
            file.writeBytes(image.bytes)
            file.absolutePath
        }.getOrNull()
    }

    override suspend fun attach(path: String, entry: MealPlanEntry): Boolean = withContext(Dispatchers.IO) {
        val source = File(path).takeIf { it.isOwned() && it.isFile } ?: return@withContext false
        // The photo is shown from its file, whatever its format: the name only has to be found again.
        source.renameTo(fileOf(entry.date, entry.id))
    }

    override fun discard(path: String) {
        File(path).takeIf { it.isOwned() && it.name.startsWith(PENDING) }?.delete()
    }

    override suspend fun sync(start: LocalDate, end: LocalDate, entries: List<MealPlanEntry>): Map<Int, String> =
        withContext(Dispatchers.IO) {
            val ids = entries.map { it.id }.toSet()
            val photos = mutableMapOf<Int, String>()
            directory.listFiles().orEmpty().forEach { file ->
                val (date, id) = parse(file.name) ?: return@forEach
                if (date < start || date > end) return@forEach
                if (id in ids) photos[id] = file.absolutePath else file.delete()
            }
            photos
        }

    override fun delete(entry: MealPlanEntry) {
        fileOf(entry.date, entry.id).delete()
    }

    private fun fileOf(date: LocalDate, id: Int) = File(directory, "${date}_$id.jpg")

    private fun parse(name: String): Pair<LocalDate, Int>? {
        val match = NAME.matchEntire(name) ?: return null
        val date = runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull() ?: return null
        val id = match.groupValues[2].toIntOrNull() ?: return null
        return date to id
    }

    /** Only files of this folder are ever read or deleted. */
    private fun File.isOwned(): Boolean = canonicalFile.parentFile == directory.canonicalFile

    private companion object {
        const val DIRECTORY = "plan_photos"
        const val PENDING = "pending-"
        val NAME = Regex("""(\d{4}-\d{2}-\d{2})_(\d+)\.jpg""")

        /** Shown as a thumbnail of the plan: no need for more. */
        const val MAX_SIDE = 800
    }
}
