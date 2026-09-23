package org.opensources.umai.planning.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.format.localizedName
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.recipe.ui.labelRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Horizontal week view. The window always starts on the day before today, so
 * yesterday is the first column and today is the second one, highlighted and
 * visible without scrolling.
 */
@Composable
fun PlanningScreen(
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: PlanningViewModel = viewModel(factory = PlanningViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()

    // The tab keeps its ViewModel while the user walks through other screens,
    // so the plan is asked for again every time the screen comes back.
    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    PlanningScreen(
        state = state,
        picker = picker,
        onRecipeClick = onRecipeClick,
        onPreviousWeek = viewModel::showPreviousWeek,
        onNextWeek = viewModel::showNextWeek,
        onBackToToday = viewModel::backToToday,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onPickerQueryChange = viewModel::onPickerQueryChange,
        onResetPicker = viewModel::resetPicker,
        onAddRecipe = viewModel::addRecipe,
        onAddNote = viewModel::addNote,
        onDeleteEntry = viewModel::deleteEntry,
        recipeImageUrl = { recipe -> container.imageUrls.thumbnail(recipe.id, recipe.imageToken) },
        modifier = modifier,
    )
}

/** Stateless planning screen, driven by [PlanningUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(
    state: PlanningUiState,
    picker: RecipePickerState,
    onRecipeClick: (String) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onBackToToday: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onPickerQueryChange: (String) -> Unit,
    onResetPicker: () -> Unit,
    onAddRecipe: (LocalDate, MealType, RecipeSummary) -> Unit,
    onAddNote: (LocalDate, MealType, String) -> Unit,
    onDeleteEntry: (MealPlanEntry) -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    var sheetTarget by remember { mutableStateOf<LocalDate?>(null) }

    val density = LocalDensity.current
    LaunchedEffect(state.anchor) {
        // Yesterday is index 0 and today index 1. Today is scrolled fully into
        // view while yesterday keeps peeking on the left, so the column order
        // stays "yesterday | today | tomorrow" and today never starts cut off.
        listState.scrollToItem(
            index = 1,
            scrollOffset = -with(density) { DayPeekWidth.roundToPx() },
        )
    }

    sheetTarget?.let { date ->
        AddMealSheet(
            date = date,
            picker = picker,
            onQueryChange = onPickerQueryChange,
            recipeImageUrl = recipeImageUrl,
            onDismiss = {
                sheetTarget = null
                onResetPicker()
            },
            onAddRecipe = { type, recipe ->
                onAddRecipe(date, type, recipe)
                sheetTarget = null
                onResetPicker()
            },
            onAddNote = { type, note ->
                onAddNote(date, type, note)
                sheetTarget = null
                onResetPicker()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.planning_title)) },
                actions = {
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
                                isToday = day == state.today,
                                entries = state.entriesByDay[day].orEmpty(),
                                onAdd = { sheetTarget = day },
                                onRecipeClick = onRecipeClick,
                                onDelete = onDeleteEntry,
                                recipeImageUrl = recipeImageUrl,
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
    isToday: Boolean,
    entries: List<MealPlanEntry>,
    onAdd: () -> Unit,
    onRecipeClick: (String) -> Unit,
    onDelete: (MealPlanEntry) -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
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
                    text = date.label(dateFormatter),
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
                            onClick = { slug -> onRecipeClick(slug) },
                            onDelete = { onDelete(sorted[index]) },
                            recipeImageUrl = recipeImageUrl,
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

@Composable
private fun MealEntryCard(
    entry: MealPlanEntry,
    onClick: (String) -> Unit,
    onDelete: () -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
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

            if (recipe != null) {
                RemoteImage(
                    url = recipeImageUrl(recipe),
                    contentDescription = null,
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
        }
    }
}

/** Width of one day, and how much of the previous day stays visible. */
private val DayColumnWidth = 264.dp
private val DayPeekWidth = 56.dp

/** "Yesterday", "Today", "Tomorrow", then the localized day name. */
@Composable
fun LocalDate.label(formatter: DateTimeFormatter): String {
    val today = LocalDate.now()
    return when (this) {
        today.minusDays(1) -> stringResource(R.string.planning_yesterday)
        today -> stringResource(R.string.planning_today)
        today.plusDays(1) -> stringResource(R.string.planning_tomorrow)
        else -> dayOfWeek.localizedName()
    }
}

/** The window shown by the planning screen, starting the day before today. */
@Composable
fun rememberPlanningWeek(): List<LocalDate> {
    val today = LocalDate.now()
    return remember(today) { (-1 until PlanningUiState.DAYS_AHEAD).map { today.plusDays(it.toLong()) } }
}
