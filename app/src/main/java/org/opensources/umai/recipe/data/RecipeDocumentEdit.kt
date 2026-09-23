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
import org.opensources.umai.core.model.RecipeAsset
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeIngredientDto
import org.opensources.umai.recipe.domain.DraftFood
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.DraftOrganizer
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.RecipeDraft
import org.opensources.umai.recipe.domain.RecipeMediaFiles
import org.opensources.umai.recipe.domain.newReferenceId

/**
 * The editable part of an existing recipe, as the form shows it.
 *
 * Ingredients become one line of text each — what Mealie displays for them —
 * and times come from the column Mealie's own editor fills. An ingredient
 * Mealie gave no reference gets one, so the steps can point at it.
 */
internal fun RecipeDetailDto.toEditableDraft(): RecipeDraft {
    val photos = RecipeMediaFiles.stepPhotos(assets.map { RecipeAsset(it.name, it.icon, it.fileName) })
    return RecipeDraft(
        id = slug,
        name = name.orEmpty(),
        description = description.orEmpty(),
        servings = recipeServings.toInt().coerceAtLeast(0),
        prepTime = prepTime.orEmpty(),
        cookTime = (performTime?.takeIf { it.isNotBlank() } ?: cookTime).orEmpty(),
        totalTime = totalTime.orEmpty(),
        ingredients = recipeIngredient.map { ingredient ->
            DraftIngredient(
                text = ingredient.editableLine(),
                referenceId = ingredient.referenceId?.takeIf { it.isNotBlank() } ?: newReferenceId(),
                food = ingredient.food?.name?.takeIf { it.isNotBlank() }
                    ?.let { DraftFood(it, ingredient.food.pluralName?.takeIf { plural -> plural.isNotBlank() }) },
            )
        },
        steps = recipeInstructions.orEmpty().mapIndexed { index, step ->
            DraftStep(
                title = step.title.orEmpty(),
                text = step.text,
                id = step.id,
                ingredientReferences = step.ingredientReferences.mapNotNull { it.referenceId }.distinct(),
                photoFile = photos[index + 1],
            )
        },
        categories = categories.orEmpty().mapNotNull { dto ->
            dto.id?.let { DraftOrganizer(id = it, name = dto.name, slug = dto.slug) }
        },
        tags = tags.orEmpty().mapNotNull { dto ->
            dto.id?.let { DraftOrganizer(id = it, name = dto.name, slug = dto.slug) }
        },
    )
}

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
 * and a step keeps its id. Photos are not part of the document: they are
 * assets, saved apart.
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
    val ingredientsChanged = edited.ingredients != original.ingredients
    if (ingredientsChanged) {
        fields["recipeIngredient"] = ingredientsFor(edited.ingredients, original.ingredients)
    }
    if (ingredientsChanged || edited.steps.withoutPhotos() != original.steps.withoutPhotos()) {
        fields["recipeInstructions"] = stepsFor(edited.writtenSteps, edited.ingredients)
    }
    if (edited.categories != original.categories) {
        fields["recipeCategory"] = organizersFor(edited.categories, this["recipeCategory"])
    }
    if (edited.tags != original.tags) fields["tags"] = organizersFor(edited.tags, this["tags"])

    return JsonObject(fields)
}

private fun List<DraftStep>.withoutPhotos() = map { it.copy(photoFile = null, photoPath = null) }

private fun String.asTime(): JsonElement = trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull

/**
 * A line left as it was keeps its original object — quantity, unit, food — and
 * an edited or new line becomes a plain note. Every line carries the
 * reference the steps point at, the one the form knows it by.
 */
private fun JsonObject.ingredientsFor(lines: List<DraftIngredient>, originalLines: List<DraftIngredient>): JsonArray {
    val originals = (this["recipeIngredient"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    val byReference = originals.associateBy { it["referenceId"]?.jsonPrimitive?.contentOrNull }

    return JsonArray(
        lines.filter { it.text.isNotBlank() }.map { line ->
            val index = originalLines.indexOf(line)
            val kept = if (index < 0) null else byReference[line.referenceId] ?: originals.getOrNull(index)
            if (kept != null) {
                JsonObject(kept + ("referenceId" to JsonPrimitive(line.referenceId)))
            } else {
                val text = line.text.trim()
                buildJsonObject {
                    put("note", text)
                    put("display", text)
                    put("originalText", text)
                    put("quantity", 0.0)
                    put("referenceId", line.referenceId)
                }
            }
        },
    )
}

/**
 * A step coming from the recipe keeps its object and id, with the new text and
 * links. A link to a line no longer in the recipe is dropped.
 */
private fun JsonObject.stepsFor(steps: List<DraftStep>, ingredients: List<DraftIngredient>): JsonArray {
    val originals = (this["recipeInstructions"] as? JsonArray).orEmpty()
        .filterIsInstance<JsonObject>()
        .associateBy { it["id"]?.jsonPrimitive?.contentOrNull }
    val known = ingredients.filter { it.text.isNotBlank() }.map { it.referenceId }.toSet()

    return JsonArray(
        steps.map { step ->
            val base = step.id?.let { originals[it] } ?: JsonObject(emptyMap())
            JsonObject(
                base + mapOf(
                    "title" to JsonPrimitive(step.title.trim()),
                    "text" to JsonPrimitive(step.text.trim()),
                    "ingredientReferences" to step.referencesTo(known),
                ),
            )
        },
    )
}

internal fun DraftStep.referencesTo(known: Set<String>): JsonArray = JsonArray(
    ingredientReferences.filter { it in known }.distinct().map { reference ->
        buildJsonObject { put("referenceId", reference) }
    },
)

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

