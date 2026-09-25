package org.opensources.umai.planning.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.ui.component.EmptyView
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.planning.domain.DishCourse

@Composable
fun DishTypesRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: DishTypesViewModel = viewModel(factory = DishTypesViewModel.factory(LocalAppContainer.current))
    val state by viewModel.state.collectAsStateWithLifecycle()
    DishTypesScreen(
        state = state,
        onBack = onBack,
        onChoose = viewModel::choose,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

/**
 * The categories and tags of the instance with the course the automatic
 * planning sees in each, which the user can correct: only dishes are
 * planned at lunch and dinner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishTypesScreen(
    state: DishTypesUiState,
    onBack: () -> Unit,
    onChoose: (String, DishCourse?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dish_types_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val error = state.error
            when {
                state.loading -> LoadingView()
                error != null -> NetworkErrorView(error = error, modifier = Modifier.fillMaxSize(), onRetry = onRetry)
                state.isEmpty -> EmptyView(
                    title = stringResource(R.string.dish_types_empty_title),
                    message = stringResource(R.string.dish_types_empty_message),
                    icon = Icons.Outlined.Category,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Text(
                            text = stringResource(R.string.dish_types_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.rows, key = { it.organizer.id }) { row ->
                        DishTypeItem(row, onChoose = { onChoose(row.organizer.id, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DishTypeItem(row: DishTypeRow, onChoose: (DishCourse?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(row.organizer.name) },
        supportingContent = {
            Text(
                stringResource(
                    when {
                        row.chosen != null -> R.string.dish_types_chosen
                        row.detected != null -> R.string.dish_types_detected
                        else -> R.string.dish_types_neutral
                    },
                    stringResource(if (row.isTag) R.string.dish_types_tag else R.string.dish_types_category),
                ),
            )
        },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(stringResource(row.effective?.labelRes() ?: R.string.dish_course_any))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.dish_course_auto)) },
                        onClick = {
                            expanded = false
                            onChoose(null)
                        },
                    )
                    DishCourse.entries.forEach { course ->
                        DropdownMenuItem(
                            text = { Text(stringResource(course.labelRes())) },
                            onClick = {
                                expanded = false
                                onChoose(course)
                            },
                        )
                    }
                }
            }
        },
    )
}

fun DishCourse.labelRes(): Int = when (this) {
    DishCourse.MAIN -> R.string.dish_course_main
    DishCourse.DESSERT -> R.string.dish_course_dessert
    DishCourse.DRINK -> R.string.dish_course_drink
    DishCourse.OTHER -> R.string.dish_course_other
}
