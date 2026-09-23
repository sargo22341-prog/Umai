package org.opensources.umai.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.opensources.umai.core.ui.component.NoticePill
import org.opensources.umai.recipe.ui.RecipeEditScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
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

    var notice by remember { mutableStateOf<AppNotice?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MILLIS)
            notice = null
        }
    }

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
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                // Painted in the app theme: the window behind follows the system theme, and pixel
                // rounding may leave a hairline between the two sliding screens.
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                enterTransition = { screenEnter() },
                exitTransition = { screenExit() },
                popEnterTransition = { screenPopEnter() },
                popExitTransition = { screenPopExit() },
                // The back gesture has its own transitions (a fade and shrink by default): same slide.
                predictivePopEnterTransition = { screenPopEnter() },
                predictivePopExitTransition = { screenPopExit() },
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
                        onLeft = { draftSaved ->
                            if (navController.popIfCurrent(entry) && draftSaved) {
                                notice = AppNotice.DRAFT_SAVED
                            }
                        },
                        onCreated = { slug, imageSaved ->
                            if (navController.isCurrent(entry)) {
                                navController.openCreatedRecipe(slug)
                                if (!imageSaved) notice = AppNotice.RECIPE_CREATED_WITHOUT_IMAGE
                            }
                        },
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
                    val recipeUpdated by entry.savedStateHandle
                        .getStateFlow(RECIPE_UPDATED, false)
                        .collectAsStateWithLifecycle()
                    RecipeDetailScreen(
                        slug = route.slug,
                        recipeUpdated = recipeUpdated,
                        onRecipeUpdateSeen = { entry.savedStateHandle[RECIPE_UPDATED] = false },
                        onBack = { navController.popBackStack() },
                        onStartCooking = { slug, servings ->
                            navController.navigate(CookingRoute(slug, servings))
                        },
                        onEdit = { navController.navigate(RecipeEditRoute(it)) },
                    )
                }

                composable<RecipeEditRoute> { entry ->
                    val route: RecipeEditRoute = entry.toRoute()
                    RecipeEditScreen(
                        slug = route.slug,
                        onBack = { navController.popIfCurrent(entry) },
                        onSaved = { slug ->
                            if (navController.popIfCurrent(entry)) {
                                navController.showSavedRecipe(editedSlug = route.slug, savedSlug = slug)
                                notice = AppNotice.RECIPE_SAVED
                            }
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

            NoticePill(
                message = notice?.let { stringResource(it.messageRes) },
                success = notice?.success ?: true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Above the tab bar when there is one, above the system bar otherwise.
                    .then(if (destination.isFullScreen()) Modifier.navigationBarsPadding() else Modifier)
                    .padding(bottom = 16.dp),
            )
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
        hasRoute(RecipeEditRoute::class) ||
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

/**
 * A screen that closes itself once its work is done asks for it from an
 * effect, which runs again after a rotation: only the screen on top may pop,
 * so it never closes the one below by mistake.
 */
private fun NavHostController.isCurrent(entry: NavBackStackEntry): Boolean =
    currentBackStackEntry?.id == entry.id

private fun NavHostController.popIfCurrent(entry: NavBackStackEntry): Boolean =
    isCurrent(entry) && popBackStack()

/**
 * Back on the recipe page the editor was opened from, once it closed: that
 * page reloads, or after a rename is replaced by the page at the new address,
 * since the old one no longer exists on Mealie.
 */
private fun NavHostController.showSavedRecipe(editedSlug: String, savedSlug: String) {
    if (savedSlug == editedSlug) {
        currentBackStackEntry?.savedStateHandle?.set(RECIPE_UPDATED, true)
    } else {
        navigate(RecipeRoute(savedSlug)) {
            popUpTo<RecipeRoute> { inclusive = true }
        }
    }
}

/** Set on the recipe page's entry when the editor saved changes to it. */
private const val RECIPE_UPDATED = "recipe_updated"

private const val NOTICE_MILLIS = 2_500L

private fun AppContainer.profileTabInfo(session: ServerSession): ProfileTabInfo {
    val name = session.userDisplayName?.takeIf { it.isNotBlank() }
        ?: session.username?.takeIf { it.isNotBlank() }
    return ProfileTabInfo(
        displayName = name,
        avatarUrl = session.userId?.let { imageUrls.userAvatar(it, session.avatarCacheKey) },
        initials = name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
    )
}
