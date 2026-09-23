package org.opensources.umai.recipe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.ui.component.EmptyView
import org.opensources.umai.recipe.domain.RecipeDraft
import java.time.Instant
import java.time.ZoneId
import java.time.format.FormatStyle

/** The recipes started in the app but not published to Mealie yet. */
@Composable
fun RecipeDraftsScreen(
    onBack: () -> Unit,
    onOpenDraft: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: RecipeDraftsViewModel =
        viewModel(factory = RecipeDraftsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    RecipeDraftsScreen(
        state = state,
        onBack = onBack,
        onOpenDraft = onOpenDraft,
        onDeleteDraft = viewModel::delete,
        modifier = modifier,
    )
}

/** Stateless drafts list, driven by [RecipeDraftsUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDraftsScreen(
    state: RecipeDraftsUiState,
    onBack: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeletion by remember { mutableStateOf<RecipeDraft?>(null) }
    val dateFormatter = rememberDateFormatter(FormatStyle.MEDIUM)

    pendingDeletion?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.drafts_delete_title)) },
            text = { Text(stringResource(R.string.drafts_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDraft(draft.id)
                        pendingDeletion = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_drafts)) },
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
        if (state.isEmpty) {
            EmptyView(
                title = stringResource(R.string.drafts_empty_title),
                message = stringResource(R.string.drafts_empty_message),
                icon = Icons.Outlined.EditNote,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(count = state.drafts.size, key = { state.drafts[it].id }) { index ->
                val draft = state.drafts[index]
                ListItem(
                    headlineContent = {
                        Text(
                            text = draft.displayName.ifBlank {
                                stringResource(R.string.drafts_untitled)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.drafts_updated_at,
                                draft.updatedAtLabel(dateFormatter),
                            ),
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.EditNote, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { pendingDeletion = draft }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDraft(draft.id) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

private fun RecipeDraft.updatedAtLabel(formatter: java.time.format.DateTimeFormatter): String =
    if (updatedAt <= 0L) {
        ""
    } else {
        Instant.ofEpochMilli(updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
    }
