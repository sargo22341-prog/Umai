package org.opensources.umai.recipe.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.home.data.RecentRecipesStore
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeImageFiles
import org.opensources.umai.recipe.domain.EditableRecipe
import org.opensources.umai.recipe.domain.RecipeDraft

data class RecipeEditUiState(
    val loading: Boolean = true,
    /** A failure to open the recipe: the form cannot be shown at all. */
    val loadError: NetworkError? = null,
    val recipe: EditableRecipe? = null,
    val draft: RecipeDraft = RecipeDraft(id = ""),
    val section: RecipeFormSection = RecipeFormSection.BASICS,
    val categories: List<Organizer> = emptyList(),
    val tags: List<Organizer> = emptyList(),
    val loadingOrganizers: Boolean = false,
    /** A picture framed on the device, uploaded when the recipe is saved. */
    val newImagePath: String? = null,
    val processingImage: Boolean = false,
    val imageFailed: Boolean = false,
    val saving: Boolean = false,
    /** A failure to save: the form stays, with what was typed. */
    val saveError: NetworkError? = null,
    /**
     * The slug of the recipe once some change reached Mealie, even if the new
     * picture then failed: leaving must refresh the recipe page either way.
     */
    val committedSlug: String? = null,
    /** Set once Mealie holds every change; the screen then closes. */
    val savedSlug: String? = null,
) {
    val hasChanges: Boolean
        get() = recipe != null && (draft != recipe.draft || newImagePath != null)

    val canSave: Boolean
        get() = hasChanges && draft.canBeCreated && !saving && !processingImage
}

/**
 * Edits an existing recipe with the same sections as the creation form, which
 * the user can visit in any order. Nothing reaches Mealie until they save;
 * only the fields they changed are written.
 */
class RecipeEditViewModel(
    slug: String,
    private val editRepository: RecipeEditRepository,
    private val organizerRepository: OrganizerRepository,
    private val imageFiles: RecipeImageFiles,
    private val recentRecipesStore: RecentRecipesStore,
) : ViewModel(), RecipeDraftEditing {

    /** Follows a rename saved here, so a retry addresses the recipe as it now is. */
    private var slug: String = slug

    private val _state = MutableStateFlow(RecipeEditUiState())
    val state: StateFlow<RecipeEditUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = editRepository.loadForEdit(slug)) {
                is ApiResult.Failure -> _state.update { it.copy(loading = false, loadError = result.error) }
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, recipe = result.value, draft = result.value.draft)
                }
            }
        }
    }

    override fun editDraft(change: (RecipeDraft) -> RecipeDraft) {
        if (_state.value.recipe == null) return
        _state.update { it.copy(draft = change(it.draft)) }
    }

    override fun setImage(sourceUri: String, region: CropRegion) {
        if (_state.value.processingImage) return
        _state.update { it.copy(processingImage = true, imageFailed = false) }
        viewModelScope.launch {
            val path = imageFiles.save(sourceUri, region)
            val previous = _state.value.newImagePath
            _state.update {
                if (path == null) {
                    it.copy(processingImage = false, imageFailed = true)
                } else {
                    it.copy(processingImage = false, newImagePath = path)
                }
            }
            if (path != null) previous?.let(imageFiles::delete)
        }
    }

    /** Drops the picture picked here; the one on Mealie stays as it is. */
    override fun removeImage() {
        val previous = _state.value.newImagePath ?: return
        _state.update { it.copy(newImagePath = null) }
        imageFiles.delete(previous)
    }

    override fun showSection(section: RecipeFormSection) {
        _state.update { it.copy(section = section) }
        if (section == RecipeFormSection.ORGANIZERS) loadOrganizers()
    }

    /**
     * Writes the recipe, then its new picture. The picture goes second because
     * it is addressed by slug, and renaming the recipe changes the slug.
     */
    fun save() {
        val current = _state.value
        val recipe = current.recipe ?: return
        if (!current.canSave) return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val newSlug = when (val result = editRepository.update(slug, recipe.draft, current.draft)) {
                is ApiResult.Failure -> {
                    _state.update { it.copy(saving = false, saveError = result.error) }
                    return@launch
                }
                is ApiResult.Success -> result.value
            }
            if (newSlug != slug) {
                recentRecipesStore.rename(slug, newSlug)
                slug = newSlug
            }

            val imagePath = current.newImagePath
            if (imagePath != null) {
                val upload = imageFiles.read(imagePath)
                    ?.let { editRepository.uploadImage(newSlug, it) }
                    ?: ApiResult.Failure(NetworkError.InvalidResponse)
                if (upload is ApiResult.Failure) {
                    // The text is saved: what is left to retry is the picture
                    // alone, against the recipe as it now is on Mealie.
                    _state.update {
                        it.copy(
                            saving = false,
                            saveError = upload.error,
                            committedSlug = newSlug,
                            recipe = recipe.copy(draft = current.draft.copy(id = newSlug)),
                            draft = it.draft.copy(id = newSlug),
                        )
                    }
                    return@launch
                }
                imageFiles.delete(imagePath)
            }
            _state.update { it.copy(saving = false, newImagePath = null, savedSlug = newSlug) }
        }
    }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    /** A picture framed here but never saved is not kept on the device. */
    override fun onCleared() {
        _state.value.newImagePath?.let(imageFiles::delete)
    }

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
        fun factory(container: AppContainer, slug: String) = viewModelFactory {
            initializer {
                RecipeEditViewModel(
                    slug = slug,
                    editRepository = container.recipeEditRepository,
                    organizerRepository = container.organizerRepository,
                    imageFiles = container.recipeImageFiles,
                    recentRecipesStore = container.recentRecipesStore,
                )
            }
        }
    }
}
