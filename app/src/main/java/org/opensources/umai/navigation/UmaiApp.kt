package org.opensources.umai.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.cooking.ui.CookingScreen
import org.opensources.umai.home.ui.HomeScreen
import org.opensources.umai.planning.ui.PlanningScreen
import org.opensources.umai.recipe.ui.RecipeDetailScreen
import org.opensources.umai.search.ui.SearchScreen
import org.opensources.umai.settings.ui.SettingsScreen
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

    when (sessionState) {
        SessionState.Loading -> Box(modifier = modifier.fillMaxSize()) { LoadingView() }
        SessionState.NotConfigured, is SessionState.Expired -> SetupScreen(modifier = modifier)
        is SessionState.Active -> MainNavigation(modifier = modifier)
    }
}

@Composable
private fun MainNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val selectedTab = TopLevelTab.entries.firstOrNull { tab ->
        when (tab) {
            TopLevelTab.HOME -> destination?.hasRoute(HomeRoute::class) == true
            TopLevelTab.PLANNING -> destination?.hasRoute(PlanningRoute::class) == true
            TopLevelTab.SHOPPING -> destination?.hasRoute(ShoppingRoute::class) == true
            TopLevelTab.SETTINGS -> destination?.hasRoute(SettingsRoute::class) == true
        }
    }
    val searchSelected = destination?.hasRoute(SearchRoute::class) == true
    // Recipe and cooking are full-screen readers: the tab bar would only steal
    // vertical space and show no tab as selected.
    val isFullScreen = destination?.hasRoute(CookingRoute::class) == true ||
        destination?.hasRoute(RecipeRoute::class) == true

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!isFullScreen) {
                UmaiBottomBar(
                    selected = selectedTab,
                    searchSelected = searchSelected,
                    onSelect = { navController.switchTab(it.route) },
                    onSearch = { navController.switchTab(SearchRoute) },
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
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(160)) },
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

            composable<SettingsRoute> { SettingsScreen() }

            composable<RecipeRoute>(
                enterTransition = {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
                },
                popExitTransition = {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
                },
            ) { entry ->
                val route: RecipeRoute = entry.toRoute()
                RecipeDetailScreen(
                    slug = route.slug,
                    onBack = { navController.popBackStack() },
                    onStartCooking = { navController.navigate(CookingRoute(it)) },
                )
            }

            composable<CookingRoute> { entry ->
                val route: CookingRoute = entry.toRoute()
                CookingScreen(
                    slug = route.slug,
                    onExit = { navController.popBackStack() },
                )
            }
        }
    }
}

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
