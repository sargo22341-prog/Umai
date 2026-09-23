package org.opensources.umai.recipe.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeIngredientDto
import org.opensources.umai.recipe.domain.DraftOrganizer
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.RecipeDraft

/**
 * The editable part of an existing recipe, as the form shows it.
 *
 * Ingredients become one line of text each — what Mealie displays for them —
 * and times come from the column Mealie's own editor fills.
 */
internal fun RecipeDetailDto.toEditableDraft(): RecipeDraft = RecipeDraft(
    id = slug,
    name = name.orEmpty(),
    description = description.orEmpty(),
    servings = recipeServings.toInt().coerceAtLeast(0),
    prepTime = prepTime.orEmpty(),
    cookTime = (performTime?.takeIf { it.isNotBlank() } ?: cookTime).orEmpty(),
    totalTime = totalTime.orEmpty(),
    ingredients = recipeIngredient.map { it.editableLine() },
    steps = recipeInstructions.orEmpty().map { DraftStep(title = it.title.orEmpty(), text = it.text, id = it.id) },
    categories = categories.orEmpty().mapNotNull { dto ->
        dto.id?.let { DraftOrganizer(id = it, name = dto.name, slug = dto.slug) }
    },
    tags = tags.orEmpty().mapNotNull { dto ->
        dto.id?.let { DraftOrganizer(id = it, name = dto.name, slug = dto.slug) }
    },
)

private fun RecipeIngredientDto.editableLine(): String =
    display.trim()
        .ifBlank { originalText?.trim().orEmpty() }
        .ifBlank { note?.trim().orEmpty() }

/**
 * Writes the fields the user changed — and only those — into the recipe
 * document Mealie sent, which is then sent back whole.
 *
 * Everything else is left exactly as the server wrote it, including what Umai
 * does not model. Where a field did change, what can be kept is kept: an
 * ingredient line left as it was keeps its structured quantity, unit and food,
 * and a step keeps its id and the ingredients linked to it.
 */
internal fun JsonObject.withEdits(original: RecipeDraft, edited: RecipeDraft, json: Json): JsonObject {
    val fields = toMutableMap()
    val detail = json.decodeFromJsonElement(RecipeDetailDto.serializer(), this)

    if (edited.name.trim() != original.name.trim()) fields["name"] = JsonPrimitive(edited.name.trim())
    if (edited.description.trim() != original.description.trim()) {
        fields["description"] = JsonPrimitive(edited.description.trim())
    }
    if (edited.servings != original.servings) {
        fields["recipeServings"] = JsonPrimitive(edited.servings.coerceAtLeast(0).toDouble())
    }
    if (edited.prepTime.trim() != original.prepTime.trim()) fields["prepTime"] = edited.prepTime.asTime()
    if (edited.totalTime.trim() != original.totalTime.trim()) fields["totalTime"] = edited.totalTime.asTime()
    if (edited.cookTime.trim() != original.cookTime.trim()) {
        // Written back where it was read from: a scraped recipe only fills `cookTime`.
        val column = if (detail.performTime.isNullOrBlank() && !detail.cookTime.isNullOrBlank()) {
            "cookTime"
        } else {
            "performTime"
        }
        fields[column] = edited.cookTime.asTime()
    }
    if (edited.ingredients != original.ingredients) {
        fields["recipeIngredient"] = ingredientsFor(edited.ingredients, detail)
    }
    if (edited.steps != original.steps) fields["recipeInstructions"] = stepsFor(edited.steps)
    if (edited.categories != original.categories) {
        fields["recipeCategory"] = organizersFor(edited.categories, this["recipeCategory"])
    }
    if (edited.tags != original.tags) fields["tags"] = organizersFor(edited.tags, this["tags"])

    return JsonObject(fields)
}

private fun String.asTime(): JsonElement = trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull

/** Unchanged lines keep their original object; new or edited lines become plain notes. */
private fun JsonObject.ingredientsFor(lines: List<String>, detail: RecipeDetailDto): JsonArray {
    val originals = (this["recipeIngredient"] as? JsonArray).orEmpty()
    val available = originals.indices
        .filter { it < detail.recipeIngredient.size }
        .groupBy({ detail.recipeIngredient[it].editableLine() }, { originals[it] })
        .mapValues { (_, objects) -> objects.toMutableList() }
        .toMutableMap()

    return JsonArray(
        lines.map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            available[line]?.removeFirstOrNull() ?: buildJsonObject {
                put("note", line)
                put("display", line)
                put("originalText", line)
                put("quantity", 0.0)
            }
        },
    )
}

/** A step coming from the recipe keeps its object — id and links — with the new text. */
private fun JsonObject.stepsFor(steps: List<DraftStep>): JsonArray {
    val originals = (this["recipeInstructions"] as? JsonArray).orEmpty()
        .filterIsInstance<JsonObject>()
        .associateBy { it["id"]?.jsonPrimitive?.contentOrNull }

    return JsonArray(
        steps.filter { it.text.isNotBlank() || it.title.isNotBlank() }.map { step ->
            val base = step.id?.let { originals[it] }
                ?: buildJsonObject { put("ingredientReferences", JsonArray(emptyList())) }
            JsonObject(
                base + mapOf(
                    "title" to JsonPrimitive(step.title.trim()),
                    "text" to JsonPrimitive(step.text.trim()),
                ),
            )
        },
    )
}

/** Organizers already on the recipe keep their object; new ones carry what Mealie needs. */
private fun organizersFor(selected: List<DraftOrganizer>, current: JsonElement?): JsonArray {
    val existing = (current as? JsonArray).orEmpty()
        .filterIsInstance<JsonObject>()
        .associateBy { it["id"]?.jsonPrimitive?.contentOrNull }

    return JsonArray(
        selected.map { organizer ->
            existing[organizer.id] ?: buildJsonObject {
                put("id", organizer.id)
                put("name", organizer.name)
                put("slug", organizer.slug)
            }
        },
    )
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
