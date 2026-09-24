package org.opensources.umai.planning.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import org.opensources.umai.recipe.ui.labelRes
import org.opensources.umai.search.ui.FilterOptionsState
import org.opensources.umai.search.ui.RecipeSearchActions
import org.opensources.umai.search.ui.RecipeSearchContent
import org.opensources.umai.search.ui.SearchUiState
import org.opensources.umai.search.ui.SearchViewModel
import java.time.LocalDate
import java.time.format.FormatStyle

/**
 * The recipe search of the app, with its order and filters, on a screen of its
 * own: tapping a result puts it on the meal plan and comes back to the week.
 */
@Composable
fun PlanRecipePickerScreen(
    date: LocalDate,
    mealType: MealType,
    fieldOriginY: Float,
    onBack: () -> Unit,
    onAdded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val search: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val picker: PlanRecipePickerViewModel =
        viewModel(factory = PlanRecipePickerViewModel.factory(container, date, mealType))
    val searchState by search.state.collectAsStateWithLifecycle()
    val filterOptions by search.filterOptions.collectAsStateWithLifecycle()
    val state by picker.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.added) {
        if (state.added) onAdded()
    }

    PlanRecipePickerScreen(
        state = state,
        search = searchState,
        filterOptions = filterOptions,
        searchActions = remember(search) { RecipeSearchActions.of(search) },
        onBack = onBack,
        onPick = picker::add,
        onErrorShown = picker::dismissError,
        recipeImageUrl = { recipe -> container.imageUrls.thumbnail(recipe.id, recipe.imageToken) },
        modifier = modifier,
        fieldOriginY = fieldOriginY,
    )
}

/**
 * Stateless recipe picker, driven by [PlanRecipePickerUiState] and [SearchUiState].
 *
 * Opened from a field at [fieldOriginY], the on-screen centre of that field,
 * its own search field first shows there, then slides up to its place while the
 * rest of the screen fades in around it. The sheet stays up until the screen is
 * composed, over this screen still transparent with its field at the origin: the
 * animation starts once the field is measured and plays whole, however long the
 * screen took to compose. The keyboard comes up once the field is in its place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanRecipePickerScreen(
    state: PlanRecipePickerUiState,
    search: SearchUiState,
    filterOptions: FilterOptionsState,
    searchActions: RecipeSearchActions,
    onBack: () -> Unit,
    onPick: (RecipeSummary) -> Unit,
    onErrorShown: () -> Unit,
    recipeImageUrl: (RecipeSummary) -> String?,
    modifier: Modifier = Modifier,
    fieldOriginY: Float? = null,
) {
    // Measured before any translation, so the distance to travel is the real one.
    var fieldCenterY by remember { mutableStateOf<Float?>(null) }
    val travel = fieldOriginY?.let { origin -> fieldCenterY?.let { origin - it } }
    // Saved, so that coming back to the screen does not play the entrance again.
    var revealed by rememberSaveable { mutableStateOf(fieldOriginY == null) }
    val reveal = remember { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(travel != null) {
        if (travel != null && !revealed) {
            reveal.animateTo(1f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
            revealed = true
        }
    }
    val background = MaterialTheme.colorScheme.background
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)

    val error = state.error
    val errorMessage = error?.let { "${it.title()}\n${it.message()}" }
    LaunchedEffect(error) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    Scaffold(
        // Transparent before the entrance: the week shows under the sheet until it goes.
        modifier = modifier.drawBehind { drawRect(background, alpha = reveal.value) },
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                modifier = Modifier.graphicsLayer { alpha = reveal.value },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.planning_choose_recipe),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.planning_entry_for,
                                "${state.date.label()} ${state.date.format(dateFormatter)}",
                                stringResource(state.mealType.labelRes()),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.adding) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            RecipeSearchContent(
                state = search,
                filterOptions = filterOptions,
                actions = searchActions,
                onRecipeClick = { if (!state.adding) onPick(it) },
                recipeImageUrl = recipeImageUrl,
                autoFocus = revealed,
                fieldModifier = Modifier
                    .onGloballyPositioned { fieldCenterY = it.positionOnScreen().y + it.size.height / 2f }
                    .graphicsLayer {
                        // Hidden for the frame it takes to measure the field, so it
                        // never flashes at its place before starting from the sheet's.
                        alpha = if (fieldOriginY == null || travel != null) 1f else 0f
                        translationY = (travel ?: 0f) * (1f - reveal.value)
                    },
                bodyModifier = Modifier.graphicsLayer {
                    alpha = ((reveal.value - BODY_DELAY) / (1f - BODY_DELAY)).coerceIn(0f, 1f)
                    translationY = (travel ?: 0f) * (1f - reveal.value)
                },
            )
        }
    }
}

/** The results wait for the field to be well on its way before fading in. */
private const val BODY_DELAY = 0.3f

private const val REVEAL_MILLIS = 350
