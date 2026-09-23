package org.opensources.umai.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable
import org.opensources.umai.R

/** Type-safe routes; Navigation Compose serializes them with kotlinx.serialization. */
@Serializable
data object SetupRoute

@Serializable
data object HomeRoute

@Serializable
data object PlanningRoute

@Serializable
data object SearchRoute

@Serializable
data object ShoppingRoute

@Serializable
data object ProfileRoute

@Serializable
data object AppSettingsRoute

@Serializable
data object MealieSettingsRoute

@Serializable
data object RecipeImportRoute

/** [draftId] resumes an unfinished recipe; `null` starts a new one. */
@Serializable
data class RecipeCreateRoute(val draftId: String? = null)

@Serializable
data object RecipeDraftsRoute

@Serializable
data class RecipeRoute(val slug: String)

/**
 * [servings] carries the number of servings the reader selected on the recipe
 * page, so the cooking mode scales the ingredients the same way. Zero means
 * "use the servings the recipe was written for".
 */
@Serializable
data class CookingRoute(val slug: String, val servings: Int = 0)

/**
 * The four tabs that sit on either side of the central search button.
 *
 * Search is deliberately not one of them: it is the primary action of the bar
 * and gets its own, visually raised control. The profile tab shows the avatar
 * of the signed-in user instead of [icon], which stays as the fallback until
 * the picture is known.
 */
enum class TopLevelTab(
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val route: Any,
) {
    HOME(R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, HomeRoute),
    PLANNING(R.string.nav_planning, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, PlanningRoute),
    SHOPPING(R.string.nav_shopping, Icons.Rounded.ShoppingCart, Icons.Outlined.ShoppingCart, ShoppingRoute),
    PROFILE(R.string.nav_profile, Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle, ProfileRoute);

    companion object {
        /** Left of the search button, then right of it. */
        val leading: List<TopLevelTab> = listOf(HOME, PLANNING)
        val trailing: List<TopLevelTab> = listOf(SHOPPING, PROFILE)
    }
}

/**
 * What the profile tab shows: the picture and the name of the signed-in user.
 * A long name is ellipsized by the bar rather than reshaping it.
 */
data class ProfileTabInfo(
    val displayName: String?,
    val avatarUrl: String?,
    val initials: String,
)
