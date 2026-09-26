package org.opensources.umai.planning.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.format.currentLocale
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.planning.domain.DayCalories
import org.opensources.umai.recipe.ui.labelRes
import java.io.File
import java.text.NumberFormat
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
    val shopping by weekShopping.state.collectAsStateWithLifecycle()

    val autoPlan: AutoPlanViewModel = viewModel(factory = AutoPlanViewModel.factory(container))
    val autoPlanState by autoPlan.state.collectAsStateWithLifecycle()

    // The tab keeps its ViewModel while the user walks through other screens,
    // so the plan is asked for again every time the screen comes back.
    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    LaunchedEffect(autoPlanState.saved) {
        if (autoPlanState.saved) {
            autoPlan.consumeSaved()
            viewModel.refresh()
            onMealsPlanned()
        }
    }

    if (autoPlanState.visible) {
        AutoPlanSheet(
            state = autoPlanState,
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
            recipeImageUrl = { recipe -> container.imageUrls.thumbnail(recipe.id, recipe.imageToken) },
        )
    }

    if (shopping.visible) {
        WeekShoppingSheet(
            state = shopping,
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

    PlanningScreen(
        state = state,
        onRecipeClick = onRecipeClick,
        onPreviousWeek = viewModel::showPreviousWeek,
        onNextWeek = viewModel::showNextWeek,
        onBackToToday = viewModel::backToToday,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        addMealActions = remember(viewModel) {
            AddMealActions(
                onSearchRecipe = onSearchRecipe,
                onAddNote = viewModel::addNote,
                onAddFood = onAddFood,
            )
        },
        onDeleteEntry = viewModel::deleteEntry,
        recipeImageUrl = { recipe -> container.imageUrls.thumbnail(recipe.id, recipe.imageToken) },
        onAddToShopping = { weekShopping.open(state.days.flatMap { state.entriesByDay[it].orEmpty() }) },
        modifier = modifier,
        onAutoPlan = { autoPlan.open(state.today, state.days, state.entriesByDay, state.focusedDay) },
    )
}

/** Stateless planning screen, driven by [PlanningUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
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
    val listState = rememberLazyListState()

    var sheetTarget by remember { mutableStateOf<LocalDate?>(null) }

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

    val weekFormatter = rememberDateFormatter(FormatStyle.MEDIUM)

    sheetTarget?.let { date ->
        AddMealSheet(
            date = date,
            actions = addMealActions,
            onDismiss = { sheetTarget = null },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
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
                    IconButton(onClick = onAutoPlan, enabled = !state.loading && state.error == null) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.auto_plan_title),
                        )
                    }
                    IconButton(onClick = onAddToShopping, enabled = state.hasRecipes) {
                        Icon(
                            Icons.Outlined.AddShoppingCart,
                            contentDescription = stringResource(R.string.week_shopping_title),
                        )
                    }
                    IconButton(onClick = onPreviousWeek) {
                        Icon(
                            Icons.Outlined.ChevronLeft,
                            contentDescription = stringResource(R.string.planning_previous_week),
                        )
                    }
                    IconButton(onClick = onBackToToday) {
                        Icon(
                            Icons.Outlined.Today,
                            contentDescription = stringResource(R.string.planning_back_to_today),
                        )
                    }
                    IconButton(onClick = onNextWeek) {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = stringResource(R.string.planning_next_week),
                        )
                    }
                },
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
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
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
                                onAdd = { sheetTarget = day },
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
            }
        }
    }
}

@Composable
private fun DayColumn(
    width: Dp,
    date: LocalDate,
    label: String,
    isToday: Boolean,
    entries: List<MealPlanEntry>,
    calories: DayCalories,
    loadingCalories: Boolean,
    onAdd: () -> Unit,
    onRecipeClick: (String) -> Unit,
    onDelete: (MealPlanEntry) -> Unit,
    entryDetails: (MealPlanEntry) -> EntryDetails,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)
    val sorted = remember(entries) {
        entries.sortedBy { MealType.displayOrder.indexOf(it.type).takeIf { i -> i >= 0 } ?: 99 }
    }

    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
        color = if (isToday) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = date.format(dateFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isToday) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!calories.isEmpty) {
                    DayCaloriesLine(calories = calories, loading = loadingCalories, isToday = isToday)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sorted.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.planning_empty_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    items(count = sorted.size, key = { sorted[it].id }) { index ->
                        MealEntryCard(
                            entry = sorted[index],
                            details = entryDetails(sorted[index]),
                            onClick = { slug -> onRecipeClick(slug) },
                            onDelete = { onDelete(sorted[index]) },
                        )
                    }
                }
            }

            TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.planning_add_meal),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * The total of the day. The entries without calories are counted apart, once
 * the calories of the recipes are known.
 */
@Composable
private fun DayCaloriesLine(calories: DayCalories, loading: Boolean, isToday: Boolean) {
    val locale = currentLocale()
    val number = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val total = stringResource(R.string.planning_calories, number.format(calories.total))
    val text = if (calories.unknown > 0 && !loading) {
        "$total " + pluralStringResource(R.plurals.planning_calories_unknown, calories.unknown, calories.unknown)
    } else {
        total
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** What a card shows besides its entry: its calories and its picture, when it has them. */
private class EntryDetails(val calories: Int?, val imageUrl: String?)

@Composable
private fun MealEntryCard(
    entry: MealPlanEntry,
    details: EntryDetails,
    onClick: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = entry.recipe

    Card(
        onClick = { recipe?.slug?.let(onClick) },
        modifier = modifier.fillMaxWidth(),
        enabled = recipe != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(entry.type.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.heightIn(max = 28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.planning_delete_entry),
                        modifier = Modifier.height(18.dp),
                    )
                }
            }

            if (recipe != null || details.imageUrl != null) {
                RemoteImage(
                    url = details.imageUrl,
                    // A recipe is named below its picture; the photo of a product is described.
                    contentDescription = if (recipe == null) {
                        stringResource(R.string.food_photo_description, entry.displayTitle)
                    } else {
                        null
                    },
                    // A product is shown whole: its package is often tall.
                    contentScale = if (recipe == null) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(MaterialTheme.shapes.small),
                    placeholderIconSize = 24.dp,
                )
            }

            Text(
                text = entry.displayTitle.ifBlank { stringResource(R.string.planning_empty_day) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            details.calories?.let { calories ->
                Text(
                    text = stringResource(R.string.planning_calories, calories),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Width of one day, and how much of the previous day stays visible. */
private val DayColumnWidth = 264.dp
private val DayPeekWidth = 56.dp
