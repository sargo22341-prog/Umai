package org.opensources.umai.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.opensources.umai.cooking.ui.CookingRoute
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.ui.component.ImageFlightState
import org.opensources.umai.core.ui.motion.LocalSharedTransitionScope
import org.opensources.umai.core.ui.motion.ScreenVisibility
import org.opensources.umai.home.ui.HomeRoute
import org.opensources.umai.llm.ui.LocalAiRoute
import org.opensources.umai.planning.ui.DishTypesRoute
import org.opensources.umai.planning.ui.FoodEntryRoute
import org.opensources.umai.planning.ui.PlanRecipePickerRoute
import org.opensources.umai.planning.ui.PlanningRoute
import org.opensources.umai.profile.ui.ProfileRoute
import org.opensources.umai.provider.ui.ProviderRoute
import org.opensources.umai.provider.ui.ProvidersRoute
import org.opensources.umai.recipe.ui.RecipeCreateRoute
import org.opensources.umai.recipe.ui.RecipeDetailRoute
import org.opensources.umai.recipe.ui.RecipeDraftsRoute
import org.opensources.umai.recipe.ui.RecipeEditRoute
import org.opensources.umai.recipe.ui.RecipeImportRoute
import org.opensources.umai.search.ui.SearchRoute
import org.opensources.umai.settings.ui.AppSettingsRoute
import org.opensources.umai.settings.ui.MealieSettingsRoute
import org.opensources.umai.shopping.ui.ShoppingModeRoute
import org.opensources.umai.shopping.ui.ShoppingRoute
import java.time.LocalDate

/** Every screen of the app once an instance is set up, and how each one is reached and left. */
@Composable
internal fun AppNavHost(
    navController: NavHostController,
    showNotice: (AppNotice) -> Unit,
    planFlight: ImageFlightState,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(
        // Painted in the app theme: the window behind follows the system theme, and pixel
        // rounding may leave a hairline between the two sliding screens.
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            AppScreens(navController, showNotice, planFlight)
        }
    }
}

@Composable
private fun AppScreens(
    navController: NavHostController,
    showNotice: (AppNotice) -> Unit,
    planFlight: ImageFlightState,
) {
    NavHost(
        navController = navController,
        startDestination = HomeDestination,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { if (unfolds()) unfoldEnter() else screenEnter() },
        exitTransition = { if (unfolds()) unfoldExit() else screenExit() },
        popEnterTransition = { if (unfolds()) unfoldEnter() else screenPopEnter() },
        popExitTransition = { if (unfolds()) unfoldExit() else screenPopExit() },
        // The back gesture has its own transitions (a fade and shrink by default): the same
        // as a tap on back, the recipe picker of the meal plan sinking over the week.
        predictivePopEnterTransition = {
            when {
                initialState.destination.hasRoute(PlanRecipePickerDestination::class) -> EnterTransition.None
                unfolds() -> unfoldEnter()
                else -> screenPopEnter()
            }
        },
        predictivePopExitTransition = {
            when {
                initialState.destination.hasRoute(PlanRecipePickerDestination::class) -> sinkExit()
                unfolds() -> unfoldExit()
                else -> screenPopExit()
            }
        },
    ) {
        discoveryGraph(navController)
        planningGraph(navController, showNotice, planFlight)
        shoppingGraph(navController)
        profileGraph(navController)
        recipeWritingGraph(navController, showNotice)
        recipeReadingGraph(navController, showNotice)
    }
}

private fun NavGraphBuilder.discoveryGraph(navController: NavHostController) {
    composable<HomeDestination> {
        ScreenVisibility(this) {
            HomeRoute(
                onRecipeClick = { navController.navigate(RecipeDestination(it)) },
                onSearchClick = { navController.switchTab(SearchDestination) },
            )
        }
    }
    composable<SearchDestination> {
        ScreenVisibility(this) {
            SearchRoute(onRecipeClick = { navController.navigate(RecipeDestination(it)) })
        }
    }
    composable<OrganizerSearchDestination> { entry ->
        val route: OrganizerSearchDestination = entry.toRoute()
        SearchRoute(
            onRecipeClick = { navController.navigate(RecipeDestination(it)) },
            initialFilters = route.filters,
        )
    }
}

private fun NavGraphBuilder.planningGraph(
    navController: NavHostController,
    showNotice: (AppNotice) -> Unit,
    planFlight: ImageFlightState,
) {
    composable<PlanningDestination>(
        // The recipe picker rises over the week, which stays in place underneath.
        exitTransition = {
            if (targetState.destination.hasRoute(PlanRecipePickerDestination::class)) stayUnderneath() else screenExit()
        },
        popEnterTransition = {
            if (initialState.destination.hasRoute(PlanRecipePickerDestination::class)) EnterTransition.None else screenPopEnter()
        },
    ) {
        PlanningRoute(
            onRecipeClick = { navController.navigate(RecipeDestination(it)) },
            onOpenDishTypes = { navController.navigate(DishCoursesDestination) },
            onMealsPlanned = { showNotice(AppNotice.MEAL_PLAN_CREATED) },
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
            onBack = { navController.closeIfCurrent(entry) },
            onAdded = { added ->
                if (navController.popIfCurrent(entry)) {
                    showNotice(if (added.photoKept) AppNotice.RECIPE_PLANNED else AppNotice.FOOD_PLANNED_WITHOUT_PHOTO)
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
            onBack = { navController.closeIfCurrent(entry) },
            onAdded = { picked ->
                if (navController.popIfCurrent(entry)) {
                    showNotice(AppNotice.RECIPE_PLANNED)
                    picked?.let(planFlight::launch)
                }
            },
        )
    }
    composable<DishCoursesDestination> {
        DishTypesRoute(onBack = { navController.popBackStack() })
    }
}

private fun NavGraphBuilder.shoppingGraph(navController: NavHostController) {
    composable<ShoppingDestination> {
        ShoppingRoute(onStartShoppingMode = { navController.navigate(ShoppingModeDestination(it)) })
    }
    composable<ShoppingModeDestination> { entry ->
        val route: ShoppingModeDestination = entry.toRoute()
        ShoppingModeRoute(listId = route.listId, onExit = { navController.closeIfCurrent(entry) })
    }
}

private fun NavGraphBuilder.profileGraph(navController: NavHostController) {
    composable<ProfileDestination> {
        ScreenVisibility(this) {
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
    }
    composable<AppSettingsDestination> {
        AppSettingsRoute(onBack = { navController.popBackStack() })
    }
    composable<MealieSettingsDestination> {
        MealieSettingsRoute(onBack = { navController.popBackStack() })
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
        ProviderRoute(providerId = route.id, onBack = { navController.closeIfCurrent(entry) })
    }
}

/** The screens that bring a recipe into Mealie: the import, the form and its drafts. */
private fun NavGraphBuilder.recipeWritingGraph(navController: NavHostController, showNotice: (AppNotice) -> Unit) {
    composable<RecipeImportDestination> { entry ->
        val route: RecipeImportDestination = entry.toRoute()
        RecipeImportRoute(
            initialUrl = route.url,
            onBack = { navController.closeIfCurrent(entry) },
            onImported = { imported ->
                if (navController.isCurrent(entry)) {
                    navController.openImportedRecipe(entry, imported.slug)
                    imported.notice?.let { showNotice(AppNotice.of(it)) }
                }
            },
            onOpenRecipe = { navController.navigate(RecipeDestination(it)) },
        )
    }
    composable<RecipeCreateDestination> { entry ->
        val route: RecipeCreateDestination = entry.toRoute()
        ScreenVisibility(this) {
            RecipeCreateRoute(
                draftId = route.draftId,
                onLeft = { draftSaved ->
                    if (navController.popIfCurrent(entry) && draftSaved) showNotice(AppNotice.DRAFT_SAVED)
                },
                onCreated = { slug, imageSaved ->
                    if (navController.isCurrent(entry)) {
                        navController.openCreatedRecipe(slug)
                        if (!imageSaved) showNotice(AppNotice.RECIPE_CREATED_WITHOUT_IMAGE)
                    }
                },
            )
        }
    }
    composable<RecipeDraftsDestination> {
        RecipeDraftsRoute(
            onBack = { navController.popBackStack() },
            onOpenDraft = { navController.navigate(RecipeCreateDestination(it)) },
        )
    }
}

/** The recipe page and what it opens: its editor and the cooking mode. */
private fun NavGraphBuilder.recipeReadingGraph(navController: NavHostController, showNotice: (AppNotice) -> Unit) {
    composable<RecipeDestination> { entry ->
        val route: RecipeDestination = entry.toRoute()
        val recipeUpdated by entry.savedStateHandle.getStateFlow(RECIPE_UPDATED, false).collectAsStateWithLifecycle()
        RecipeDetailRoute(
            slug = route.slug,
            recipeUpdated = recipeUpdated,
            onRecipeUpdateSeen = { entry.savedStateHandle[RECIPE_UPDATED] = false },
            onBack = { navController.popBackStack() },
            onStartCooking = { slug, servings -> navController.navigate(CookingDestination(slug, servings)) },
            onEdit = { navController.navigate(RecipeEditDestination(it)) },
            onOrganizerClick = { navController.navigate(OrganizerSearchDestination.of(it)) },
        )
    }
    composable<RecipeEditDestination> { entry ->
        val route: RecipeEditDestination = entry.toRoute()
        RecipeEditRoute(
            slug = route.slug,
            onBack = { navController.closeIfCurrent(entry) },
            onSaved = { slug ->
                if (navController.popIfCurrent(entry)) {
                    navController.showSavedRecipe(editedSlug = route.slug, savedSlug = slug)
                    showNotice(AppNotice.RECIPE_SAVED)
                }
            },
            onDeleted = {
                if (navController.popIfCurrent(entry)) {
                    navController.leaveDeletedRecipe(route.slug)
                    showNotice(AppNotice.RECIPE_DELETED)
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
            onExit = { navController.closeIfCurrent(entry) },
            onCooked = { if (navController.popIfCurrent(entry)) showNotice(AppNotice.RECIPE_COOKED) },
            onOpenTimer = { navController.openCooking(it.recipe.slug, it.recipe.servings, it.stepIndex) },
        )
    }
}
