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
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.recipe.data.RecipeDraftStore
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeImageFiles
import org.opensources.umai.recipe.domain.RecipeDraft
import java.util.UUID

/** How the form was left, once the draft is safely written. */
sealed interface RecipeCreateExit {
    /** [draftSaved] is false when nothing had been typed, so nothing was kept. */
    data class Left(val draftSaved: Boolean) : RecipeCreateExit

    /** [imageSaved] is false when Mealie refused the picture of the new recipe. */
    data class Created(val slug: String, val imageSaved: Boolean) : RecipeCreateExit
}

data class RecipeCreateUiState(
    val draft: RecipeDraft = RecipeDraft(id = ""),
    val step: RecipeFormSection = RecipeFormSection.BASICS,
    val categories: List<Organizer> = emptyList(),
    val tags: List<Organizer> = emptyList(),
    val loadingOrganizers: Boolean = false,
    val processingImage: Boolean = false,
    val imageFailed: Boolean = false,
    val creating: Boolean = false,
    val error: NetworkError? = null,
    /** Set once the form may close; the screen then navigates away. */
    val exit: RecipeCreateExit? = null,
) {
    val isFirstStep: Boolean get() = step == RecipeFormSection.entries.first()
    val isLastStep: Boolean get() = step == RecipeFormSection.entries.last()
    val canCreate: Boolean get() = draft.canBeCreated && !creating && !processingImage
    val stepNumber: Int get() = RecipeFormSection.entries.indexOf(step) + 1
    val stepCount: Int get() = RecipeFormSection.entries.size
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
    private val imageFiles: RecipeImageFiles,
) : ViewModel(), RecipeDraftEditing {

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

    override fun editDraft(change: (RecipeDraft) -> RecipeDraft) {
        _state.update { current ->
            val updated = change(current.draft)
            pendingDraft.value = updated
            current.copy(draft = updated)
        }
    }

    /** The picture is framed and stored at once: the picker's access to it does not last. */
    override fun setImage(sourceUri: String, region: CropRegion) {
        if (_state.value.processingImage) return
        _state.update { it.copy(processingImage = true, imageFailed = false) }
        viewModelScope.launch {
            val path = imageFiles.save(sourceUri, region)
            if (path == null) {
                _state.update { it.copy(processingImage = false, imageFailed = true) }
                return@launch
            }
            val previous = _state.value.draft.imagePath
            editDraft { it.copy(imagePath = path) }
            _state.update { it.copy(processingImage = false) }
            previous?.let(imageFiles::delete)
        }
    }

    override fun removeImage() {
        val previous = _state.value.draft.imagePath ?: return
        editDraft { it.copy(imagePath = null) }
        imageFiles.delete(previous)
    }

    override fun showSection(section: RecipeFormSection) {
        _state.update { it.copy(step = section) }
        if (section == RecipeFormSection.ORGANIZERS) loadOrganizers()
    }

    fun next() {
        val index = RecipeFormSection.entries.indexOf(_state.value.step)
        RecipeFormSection.entries.getOrNull(index + 1)?.let(::showSection)
    }

    fun previous() {
        val index = RecipeFormSection.entries.indexOf(_state.value.step)
        RecipeFormSection.entries.getOrNull(index - 1)?.let(::showSection)
    }

    /**
     * Leaves the form, by the back button or the save button alike. The draft
     * is written before the screen is allowed to close: the ViewModel is gone
     * right after, and a write still pending in the debounce would be lost.
     */
    fun leave() {
        if (_state.value.exit != null || _state.value.creating) return
        val draft = _state.value.draft
        val keep = draft.id.isNotBlank() && !draft.isBlank
        viewModelScope.launch {
            if (keep) draftStore.save(draft)
            _state.update { it.copy(exit = RecipeCreateExit.Left(draftSaved = keep)) }
        }
    }

    fun create() {
        val draft = _state.value.draft
        if (!_state.value.canCreate) return
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            val image = draft.imagePath?.let { imageFiles.read(it) }
            when (val result = editRepository.create(draft, image)) {
                is ApiResult.Failure -> _state.update { it.copy(creating = false, error = result.error) }
                is ApiResult.Success -> {
                    // The recipe now lives on Mealie; keeping the draft would
                    // only invite a duplicate. Its picture goes with it.
                    draftStore.delete(draft.id)
                    val created = result.value
                    _state.update {
                        it.copy(
                            creating = false,
                            exit = RecipeCreateExit.Created(created.slug, created.imageSaved),
                        )
                    }
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

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

    companion object {
        private const val SAVE_DEBOUNCE_MS = 700L

        fun factory(container: AppContainer, draftId: String?) = viewModelFactory {
            initializer {
                RecipeCreateViewModel(
                    draftId = draftId,
                    draftStore = container.recipeDraftStore,
                    editRepository = container.recipeEditRepository,
                    organizerRepository = container.organizerRepository,
                    imageFiles = container.recipeImageFiles,
                )
            }
        }
    }
}
