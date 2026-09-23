package org.opensources.umai.recipe.data

import org.opensources.umai.core.network.ApiResult
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
import org.opensources.umai.recipe.domain.RecipeDraft

/**
 * Creates recipes on the Mealie instance, either by scraping a page or from a
 * draft written in the app. Both answer with the slug of the new recipe.
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

    private fun ApiResult<String>.validSlug(): ApiResult<String> = when (this) {
        is ApiResult.Failure -> this
        is ApiResult.Success -> value.trim().trim('"')
            .takeIf { it.isNotBlank() }
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Failure(NetworkError.InvalidResponse)
    }
}

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
