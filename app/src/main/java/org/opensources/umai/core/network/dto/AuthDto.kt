package org.opensources.umai.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)

@Serializable
data class AppInfoDto(
    val production: Boolean = true,
    val version: String = "",
    val demoStatus: Boolean = false,
    val allowSignup: Boolean = false,
    val allowPasswordLogin: Boolean = true,
    val enableOidc: Boolean = false,
    val oidcProviderName: String = "",
    val defaultGroupSlug: String? = null,
    val defaultHouseholdSlug: String? = null,
)

@Serializable
data class UserDto(
    val id: String = "",
    val username: String? = null,
    val fullName: String? = null,
    val email: String = "",
    val admin: Boolean = false,
    val group: String = "",
    val household: String = "",
    val groupId: String = "",
    val groupSlug: String = "",
    val householdId: String = "",
    val householdSlug: String = "",
)

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

@Serializable
data class HouseholdPreferencesDto(
    val firstDayOfWeek: Int = 0,
    val recipeShowNutrition: Boolean = false,
    val recipeShowAssets: Boolean = false,
    val recipePublic: Boolean = true,
    val recipeDisableComments: Boolean = false,
    val privateHousehold: Boolean = true,
)
