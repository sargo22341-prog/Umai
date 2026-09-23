package org.opensources.umai.recipe.ui

import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.recipe.domain.DraftOrganizer
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.RecipeDraft

/** The sections of the recipe form, in the order the creation walks through them. */
enum class RecipeFormSection { BASICS, IMAGE, INGREDIENTS, INSTRUCTIONS, ORGANIZERS }

/**
 * The edits the recipe form makes to a [RecipeDraft], shared by the creation
 * and the edition of a recipe: each ViewModel only says where the draft lives.
 */
interface RecipeDraftEditing {

    fun editDraft(change: (RecipeDraft) -> RecipeDraft)

    /** [sourceUri] is the picked picture, [region] the part the user framed. */
    fun setImage(sourceUri: String, region: CropRegion)

    fun removeImage()

    fun showSection(section: RecipeFormSection)

    fun onNameChange(value: String) = editDraft { it.copy(name = value) }

    fun onDescriptionChange(value: String) = editDraft { it.copy(description = value) }

    fun onServingsChange(value: Int) = editDraft { it.copy(servings = value.coerceIn(0, MAX_SERVINGS)) }

    fun onPrepTimeChange(value: String) = editDraft { it.copy(prepTime = value) }

    fun onCookTimeChange(value: String) = editDraft { it.copy(cookTime = value) }

    fun onTotalTimeChange(value: String) = editDraft { it.copy(totalTime = value) }

    fun onIngredientChange(index: Int, value: String) = editDraft { draft ->
        draft.copy(ingredients = draft.ingredients.replaceAt(index, value))
    }

    fun addIngredient() = editDraft { it.copy(ingredients = it.ingredients + "") }

    fun removeIngredient(index: Int) = editDraft { it.copy(ingredients = it.ingredients.removeAt(index)) }

    fun onStepTitleChange(index: Int, value: String) = editDraft { draft ->
        draft.copy(steps = draft.steps.updateAt(index) { it.copy(title = value) })
    }

    fun onStepTextChange(index: Int, value: String) = editDraft { draft ->
        draft.copy(steps = draft.steps.updateAt(index) { it.copy(text = value) })
    }

    fun addStep() = editDraft { it.copy(steps = it.steps + DraftStep()) }

    fun removeStep(index: Int) = editDraft { it.copy(steps = it.steps.removeAt(index)) }

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

private fun List<String>.replaceAt(index: Int, value: String): List<String> =
    if (index !in indices) this else toMutableList().also { it[index] = value }

private fun <T> List<T>.removeAt(index: Int): List<T> =
    if (index !in indices) this else toMutableList().also { it.removeAt(index) }

private fun List<DraftStep>.updateAt(index: Int, change: (DraftStep) -> DraftStep): List<DraftStep> =
    if (index !in indices) this else toMutableList().also { it[index] = change(it[index]) }

private fun List<DraftOrganizer>.toggle(organizer: Organizer): List<DraftOrganizer> =
    if (any { it.id == organizer.id }) {
        filterNot { it.id == organizer.id }
    } else {
        this + DraftOrganizer(organizer.id, organizer.name, organizer.slug)
    }
