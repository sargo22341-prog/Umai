package org.opensources.umai.recipe.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.CreateRecipeDto
import org.opensources.umai.core.network.dto.RecipeCategoryDto
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeIngredientDto
import org.opensources.umai.core.network.dto.RecipeStepDto
import org.opensources.umai.core.network.dto.RecipeTagDto
import org.opensources.umai.core.network.dto.ScrapeRecipeDto
import org.opensources.umai.recipe.domain.EditableRecipe
import org.opensources.umai.recipe.domain.RecipeDraft

/**
 * Writes recipes on the Mealie instance: creates them by scraping a page or
 * from a draft written in the app, and edits existing ones.
 */
class RecipeEditRepository(private val apiProvider: () -> MealieApi?) {

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
     * Creates the recipe, then uploads its picture. A picture Mealie refuses
     * does not undo the recipe — it exists by then — and is reported instead,
     * so the user can add it again from the editor.
     */
    suspend fun create(draft: RecipeDraft, image: EncodedImage?): ApiResult<CreatedRecipe> =
        when (val created = create(draft)) {
            is ApiResult.Failure -> created
            is ApiResult.Success -> {
                val slug = created.value
                val imageSaved = image == null || uploadImage(slug, image) is ApiResult.Success
                ApiResult.Success(CreatedRecipe(slug = slug, imageSaved = imageSaved))
            }
        }

    /** The recipe as the edit form shows it, with what is needed to show its picture. */
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

        return when (val updated = apiCall { api.replaceRecipe(slug, document.withEdits(original, edited, MealieClientFactory.json)) }) {
            is ApiResult.Failure -> updated
            is ApiResult.Success -> ApiResult.Success(updated.value.slug.ifBlank { slug })
        }
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

/** A recipe just created; [imageSaved] is false when Mealie refused its picture. */
data class CreatedRecipe(val slug: String, val imageSaved: Boolean)

private val TEXT_PLAIN = "text/plain".toMediaType()

/**
 * Copies what the user wrote onto the recipe Mealie just created, leaving every
 * other field of the server payload untouched.
 *
 * Times are free text on the Mealie side; `performTime` is the field its own
 * web UI labels "cook time", so that is where the cooking time goes.
 */
private fun RecipeDetailDto.merge(draft: RecipeDraft): RecipeDetailDto = copy(
    name = draft.name.trim(),
    description = draft.description.trim(),
    recipeServings = draft.servings.coerceAtLeast(0).toDouble(),
    prepTime = draft.prepTime.trim().ifBlank { null },
    performTime = draft.cookTime.trim().ifBlank { null },
    totalTime = draft.totalTime.trim().ifBlank { null },
    recipeIngredient = draft.ingredients
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line -> RecipeIngredientDto(note = line, display = line, originalText = line) },
    recipeInstructions = draft.steps
        .filter { it.text.isNotBlank() || it.title.isNotBlank() }
        .map { step -> RecipeStepDto(title = step.title.trim(), text = step.text.trim()) },
    categories = draft.categories.map { RecipeCategoryDto(id = it.id, name = it.name, slug = it.slug) },
    tags = draft.tags.map { RecipeTagDto(id = it.id, name = it.name, slug = it.slug) },
)
