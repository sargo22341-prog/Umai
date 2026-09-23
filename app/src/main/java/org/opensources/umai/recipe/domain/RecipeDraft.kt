package org.opensources.umai.recipe.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import java.util.UUID

/**
 * A recipe being written on the device.
 *
 * Mealie has no notion of an unfinished recipe, so a draft only ever lives
 * here; it is turned into a real recipe the day the user finishes it.
 */
@Serializable
data class RecipeDraft(
    val id: String,
    val name: String = "",
    val description: String = "",
    val servings: Int = 0,
    val prepTime: String = "",
    val cookTime: String = "",
    val totalTime: String = "",
    val ingredients: List<@Serializable(with = DraftIngredientSerializer::class) DraftIngredient> = emptyList(),
    val steps: List<DraftStep> = emptyList(),
    val categories: List<DraftOrganizer> = emptyList(),
    val tags: List<DraftOrganizer> = emptyList(),
    /** The framed picture, stored on the device until the recipe reaches Mealie. */
    val imagePath: String? = null,
    /** Epoch millis, so the list can show the most recent draft first. */
    val updatedAt: Long = 0L,
) {
    val isBlank: Boolean
        get() = name.isBlank() && description.isBlank() && imagePath == null &&
            ingredients.all { it.text.isBlank() } && steps.all { it.text.isBlank() && it.photoPath == null }

    /** A draft can only be pushed to Mealie once it has a name. */
    val canBeCreated: Boolean get() = name.isNotBlank()

    val displayName: String get() = name.trim()

    /** The steps as they will be written: an empty step is dropped. */
    val writtenSteps: List<DraftStep>
        get() = steps.filter { it.text.isNotBlank() || it.title.isNotBlank() }

    /** Pictures framed on the device for this draft, which it owns. */
    val devicePhotoPaths: List<String>
        get() = listOfNotNull(imagePath) + steps.mapNotNull { it.photoPath }
}

/**
 * One ingredient line. [referenceId] is how a step points at it; [food] is the
 * food Mealie linked the line to, kept only while the line is left unchanged.
 */
@Serializable
data class DraftIngredient(
    val text: String = "",
    val referenceId: String = newReferenceId(),
    val food: DraftFood? = null,
)

/** The names a food goes by, which are what the steps mention. */
@Serializable
data class DraftFood(val name: String, val pluralName: String? = null)

/**
 * One instruction. [id] is Mealie's own id when the step comes from an
 * existing recipe, so an edit keeps what hangs off it.
 *
 * [photoFile] is the photo the step has on Mealie, [photoPath] a new one framed
 * on the device and not uploaded yet.
 */
@Serializable
data class DraftStep(
    val title: String = "",
    val text: String = "",
    val id: String? = null,
    val ingredientReferences: List<String> = emptyList(),
    val photoFile: String? = null,
    val photoPath: String? = null,
)

/** A category or a tag the user picked, kept with what Mealie needs to store it. */
@Serializable
data class DraftOrganizer(val id: String, val name: String, val slug: String)

/**
 * An existing recipe opened in the editor: the form's content, plus what is
 * needed to show its pictures.
 */
data class EditableRecipe(
    val recipeId: String,
    val imageToken: String?,
    val draft: RecipeDraft,
    /** Changes whenever the recipe does, so a replaced photo is not served from a cache. */
    val mediaVersion: String? = null,
)

fun newReferenceId(): String = UUID.randomUUID().toString()

/** Drafts written before ingredients carried a reference stored each line as bare text. */
internal object DraftIngredientSerializer :
    JsonTransformingSerializer<DraftIngredient>(DraftIngredient.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive) JsonObject(mapOf("text" to element)) else element
}
