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
    val canInvite: Boolean = false,
    val canManage: Boolean = false,
    val canManageHousehold: Boolean = false,
    val canOrganize: Boolean = false,
    /** Changes whenever Mealie rewrites the profile picture; used to bust caches. */
    val cacheKey: String = "",
)

/** Payload of `PUT /api/users/{item_id}`; only the editable identity fields. */
@Serializable
data class UserUpdateDto(
    val id: String,
    val username: String?,
    val fullName: String?,
    val email: String,
    val admin: Boolean,
    val group: String?,
    val household: String?,
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

/**
 * Mirrors `ReadHouseholdPreferences`. Every field of `UpdateHouseholdPreferences`
 * is kept, because `PUT /api/households/preferences` replaces the whole object:
 * a field left out would be reset to its server-side default.
 */
@Serializable
data class HouseholdPreferencesDto(
    val id: String? = null,
    val privateHousehold: Boolean = true,
    val showAnnouncements: Boolean = true,
    val lockRecipeEditsFromOtherHouseholds: Boolean = true,
    /** Mealie numbers the days the JavaScript way: 0 = Sunday … 6 = Saturday. */
    val firstDayOfWeek: Int = 0,
    val recipePublic: Boolean = true,
    val recipeShowNutrition: Boolean = false,
    val recipeShowAssets: Boolean = false,
    val recipeLandscapeView: Boolean = false,
    val recipeDisableComments: Boolean = false,
)

@Serializable
data class UpdateHouseholdPreferencesDto(
    val privateHousehold: Boolean,
    val showAnnouncements: Boolean,
    val lockRecipeEditsFromOtherHouseholds: Boolean,
    val firstDayOfWeek: Int,
    val recipePublic: Boolean,
    val recipeShowNutrition: Boolean,
    val recipeShowAssets: Boolean,
    val recipeLandscapeView: Boolean,
    val recipeDisableComments: Boolean,
)

@Serializable
data class HouseholdStatisticsDto(
    val totalRecipes: Int = 0,
    val totalUsers: Int = 0,
    val totalCategories: Int = 0,
    val totalTags: Int = 0,
    val totalTools: Int = 0,
)
