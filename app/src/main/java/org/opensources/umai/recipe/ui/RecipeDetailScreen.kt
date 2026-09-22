package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    slug: String,
    onBack: () -> Unit,
    onStartCooking: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: RecipeDetailViewModel =
        viewModel(factory = RecipeDetailViewModel.factory(container, slug), key = slug)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current

    var listPickerVisible by remember { mutableStateOf(false) }
    var planPickerVisible by remember { mutableStateOf(false) }

    // Resolved during composition so the message follows the language chosen in
    // the settings rather than the one that was current when the app started.
    val event = state.event
    val message: String? = when (event) {
        null -> null
        is RecipeEvent.AddedToList -> stringResource(R.string.recipe_added_to_list, event.listName)
        RecipeEvent.AddedToPlan -> stringResource(R.string.recipe_added_to_plan)
        RecipeEvent.FavoritesNeedAccount -> stringResource(R.string.recipe_favorite_needs_account)
        is RecipeEvent.Failed -> "${event.error.title()}\n${event.error.message()}"
    }

    LaunchedEffect(event) {
        message?.let { snackbarHostState.showMessage(it) }
        if (event != null) viewModel.consumeEvent()
    }

    if (listPickerVisible) {
        ShoppingListPicker(
            lists = state.shoppingLists,
            onDismiss = { listPickerVisible = false },
            onSelect = {
                viewModel.addToShoppingList(it)
                listPickerVisible = false
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
        onOpenShoppingLists = {
            viewModel.loadShoppingLists()
            listPickerVisible = true
        },
        onOpenPlanPicker = { planPickerVisible = true },
        onRetry = viewModel::load,
        imageUrl = { recipe ->
            container.imageUrls.original(recipe.id, recipe.summary.imageToken)
        },
        stepImageUrl = { recipeId, source -> container.imageUrls.stepImage(recipeId, source) },
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScaffold(
    state: RecipeDetailUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartCooking: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenShoppingLists: () -> Unit,
    onOpenPlanPicker: () -> Unit,
    onRetry: () -> Unit,
    imageUrl: (Recipe) -> String?,
    stepImageUrl: (String, String) -> String?,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
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
                    if (state.recipe != null) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = stringResource(
                                    if (state.isFavorite) {
                                        R.string.recipe_favorite_remove
                                    } else {
                                        R.string.recipe_favorite_add
                                    },
                                ),
                            )
                        }
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
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            val recipe = state.recipe
            if (recipe != null && recipe.steps.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onStartCooking(recipe.slug) },
                    icon = { Icon(Icons.Rounded.Restaurant, contentDescription = null) },
                    text = { Text(stringResource(R.string.recipe_cook_mode)) },
                )
            }
        },
    ) { padding ->
        val error = state.error
        when {
            state.loading -> LoadingView(Modifier.padding(padding))

            error != null -> NetworkErrorView(
                error = error,
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = onRetry,
            )

            else -> state.recipe?.let { recipe ->
                RecipeContent(
                    recipe = recipe,
                    imageUrl = imageUrl(recipe),
                    stepImageUrl = stepImageUrl,
                    contentPadding = padding,
                    onOpenSource = onOpenSource,
                )
            }
        }
    }
}

private suspend fun SnackbarHostState.showMessage(message: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}
