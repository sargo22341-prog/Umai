package org.opensources.umai.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.session.ServerSession
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.cooking.ui.CookingScreen
import org.opensources.umai.home.ui.HomeScreen
import org.opensources.umai.planning.ui.PlanningScreen
import org.opensources.umai.profile.ui.ProfileScreen
import org.opensources.umai.recipe.ui.RecipeCreateScreen
import org.opensources.umai.recipe.ui.RecipeDetailScreen
import org.opensources.umai.recipe.ui.RecipeDraftsScreen
import org.opensources.umai.recipe.ui.RecipeImportScreen
import org.opensources.umai.search.ui.SearchScreen
import org.opensources.umai.settings.ui.AppSettingsScreen
import org.opensources.umai.settings.ui.MealieSettingsScreen
import org.opensources.umai.setup.ui.SetupScreen
import org.opensources.umai.shopping.ui.ShoppingScreen

/**
 * Root of the UI. Until an instance is configured (or after its token has been
 * refused) the setup screen replaces the whole navigation graph, so the user
 * never reaches an empty Home.
 */
@Composable
fun UmaiApp(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Loading -> Box(modifier = modifier.fillMaxSize()) { LoadingView() }
        SessionState.NotConfigured, is SessionState.Expired -> SetupScreen(modifier = modifier)
        is SessionState.Active -> MainNavigation(session = state.session, modifier = modifier)
    }
}

@Composable
private fun MainNavigation(session: ServerSession, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val selectedTab = TopLevelTab.entries.firstOrNull { it.matches(destination) }
    val searchSelected = destination?.hasRoute(SearchRoute::class) == true

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!destination.isFullScreen()) {
                UmaiBottomBar(
                    selected = selectedTab,
                    searchSelected = searchSelected,
                    onSelect = { navController.switchTab(it.route) },
                    onSearch = { navController.switchTab(SearchRoute) },
                    profile = container.profileTabInfo(session),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            enterTransition = NavTransitions.enter,
            exitTransition = NavTransitions.exit,
            popEnterTransition = NavTransitions.popEnter,
            popExitTransition = NavTransitions.popExit,
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onRecipeClick = { navController.navigate(RecipeRoute(it)) },
                    onSearchClick = { navController.switchTab(SearchRoute) },
                )
            }

            composable<SearchRoute> {
                SearchScreen(onRecipeClick = { navController.navigate(RecipeRoute(it)) })
            }

            composable<PlanningRoute> {
                PlanningScreen(onRecipeClick = { navController.navigate(RecipeRoute(it)) })
            }

            composable<ShoppingRoute> { ShoppingScreen() }

            composable<ProfileRoute> {
                ProfileScreen(
                    onOpenAppSettings = { navController.navigate(AppSettingsRoute) },
                    onOpenMealieSettings = { navController.navigate(MealieSettingsRoute) },
                    onImportRecipe = { navController.navigate(RecipeImportRoute) },
                    onCreateRecipe = { navController.navigate(RecipeCreateRoute()) },
                    onOpenDrafts = { navController.navigate(RecipeDraftsRoute) },
                )
            }

            composable<AppSettingsRoute> {
                AppSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable<MealieSettingsRoute> {
                MealieSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable<RecipeImportRoute> {
                RecipeImportScreen(
                    onBack = { navController.popBackStack() },
                    onImported = { navController.openCreatedRecipe(it) },
                )
            }

            composable<RecipeCreateRoute> { entry ->
                val route: RecipeCreateRoute = entry.toRoute()
                RecipeCreateScreen(
                    draftId = route.draftId,
                    onBack = { navController.popBackStack() },
                    onCreated = { navController.openCreatedRecipe(it) },
                )
            }

            composable<RecipeDraftsRoute> {
                RecipeDraftsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDraft = { navController.navigate(RecipeCreateRoute(it)) },
                )
            }

            composable<RecipeRoute> { entry ->
                val route: RecipeRoute = entry.toRoute()
                RecipeDetailScreen(
                    slug = route.slug,
                    onBack = { navController.popBackStack() },
                    onStartCooking = { slug, servings ->
                        navController.navigate(CookingRoute(slug, servings))
                    },
                )
            }

            composable<CookingRoute> { entry ->
                val route: CookingRoute = entry.toRoute()
                CookingScreen(
                    slug = route.slug,
                    servings = route.servings,
                    onExit = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun TopLevelTab.matches(destination: NavDestination?): Boolean = when (this) {
    TopLevelTab.HOME -> destination?.hasRoute(HomeRoute::class) == true
    TopLevelTab.PLANNING -> destination?.hasRoute(PlanningRoute::class) == true
    TopLevelTab.SHOPPING -> destination?.hasRoute(ShoppingRoute::class) == true
    TopLevelTab.PROFILE -> destination?.hasRoute(ProfileRoute::class) == true
}

/**
 * Readers and forms own the whole screen: the tab bar would only steal vertical
 * space and show no tab as selected.
 */
private fun NavDestination?.isFullScreen(): Boolean = this != null && (
    hasRoute(RecipeRoute::class) ||
        hasRoute(CookingRoute::class) ||
        hasRoute(AppSettingsRoute::class) ||
        hasRoute(MealieSettingsRoute::class) ||
        hasRoute(RecipeImportRoute::class) ||
        hasRoute(RecipeCreateRoute::class) ||
        hasRoute(RecipeDraftsRoute::class)
    )

/**
 * Switching tabs keeps a single entry per tab on the back stack and restores
 * the scroll position of the tab being returned to.
 */
private fun NavHostController.switchTab(route: Any) {
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
private fun NavHostController.openCreatedRecipe(slug: String) {
    navigate(RecipeRoute(slug)) {
        popUpTo(ProfileRoute) { inclusive = false }
    }
}

private fun AppContainer.profileTabInfo(session: ServerSession): ProfileTabInfo {
    val name = session.userDisplayName?.takeIf { it.isNotBlank() }
        ?: session.username?.takeIf { it.isNotBlank() }
    return ProfileTabInfo(
        displayName = name,
        avatarUrl = session.userId?.let { imageUrls.userAvatar(it, session.avatarCacheKey) },
        initials = name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
    )
}
