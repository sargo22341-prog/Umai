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
import org.opensources.umai.search.domain.OrganizerEntry
import org.opensources.umai.search.domain.OrganizerKind
import org.opensources.umai.search.domain.RecipeFilters
import kotlin.reflect.KClass

/** Type-safe routes; Navigation Compose serializes them with kotlinx.serialization. */
@Serializable
data object HomeDestination

@Serializable
data object PlanningDestination

@Serializable
data object SearchDestination

/**
 * A search opened on one category, tag or tool of a recipe; [kind] is an
 * [OrganizerKind] name.
 *
 * It is a destination of its own rather than arguments of [SearchDestination]: the
 * search tab keeps its saved state, and the two never restore each other.
 */
@Serializable
data class OrganizerSearchDestination(val kind: String, val id: String) {

    val filters: RecipeFilters
        get() = OrganizerKind.entries.firstOrNull { it.name == kind }
            ?.let { RecipeFilters.forOrganizer(it, id) }
            ?: RecipeFilters.None

    companion object {
        fun of(entry: OrganizerEntry) = OrganizerSearchDestination(entry.kind.name, entry.organizer.id)
    }
}

/**
 * Picks a recipe for one meal of the plan: [date] is ISO-8601 and [mealType]
 * a Mealie entry type. [fieldOriginY] is where, on screen, the centre of the
 * search field the picker was opened from stood, so its own field starts there.
 */
@Serializable
data class PlanRecipePickerDestination(val date: String, val mealType: String, val fieldOriginY: Float)

/** Adds a product eaten on [date], ISO-8601, to the plan, with its nutrition. */
@Serializable
data class PlanFoodDestination(val date: String)

@Serializable
data object ShoppingDestination

/** The in-store view of one shopping list: big rows, one tap per item. */
@Serializable
data class ShoppingModeDestination(val listId: String)

@Serializable
data object ProfileDestination

@Serializable
data object AppSettingsDestination

@Serializable
data object MealieSettingsDestination

/** [url] fills the address in, as when a page is shared to the app. */
@Serializable
data class RecipeImportDestination(val url: String? = null)

@Serializable
data object ProvidersDestination

@Serializable
data object LocalAiSettingsDestination

/** The course the automatic planning sees in each category and tag. */
@Serializable
data object DishCoursesDestination

@Serializable
data class ProviderDestination(val id: String)

/** [draftId] resumes an unfinished recipe; `null` starts a new one. */
@Serializable
data class RecipeCreateDestination(val draftId: String? = null)

@Serializable
data object RecipeDraftsDestination

@Serializable
data class RecipeDestination(val slug: String)

@Serializable
data class RecipeEditDestination(val slug: String)

/**
 * [servings] carries the number of servings the reader selected on the recipe
 * page, so the cooking mode scales the ingredients the same way. Zero means
 * "use the servings the recipe was written for". [step] is the step to open
 * on: the one a timer was started from, when the cooking mode is reopened by it.
 */
@Serializable
data class CookingDestination(val slug: String, val servings: Int = 0, val step: Int = 0)

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
    HOME(R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, HomeDestination),
    PLANNING(R.string.nav_planning, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, PlanningDestination),
    SHOPPING(R.string.nav_shopping, Icons.Rounded.ShoppingCart, Icons.Outlined.ShoppingCart, ShoppingDestination),
    PROFILE(R.string.nav_profile, Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle, ProfileDestination);

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

/**
 * The destinations that own the whole screen, without the tab bar: a new
 * reader or form is added here.
 */
val FullScreenDestinations: List<KClass<*>> = listOf(
    RecipeDestination::class,
    CookingDestination::class,
    AppSettingsDestination::class,
    MealieSettingsDestination::class,
    RecipeImportDestination::class,
    ProvidersDestination::class,
    ProviderDestination::class,
    RecipeCreateDestination::class,
    RecipeEditDestination::class,
    RecipeDraftsDestination::class,
    PlanRecipePickerDestination::class,
    PlanFoodDestination::class,
    ShoppingModeDestination::class,
)
