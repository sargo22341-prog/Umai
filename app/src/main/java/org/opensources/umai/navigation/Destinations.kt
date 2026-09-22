package org.opensources.umai.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
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
data object SettingsRoute

@Serializable
data class RecipeRoute(val slug: String)

@Serializable
data class CookingRoute(val slug: String)

/**
 * The four tabs that sit on either side of the central search button.
 *
 * Search is deliberately not one of them: it is the primary action of the bar
 * and gets its own, visually raised control.
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
    SETTINGS(R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, SettingsRoute);

    companion object {
        /** Left of the search button, then right of it. */
        val leading: List<TopLevelTab> = listOf(HOME, PLANNING)
        val trailing: List<TopLevelTab> = listOf(SHOPPING, SETTINGS)
    }
}
