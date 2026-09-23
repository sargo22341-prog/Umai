package org.opensources.umai.recipe.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.CreateRecipeDto
import org.opensources.umai.core.network.dto.IngredientReferenceDto
import org.opensources.umai.core.network.dto.RecipeCategoryDto
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeIngredientDto
import org.opensources.umai.core.network.dto.RecipeStepDto
import org.opensources.umai.core.network.dto.RecipeTagDto
import org.opensources.umai.core.network.dto.ScrapeRecipeDto
import org.opensources.umai.recipe.domain.EditableRecipe
import org.opensources.umai.recipe.domain.RecipeDraft
import org.opensources.umai.recipe.domain.RecipeLinks
import org.opensources.umai.recipe.domain.RecipeMediaFiles

/**
 * Writes recipes on the Mealie instance: creates them by scraping a page or
 * from a draft written in the app, and edits existing ones.
 */
class RecipeEditRepository(
    private val apiProvider: () -> MealieApi?,
    private val media: RecipeMediaRepository = RecipeMediaRepository(apiProvider),
) {

    /**
     * A recipe of the instance imported from the same page as [url], `null`
     * when there is none. The address is looked up loosely, then compared
     * exactly, so `http`, `www.` or a query string make no difference.
     */
    suspend fun findBySource(url: String): ApiResult<RecipeSummary?> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val fragment = RecipeLinks.searchFragment(url) ?: return ApiResult.Success(null)
        return apiCall {
            api.recipes(perPage = DUPLICATE_CANDIDATES, queryFilter = "orgURL LIKE \"%$fragment%\"")
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
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val address = url.trim()
        if (address.isEmpty()) return ApiResult.Failure(NetworkError.InvalidResponse)
        return apiCall {
            api.createRecipeFromUrl(
                ScrapeRecipeDto(
                    url = address,
                    includeTags = includeTags,
                    includeCategories = includeCategories,
                ),
            )
        }.validSlug()
    }

    /**
     * Mealie creates a recipe from a name alone, then accepts the rest through
     * an update. The freshly created recipe is read back first so the payload
     * keeps the identifiers and settings the server chose.
     */
    suspend fun create(draft: RecipeDraft): ApiResult<String> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        if (!draft.canBeCreated) return ApiResult.Failure(NetworkError.InvalidResponse)

        val slug = when (val created = apiCall { api.createRecipe(CreateRecipeDto(draft.name.trim())) }) {
            is ApiResult.Failure -> return created
            is ApiResult.Success -> created.value.trim().trim('"')
                .ifBlank { return ApiResult.Failure(NetworkError.InvalidResponse) }
        }

        val existing = when (val result = apiCall { api.recipe(slug) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }

        return when (val updated = apiCall { api.updateRecipe(slug, existing.merge(draft)) }) {
            is ApiResult.Failure -> updated
            is ApiResult.Success -> ApiResult.Success(updated.value.slug.ifBlank { slug })
        }
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

    /** The recipe as the edit form shows it, with what is needed to show its pictures. */
    suspend fun loadForEdit(slug: String): ApiResult<EditableRecipe> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return when (val result = apiCall { api.recipe(slug) }) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val dto = result.value
                val recipe = dto.toDomain() ?: return ApiResult.Failure(NetworkError.InvalidResponse)
                ApiResult.Success(
                    EditableRecipe(
                        recipeId = recipe.id,
                        imageToken = recipe.summary.imageToken,
                        draft = dto.toEditableDraft(),
                        mediaVersion = recipe.mediaVersion,
                    ),
                )
            }
        }
    }

    /**
     * Saves the changes between [original] and [edited]. The recipe is read
     * again just before, so a field changed elsewhere in the meantime and left
     * alone here is not overwritten. Answers with the slug, which Mealie
     * derives from the name and so changes along with it.
     */
    suspend fun update(slug: String, original: RecipeDraft, edited: RecipeDraft): ApiResult<String> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        if (!edited.canBeCreated) return ApiResult.Failure(NetworkError.InvalidResponse)
        if (edited == original) return ApiResult.Success(slug)

        val document = when (val result = apiCall { api.recipeDocument(slug) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }

        val edits = document.withEdits(original, edited, MealieClientFactory.json)
        // Only photos changed: they are assets, and the document stays as it is.
        if (edits == document) return ApiResult.Success(slug)
        return when (val updated = apiCall { api.replaceRecipe(slug, edits) }) {
            is ApiResult.Failure -> updated
            is ApiResult.Success -> ApiResult.Success(updated.value.slug.ifBlank { slug })
        }
    }

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
            when (val result = media.assetBytes(recipeId, file)) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> EncodedImage(result.value, imageMediaType(file), file.substringAfterLast('.'))
            }
        }
        (copies + newPhotos).forEach { (number, photo) ->
            val result = media.saveStepPhoto(slug, number, photo)
            if (result is ApiResult.Failure) return result
        }
        return media.tidyAssets(slug, keptStepPhotos = plan.kept)
    }

    /** Replaces the picture of the recipe; Mealie builds its smaller sizes itself. */
    suspend fun uploadImage(slug: String, image: EncodedImage): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val part = MultipartBody.Part.createFormData(
            name = "image",
            filename = "recipe.${image.extension}",
            body = image.bytes.toRequestBody(image.mediaType.toMediaTypeOrNull()),
        )
        val extension = image.extension.toRequestBody(TEXT_PLAIN)
        return apiCall { api.updateRecipeImage(slug, part, extension) }.let { result ->
            when (result) {
                is ApiResult.Failure -> result
                is ApiResult.Success -> ApiResult.Success(Unit)
            }
        }
    }

    private fun ApiResult<String>.validSlug(): ApiResult<String> = when (this) {
        is ApiResult.Failure -> this
        is ApiResult.Success -> value.trim().trim('"')
            .takeIf { it.isNotBlank() }
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(NetworkError.InvalidResponse)
    }
}

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

private fun imageMediaType(fileName: String): String =
    when (val extension = fileName.substringAfterLast('.').lowercase()) {
        "jpg" -> "image/jpeg"
        else -> "image/$extension"
    }

private val TEXT_PLAIN = "text/plain".toMediaType()

/** Enough to recognise the recipe among the few that share its address fragment. */
private const val DUPLICATE_CANDIDATES = 20

/**
 * Copies what the user wrote onto the recipe Mealie just created, leaving every
 * other field of the server payload untouched.
 *
 * Times are free text on the Mealie side; `performTime` is the field its own
 * web UI labels "cook time", so that is where the cooking time goes.
 */
private fun RecipeDetailDto.merge(draft: RecipeDraft): RecipeDetailDto {
    val written = draft.ingredients.filter { it.text.isNotBlank() }
    val known = written.map { it.referenceId }.toSet()
    return copy(
        name = draft.name.trim(),
        description = draft.description.trim(),
        recipeServings = draft.servings.coerceAtLeast(0).toDouble(),
        prepTime = draft.prepTime.trim().ifBlank { null },
        performTime = draft.cookTime.trim().ifBlank { null },
        totalTime = draft.totalTime.trim().ifBlank { null },
        recipeIngredient = written.map { line ->
            val text = line.text.trim()
            RecipeIngredientDto(note = text, display = text, originalText = text, referenceId = line.referenceId)
        },
        recipeInstructions = draft.writtenSteps.map { step ->
            RecipeStepDto(
                title = step.title.trim(),
                text = step.text.trim(),
                ingredientReferences = step.ingredientReferences.filter { it in known }.distinct()
                    .map { IngredientReferenceDto(it) },
            )
        },
        categories = draft.categories.map { RecipeCategoryDto(id = it.id, name = it.name, slug = it.slug) },
        tags = draft.tags.map { RecipeTagDto(id = it.id, name = it.name, slug = it.slug) },
    )
}
