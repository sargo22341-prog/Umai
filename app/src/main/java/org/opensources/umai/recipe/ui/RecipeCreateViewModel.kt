package org.opensources.umai.recipe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.recipe.data.RecipeDraftStore
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.domain.DraftOrganizer
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.RecipeDraft
import java.util.UUID

/** The four stages of the form, in order. */
enum class RecipeCreateStep { BASICS, INGREDIENTS, INSTRUCTIONS, ORGANIZERS }

data class RecipeCreateUiState(
    val draft: RecipeDraft = RecipeDraft(id = ""),
    val step: RecipeCreateStep = RecipeCreateStep.BASICS,
    val categories: List<Organizer> = emptyList(),
    val tags: List<Organizer> = emptyList(),
    val loadingOrganizers: Boolean = false,
    val creating: Boolean = false,
    val error: NetworkError? = null,
    /** Set once Mealie has created the recipe; the screen then navigates to it. */
    val createdSlug: String? = null,
) {
    val isFirstStep: Boolean get() = step == RecipeCreateStep.BASICS
    val isLastStep: Boolean get() = step == RecipeCreateStep.ORGANIZERS
    val canCreate: Boolean get() = draft.canBeCreated && !creating
    val stepNumber: Int get() = RecipeCreateStep.entries.indexOf(step) + 1
    val stepCount: Int get() = RecipeCreateStep.entries.size
}

/**
 * Writes a recipe step by step.
 *
 * The draft is written to the device as the user types — debounced, so a
 * keystroke is not a disk write — which is what lets them leave the screen and
 * pick the recipe up later. It only reaches Mealie when they finish it.
 */
class RecipeCreateViewModel(
    private val draftId: String?,
    private val draftStore: RecipeDraftStore,
    private val editRepository: RecipeEditRepository,
    private val organizerRepository: OrganizerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeCreateUiState())
    val state: StateFlow<RecipeCreateUiState> = _state.asStateFlow()

    /** Mirrors the draft so it can be persisted without writing on every key. */
    private val pendingDraft = MutableStateFlow<RecipeDraft?>(null)

    init {
        viewModelScope.launch {
            val existing = draftId?.let { draftStore.draft(it) }
            _state.update { current ->
                // Anything the user already typed wins: the read is asynchronous
                // and must not wipe the first keystrokes.
                if (current.draft.id.isNotBlank()) {
                    current
                } else {
                    current.copy(draft = existing ?: RecipeDraft(id = UUID.randomUUID().toString()))
                }
            }
        }
        observeDraftChanges()
    }

    @OptIn(FlowPreview::class)
    private fun observeDraftChanges() {
        viewModelScope.launch {
            // A MutableStateFlow already drops identical values, so debouncing
            // is all that is needed to turn a burst of keystrokes into one write.
            pendingDraft
                .debounce(SAVE_DEBOUNCE_MS)
                .collect { draft ->
                    if (draft != null && draft.id.isNotBlank() && !draft.isBlank) {
                        draftStore.save(draft)
                    }
                }
        }
    }

    fun onNameChange(value: String) = edit { it.copy(name = value) }

    fun onDescriptionChange(value: String) = edit { it.copy(description = value) }

    fun onServingsChange(value: Int) = edit { it.copy(servings = value.coerceIn(0, 999)) }

    fun onPrepTimeChange(value: String) = edit { it.copy(prepTime = value) }

    fun onCookTimeChange(value: String) = edit { it.copy(cookTime = value) }

    fun onTotalTimeChange(value: String) = edit { it.copy(totalTime = value) }

    fun onIngredientChange(index: Int, value: String) = edit { draft ->
        draft.copy(ingredients = draft.ingredients.replaceAt(index, value))
    }

    fun addIngredient() = edit { it.copy(ingredients = it.ingredients + "") }

    fun removeIngredient(index: Int) = edit { it.copy(ingredients = it.ingredients.removeAt(index)) }

    fun onStepTitleChange(index: Int, value: String) = edit { draft ->
        draft.copy(steps = draft.steps.updateAt(index) { it.copy(title = value) })
    }

    fun onStepTextChange(index: Int, value: String) = edit { draft ->
        draft.copy(steps = draft.steps.updateAt(index) { it.copy(text = value) })
    }

    fun addStep() = edit { it.copy(steps = it.steps + DraftStep()) }

    fun removeStep(index: Int) = edit { it.copy(steps = it.steps.removeAt(index)) }

    fun toggleCategory(organizer: Organizer) = edit { draft ->
        draft.copy(categories = draft.categories.toggle(organizer))
    }

    fun toggleTag(organizer: Organizer) = edit { draft ->
        draft.copy(tags = draft.tags.toggle(organizer))
    }

    fun goToStep(step: RecipeCreateStep) {
        _state.update { it.copy(step = step) }
        if (step == RecipeCreateStep.ORGANIZERS) loadOrganizers()
    }

    fun next() {
        val index = RecipeCreateStep.entries.indexOf(_state.value.step)
        RecipeCreateStep.entries.getOrNull(index + 1)?.let { goToStep(it) }
    }

    fun previous() {
        val index = RecipeCreateStep.entries.indexOf(_state.value.step)
        RecipeCreateStep.entries.getOrNull(index - 1)?.let { goToStep(it) }
    }

    /** Persists at once, for when the user asks to leave and come back later. */
    fun saveDraftNow() {
        val draft = _state.value.draft
        if (draft.id.isBlank() || draft.isBlank) return
        viewModelScope.launch { draftStore.save(draft) }
    }

    fun create() {
        val draft = _state.value.draft
        if (!draft.canBeCreated || _state.value.creating) return
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            when (val result = editRepository.create(draft)) {
                is ApiResult.Failure -> _state.update { it.copy(creating = false, error = result.error) }
                is ApiResult.Success -> {
                    // The recipe now lives on Mealie; keeping the draft would
                    // only invite a duplicate.
                    draftStore.delete(draft.id)
                    _state.update { it.copy(creating = false, createdSlug = result.value) }
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun consumeCreatedSlug() = _state.update { it.copy(createdSlug = null) }

    private fun loadOrganizers() {
        if (_state.value.categories.isNotEmpty() || _state.value.loadingOrganizers) return
        _state.update { it.copy(loadingOrganizers = true) }
        viewModelScope.launch {
            val categories = organizerRepository.categories()
            val tags = organizerRepository.tags()
            _state.update {
                it.copy(
                    categories = (categories as? ApiResult.Success)?.value.orEmpty(),
                    tags = (tags as? ApiResult.Success)?.value.orEmpty(),
                    loadingOrganizers = false,
                )
            }
        }
    }

    private fun edit(change: (RecipeDraft) -> RecipeDraft) {
        _state.update { current ->
            val updated = change(current.draft)
            pendingDraft.value = updated
            current.copy(draft = updated)
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 700L

        fun factory(container: AppContainer, draftId: String?) = viewModelFactory {
            initializer {
                RecipeCreateViewModel(
                    draftId = draftId,
                    draftStore = container.recipeDraftStore,
                    editRepository = container.recipeEditRepository,
                    organizerRepository = container.organizerRepository,
                )
            }
        }
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
