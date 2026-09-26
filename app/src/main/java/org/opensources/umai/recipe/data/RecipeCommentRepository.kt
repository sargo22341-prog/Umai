package org.opensources.umai.recipe.data

import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.dto.RecipeCommentCreateDto
import org.opensources.umai.core.network.map
import org.opensources.umai.core.network.orInvalid

/**
 * Comments on a recipe, backed by `/api/recipes/{slug}/comments` for reading
 * and `/api/comments` for writing.
 *
 * Mealie decides who may delete a comment: its author, or an administrator.
 * Umai only mirrors that rule in the UI; the server enforces it.
 */
class RecipeCommentRepository(private val apiProvider: () -> MealieApi?) {

    /** Oldest first: the conversation reads top to bottom, towards the field. */
    suspend fun comments(slug: String): ApiResult<List<RecipeComment>> = apiProvider.call {
        recipeComments(slug).mapNotNull { it.toDomain() }.sortedBy { it.createdAt }
    }

    suspend fun add(recipeId: String, text: String): ApiResult<RecipeComment> {
        val body = text.trim()
        require(body.isNotEmpty()) { "A comment has some text" }
        return apiProvider.call { createComment(RecipeCommentCreateDto(recipeId = recipeId, text = body)) }
            .map { it.toDomain() }
            .orInvalid()
    }

    suspend fun delete(commentId: String): ApiResult<Unit> = apiProvider.call { deleteComment(commentId) }
}
