package org.opensources.umai.profile.data

import org.opensources.umai.core.model.HouseholdPreferences
import org.opensources.umai.core.model.HouseholdStatistics
import org.opensources.umai.core.model.UserProfile
import org.opensources.umai.core.network.dto.HouseholdPreferencesDto
import org.opensources.umai.core.network.dto.HouseholdStatisticsDto
import org.opensources.umai.core.network.dto.UpdateHouseholdPreferencesDto
import org.opensources.umai.core.network.dto.UserDto

fun UserDto.toDomain(): UserProfile? {
    val identifier = id.takeIf { it.isNotBlank() } ?: return null
    return UserProfile(
        id = identifier,
        username = username.orEmpty(),
        fullName = fullName.orEmpty(),
        email = email,
        isAdmin = admin,
        groupName = group,
        householdName = household,
        cacheKey = cacheKey,
        // An administrator can always manage the household in Mealie, even when
        // the per-user flag was never set on their account.
        canManageHousehold = canManageHousehold || admin,
    )
}

fun HouseholdStatisticsDto.toDomain() = HouseholdStatistics(
    recipes = totalRecipes,
    users = totalUsers,
    categories = totalCategories,
    tags = totalTags,
    tools = totalTools,
)

fun HouseholdPreferencesDto.toDomain() = HouseholdPreferences(
    firstDayOfWeek = firstDayOfWeek,
    privateHousehold = privateHousehold,
    showAnnouncements = showAnnouncements,
    lockRecipeEditsFromOtherHouseholds = lockRecipeEditsFromOtherHouseholds,
    recipePublic = recipePublic,
    recipeShowNutrition = recipeShowNutrition,
    recipeShowAssets = recipeShowAssets,
    recipeLandscapeView = recipeLandscapeView,
    recipeDisableComments = recipeDisableComments,
)

/**
 * `PUT /api/households/preferences` replaces the whole object, so every field
 * is sent back even when a single one changed.
 */
fun HouseholdPreferences.toUpdateDto() = UpdateHouseholdPreferencesDto(
    privateHousehold = privateHousehold,
    showAnnouncements = showAnnouncements,
    lockRecipeEditsFromOtherHouseholds = lockRecipeEditsFromOtherHouseholds,
    firstDayOfWeek = firstDayOfWeek,
    recipePublic = recipePublic,
    recipeShowNutrition = recipeShowNutrition,
    recipeShowAssets = recipeShowAssets,
    recipeLandscapeView = recipeLandscapeView,
    recipeDisableComments = recipeDisableComments,
)
