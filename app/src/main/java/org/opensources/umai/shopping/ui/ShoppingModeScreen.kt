package org.opensources.umai.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.ui.component.KeepScreenOn
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title

/**
 * One shopping list, made for the shop: large rows ticked with a single tap
 * anywhere on them, grouped by aisle, what is already in the basket gathered
 * at the bottom, and the screen kept awake.
 */
@Composable
fun ShoppingModeScreen(
    listId: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.factory(container, listId))
    val state by viewModel.state.collectAsStateWithLifecycle()

    ShoppingModeScreen(
        state = state,
        onExit = onExit,
        onCheckedChange = viewModel::setChecked,
        onRetry = viewModel::loadLists,
        onErrorShown = viewModel::dismissError,
        modifier = modifier,
    )
}

/** Stateless shopping mode, driven by [ShoppingUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingModeScreen(
    state: ShoppingUiState,
    onExit: () -> Unit,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    onRetry: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val list = state.list

    KeepScreenOn()

    // A failed tick is put back and explained, without leaving the list.
    val error = state.error
    val errorMessage = error?.let { "${it.title()}\n${it.message()}" }
    LaunchedEffect(error) {
        if (errorMessage != null && list != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = list?.name ?: stringResource(R.string.shopping_mode_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (list != null) {
                                Text(
                                    text = stringResource(
                                        R.string.shopping_mode_progress,
                                        state.basketItems.size,
                                        list.items.size,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onExit) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.shopping_mode_exit),
                            )
                        }
                    },
                )
                if (list != null && list.items.isNotEmpty()) {
                    LinearProgressIndicator(
                        progress = { state.basketItems.size.toFloat() / list.items.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                list == null && error != null -> NetworkErrorView(
                    error = error,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = onRetry,
                )

                list == null -> LoadingView()

                list.items.isEmpty() -> EmptyList()

                else -> ShoppingModeList(
                    remaining = state.remainingItems,
                    basket = state.basketItems,
                    onCheckedChange = onCheckedChange,
                    onExit = onExit,
                )
            }
        }
    }
}

@Composable
private fun ShoppingModeList(
    remaining: List<ShoppingItem>,
    basket: List<ShoppingItem>,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    onExit: () -> Unit,
) {
    var basketVisible by rememberSaveable { mutableStateOf(false) }
    val aisles = remember(remaining) { remaining.groupedByLabel() }
    val unlabelled = stringResource(R.string.shopping_unlabelled)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Everything is in the basket: said at the top, the basket still at hand below.
        if (remaining.isEmpty()) {
            item(key = "done") { Complete(onExit = onExit, modifier = Modifier.animateItem()) }
        }

        aisles.forEach { (label, items) ->
            item(key = "aisle-${label ?: "none"}") {
                SectionHeader(
                    text = label ?: unlabelled,
                    color = items.firstOrNull()?.labelColor,
                    modifier = Modifier.animateItem(),
                )
            }
            items(count = items.size, key = { items[it].id }) { index ->
                ShoppingModeRow(
                    item = items[index],
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (basket.isNotEmpty()) {
            item(key = "basket") {
                BasketHeader(
                    count = basket.size,
                    expanded = basketVisible,
                    onToggle = { basketVisible = !basketVisible },
                    modifier = Modifier.animateItem(),
                )
            }
            if (basketVisible) {
                items(count = basket.size, key = { basket[it].id }) { index ->
                    ShoppingModeRow(
                        item = basket[index],
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** The whole row is the touch target: in a shop, a checkbox is too small to aim at. */
@Composable
private fun ShoppingModeRow(
    item: ShoppingItem,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = item.checked,
                role = Role.Checkbox,
                onValueChange = { onCheckedChange(item, it) },
            ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = if (item.checked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (item.checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = item.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
        }
    }
}

@Composable
private fun BasketHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .toggleable(value = expanded, role = Role.Button, onValueChange = { onToggle() }),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.shopping_mode_basket, count),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun Complete(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = stringResource(R.string.shopping_mode_done),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onExit, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.shopping_mode_finish))
        }
    }
}

@Composable
private fun EmptyList() {
    Text(
        text = stringResource(R.string.shopping_empty_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}
