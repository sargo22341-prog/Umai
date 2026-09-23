package org.opensources.umai.recipe.ui

import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.DraftOrganizer
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.IngredientLinker
import org.opensources.umai.recipe.domain.RecipeDraft

/** The sections of the recipe form, in the order the creation walks through them. */
enum class RecipeFormSection { BASICS, IMAGE, INGREDIENTS, INSTRUCTIONS, ORGANIZERS }

/** The outcome of the last automatic linking: [added] new links, [total] links in all. */
data class IngredientLinkResult(val added: Int, val total: Int)

/**
 * The edits the recipe form makes to a [RecipeDraft], shared by the creation
 * and the edition of a recipe: each ViewModel only says where the draft lives.
 */
interface RecipeDraftEditing {

    fun editDraft(change: (RecipeDraft) -> RecipeDraft)

    /** [sourceUri] is the picked picture, [region] the part the user framed. */
    fun setImage(sourceUri: String, region: CropRegion)

    fun removeImage()

    /** Frames a new photo for the step at [index], saved with the recipe. */
    fun setStepPhoto(index: Int, sourceUri: String, region: CropRegion)

    /** Drops the photo framed on the device for the step; one already on Mealie stays. */
    fun removeStepPhoto(index: Int)

    fun showSection(section: RecipeFormSection)

    /** Reports the outcome of [linkIngredients]. */
    fun onIngredientsLinked(result: IngredientLinkResult)

    fun onNameChange(value: String) = editDraft { it.copy(name = value) }

    fun onDescriptionChange(value: String) = editDraft { it.copy(description = value) }

    fun onServingsChange(value: Int) = editDraft { it.copy(servings = value.coerceIn(0, MAX_SERVINGS)) }

    fun onPrepTimeChange(value: String) = editDraft { it.copy(prepTime = value) }

    fun onCookTimeChange(value: String) = editDraft { it.copy(cookTime = value) }

    fun onTotalTimeChange(value: String) = editDraft { it.copy(totalTime = value) }

    /** A retyped line keeps its reference, so its links stay; its food no longer says what it is. */
    fun onIngredientChange(index: Int, value: String) = editDraft { draft ->
        draft.copy(
            ingredients = draft.ingredients.updateAt(index) {
                if (it.text == value) it else it.copy(text = value, food = null)
            },
        )
    }

    fun addIngredient() = editDraft { it.copy(ingredients = it.ingredients + DraftIngredient()) }

    /** The steps stop pointing at a line that is removed. */
    fun removeIngredient(index: Int) = editDraft { draft ->
        val removed = draft.ingredients.getOrNull(index) ?: return@editDraft draft
        draft.copy(
            ingredients = draft.ingredients.removeAt(index),
            steps = draft.steps.map { step ->
                step.copy(ingredientReferences = step.ingredientReferences - removed.referenceId)
            },
        )
    }

    fun onStepTitleChange(index: Int, value: String) = editDraft { draft ->
        draft.copy(steps = draft.steps.updateAt(index) { it.copy(title = value) })
    }

    fun onStepTextChange(index: Int, value: String) = editDraft { draft ->
        draft.copy(steps = draft.steps.updateAt(index) { it.copy(text = value) })
    }

    fun addStep() = editDraft { it.copy(steps = it.steps + DraftStep()) }

    fun removeStep(index: Int) = editDraft { it.copy(steps = it.steps.removeAt(index)) }

    /** Adds the links the steps' wording suggests to the ones they already have. */
    fun linkIngredients() {
        var outcome = IngredientLinkResult(0, 0)
        editDraft { draft ->
            val result = IngredientLinker.link(draft.ingredients, draft.steps)
            outcome = IngredientLinkResult(result.added, result.total)
            draft.copy(steps = result.steps)
        }
        onIngredientsLinked(outcome)
    }

    fun unlinkIngredient(stepIndex: Int, referenceId: String) = editDraft { draft ->
        draft.copy(
            steps = draft.steps.updateAt(stepIndex) { step ->
                step.copy(ingredientReferences = step.ingredientReferences - referenceId)
            },
        )
    }

    fun toggleCategory(organizer: Organizer) = editDraft { draft ->
        draft.copy(categories = draft.categories.toggle(organizer))
    }

    fun toggleTag(organizer: Organizer) = editDraft { draft ->
        draft.copy(tags = draft.tags.toggle(organizer))
    }

    private companion object {
        const val MAX_SERVINGS = 999
    }
}

internal fun <T> List<T>.updateAt(index: Int, change: (T) -> T): List<T> =
    if (index !in indices) this else toMutableList().also { it[index] = change(it[index]) }

private fun <T> List<T>.removeAt(index: Int): List<T> =
    if (index !in indices) this else toMutableList().also { it.removeAt(index) }

private fun List<DraftOrganizer>.toggle(organizer: Organizer): List<DraftOrganizer> =
    if (any { it.id == organizer.id }) {
        filterNot { it.id == organizer.id }
    } else {
        this + DraftOrganizer(organizer.id, organizer.name, organizer.slug)
    }
