package org.opensources.umai.provider.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.net.URISyntaxException

/**
 * Reading of the schema.org `Recipe` a page publishes, as Mealie returns it
 * from `test-scrape-url`. Pages differ in how they nest things, so every read
 * accepts the shapes schema.org allows and ignores what it cannot use.
 */
object SchemaOrgRecipe {

    /** The first object typed `Recipe`, searched breadth-first. */
    fun find(root: JsonElement): JsonObject? {
        val queue = ArrayDeque<JsonElement>().apply { add(root) }
        while (queue.isNotEmpty()) {
            when (val current = queue.removeFirst()) {
                is JsonArray -> queue.addAll(current)
                is JsonObject -> {
                    if ("Recipe" in types(current)) return current
                    current.values.forEach { if (it is JsonArray || it is JsonObject) queue.add(it) }
                }
                is JsonPrimitive -> Unit
            }
        }
        return null
    }

    /**
     * The instructions, one element per step, in order: sections are opened,
     * and a bare string counts as a step of its own.
     */
    fun steps(recipe: JsonObject): List<JsonElement> {
        val steps = mutableListOf<JsonElement>()
        // Depth first, in the order of the page: the next node to read is on top.
        val pending = ArrayDeque<JsonElement>()
        recipe["recipeInstructions"]?.let(pending::addLast)
        while (pending.isNotEmpty()) {
            when (val node = pending.removeLast()) {
                is JsonArray -> node.asReversed().forEach(pending::addLast)
                is JsonPrimitive -> if (node.isString && node.content.isNotBlank()) steps += node
                is JsonObject ->
                    if ("HowToSection" in types(node)) {
                        (node["itemListElement"] ?: node["steps"] ?: node["recipeInstructions"])?.let(pending::addLast)
                    } else {
                        steps += node
                    }
            }
        }
        return steps
    }

    /**
     * The photo each step gives as its `image`, by step number (`1` is the
     * first step), after [normalize]. A picture of the whole recipe is not a
     * photo of a step and is left out: such a step gets no photo at all.
     */
    fun stepPhotos(recipe: JsonObject, base: String, normalize: (String) -> String = { it }): Map<Int, String> {
        val recipePictures = urls(recipe["image"], base).map(normalize).toSet()
        return steps(recipe).mapIndexedNotNull { index, step ->
            val image = (step as? JsonObject)?.let { url(it["image"], base) }?.let(normalize)
            image?.takeIf { it !in recipePictures }?.let { (index + 1) to it }
        }.toMap()
    }

    /** The first address found in a string, a list, or an object's `url`, `contentUrl` or `thumbnailUrl`. */
    fun url(value: JsonElement?, base: String): String? {
        // Depth first, in the order of the page: the next node to read is on top.
        val pending = ArrayDeque<JsonElement>()
        value?.let(pending::addLast)
        while (pending.isNotEmpty()) {
            when (val node = pending.removeLast()) {
                is JsonPrimitive -> node.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return absolute(it, base) }
                is JsonArray -> node.asReversed().forEach(pending::addLast)
                is JsonObject ->
                    listOfNotNull(node["url"], node["contentUrl"], node["thumbnailUrl"]).asReversed().forEach(pending::addLast)
            }
        }
        return null
    }

    /** Every address a value holds, for lists of pictures. */
    fun urls(value: JsonElement?, base: String): List<String> {
        val found = mutableListOf<String>()
        val pending = ArrayDeque<JsonElement>()
        value?.let(pending::addLast)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (node is JsonArray) node.asReversed().forEach(pending::addLast) else url(node, base)?.let(found::add)
        }
        return found
    }

    fun types(node: JsonObject): List<String> = when (val type = node["@type"]) {
        is JsonPrimitive -> listOfNotNull(type.contentOrNull)
        is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonObject, null -> emptyList()
    }

    fun text(node: JsonObject, key: String): String? =
        (node[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    /** A number written as a number or as text. */
    fun number(node: JsonObject, key: String): Double? =
        (node[key] as? JsonPrimitive)?.contentOrNull?.trim()?.toDoubleOrNull()

    /** [value] against the page [base]; left as written when either is not an address. */
    private fun absolute(value: String, base: String): String = try {
        URI(base).resolve(value).toString()
    } catch (_: URISyntaxException) {
        value
    } catch (_: IllegalArgumentException) {
        value
    }
}
