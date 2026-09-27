package org.opensources.umai.planning.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import java.io.File
import java.time.LocalDate
import java.time.format.FormatStyle

/**
 * The meal plan, one week at a time from the first day of the week chosen in
 * Mealie. The week opens on today, highlighted and scrolled into view.
 */
@Composable
fun PlanningRoute(
    onRecipeClick: (String) -> Unit,
    onSearchRecipe: (LocalDate, MealType, Float) -> Unit,
    onAddFood: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDishTypes: () -> Unit = {},
    onMealsPlanned: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val viewModel: PlanningViewModel = viewModel(factory = PlanningViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weekShopping: WeekShoppingViewModel = viewModel(factory = WeekShoppingViewModel.factory(container))
    val autoPlan: AutoPlanViewModel = viewModel(factory = AutoPlanViewModel.factory(container))
    val recipeImageUrl = { recipe: RecipeSummary -> container.imageUrls.thumbnail(recipe.id, recipe.imageToken) }

    // The tab keeps its ViewModel while the user walks through other screens,
    // so the plan is asked for again every time the screen comes back.
    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    AutoPlanHost(
        autoPlan = autoPlan,
        recipeImageUrl = recipeImageUrl,
        onOpenDishTypes = onOpenDishTypes,
        onSaved = {
            viewModel.refresh()
            onMealsPlanned()
        },
    )
    WeekShoppingHost(weekShopping)

    PlanningScreen(
        state = state,
        onRecipeClick = onRecipeClick,
        onPreviousWeek = viewModel::showPreviousWeek,
        onNextWeek = viewModel::showNextWeek,
        onBackToToday = viewModel::backToToday,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        addMealActions = remember(viewModel) {
            AddMealActions(onSearchRecipe = onSearchRecipe, onAddNote = viewModel::addNote, onAddFood = onAddFood)
        },
        onDeleteEntry = viewModel::deleteEntry,
        recipeImageUrl = recipeImageUrl,
        onAddToShopping = { weekShopping.open(state.days.flatMap { state.entriesByDay[it].orEmpty() }) },
        modifier = modifier,
        onAutoPlan = { autoPlan.open(state.today, state.days, state.entriesByDay, state.focusedDay) },
    )
}

/** The sheet that plans meals for the reader, while it is open. */
@Composable
private fun AutoPlanHost(
    autoPlan: AutoPlanViewModel,
    recipeImageUrl: (RecipeSummary) -> String?,
    onOpenDishTypes: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by autoPlan.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) {
            autoPlan.consumeSaved()
            onSaved()
        }
    }
    if (!state.visible) return
    AutoPlanSheet(
        state = state,
        actions = remember(autoPlan) {
            AutoPlanActions(
                onDismiss = autoPlan::close,
                onScopeChange = autoPlan::setScope,
                onDayChange = autoPlan::setDay,
                onPropose = autoPlan::propose,
                onRegenerate = autoPlan::regenerate,
                onReplace = autoPlan::replace,
                onAccept = autoPlan::accept,
                onOpenDishTypes = {
                    autoPlan.close()
                    onOpenDishTypes()
                },
            )
        },
        recipeImageUrl = recipeImageUrl,
    )
}

/** The sheet that puts the ingredients of the week on a shopping list, while it is open. */
@Composable
private fun WeekShoppingHost(weekShopping: WeekShoppingViewModel) {
    val state by weekShopping.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    WeekShoppingSheet(
        state = state,
        actions = remember(weekShopping) {
            WeekShoppingActions(
                onDismiss = weekShopping::close,
                onToggle = weekShopping::toggle,
                onSelectList = weekShopping::selectList,
                onServingsChange = weekShopping::setServings,
                onToggleIngredient = weekShopping::toggleIngredient,
                onBack = weekShopping::back,
                onNext = weekShopping::next,
                onRetry = weekShopping::retry,
            )
        },
    )
}

/** Stateless planning screen, driven by [PlanningUiState]. */
@Composable
fun PlanningScreen(
    state: PlanningUiState,
    onRecipeClick: (String) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToToday: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    addMealActions: AddMealActions,
    onDeleteEntry: (MealPlanEntry) -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
    modifier: Modifier = Modifier,
    onAddToShopping: () -> Unit = {},
    onAutoPlan: () -> Unit = {},
) {
    var sheetTarget by remember { mutableStateOf<LocalDate?>(null) }
    sheetTarget?.let { date ->
        AddMealSheet(date = date, actions = addMealActions, onDismiss = { sheetTarget = null })
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            PlanningTopBar(
                state = state,
                weekActions = WeekActions(onAutoPlan, onAddToShopping, onPreviousWeek, onBackToToday, onNextWeek),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val error = state.error
            when {
                state.loading && state.entriesByDay.isEmpty() -> LoadingView()
                error != null && state.entriesByDay.isEmpty() -> NetworkErrorView(
                    error = error,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = onRetry,
                )
                // The week scrolls sideways, so the pull gesture is picked up by
                // the vertical list of meals inside each day.
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    WeekRow(
                        state = state,
                        onAdd = { sheetTarget = it },
                        onRecipeClick = onRecipeClick,
                        onDeleteEntry = onDeleteEntry,
                        recipeImageUrl = recipeImageUrl,
                    )
                }
            }
        }
    }
}

/** What the buttons of the bar do to the week shown. */
private class WeekActions(
    val onAutoPlan: () -> Unit,
    val onAddToShopping: () -> Unit,
    val onPreviousWeek: () -> Unit,
    val onBackToToday: () -> Unit,
    val onNextWeek: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanningTopBar(state: PlanningUiState, weekActions: WeekActions) {
    val weekFormatter = rememberDateFormatter(FormatStyle.MEDIUM)
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.planning_title))
                Text(
                    text = stringResource(R.string.planning_week_of, state.weekStart.format(weekFormatter)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = weekActions.onAutoPlan, enabled = !state.loading && state.error == null) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.auto_plan_title))
            }
            IconButton(onClick = weekActions.onAddToShopping, enabled = state.hasRecipes) {
                Icon(Icons.Outlined.AddShoppingCart, contentDescription = stringResource(R.string.week_shopping_title))
            }
            IconButton(onClick = weekActions.onPreviousWeek) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.planning_previous_week))
            }
            IconButton(onClick = weekActions.onBackToToday) {
                Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.planning_back_to_today))
            }
            IconButton(onClick = weekActions.onNextWeek) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.planning_next_week))
            }
        },
    )
}

/** The days of the week side by side, the focused one scrolled into view. */
@Composable
private fun WeekRow(
    state: PlanningUiState,
    onAdd: (LocalDate) -> Unit,
    onRecipeClick: (String) -> Unit,
    onDeleteEntry: (MealPlanEntry) -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val focusedIndex = state.days.indexOf(state.focusedDay)
    LaunchedEffect(state.weekStart, state.focusedDay) {
        // Today is scrolled fully into view while the day before keeps peeking
        // on the left, so the order of the days stays obvious. The first day has
        // nothing before it and starts at the edge.
        listState.scrollToItem(
            index = focusedIndex,
            scrollOffset = if (focusedIndex > 0) -with(density) { DayPeekWidth.roundToPx() } else 0,
        )
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val days = state.days
        items(count = days.size, key = { days[it].toString() }) { index ->
            val day = days[index]
            DayColumn(
                width = DayColumnWidth,
                date = day,
                label = day.label(today = state.today),
                isToday = day == state.today,
                entries = state.entriesByDay[day].orEmpty(),
                calories = state.calories(day),
                loadingCalories = state.loadingCalories,
                onAdd = { onAdd(day) },
                onRecipeClick = onRecipeClick,
                onDelete = onDeleteEntry,
                entryDetails = { entry ->
                    EntryDetails(
                        calories = state.caloriesOf(entry),
                        imageUrl = entry.recipe?.let(recipeImageUrl)
                            ?: state.photos[entry.id]?.let { Uri.fromFile(File(it)).toString() },
                    )
                },
            )
        }
    }
}

/** Width of one day, and how much of the previous day stays visible. */
private val DayColumnWidth = 264.dp
private val DayPeekWidth = 56.dp
