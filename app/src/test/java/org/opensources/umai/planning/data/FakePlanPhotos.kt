package org.opensources.umai.planning.data

import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.model.MealPlanEntry
import java.time.LocalDate

/** Photos of the plan kept in memory: [attached] maps entry ids to their photo. */
class FakePlanPhotos(
    /** The path a framed photo gets, `null` to fail framing. */
    private val framedPath: String? = "pending.jpg",
    private val attaches: Boolean = true,
) : PlanPhotos {

    val attached = mutableMapOf<Int, Pair<LocalDate, String>>()
    val discarded = mutableListOf<String>()
    val deleted = mutableListOf<Int>()

    override suspend fun frame(sourceUri: String, region: CropRegion): String? = framedPath

    /** The pictures kept as they came, such as a product photo downloaded. */
    val kept = mutableListOf<EncodedImage>()

    override suspend fun keep(image: EncodedImage): String? {
        kept += image
        return framedPath
    }

    override suspend fun attach(path: String, entry: MealPlanEntry): Boolean {
        if (attaches) attached[entry.id] = entry.date to path
        return attaches
    }

    override fun discard(path: String) {
        discarded += path
    }

    override suspend fun sync(start: LocalDate, end: LocalDate, entries: List<MealPlanEntry>): Map<Int, String> {
        val ids = entries.map { it.id }.toSet()
        attached.entries.removeAll { (id, photo) -> photo.first in start..end && id !in ids }
        return attached.filterKeys { it in ids }.mapValues { it.value.second }
    }

    override fun delete(entry: MealPlanEntry) {
        deleted += entry.id
        attached.remove(entry.id)
    }
}
