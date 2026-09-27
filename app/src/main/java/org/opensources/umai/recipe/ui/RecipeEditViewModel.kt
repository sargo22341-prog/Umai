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
import org.opensources.umai.home.data.RecentRecipes
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.recipe.data.CalorieTagRepository
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeImageFiles
import org.opensources.umai.recipe.domain.EditableRecipe
import org.opensources.umai.recipe.domain.DraftChapter
import org.opensources.umai.recipe.domain.RecipeDraft
import org.opensources.umai.recipe.domain.VideoChapters
import org.opensources.umai.recipe.domain.VideoStream

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
    val steps: StepsFormState = StepsFormState(),
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
    val deleting: Boolean = false,
    /** A failure to delete: the recipe and the form stay as they were. */
    val deleteError: NetworkError? = null,
    /** Set once Mealie deleted the recipe; the screen then closes. */
    val deleted: Boolean = false,
    /** Where the player of the video section reads the recipe video, once found. */
    val videoStream: VideoStream? = null,
    val videoStreamLoading: Boolean = false,
    /** The video could not be read at the moment: the chapters can still be typed. */
    val videoStreamFailed: Boolean = false,
) {
    /**
     * Chapters count as a change until Mealie holds them: after a save that
     * stopped halfway, what is left to write is still there to retry.
     */
    val hasChanges: Boolean
        get() = recipe != null &&
            (draft != recipe.draft || newImagePath != null || VideoChapters.changed(draft, recipe.video))

    val canSave: Boolean
        get() = hasChanges && draft.canBeCreated && VideoChapters.areValid(draft) &&
            !saving && !deleting && !processingImage && steps.processingPhoto == null

    /** The tabs of the editor: the video one only for a recipe with a video. */
    val sections: List<RecipeFormSection>
        get() = RecipeFormSection.entries.filter { it != RecipeFormSection.VIDEO || draft.video != null }

    /** Deleting waits for a save under way, whose result would be lost. */
    val canDelete: Boolean
        get() = recipe != null && !saving && !deleting && !deleted

    /** The name the recipe has on Mealie, which the confirmation names. */
    val savedName: String
        get() = recipe?.draft?.name.orEmpty()
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
    private val recentRecipes: RecentRecipes,
    private val calorieTags: CalorieTagRepository? = null,
    /** Where a video is read from: a YouTube page is turned into a stream the player reads. */
    private val streamFor: suspend (String) -> VideoStream? = { null },
) : ViewModel(), RecipeDraftEditing, VideoChapterEditing {

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

    override fun setStepPhoto(index: Int, sourceUri: String, region: CropRegion) {
        if (_state.value.steps.processingPhoto != null) return
        _state.update { it.copy(steps = it.steps.copy(processingPhoto = index, photoFailed = false)) }
        viewModelScope.launch {
            val path = imageFiles.save(sourceUri, region)
            val previous = _state.value.draft.steps.getOrNull(index)?.photoPath
            if (path != null) editDraft { draft -> draft.copy(steps = draft.steps.updateAt(index) { it.copy(photoPath = path) }) }
            _state.update { it.copy(steps = it.steps.copy(processingPhoto = null, photoFailed = path == null)) }
            if (path != null) previous?.let(imageFiles::delete)
        }
    }

    override fun removeStepPhoto(index: Int) {
        val previous = _state.value.draft.steps.getOrNull(index)?.photoPath ?: return
        editDraft { draft -> draft.copy(steps = draft.steps.updateAt(index) { it.copy(photoPath = null) }) }
        imageFiles.delete(previous)
    }

    override fun onIngredientsLinked(result: IngredientLinkResult) =
        _state.update { it.copy(steps = it.steps.copy(linkResult = result)) }

    override fun showSection(section: RecipeFormSection) {
        _state.update { it.copy(section = section) }
        if (section == RecipeFormSection.ORGANIZERS) loadOrganizers()
        if (section == RecipeFormSection.VIDEO && _state.value.videoStream == null) loadVideoStream()
    }

    override fun setChapterStart(stepIndex: Int, seconds: Double?) = editDraft { draft ->
        VideoChapters.update(draft, stepIndex) { chapter -> seconds?.let { start -> DraftChapter(start, chapter?.end) } }
    }

    override fun setChapterEnd(stepIndex: Int, seconds: Double?) = editDraft { draft ->
        VideoChapters.update(draft, stepIndex) { chapter -> chapter?.copy(end = seconds) }
    }

    /** Looks the video up again, when it could not be read. */
    override fun loadVideoStream() {
        val url = _state.value.draft.video?.url ?: return
        if (_state.value.videoStreamLoading) return
        _state.update { it.copy(videoStreamLoading = true, videoStreamFailed = false) }
        viewModelScope.launch {
            val stream = streamFor(url)
            _state.update { it.copy(videoStreamLoading = false, videoStream = stream, videoStreamFailed = stream == null) }
        }
    }

    /**
     * Writes the recipe, then its video chapters, its step photos and its new
     * picture. Those go after the text because they are addressed by slug, and
     * renaming the recipe changes the slug.
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
                recentRecipes.rename(slug, newSlug)
                slug = newSlug
            }
            val chaptered = when (val chapters = editRepository.saveVideoChapters(newSlug, recipe, current.draft)) {
                is ApiResult.Failure -> return@launch savedPartly(chapters.error, newSlug, recipe, current.draft)
                is ApiResult.Success -> chapters.value
            }
            saveMedia(newSlug, recipe, current)?.let { error ->
                return@launch savedPartly(error, newSlug, chaptered, current.draft)
            }
            current.draft.steps.mapNotNull { it.photoPath }.forEach(imageFiles::delete)
            // The nutrition may have been changed on Mealie: the tag follows it. When that
            // fails, the settings' tag sync catches it up; the recipe itself is saved.
            val _ = calorieTags?.sync(newSlug)
            _state.update { it.copy(saving = false, newImagePath = null, savedSlug = newSlug) }
        }
    }

    /**
     * The step photos, then the picture framed here: the first failure, or
     * `null` once both are on Mealie. A picture left to upload stays on the
     * device, ready for another try.
     */
    private suspend fun saveMedia(newSlug: String, recipe: EditableRecipe, current: RecipeEditUiState): NetworkError? {
        val newPhotos = current.draft.writtenSteps.mapIndexedNotNull { index, step ->
            step.photoPath?.let { path -> imageFiles.read(path)?.let { (index + 1) to it } }
        }.toMap()
        val photos = editRepository.saveStepPhotos(newSlug, recipe.recipeId, recipe.draft, current.draft, newPhotos)
        if (photos is ApiResult.Failure) return photos.error
        val imagePath = current.newImagePath ?: return null
        val upload = imageFiles.read(imagePath)
            ?.let { editRepository.uploadImage(newSlug, it) }
            ?: ApiResult.Failure(NetworkError.InvalidResponse)
        if (upload is ApiResult.Failure) return upload.error
        imageFiles.delete(imagePath)
        return null
    }

    /**
     * The text is saved, not all that goes with it: what is left stays a change
     * to retry, against the recipe as it now is on Mealie, [saved].
     */
    private fun savedPartly(error: NetworkError, newSlug: String, saved: EditableRecipe, draft: RecipeDraft) {
        _state.update {
            it.copy(
                saving = false,
                saveError = error,
                committedSlug = newSlug,
                recipe = saved.copy(draft = draft.copy(id = newSlug)),
                draft = it.draft.copy(id = newSlug),
            )
        }
    }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    /** Deletes the recipe from Mealie; what was being edited goes with it. */
    fun delete() {
        if (!_state.value.canDelete) return
        _state.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            when (val result = editRepository.delete(slug)) {
                is ApiResult.Failure -> _state.update { it.copy(deleting = false, deleteError = result.error) }
                is ApiResult.Success -> {
                    recentRecipes.forget(slug)
                    _state.update { it.copy(deleting = false, deleted = true) }
                }
            }
        }
    }

    fun dismissDeleteError() = _state.update { it.copy(deleteError = null) }

    /** A picture framed here but never saved is not kept on the device. */
    override fun onCleared() {
        _state.value.newImagePath?.let(imageFiles::delete)
        _state.value.draft.steps.mapNotNull { it.photoPath }.forEach(imageFiles::delete)
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
                    recentRecipes = container.recentRecipesStore,
                    calorieTags = container.calorieTagRepository,
                    streamFor = container.videoStreams::streamFor,
                )
            }
        }
    }
}
