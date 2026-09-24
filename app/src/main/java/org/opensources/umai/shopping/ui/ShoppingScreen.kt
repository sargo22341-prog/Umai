package org.opensources.umai.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.ui.component.EmptyView
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView

/**
 * Shopping lists backed by Mealie. Items are grouped by the Mealie label of
 * their food, which is how the web UI organizes an aisle-friendly list.
 */
@Composable
fun ShoppingScreen(onStartShoppingMode: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The tab keeps its ViewModel while the user walks through other screens,
    // so the data is asked for again every time the screen comes back.
    LaunchedEffect(Unit) { viewModel.onScreenShown() }

    ShoppingScreen(
        state = state,
        onSelectList = viewModel::selectList,
        onCreateList = viewModel::createList,
        onDeleteList = viewModel::deleteList,
        onAddItem = viewModel::addItem,
        onCheckedChange = viewModel::setChecked,
        onDeleteItem = viewModel::deleteItem,
        onRetry = viewModel::loadLists,
        onRefresh = viewModel::refresh,
        onStartShoppingMode = onStartShoppingMode,
        modifier = modifier,
    )
}

/** Stateless shopping screen, driven by [ShoppingUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    state: ShoppingUiState,
    onSelectList: (String) -> Unit,
    onCreateList: (String) -> Unit,
    onDeleteList: (String) -> Unit,
    onAddItem: (String) -> Unit,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onStartShoppingMode: (String) -> Unit = {},
) {
    var newListDialogVisible by remember { mutableStateOf(false) }
    var deleteDialogVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    if (newListDialogVisible) {
        NewListDialog(
            onDismiss = { newListDialogVisible = false },
            onConfirm = {
                onCreateList(it)
                newListDialogVisible = false
            },
        )
    }

    val currentList = state.list
    if (deleteDialogVisible && currentList != null) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text(stringResource(R.string.shopping_delete_list_title, currentList.name)) },
            text = { Text(stringResource(R.string.shopping_delete_list_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteList(currentList.id)
                        deleteDialogVisible = false
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                actions = {
                    if (currentList != null && currentList.items.isNotEmpty()) {
                        TextButton(onClick = { onStartShoppingMode(currentList.id) }) {
                            Icon(Icons.Outlined.ShoppingBasket, contentDescription = null)
                            Text(
                                text = stringResource(R.string.shopping_mode_title),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    IconButton(onClick = { newListDialogVisible = true }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.shopping_new_list),
                        )
                    }
                    if (currentList != null) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.shopping_delete_list)) },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Delete, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        deleteDialogVisible = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (state.lists.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(count = state.lists.size, key = { state.lists[it].id }) { index ->
                        val list = state.lists[index]
                        FilterChip(
                            selected = list.id == state.selectedListId,
                            onClick = { onSelectList(list.id) },
                            label = { Text(list.name) },
                        )
                    }
                }
            }

            val error = state.error
            when {
                state.loadingLists -> LoadingView()

                error != null && state.list == null -> NetworkErrorView(
                    error = error,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = onRetry,
                )

                state.hasNoList -> EmptyView(
                    title = stringResource(R.string.shopping_no_lists_title),
                    message = stringResource(R.string.shopping_no_lists_message),
                    modifier = Modifier.fillMaxSize(),
                    actionLabel = stringResource(R.string.shopping_new_list),
                    onAction = { newListDialogVisible = true },
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ListContent(
                        items = currentList?.items.orEmpty(),
                        onCheckedChange = onCheckedChange,
                        onDelete = onDeleteItem,
                        onAddItem = onAddItem,
                        isEmpty = state.isListEmpty,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListContent(
    items: List<ShoppingItem>,
    isEmpty: Boolean,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onAddItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val unchecked = items.filterNot { it.checked }
    val checked = items.filter { it.checked }
    val grouped = remember(unchecked) { unchecked.groupedByLabel() }
    val unlabelled = stringResource(R.string.shopping_unlabelled)

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (isEmpty) {
                item {
                    EmptyView(
                        title = stringResource(R.string.shopping_empty_title),
                        message = stringResource(R.string.shopping_empty_message),
                    )
                }
            }

            grouped.forEach { (label, groupItems) ->
                item(key = "header-${label ?: "none"}") {
                    SectionHeader(
                        text = label ?: unlabelled,
                        color = groupItems.firstOrNull()?.labelColor,
                    )
                }
                items(count = groupItems.size, key = { groupItems[it].id }) { index ->
                    ShoppingItemRow(
                        item = groupItems[index],
                        onCheckedChange = onCheckedChange,
                        onDelete = onDelete,
                    )
                }
            }

            if (checked.isNotEmpty()) {
                item(key = "header-checked") {
                    SectionHeader(
                        text = stringResource(R.string.shopping_checked_section, checked.size),
                        color = null,
                    )
                }
                items(count = checked.size, key = { checked[it].id }) { index ->
                    ShoppingItemRow(
                        item = checked[index],
                        onCheckedChange = onCheckedChange,
                        onDelete = onDelete,
                    )
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.shopping_item_placeholder)) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            IconButton(
                onClick = {
                    onAddItem(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.shopping_add_item),
                )
            }
        }
    }
}

/** The name of an aisle — a Mealie label — with its colour. */
@Composable
internal fun SectionHeader(text: String, color: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        color?.let { hex ->
            parseHexColor(hex)?.let { parsed ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(parsed, CircleShape),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    onCheckedChange: (ShoppingItem, Boolean) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = { onCheckedChange(item, it) },
            )
            Text(
                text = item.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            IconButton(onClick = { onDelete(item) }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_new_list)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.shopping_list_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Items by the Mealie label of their food, labels in alphabetical order and unlabelled items last. */
internal fun List<ShoppingItem>.groupedByLabel(): Map<String?, List<ShoppingItem>> =
    groupBy { it.labelName }.toSortedMap(compareBy(nullsLast()) { it })

/** Mealie labels carry a `#rrggbb` colour; anything else is ignored. */
private fun parseHexColor(hex: String): Color? {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val value = cleaned.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}
