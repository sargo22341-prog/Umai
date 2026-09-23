package org.opensources.umai.recipe.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.recipe.domain.VideoChapter
import org.opensources.umai.recipe.domain.VideoManifest

/**
 * Reads and writes the chapters file of a recipe video (see
 * [org.opensources.umai.recipe.domain.RecipeMediaFiles]).
 *
 * Reading is lenient, as files already on Mealie were written by other tools:
 * a step index may be a number or a string, or be named `stepId`, and a chapter
 * that makes no sense is skipped rather than failing the whole file.
 */
internal object VideoManifestJson {

    fun parse(text: String): VideoManifest? {
        val root = runCatching { MealieClientFactory.json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            ?: return null
        val source = root["source"] as? JsonObject
        val chapters = (root["chapters"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.toChapter() }
            .sortedBy { it.start }
        if (chapters.isEmpty()) return null
        return VideoManifest(
            title = root.text("title").orEmpty(),
            sourceUrl = source?.text("url_ori"),
            videoUrl = source?.text("originalVideoUrl"),
            chapters = chapters,
        )
    }

    fun write(manifest: VideoManifest): String = MealieClientFactory.json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("version", 1)
            put("title", manifest.title)
            put(
                "source",
                buildJsonObject {
                    put("type", "schema.org/Recipe")
                    put("name", manifest.title)
                    manifest.sourceUrl?.let { put("url_ori", it) }
                    manifest.videoUrl?.let { put("originalVideoUrl", it) }
                },
            )
            put(
                "chapters",
                buildJsonArray {
                    manifest.chapters.forEach { chapter ->
                        add(
                            buildJsonObject {
                                put("key", if (chapter.stepIndex < 0) "ingredients" else "step-${chapter.stepIndex + 1}")
                                put("stepIndex", chapter.stepIndex)
                                put("start", chapter.start)
                                put("end", chapter.end?.let(::JsonPrimitive) ?: JsonNull)
                            },
                        )
                    }
                },
            )
        },
    )

    private fun JsonObject.toChapter(): VideoChapter? {
        val start = number("start")?.takeIf { it >= 0 } ?: return null
        val step = (get("stepIndex") ?: get("stepId")) as? JsonPrimitive ?: return null
        val stepIndex = step.contentOrNull?.trim()?.toDoubleOrNull()?.toInt() ?: return null
        val end = number("end")?.takeIf { it > start }
        return VideoChapter(stepIndex = stepIndex.coerceAtLeast(-1), start = start, end = end)
    }

    private fun JsonObject.number(key: String): Double? =
        (get(key) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

    private fun JsonObject.text(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
}
