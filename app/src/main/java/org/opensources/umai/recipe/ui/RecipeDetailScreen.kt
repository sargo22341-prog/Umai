package org.opensources.umai.recipe.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    slug: String,
    recipeUpdated: Boolean,
    onRecipeUpdateSeen: () -> Unit,
    onBack: () -> Unit,
    onStartCooking: (String, Int) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: RecipeDetailViewModel =
        viewModel(factory = RecipeDetailViewModel.factory(container, slug), key = slug)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current

    // Coming back from the editor: the recipe on screen is out of date.
    LaunchedEffect(recipeUpdated) {
        if (recipeUpdated) {
            viewModel.refresh()
            onRecipeUpdateSeen()
        }
    }

    var listSheetVisible by remember { mutableStateOf(false) }
    var planPickerVisible by remember { mutableStateOf(false) }

    // Resolved during composition so the message follows the language chosen in
    // the settings rather than the one that was current when the app started.
    val event = state.event
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
        if (event != null) viewModel.consumeEvent()
    }

    val recipe = state.recipe
    if (listSheetVisible && recipe != null) {
        AddToShoppingListSheet(
            recipe = recipe,
            lists = state.shoppingLists,
            loadingLists = state.loadingShoppingLists,
            initialServings = state.servings,
            onDismiss = { listSheetVisible = false },
            onConfirm = { list, servings, ingredients ->
                viewModel.addToShoppingList(list, servings, ingredients)
                listSheetVisible = false
            },
        )
    }

    if (planPickerVisible) {
        MealPlanPicker(
            onDismiss = { planPickerVisible = false },
            onConfirm = { date, type ->
                viewModel.addToMealPlan(date, type)
                planPickerVisible = false
            },
        )
    }

    RecipeDetailScaffold(
        state = state,
        scrollBehavior = scrollBehavior,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onStartCooking = onStartCooking,
        onToggleFavorite = viewModel::toggleFavorite,
        onRate = viewModel::setRating,
        onEdit = onEdit,
        onOpenShoppingLists = {
            viewModel.loadShoppingLists()
            listSheetVisible = true
        },
        onOpenPlanPicker = { planPickerVisible = true },
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onServingsChange = viewModel::setServings,
        onPostComment = viewModel::postComment,
        onDeleteComment = viewModel::deleteComment,
        imageUrl = { value ->
            container.imageUrls.original(value.id, value.summary.imageToken)
        },
        stepImageUrl = { recipeId, source -> container.imageUrls.stepImage(recipeId, source) },
        stepPhotoUrl = { value, file -> container.imageUrls.recipeAsset(value.id, file, value.mediaVersion) },
        onOpenSource = { url ->
            runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()),
                )
            }
        },
        modifier = modifier,
    )
}

/** Stateless recipe page, driven by [RecipeDetailUiState]. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScaffold(
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
) {
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.recipe?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    val recipe = state.recipe
                    if (recipe != null) {
                        IconButton(onClick = onOpenShoppingLists) {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingCart,
                                contentDescription = stringResource(R.string.recipe_add_to_list),
                            )
                        }
                        IconButton(onClick = onOpenPlanPicker) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = stringResource(R.string.recipe_add_to_plan),
                            )
                        }
                        IconButton(onClick = { onEdit(recipe.slug) }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.recipe_edit),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            val recipe = state.recipe
            // Hidden while typing a comment: it would sit on top of the field.
            if (recipe != null && recipe.steps.isNotEmpty() && !WindowInsets.isImeVisible) {
                ExtendedFloatingActionButton(
                    onClick = { onStartCooking(recipe.slug, state.servings) },
                    icon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) },
                    text = { Text(stringResource(R.string.recipe_cook_mode)) },
                )
            }
        },
    ) { padding ->
        val error = state.error
        when {
            state.loading -> LoadingView(Modifier.padding(padding))

            error != null && state.recipe == null -> NetworkErrorView(
                error = error,
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = onRetry,
            )

            else -> state.recipe?.let { recipe ->
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RecipeContent(
                        recipe = recipe,
                        state = state,
                        imageUrl = imageUrl(recipe),
                        stepImageUrl = stepImageUrl,
                        stepPhotoUrl = { file -> stepPhotoUrl(recipe, file) },
                        contentPadding = padding,
                        onServingsChange = onServingsChange,
                        onToggleFavorite = onToggleFavorite,
                        onRate = onRate,
                        onPostComment = onPostComment,
                        onDeleteComment = onDeleteComment,
                        onOpenSource = onOpenSource,
                    )
                }
            }
        }
    }
}

private suspend fun SnackbarHostState.showMessage(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}
