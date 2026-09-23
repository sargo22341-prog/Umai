package org.opensources.umai.recipe.data

import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.RecipeCommentCreateDto

/**
 * Comments on a recipe, backed by `/api/recipes/{slug}/comments` for reading
 * and `/api/comments` for writing.
 *
 * Mealie decides who may delete a comment: its author, or an administrator.
 * Umai only mirrors that rule in the UI; the server enforces it.
 */
class RecipeCommentRepository(private val apiProvider: () -> MealieApi?) {

    suspend fun comments(slug: String): ApiResult<List<RecipeComment>> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall {
            api.recipeComments(slug)
                .mapNotNull { it.toDomain() }
                .sortedByDescending { it.createdAt }
        }
    }

    suspend fun add(recipeId: String, text: String): ApiResult<RecipeComment> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val body = text.trim()
        if (body.isEmpty()) return ApiResult.Failure(NetworkError.InvalidResponse)
        return when (
            val result = apiCall {
                api.createComment(RecipeCommentCreateDto(recipeId = recipeId, text = body))
            }
        ) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.value.toDomain()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(NetworkError.InvalidResponse)
        }
    }

    suspend fun delete(commentId: String): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.deleteComment(commentId) }
    }
}
