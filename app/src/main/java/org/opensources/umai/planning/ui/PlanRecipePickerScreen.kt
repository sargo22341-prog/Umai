package org.opensources.umai.planning.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    )
}

/** Stateless recipe picker, driven by [PlanRecipePickerUiState] and [SearchUiState]. */
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
) {
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
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
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
            )
        }
    }
}
