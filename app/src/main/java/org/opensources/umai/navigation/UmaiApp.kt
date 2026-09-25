package org.opensources.umai.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.session.ServerSession
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.cooking.data.CookingStepRequest
import org.opensources.umai.cooking.ui.ActiveTimerPills
import org.opensources.umai.cooking.ui.ActiveTimersViewModel
import org.opensources.umai.cooking.ui.CookingScreen
import org.opensources.umai.home.ui.HomeScreen
import org.opensources.umai.planning.ui.PlanningScreen
import org.opensources.umai.profile.ui.ProfileScreen
import org.opensources.umai.llm.ui.LocalAiRoute
import org.opensources.umai.planning.ui.DishTypesRoute
import org.opensources.umai.provider.ui.ProviderScreen
import org.opensources.umai.provider.ui.ProvidersScreen
import org.opensources.umai.recipe.ui.RecipeCreateScreen
import org.opensources.umai.recipe.ui.RecipeDetailScreen
import org.opensources.umai.recipe.ui.RecipeDraftsScreen
import org.opensources.umai.recipe.ui.RecipeImportScreen
import org.opensources.umai.search.ui.SearchScreen
import org.opensources.umai.settings.ui.AppSettingsScreen
import org.opensources.umai.settings.ui.MealieSettingsScreen
import org.opensources.umai.setup.ui.SetupScreen
import org.opensources.umai.shopping.ui.ShoppingModeScreen
import org.opensources.umai.shopping.ui.ShoppingScreen
import org.opensources.umai.planning.ui.PlanRecipePickerScreen
import org.opensources.umai.core.model.MealType
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import java.time.LocalDate

/**
 * Root of the UI. Until an instance is configured (or after its token has been
 * refused) the setup screen replaces the whole navigation graph, so the user
 * never reaches an empty Home.
 */
/**
 * [sharedUrl] is a recipe page shared to the app from another one: it opens the
 * import as soon as an instance is available, then [onSharedUrlHandled] clears it.
 * [cookingRequest] likewise opens the cooking mode a timer notification asks for.
 */
@Composable
fun UmaiApp(
    modifier: Modifier = Modifier,
    sharedUrl: String? = null,
    onSharedUrlHandled: () -> Unit = {},
    cookingRequest: CookingStepRequest? = null,
    onCookingRequestHandled: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Loading -> Box(modifier = modifier.fillMaxSize()) { LoadingView() }
        SessionState.NotConfigured, is SessionState.Expired -> SetupScreen(modifier = modifier)
        is SessionState.Active -> MainNavigation(
            session = state.session,
            sharedUrl = sharedUrl,
            onSharedUrlHandled = onSharedUrlHandled,
            cookingRequest = cookingRequest,
            onCookingRequestHandled = onCookingRequestHandled,
            modifier = modifier,
        )
    }
}

@Composable
private fun MainNavigation(
    session: ServerSession,
    sharedUrl: String?,
    onSharedUrlHandled: () -> Unit,
    cookingRequest: CookingStepRequest?,
    onCookingRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val activeTimers: ActiveTimersViewModel = viewModel(factory = ActiveTimersViewModel.factory(container))
    val timers by activeTimers.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val selectedTab = TopLevelTab.entries.firstOrNull { it.matches(destination) }
    val searchSelected = destination?.hasRoute(SearchRoute::class) == true ||
        destination?.hasRoute(OrganizerSearchRoute::class) == true

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            navController.navigate(RecipeImportRoute(sharedUrl))
            onSharedUrlHandled()
        }
    }

    LaunchedEffect(cookingRequest) {
        if (cookingRequest != null) {
            navController.openCooking(cookingRequest.slug, cookingRequest.servings, cookingRequest.step)
            onCookingRequestHandled()
        }
    }

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
        // The tab bar already sits over the system navigation bar: the screens below must
        // not leave room for it a second time. Full-screen destinations keep that inset.
        val bottomBarPadding = PaddingValues(bottom = padding.calculateBottomPadding())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottomBarPadding)
                .consumeWindowInsets(bottomBarPadding),
        ) {
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
                // The back gesture has its own transitions (a fade and shrink by default): the same
                // as a tap on back, the recipe picker of the meal plan sinking over the week.
                predictivePopEnterTransition = {
                    if (initialState.destination.hasRoute(PlanRecipePickerRoute::class)) {
                        EnterTransition.None
                    } else {
                        screenPopEnter()
                    }
                },
                predictivePopExitTransition = {
                    if (initialState.destination.hasRoute(PlanRecipePickerRoute::class)) sinkExit() else screenPopExit()
                },
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

                composable<OrganizerSearchRoute> { entry ->
                    val route: OrganizerSearchRoute = entry.toRoute()
                    SearchScreen(
                        onRecipeClick = { navController.navigate(RecipeRoute(it)) },
                        initialFilters = route.filters,
                    )
                }

                composable<PlanningRoute>(
                    // The recipe picker rises over the week, which stays in place underneath.
                    exitTransition = {
                        if (targetState.destination.hasRoute(PlanRecipePickerRoute::class)) {
                            ExitTransition.KeepUntilTransitionsFinished
                        } else {
                            screenExit()
                        }
                    },
                    popEnterTransition = {
                        if (initialState.destination.hasRoute(PlanRecipePickerRoute::class)) {
                            EnterTransition.None
                        } else {
                            screenPopEnter()
                        }
                    },
                ) {
                    PlanningScreen(
                        onRecipeClick = { navController.navigate(RecipeRoute(it)) },
                        onOpenDishTypes = { navController.navigate(DishCoursesRoute) },
                        onMealsPlanned = { notice = AppNotice.MEAL_PLAN_CREATED },
                        onSearchRecipe = { date, type, fieldOriginY ->
                            navController.navigate(PlanRecipePickerRoute(date.toString(), type.apiValue, fieldOriginY))
                        },
                    )
                }

                composable<PlanRecipePickerRoute>(
                    // The picker animates its own entrance, from the field of the sheet it replaces.
                    enterTransition = { EnterTransition.None },
                    popExitTransition = { sinkExit() },
                ) { entry ->
                    val route: PlanRecipePickerRoute = entry.toRoute()
                    PlanRecipePickerScreen(
                        date = LocalDate.parse(route.date),
                        mealType = MealType.fromApi(route.mealType),
                        fieldOriginY = route.fieldOriginY,
                        onBack = { navController.popIfCurrent(entry) },
                        onAdded = { if (navController.popIfCurrent(entry)) notice = AppNotice.RECIPE_PLANNED },
                    )
                }

                composable<ShoppingRoute> {
                    ShoppingScreen(onStartShoppingMode = { navController.navigate(ShoppingModeRoute(it)) })
                }

                composable<ShoppingModeRoute> { entry ->
                    val route: ShoppingModeRoute = entry.toRoute()
                    ShoppingModeScreen(
                        listId = route.listId,
                        onExit = { navController.popIfCurrent(entry) },
                    )
                }

                composable<ProfileRoute> {
                    ProfileScreen(
                        onOpenAppSettings = { navController.navigate(AppSettingsRoute) },
                        onOpenMealieSettings = { navController.navigate(MealieSettingsRoute) },
                        onImportRecipe = { navController.navigate(RecipeImportRoute()) },
                        onOpenProviders = { navController.navigate(ProvidersRoute) },
                        onOpenLocalAi = { navController.navigate(LocalAiSettingsRoute) },
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

                composable<RecipeImportRoute> { entry ->
                    val route: RecipeImportRoute = entry.toRoute()
                    RecipeImportScreen(
                        initialUrl = route.url,
                        onBack = { navController.popIfCurrent(entry) },
                        onImported = { imported ->
                            if (navController.isCurrent(entry)) {
                                navController.openImportedRecipe(entry, imported.slug)
                                imported.notice?.let { notice = AppNotice.of(it) }
                            }
                        },
                        onOpenRecipe = { navController.navigate(RecipeRoute(it)) },
                    )
                }

                composable<DishCoursesRoute> {
                    DishTypesRoute(onBack = { navController.popBackStack() })
                }

                composable<LocalAiSettingsRoute> {
                    LocalAiRoute(onBack = { navController.popBackStack() })
                }

                composable<ProvidersRoute> {
                    ProvidersScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProvider = { navController.navigate(ProviderRoute(it)) },
                    )
                }

                composable<ProviderRoute> { entry ->
                    val route: ProviderRoute = entry.toRoute()
                    ProviderScreen(
                        providerId = route.id,
                        onBack = { navController.popIfCurrent(entry) },
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
                        onOrganizerClick = { navController.navigate(OrganizerSearchRoute.of(it)) },
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
                        onDeleted = {
                            if (navController.popIfCurrent(entry)) {
                                navController.leaveDeletedRecipe(route.slug)
                                notice = AppNotice.RECIPE_DELETED
                            }
                        },
                    )
                }

                composable<CookingRoute> { entry ->
                    val route: CookingRoute = entry.toRoute()
                    CookingScreen(
                        slug = route.slug,
                        servings = route.servings,
                        step = route.step,
                        onExit = { navController.popIfCurrent(entry) },
                        onCooked = {
                            if (navController.popIfCurrent(entry)) notice = AppNotice.RECIPE_COOKED
                        },
                        onOpenTimer = { navController.openCooking(it.recipe.slug, it.recipe.servings, it.stepIndex) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Above the tab bar when there is one, above the system bar otherwise.
                    .then(if (destination.isFullScreen()) Modifier.navigationBarsPadding() else Modifier)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The cooking mode shows its timers itself; every other screen gets
                // them as pills, on the left, clear of the buttons on the right.
                if (destination?.hasRoute(CookingRoute::class) != true) {
                    ActiveTimerPills(
                        state = timers,
                        onOpen = { navController.openCooking(it.recipe.slug, it.recipe.servings, it.stepIndex) },
                        onStop = activeTimers::dismiss,
                        modifier = Modifier.align(Alignment.Start),
                    )
                }
                NoticePill(
                    message = notice?.let { stringResource(it.messageRes) },
                    success = notice?.success ?: true,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
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
        hasRoute(ProvidersRoute::class) ||
        hasRoute(ProviderRoute::class) ||
        hasRoute(RecipeCreateRoute::class) ||
        hasRoute(RecipeEditRoute::class) ||
        hasRoute(RecipeDraftsRoute::class) ||
        hasRoute(PlanRecipePickerRoute::class) ||
        hasRoute(ShoppingModeRoute::class)
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
 * The import replaces itself with the recipe it created: it may have been
 * opened by a page shared from another app, over any screen.
 */
private fun NavHostController.openImportedRecipe(entry: NavBackStackEntry, slug: String) {
    navigate(RecipeRoute(slug)) {
        popUpTo(entry.destination.id) { inclusive = true }
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

/**
 * Once the editor closed on a deleted recipe, its page, under it, goes too:
 * it would show a recipe that no longer exists.
 */
private fun NavHostController.leaveDeletedRecipe(slug: String) {
    val below = currentBackStackEntry ?: return
    if (below.destination.hasRoute(RecipeRoute::class) && below.toRoute<RecipeRoute>().slug == slug) popBackStack()
}

/**
 * Opens the cooking mode of a recipe at a step, as a timer asks: it replaces
 * the cooking mode on screen, if any, rather than stacking a second one.
 */
private fun NavHostController.openCooking(slug: String, servings: Int, step: Int) {
    navigate(CookingRoute(slug, servings, step)) {
        popUpTo<CookingRoute> { inclusive = true }
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
