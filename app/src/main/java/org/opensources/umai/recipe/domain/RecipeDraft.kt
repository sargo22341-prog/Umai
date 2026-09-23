package org.opensources.umai.recipe.domain

import kotlinx.serialization.Serializable

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
    val ingredients: List<String> = emptyList(),
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
            ingredients.all { it.isBlank() } && steps.all { it.text.isBlank() }

    /** A draft can only be pushed to Mealie once it has a name. */
    val canBeCreated: Boolean get() = name.isNotBlank()

    val displayName: String get() = name.trim()
}

/**
 * One instruction. [id] is Mealie's own id when the step comes from an
 * existing recipe, so an edit keeps what hangs off it (linked ingredients).
 */
@Serializable
data class DraftStep(val title: String = "", val text: String = "", val id: String? = null)

/** A category or a tag the user picked, kept with what Mealie needs to store it. */
@Serializable
data class DraftOrganizer(val id: String, val name: String, val slug: String)

/**
 * An existing recipe opened in the editor: the form's content, plus what is
 * needed to show the picture it has on Mealie.
 */
data class EditableRecipe(
    val recipeId: String,
    val imageToken: String?,
    val draft: RecipeDraft,
)
