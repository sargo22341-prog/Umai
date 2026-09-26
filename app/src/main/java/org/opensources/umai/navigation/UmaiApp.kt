package org.opensources.umai.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.delay
import org.opensources.umai.cooking.data.CookingStepRequest
import org.opensources.umai.cooking.ui.ActiveTimerPills
import org.opensources.umai.cooking.ui.ActiveTimersViewModel
import org.opensources.umai.cooking.ui.CookingRoute
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.session.ServerSession
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.home.ui.HomeRoute
import org.opensources.umai.llm.ui.LocalAiRoute
import org.opensources.umai.planning.ui.DishTypesRoute
import org.opensources.umai.planning.ui.FoodEntryRoute
import org.opensources.umai.planning.ui.PlanRecipePickerRoute
import org.opensources.umai.planning.ui.PlanningRoute
import org.opensources.umai.profile.ui.ProfileRoute
import org.opensources.umai.provider.ui.ProviderRoute
import org.opensources.umai.provider.ui.ProvidersRoute
import org.opensources.umai.recipe.data.ImportRequest
import org.opensources.umai.recipe.ui.RecipeCreateRoute
import org.opensources.umai.recipe.ui.RecipeDetailRoute
import org.opensources.umai.recipe.ui.RecipeDraftsRoute
import org.opensources.umai.recipe.ui.RecipeEditRoute
import org.opensources.umai.recipe.ui.RecipeImportRoute
import org.opensources.umai.search.ui.SearchRoute
import org.opensources.umai.settings.ui.AppSettingsRoute
import org.opensources.umai.settings.ui.MealieSettingsRoute
import org.opensources.umai.setup.ui.SetupRoute
import org.opensources.umai.shopping.ui.ShoppingModeRoute
import org.opensources.umai.shopping.ui.ShoppingRoute
import java.time.LocalDate

/**
 * Root of the UI. Until an instance is configured (or after its token has been
 * refused) the setup screen replaces the whole navigation graph, so the user
 * never reaches an empty Home.
 *
 * [sharedUrl] is a recipe page shared to the app from another one: it opens the
 * import as soon as an instance is available, then [onSharedUrlHandled] clears it.
 * [cookingRequest] likewise opens the cooking mode a timer notification asks for,
 * and [importRequest] the import, or the recipe it created, an import notification does.
 */
@Composable
fun UmaiApp(
    modifier: Modifier = Modifier,
    sharedUrl: String? = null,
    onSharedUrlHandled: () -> Unit = {},
    cookingRequest: CookingStepRequest? = null,
    onCookingRequestHandled: () -> Unit = {},
    importRequest: ImportRequest? = null,
    onImportRequestHandled: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Loading -> Box(modifier = modifier.fillMaxSize()) { LoadingView() }
        SessionState.NotConfigured, is SessionState.Expired -> SetupRoute(modifier = modifier)
        is SessionState.Active -> MainNavigation(
            session = state.session,
            sharedUrl = sharedUrl,
            onSharedUrlHandled = onSharedUrlHandled,
            cookingRequest = cookingRequest,
            onCookingRequestHandled = onCookingRequestHandled,
            importRequest = importRequest,
            onImportRequestHandled = onImportRequestHandled,
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
    importRequest: ImportRequest?,
    onImportRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val activeTimers: ActiveTimersViewModel = viewModel(factory = ActiveTimersViewModel.factory(container))
    val timers by activeTimers.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    val selectedTab = TopLevelTab.entries.firstOrNull { it.matches(destination) }
    val searchSelected = destination?.hasRoute(SearchDestination::class) == true ||
        destination?.hasRoute(OrganizerSearchDestination::class) == true

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            navController.navigate(RecipeImportDestination(sharedUrl))
            onSharedUrlHandled()
        }
    }

    LaunchedEffect(cookingRequest) {
        if (cookingRequest != null) {
            navController.openCooking(cookingRequest.slug, cookingRequest.servings, cookingRequest.step)
            onCookingRequestHandled()
        }
    }

    LaunchedEffect(importRequest) {
        when (importRequest) {
            null -> return@LaunchedEffect
            ImportRequest.OpenImport -> navController.openImport()
            is ImportRequest.OpenRecipe -> navController.navigate(RecipeDestination(importRequest.slug))
        }
        onImportRequestHandled()
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
                    onSearch = { navController.switchTab(SearchDestination) },
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
                startDestination = HomeDestination,
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
                    if (initialState.destination.hasRoute(PlanRecipePickerDestination::class)) {
                        EnterTransition.None
                    } else {
                        screenPopEnter()
                    }
                },
                predictivePopExitTransition = {
                    if (initialState.destination.hasRoute(PlanRecipePickerDestination::class)) sinkExit() else screenPopExit()
                },
            ) {
                composable<HomeDestination> {
                    HomeRoute(
                        onRecipeClick = { navController.navigate(RecipeDestination(it)) },
                        onSearchClick = { navController.switchTab(SearchDestination) },
                    )
                }

                composable<SearchDestination> {
                    SearchRoute(onRecipeClick = { navController.navigate(RecipeDestination(it)) })
                }

                composable<OrganizerSearchDestination> { entry ->
                    val route: OrganizerSearchDestination = entry.toRoute()
                    SearchRoute(
                        onRecipeClick = { navController.navigate(RecipeDestination(it)) },
                        initialFilters = route.filters,
                    )
                }

                composable<PlanningDestination>(
                    // The recipe picker rises over the week, which stays in place underneath.
                    exitTransition = {
                        if (targetState.destination.hasRoute(PlanRecipePickerDestination::class)) {
                            stayUnderneath()
                        } else {
                            screenExit()
                        }
                    },
                    popEnterTransition = {
                        if (initialState.destination.hasRoute(PlanRecipePickerDestination::class)) {
                            EnterTransition.None
                        } else {
                            screenPopEnter()
                        }
                    },
                ) {
                    PlanningRoute(
                        onRecipeClick = { navController.navigate(RecipeDestination(it)) },
                        onOpenDishTypes = { navController.navigate(DishCoursesDestination) },
                        onMealsPlanned = { notice = AppNotice.MEAL_PLAN_CREATED },
                        onSearchRecipe = { date, type, fieldOriginY ->
                            navController.navigate(PlanRecipePickerDestination(date.toString(), type.apiValue, fieldOriginY))
                        },
                        onAddFood = { date -> navController.navigate(PlanFoodDestination(date.toString())) },
                    )
                }

                composable<PlanFoodDestination> { entry ->
                    val route: PlanFoodDestination = entry.toRoute()
                    FoodEntryRoute(
                        date = LocalDate.parse(route.date),
                        onBack = { navController.popIfCurrent(entry) },
                        onAdded = { added ->
                            if (navController.popIfCurrent(entry)) {
                                notice = if (added.photoKept) AppNotice.RECIPE_PLANNED else AppNotice.FOOD_PLANNED_WITHOUT_PHOTO
                            }
                        },
                    )
                }

                composable<PlanRecipePickerDestination>(
                    // The picker animates its own entrance, from the field of the sheet it replaces.
                    enterTransition = { EnterTransition.None },
                    popExitTransition = { sinkExit() },
                ) { entry ->
                    val route: PlanRecipePickerDestination = entry.toRoute()
                    PlanRecipePickerRoute(
                        date = LocalDate.parse(route.date),
                        mealType = MealType.fromApi(route.mealType),
                        fieldOriginY = route.fieldOriginY,
                        onBack = { navController.popIfCurrent(entry) },
                        onAdded = { if (navController.popIfCurrent(entry)) notice = AppNotice.RECIPE_PLANNED },
                    )
                }

                composable<ShoppingDestination> {
                    ShoppingRoute(onStartShoppingMode = { navController.navigate(ShoppingModeDestination(it)) })
                }

                composable<ShoppingModeDestination> { entry ->
                    val route: ShoppingModeDestination = entry.toRoute()
                    ShoppingModeRoute(
                        listId = route.listId,
                        onExit = { navController.popIfCurrent(entry) },
                    )
                }

                composable<ProfileDestination> {
                    ProfileRoute(
                        onOpenAppSettings = { navController.navigate(AppSettingsDestination) },
                        onOpenMealieSettings = { navController.navigate(MealieSettingsDestination) },
                        onImportRecipe = { navController.navigate(RecipeImportDestination()) },
                        onOpenProviders = { navController.navigate(ProvidersDestination) },
                        onOpenLocalAi = { navController.navigate(LocalAiSettingsDestination) },
                        onCreateRecipe = { navController.navigate(RecipeCreateDestination()) },
                        onOpenDrafts = { navController.navigate(RecipeDraftsDestination) },
                    )
                }

                composable<AppSettingsDestination> {
                    AppSettingsRoute(onBack = { navController.popBackStack() })
                }

                composable<MealieSettingsDestination> {
                    MealieSettingsRoute(onBack = { navController.popBackStack() })
                }

                composable<RecipeImportDestination> { entry ->
                    val route: RecipeImportDestination = entry.toRoute()
                    RecipeImportRoute(
                        initialUrl = route.url,
                        onBack = { navController.popIfCurrent(entry) },
                        onImported = { imported ->
                            if (navController.isCurrent(entry)) {
                                navController.openImportedRecipe(entry, imported.slug)
                                imported.notice?.let { notice = AppNotice.of(it) }
                            }
                        },
                        onOpenRecipe = { navController.navigate(RecipeDestination(it)) },
                    )
                }

                composable<DishCoursesDestination> {
                    DishTypesRoute(onBack = { navController.popBackStack() })
                }

                composable<LocalAiSettingsDestination> {
                    LocalAiRoute(onBack = { navController.popBackStack() })
                }

                composable<ProvidersDestination> {
                    ProvidersRoute(
                        onBack = { navController.popBackStack() },
                        onOpenProvider = { navController.navigate(ProviderDestination(it)) },
                    )
                }

                composable<ProviderDestination> { entry ->
                    val route: ProviderDestination = entry.toRoute()
                    ProviderRoute(
                        providerId = route.id,
                        onBack = { navController.popIfCurrent(entry) },
                    )
                }

                composable<RecipeCreateDestination> { entry ->
                    val route: RecipeCreateDestination = entry.toRoute()
                    RecipeCreateRoute(
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

                composable<RecipeDraftsDestination> {
                    RecipeDraftsRoute(
                        onBack = { navController.popBackStack() },
                        onOpenDraft = { navController.navigate(RecipeCreateDestination(it)) },
                    )
                }

                composable<RecipeDestination> { entry ->
                    val route: RecipeDestination = entry.toRoute()
                    val recipeUpdated by entry.savedStateHandle
                        .getStateFlow(RECIPE_UPDATED, false)
                        .collectAsStateWithLifecycle()
                    RecipeDetailRoute(
                        slug = route.slug,
                        recipeUpdated = recipeUpdated,
                        onRecipeUpdateSeen = { entry.savedStateHandle[RECIPE_UPDATED] = false },
                        onBack = { navController.popBackStack() },
                        onStartCooking = { slug, servings ->
                            navController.navigate(CookingDestination(slug, servings))
                        },
                        onEdit = { navController.navigate(RecipeEditDestination(it)) },
                        onOrganizerClick = { navController.navigate(OrganizerSearchDestination.of(it)) },
                    )
                }

                composable<RecipeEditDestination> { entry ->
                    val route: RecipeEditDestination = entry.toRoute()
                    RecipeEditRoute(
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

                composable<CookingDestination> { entry ->
                    val route: CookingDestination = entry.toRoute()
                    CookingRoute(
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
                if (destination?.hasRoute(CookingDestination::class) != true) {
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
    TopLevelTab.HOME -> destination?.hasRoute(HomeDestination::class) == true
    TopLevelTab.PLANNING -> destination?.hasRoute(PlanningDestination::class) == true
    TopLevelTab.SHOPPING -> destination?.hasRoute(ShoppingDestination::class) == true
    TopLevelTab.PROFILE -> destination?.hasRoute(ProfileDestination::class) == true
}

/**
 * Readers and forms own the whole screen: the tab bar would only steal vertical
 * space and show no tab as selected.
 */
private fun NavDestination?.isFullScreen(): Boolean = this != null && FullScreenDestinations.any { hasRoute(it) }

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
