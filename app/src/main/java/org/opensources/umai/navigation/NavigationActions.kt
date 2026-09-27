package org.opensources.umai.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute

/**
 * Switching tabs keeps a single entry per tab on the back stack and restores
 * the scroll position of the tab being returned to.
 */
internal fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * After a recipe has been created the form must not stay on the back stack:
 * going back from the new recipe returns to the profile page.
 */
internal fun NavHostController.openCreatedRecipe(slug: String) {
    navigate(RecipeDestination(slug)) {
        popUpTo(ProfileDestination) { inclusive = false }
    }
}

/**
 * The import replaces itself with the recipe it created: it may have been
 * opened by a page shared from another app, over any screen.
 */
internal fun NavHostController.openImportedRecipe(entry: NavBackStackEntry, slug: String) {
    navigate(RecipeDestination(slug)) {
        popUpTo(entry.destination.id) { inclusive = true }
    }
}

/**
 * A screen that closes itself once its work is done asks for it from an
 * effect, which runs again after a rotation: only the screen on top may pop,
 * so it never closes the one below by mistake.
 */
internal fun NavHostController.isCurrent(entry: NavBackStackEntry): Boolean =
    currentBackStackEntry?.id == entry.id

/** Whether [entry] was on top and is now gone: what follows a close only happens then. */
internal fun NavHostController.popIfCurrent(entry: NavBackStackEntry): Boolean =
    isCurrent(entry) && popBackStack()

/** A back asked for by [entry], which only closes it when it is still on top. */
internal fun NavHostController.closeIfCurrent(entry: NavBackStackEntry) {
    if (isCurrent(entry)) popBackStack()
}

/**
 * Back on the recipe page the editor was opened from, once it closed: that
 * page reloads, or after a rename is replaced by the page at the new address,
 * since the old one no longer exists on Mealie.
 */
internal fun NavHostController.showSavedRecipe(editedSlug: String, savedSlug: String) {
    if (savedSlug == editedSlug) {
        currentBackStackEntry?.savedStateHandle?.set(RECIPE_UPDATED, true)
    } else {
        navigate(RecipeDestination(savedSlug)) {
            popUpTo<RecipeDestination> { inclusive = true }
        }
    }
}

/**
 * Once the editor closed on a deleted recipe, its page, under it, goes too:
 * it would show a recipe that no longer exists.
 */
internal fun NavHostController.leaveDeletedRecipe(slug: String) {
    val below = currentBackStackEntry ?: return
    if (below.destination.hasRoute(RecipeDestination::class) && below.toRoute<RecipeDestination>().slug == slug) popBackStack()
}

/**
 * Opens the cooking mode of a recipe at a step, as a timer asks: it replaces
 * the cooking mode on screen, if any, rather than stacking a second one.
 */
internal fun NavHostController.openCooking(slug: String, servings: Int, step: Int) {
    navigate(CookingDestination(slug, servings, step)) {
        popUpTo<CookingDestination> { inclusive = true }
    }
}

/**
 * Opens the import, where it runs or tells how it ended: it replaces the
 * import on screen, if any, rather than stacking a second one.
 */
internal fun NavHostController.openImport() {
    navigate(RecipeImportDestination()) {
        popUpTo<RecipeImportDestination> { inclusive = true }
    }
}

/** Set on the recipe page's entry when the editor saved changes to it. */
internal const val RECIPE_UPDATED = "recipe_updated"
