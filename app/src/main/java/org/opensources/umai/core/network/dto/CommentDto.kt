package org.opensources.umai.core.network.dto

import kotlinx.serialization.Serializable

/** Mirrors `RecipeCommentOut` from the Mealie OpenAPI schema. */
@Serializable
data class RecipeCommentDto(
    val id: String = "",
    val recipeId: String = "",
    val text: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val userId: String = "",
    val user: CommentUserDto? = null,
)

@Serializable
data class CommentUserDto(
    val id: String = "",
    val username: String? = null,
    val fullName: String? = null,
    val admin: Boolean = false,
)

@Serializable
data class RecipeCommentCreateDto(
    val recipeId: String,
    val text: String,
)
