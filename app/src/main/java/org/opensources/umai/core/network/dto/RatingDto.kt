package org.opensources.umai.core.network.dto

import kotlinx.serialization.Serializable

/** Mirrors `UserRatingSummary`: what one user gave one recipe, stars and favourite on the same row. */
@Serializable
data class UserRatingSummaryDto(
    val recipeId: String = "",
    val rating: Double? = null,
    val isFavorite: Boolean = false,
)

@Serializable
data class UserRatingsDto(
    val ratings: List<UserRatingSummaryDto> = emptyList(),
)

/** Mirrors `UserRatingUpdate`; both fields are always sent. */
@Serializable
data class UserRatingUpdateDto(
    val rating: Double?,
    val isFavorite: Boolean?,
)
