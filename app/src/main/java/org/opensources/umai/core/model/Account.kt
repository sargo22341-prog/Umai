package org.opensources.umai.core.model

/** The signed-in Mealie user, as served by `GET /api/users/self`. */
data class UserProfile(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
    val isAdmin: Boolean,
    val groupName: String,
    val householdName: String,
    /** Mealie changes it whenever the profile picture is rewritten. */
    val cacheKey: String,
    /** Whether Mealie lets this user edit the household preferences. */
    val canManageHousehold: Boolean,
) {
    val displayName: String get() = fullName.ifBlank { username }

    /** One or two letters for the fallback avatar. */
    val initials: String
        get() = displayName.split(' ', '-', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
}

/** Counters served by `GET /api/households/statistics`. */
data class HouseholdStatistics(
    val recipes: Int,
    val users: Int,
    val categories: Int,
    val tags: Int,
    val tools: Int,
)

/**
 * Household preferences owned by Mealie.
 *
 * [firstDayOfWeek] keeps Mealie's own numbering — 0 is Sunday, 6 is Saturday,
 * the JavaScript convention its web UI uses — so the value written back is the
 * value the server expects.
 */
data class HouseholdPreferences(
    val firstDayOfWeek: Int,
    val privateHousehold: Boolean,
    val showAnnouncements: Boolean,
    val lockRecipeEditsFromOtherHouseholds: Boolean,
    val recipePublic: Boolean,
    val recipeShowNutrition: Boolean,
    val recipeShowAssets: Boolean,
    val recipeLandscapeView: Boolean,
    val recipeDisableComments: Boolean,
) {
    /** `java.time` numbers Monday 1 … Sunday 7; Mealie numbers Sunday 0 … Saturday 6. */
    val firstDay: java.time.DayOfWeek
        get() = java.time.DayOfWeek.of(if (normalizedFirstDayOfWeek == 0) 7 else normalizedFirstDayOfWeek)

    private val normalizedFirstDayOfWeek: Int get() = ((firstDayOfWeek % 7) + 7) % 7

    companion object {
        /** The reverse mapping, used when the user picks a day in the UI. */
        fun mealieDayNumber(day: java.time.DayOfWeek): Int = day.value % 7
    }
}
