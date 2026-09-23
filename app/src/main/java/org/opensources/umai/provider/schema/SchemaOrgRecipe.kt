package org.opensources.umai.provider.schema

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URI

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
                else -> Unit
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
        fun visit(node: JsonElement?) {
            when (node) {
                is JsonArray -> node.forEach(::visit)
                is JsonPrimitive -> if (node.isString && node.content.isNotBlank()) steps += node
                is JsonObject ->
                    if ("HowToSection" in types(node)) {
                        visit(node["itemListElement"] ?: node["steps"] ?: node["recipeInstructions"])
                    } else {
                        steps += node
                    }
                else -> Unit
            }
        }
        visit(recipe["recipeInstructions"])
        return steps
    }

    /** The first address found in a string, a list, or an object's `url`, `contentUrl` or `thumbnailUrl`. */
    fun url(value: JsonElement?, base: String): String? = when (value) {
        is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { absolute(it, base) }
        is JsonArray -> value.firstNotNullOfOrNull { url(it, base) }
        is JsonObject -> url(value["url"], base) ?: url(value["contentUrl"], base) ?: url(value["thumbnailUrl"], base)
        else -> null
    }

    /** Every address a value holds, for lists of pictures. */
    fun urls(value: JsonElement?, base: String): List<String> = when (value) {
        is JsonArray -> value.flatMap { urls(it, base) }
        null -> emptyList()
        else -> listOfNotNull(url(value, base))
    }

    fun types(node: JsonObject): List<String> = when (val type = node["@type"]) {
        is JsonPrimitive -> listOfNotNull(type.contentOrNull)
        is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }

    fun text(node: JsonObject, key: String): String? =
        (node[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    /** A number written as a number or as text. */
    fun number(node: JsonObject, key: String): Double? =
        (node[key] as? JsonPrimitive)?.contentOrNull?.trim()?.toDoubleOrNull()

    private fun absolute(value: String, base: String): String =
        runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)
}
