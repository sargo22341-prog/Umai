package org.opensources.umai.recipe.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.ui.component.ImageFlightOverlay
import org.opensources.umai.core.ui.component.ImageFlightState
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.RecipePageLoadingView
import org.opensources.umai.core.ui.component.flightTarget
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import org.opensources.umai.search.domain.OrganizerEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailRoute(
    slug: String,
    recipeUpdated: Boolean,
    onRecipeUpdateSeen: () -> Unit,
    onBack: () -> Unit,
    onStartCooking: (String, Int) -> Unit,
    onEdit: (String) -> Unit,
    onOrganizerClick: (OrganizerEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: RecipeDetailViewModel =
        viewModel(factory = RecipeDetailViewModel.factory(container, slug), key = slug)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val planFlight = remember { ImageFlightState() }
    val imageUrl = { value: Recipe -> container.imageUrls.original(value.id, value.summary.imageToken) }
    // Coming back from the editor: the recipe on screen is out of date.
    RefreshWhen(recipeUpdated, onRefresh = viewModel::refresh, onSeen = onRecipeUpdateSeen)
    RecipeEventSnackbar(state.event, snackbarHostState, onShown = viewModel::consumeEvent)
    // A copy of the picture drops into the button of the meal plan once Mealie has the entry.
    LaunchedEffect(state.event) {
        if (state.event == RecipeEvent.AddedToPlan) state.recipe?.let { planFlight.launchFromSource(imageUrl(it)) }
    }

    var listSheetVisible by remember { mutableStateOf(false) }
    var planPickerVisible by remember { mutableStateOf(false) }
    RecipeDetailSheets(
        state = state,
        viewModel = viewModel,
        listSheetVisible = listSheetVisible,
        planPickerVisible = planPickerVisible,
        onListSheetClosed = { listSheetVisible = false },
        onPlanPickerClosed = { planPickerVisible = false },
    )

    RecipeDetailScreen(
        state = state,
        scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState()),
        snackbarHostState = snackbarHostState,
        planFlight = planFlight,
        onBack = onBack,
        onStartCooking = onStartCooking,
        onToggleFavorite = viewModel::toggleFavorite,
        onRate = viewModel::setRating,
        onEdit = onEdit,
        onOpenShoppingLists = {
            viewModel.loadShoppingLists()
            listSheetVisible = true
        },
        onOpenPlanPicker = {
            viewModel.loadPlanFirstDay()
            planPickerVisible = true
        },
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onServingsChange = viewModel::setServings,
        onPostComment = viewModel::postComment,
        onDeleteComment = viewModel::deleteComment,
        imageUrl = imageUrl,
        stepImageUrl = { recipeId, source -> container.imageUrls.stepImage(recipeId, source) },
        stepPhotoUrl = { value, file -> container.imageUrls.recipeAsset(value.id, file, value.mediaVersion) },
        onOpenSource = rememberSourceOpener(snackbarHostState),
        onOrganizerClick = onOrganizerClick,
        modifier = modifier,
    )
}

@Composable
private fun RefreshWhen(updated: Boolean, onRefresh: () -> Unit, onSeen: () -> Unit) {
    LaunchedEffect(updated) {
        if (updated) {
            onRefresh()
            onSeen()
        }
    }
}

/** Shows each event of the page once, as a snackbar, then lets it go. */
@Composable
private fun RecipeEventSnackbar(event: RecipeEvent?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    // Resolved during composition so the message follows the language chosen in
    // the settings rather than the one that was current when the app started.
    val message: String? = when (event) {
        null -> null
        is RecipeEvent.AddedToList -> stringResource(R.string.recipe_added_to_list, event.listName)
        RecipeEvent.AddedToPlan -> stringResource(R.string.recipe_added_to_plan)
        RecipeEvent.FavoritesNeedAccount -> stringResource(R.string.recipe_favorite_needs_account)
        RecipeEvent.RatingNeedsAccount -> stringResource(R.string.recipe_rating_needs_account)
        is RecipeEvent.Failed -> "${event.error.title()}\n${event.error.message()}"
    }
    LaunchedEffect(event) {
        message?.let { snackbarHostState.showMessage(it) }
        if (event != null) onShown()
    }
}

@Composable
private fun RecipeDetailSheets(
    state: RecipeDetailUiState,
    viewModel: RecipeDetailViewModel,
    listSheetVisible: Boolean,
    planPickerVisible: Boolean,
    onListSheetClosed: () -> Unit,
    onPlanPickerClosed: () -> Unit,
) {
    val recipe = state.recipe
    if (listSheetVisible && recipe != null) {
        AddToShoppingListSheet(
            recipe = recipe,
            lists = state.shoppingLists,
            loadingLists = state.loadingShoppingLists,
            initialServings = state.servings,
            onDismiss = onListSheetClosed,
            onConfirm = { list, servings, ingredients ->
                viewModel.addToShoppingList(list, servings, ingredients)
                onListSheetClosed()
            },
        )
    }
    if (planPickerVisible) {
        MealPlanPicker(
            firstDay = state.planFirstDay,
            onDismiss = onPlanPickerClosed,
            onConfirm = { date, type ->
                viewModel.addToMealPlan(date, type)
                onPlanPickerClosed()
            },
        )
    }
}

/** Opens a page in the browser, or says on the page that no app of the device can. */
@Composable
private fun rememberSourceOpener(snackbarHostState: SnackbarHostState): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noApp = stringResource(R.string.recipe_source_no_app)
    return remember(context, scope, snackbarHostState, noApp) {
        { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbarHostState.showMessage(noApp) }
            }
        }
    }
}

/**
 * Stateless recipe page, driven by [RecipeDetailUiState]. The flights of
 * [planFlight] leave from the picture of the recipe for the meal plan button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartCooking: (String, Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onRate: (Int) -> Unit,
    onEdit: (String) -> Unit,
    onOpenShoppingLists: () -> Unit,
    onOpenPlanPicker: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onServingsChange: (Int) -> Unit,
    onPostComment: (String) -> Unit,
    onDeleteComment: (RecipeComment) -> Unit,
    imageUrl: (Recipe) -> String?,
    stepImageUrl: (String, String) -> String?,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
    stepPhotoUrl: (Recipe, String) -> String? = { _, _ -> null },
    onOrganizerClick: (OrganizerEntry) -> Unit = {},
    planFlight: ImageFlightState = remember { ImageFlightState() },
) {
    Box(modifier = modifier) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = { RecipeTopBar(state.recipe, scrollBehavior, onBack, onOpenShoppingLists, onOpenPlanPicker, onEdit, planFlight) },
            floatingActionButton = { CookButton(state, onStartCooking) },
        ) { padding ->
            val error = state.error
            val recipe = state.recipe
            when {
                state.loading -> RecipePageLoadingView(Modifier.padding(padding))
                error != null && recipe == null -> NetworkErrorView(error, Modifier.fillMaxSize().padding(padding), onRetry = onRetry)
                recipe == null -> Unit
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    RecipeContent(
                        recipe = recipe,
                        state = state,
                        imageUrl = imageUrl(recipe),
                        imagePlace = planFlight.source,
                        stepImageUrl = stepImageUrl,
                        stepPhotoUrl = { file -> stepPhotoUrl(recipe, file) },
                        contentPadding = padding,
                        onServingsChange = onServingsChange,
                        onToggleFavorite = onToggleFavorite,
                        onRate = onRate,
                        onPostComment = onPostComment,
                        onDeleteComment = onDeleteComment,
                        onOpenSource = onOpenSource,
                        onOrganizerClick = onOrganizerClick,
                        onStartCooking = { onStartCooking(recipe.slug, state.servings) },
                    )
                }
            }
        }
        // Over the bar: the picture of the recipe flies into its meal plan button.
        ImageFlightOverlay(planFlight)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeTopBar(
    recipe: Recipe?,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onOpenShoppingLists: () -> Unit,
    onOpenPlanPicker: () -> Unit,
    onEdit: (String) -> Unit,
    planFlight: ImageFlightState,
) {
    TopAppBar(
        title = { Text(text = recipe?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            if (recipe != null) {
                IconButton(onClick = onOpenShoppingLists) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = stringResource(R.string.recipe_add_to_list))
                }
                IconButton(onClick = onOpenPlanPicker) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = stringResource(R.string.recipe_add_to_plan),
                        modifier = Modifier.flightTarget(planFlight),
                    )
                }
                IconButton(onClick = { onEdit(recipe.slug) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.recipe_edit))
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookButton(state: RecipeDetailUiState, onStartCooking: (String, Int) -> Unit) {
    val recipe = state.recipe
    // Hidden while typing a comment: it would sit on top of the field.
    if (recipe != null && state.canCook && !WindowInsets.isImeVisible) {
        ExtendedFloatingActionButton(
            onClick = { onStartCooking(recipe.slug, state.servings) },
            icon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) },
            text = { Text(stringResource(R.string.recipe_cook_mode)) },
        )
    }
}

private suspend fun SnackbarHostState.showMessage(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}
