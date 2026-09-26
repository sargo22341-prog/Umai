package org.opensources.umai.recipe.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody.Companion.toRequestBody
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.MediaTypes
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.dto.CreateRecipeDto
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.ScrapeRecipeDto
import org.opensources.umai.core.network.formPart
import org.opensources.umai.core.network.map
import org.opensources.umai.core.network.orInvalid
import org.opensources.umai.core.network.valueOr
import org.opensources.umai.recipe.domain.EditableRecipe
import org.opensources.umai.recipe.domain.RecipeDraft
import org.opensources.umai.recipe.domain.RecipeLinks
import org.opensources.umai.recipe.domain.RecipeMediaFiles
import org.opensources.umai.recipe.domain.VideoChapters

/**
 * Writes recipes on the Mealie instance: creates them by scraping a page or
 * from a draft written in the app, and edits existing ones.
 */
class RecipeEditRepository(
    private val apiProvider: () -> MealieApi?,
    private val media: RecipeMediaRepository = RecipeMediaRepository(apiProvider),
) {

    private val _deletedRecipes = MutableSharedFlow<String>(extraBufferCapacity = DELETION_BUFFER)

    /**
     * The slugs of the recipes deleted from the app, as they are deleted: the
     * lists already on screen drop them without reading the instance again.
     */
    val deletedRecipes: SharedFlow<String> = _deletedRecipes.asSharedFlow()

    /**
     * A recipe of the instance imported from the same page as [url], `null`
     * when there is none. The address is looked up loosely, then compared
     * exactly, so `http`, `www.` or a query string make no difference.
     */
    suspend fun findBySource(url: String): ApiResult<RecipeSummary?> {
        val fragment = RecipeLinks.searchFragment(url) ?: return ApiResult.Success(null)
        return apiProvider.call {
            recipes(perPage = DUPLICATE_CANDIDATES, queryFilter = "orgURL LIKE \"%$fragment%\"")
                .items
                .mapNotNull { it.toDomain() }
                .firstOrNull { candidate -> candidate.sourceUrl?.let { RecipeLinks.sameSource(it, url) } == true }
        }
    }

    suspend fun importFromUrl(
        url: String,
        includeTags: Boolean,
        includeCategories: Boolean,
    ): ApiResult<String> {
        val address = url.trim()
        require(address.isNotEmpty()) { "An import needs the address of a page" }
        return apiProvider.call {
            createRecipeFromUrl(ScrapeRecipeDto(url = address, includeTags = includeTags, includeCategories = includeCategories))
        }.asSlug()
    }

    /**
     * Mealie creates a recipe from a name alone, then accepts the rest through
     * an update — the same one as an edit, so every field Mealie chose for the
     * new recipe is kept. The example ingredient and step Mealie writes into
     * it count as changed by the draft, which replaces them.
     */
    suspend fun create(draft: RecipeDraft): ApiResult<String> {
        require(draft.canBeCreated) { "A recipe is created with a name" }
        val slug = apiProvider.call { createRecipe(CreateRecipeDto(draft.name.trim())) }.asSlug().valueOr { return it }
        return write(slug) { document -> document.withEdits(document.toDetail().toEditableDraft(), draft, json) }
    }

    /**
     * Creates the recipe, then uploads its picture and the photos of its steps,
     * keyed by step number. A picture Mealie refuses does not undo the recipe —
     * it exists by then — and is reported instead, so the user can add it again
     * from the editor.
     */
    suspend fun create(
        draft: RecipeDraft,
        image: EncodedImage?,
        stepPhotos: Map<Int, EncodedImage> = emptyMap(),
    ): ApiResult<CreatedRecipe> =
        when (val created = create(draft)) {
            is ApiResult.Failure -> created
            is ApiResult.Success -> {
                val slug = created.value
                val imageSaved = image == null || uploadImage(slug, image) is ApiResult.Success
                val photosSaved = stepPhotos.map { (number, photo) ->
                    media.saveStepPhoto(slug, number, photo) is ApiResult.Success
                }.all { it }
                ApiResult.Success(CreatedRecipe(slug = slug, imageSaved = imageSaved && photosSaved))
            }
        }

    /**
     * The recipe as the edit form shows it, with what is needed to show its
     * pictures, and its video with the chapters of its steps. A chapters file
     * that cannot be read fails the whole load: saving without it would
     * write the chapters again from nothing.
     */
    suspend fun loadForEdit(slug: String): ApiResult<EditableRecipe> {
        val dto = apiProvider.call { recipe(slug) }.valueOr { return it }
        val recipe = dto.toDomain() ?: return ApiResult.Failure(NetworkError.InvalidResponse)
        val video = media.videoManifest(recipe.id, recipe.assets).valueOr { return it }
        return ApiResult.Success(
            EditableRecipe(
                recipeId = recipe.id,
                imageToken = recipe.summary.imageToken,
                draft = VideoChapters.withVideo(dto.toEditableDraft(), video, recipe.summary.sourceUrl),
                mediaVersion = recipe.mediaVersion,
                video = video,
                videoFile = RecipeMediaFiles.chaptersFile(recipe.assets),
            ),
        )
    }

    /**
     * Writes the chapters of [edited] when they differ from the file on
     * Mealie — placed in the editor, or numbered again because steps were
     * removed. Answers with [recipe] as it now is on Mealie.
     */
    suspend fun saveVideoChapters(slug: String, recipe: EditableRecipe, edited: RecipeDraft): ApiResult<EditableRecipe> {
        if (!VideoChapters.changed(edited, recipe.video)) return ApiResult.Success(recipe)
        val manifest = VideoChapters.manifest(edited, recipe.video) ?: return ApiResult.Success(recipe)
        return media.saveVideoManifest(slug, manifest, recipe.videoFile)
            .map { file -> recipe.copy(video = manifest, videoFile = file) }
    }

    /**
     * Saves the changes between [original] and [edited]. The recipe is read
     * again just before, so a field changed elsewhere in the meantime and left
     * alone here is not overwritten. Answers with the slug, which Mealie
     * derives from the name and so changes along with it.
     */
    suspend fun update(slug: String, original: RecipeDraft, edited: RecipeDraft): ApiResult<String> {
        require(edited.canBeCreated) { "A recipe keeps a name" }
        if (edited == original) return ApiResult.Success(slug)
        return write(slug) { document -> document.withEdits(original, edited, json) }
    }

    /**
     * Reads the recipe document, has [edit] change it, and sends it back whole.
     * Answers with the slug, which Mealie derives from the name.
     */
    private suspend fun write(slug: String, edit: (JsonObject) -> JsonObject): ApiResult<String> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val document = apiCall { api.recipeDocument(slug) }.valueOr { return it }
        val edited = edit(document)
        // Only photos changed: they are assets, and the document stays as it is.
        if (edited == document) return ApiResult.Success(slug)
        return apiCall { api.replaceRecipe(slug, edited) }.map { it.slug.ifBlank { slug } }
    }

    private fun JsonObject.toDetail(): RecipeDetailDto = json.decodeFromJsonElement(RecipeDetailDto.serializer(), this)

    /**
     * Brings the step photos on Mealie in line with the edited steps.
     *
     * Photos are named after the position of their step, so removing a step
     * moves the photos of the following ones: those are read first, then
     * stored under their new number, and the entries of photos no step uses
     * any more are dropped from the recipe. [newPhotos] are the pictures framed
     * on the device, keyed by step number.
     */
    suspend fun saveStepPhotos(
        slug: String,
        recipeId: String,
        original: RecipeDraft,
        edited: RecipeDraft,
        newPhotos: Map<Int, EncodedImage>,
    ): ApiResult<Unit> {
        val plan = StepPhotoPlan.of(original, edited, newPhotos.mapValues { it.value.extension })
        if (plan.isEmpty) return ApiResult.Success(Unit)

        // Every moved photo is read before anything is written over it.
        val copies = plan.moves.mapValues { (_, file) ->
            val bytes = media.assetBytes(recipeId, file).valueOr { return it }
            EncodedImage(bytes, EncodedImage.mediaTypeOf(file), file.substringAfterLast('.'))
        }
        (copies + newPhotos).forEach { (number, photo) ->
            val result = media.saveStepPhoto(slug, number, photo)
            if (result is ApiResult.Failure) return result
        }
        return media.tidyAssets(slug, keptStepPhotos = plan.kept)
    }

    /** Replaces the picture of the recipe; Mealie builds its smaller sizes itself. */
    suspend fun uploadImage(slug: String, image: EncodedImage): ApiResult<Unit> = apiProvider.call {
        updateRecipeImage(slug, image.formPart("image", fileName = "recipe"), image.extension.toRequestBody(MediaTypes.TextPlain))
    }.map { }

    /** Deletes the recipe from the instance, for every user of it. */
    suspend fun delete(slug: String): ApiResult<Unit> =
        apiProvider.call { deleteRecipe(slug) }.also { result ->
            if (result is ApiResult.Success) _deletedRecipes.emit(slug)
        }

    private companion object {
        val json = MealieClientFactory.json
    }
}

/** Mealie answers the creation of a recipe with its slug, as a JSON string. */
internal fun ApiResult<String>.asSlug(): ApiResult<String> =
    map { answer -> answer.trim().trim('"').takeIf { it.isNotBlank() } }.orInvalid()

/** A recipe just created; [imageSaved] is false when Mealie refused one of its pictures. */
data class CreatedRecipe(val slug: String, val imageSaved: Boolean)

/**
 * What saving the steps of an edited recipe does to their photos, which are
 * named after the step number.
 *
 * [moves] are photos already on Mealie that now belong to another number, keyed
 * by that number; [kept] is every step photo file the recipe keeps.
 */
internal data class StepPhotoPlan(
    val moves: Map<Int, String>,
    val kept: Set<String>,
    val isEmpty: Boolean,
) {
    companion object {
        /** [newPhotos] gives the extension of each photo framed on the device, by step number. */
        fun of(original: RecipeDraft, edited: RecipeDraft, newPhotos: Map<Int, String>): StepPhotoPlan {
            val stored = original.steps.mapNotNull { it.photoFile }.toSet()
            val moves = mutableMapOf<Int, String>()
            val kept = mutableSetOf<String>()
            edited.writtenSteps.forEachIndexed { index, step ->
                val number = index + 1
                val newExtension = newPhotos[number]
                val file = step.photoFile
                when {
                    newExtension != null -> kept += fileName(number, newExtension)
                    file != null -> {
                        if (RecipeMediaFiles.stepNumberOf(file) != number) moves[number] = file
                        kept += fileName(number, file.substringAfterLast('.'))
                    }
                }
            }
            return StepPhotoPlan(moves, kept, isEmpty = newPhotos.isEmpty() && moves.isEmpty() && kept == stored)
        }

        private fun fileName(number: Int, extension: String) =
            "${RecipeMediaFiles.stepPhotoName(number)}.${extension.lowercase()}"
    }
}

/** Enough to recognise the recipe among the few that share its address fragment. */
private const val DUPLICATE_CANDIDATES = 20

/** Lets a deletion be announced without waiting for a list busy with something else. */
private const val DELETION_BUFFER = 8
