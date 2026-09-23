package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.rememberDateFormatter
import org.opensources.umai.core.model.RecipeComment
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The comments Mealie holds for this recipe, and the field that adds one.
 *
 * The bin only appears where Mealie would accept the deletion — on the reader's
 * own comments, or on any of them for an administrator — so the UI never offers
 * an action the server will refuse.
 */
internal fun LazyListScope.recipeComments(
    state: RecipeDetailUiState,
    onPostComment: (String) -> Unit,
    onDeleteComment: (RecipeComment) -> Unit,
) {
    item {
        Text(
            text = stringResource(R.string.recipe_comments),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
        )
    }

    item {
        CommentField(
            posting = state.postingComment,
            onSend = onPostComment,
        )
    }

    when {
        state.commentsLoading && state.comments.isEmpty() -> item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        state.comments.isEmpty() -> item {
            Text(
                text = stringResource(R.string.recipe_comments_empty),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> items(count = state.comments.size, key = { state.comments[it].id }) { index ->
            val comment = state.comments[index]
            CommentRow(
                comment = comment,
                canDelete = state.canDelete(comment),
                onDelete = { onDeleteComment(comment) },
            )
        }
    }
}

@Composable
private fun CommentField(posting: Boolean, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.recipe_comment_placeholder)) },
            enabled = !posting,
            minLines = 1,
            maxLines = 4,
            shape = MaterialTheme.shapes.large,
        )
        IconButton(
            onClick = {
                onSend(draft)
                draft = ""
            },
            enabled = draft.isNotBlank() && !posting,
        ) {
            if (posting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(R.string.recipe_comment_send),
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: RecipeComment,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val formatter = rememberDateFormatter(FormatStyle.MEDIUM)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = comment.authorName.ifBlank {
                        stringResource(R.string.recipe_comment_unknown_author)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                comment.dateLabel(formatter)?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.recipe_comment_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun RecipeComment.dateLabel(formatter: DateTimeFormatter): String? =
    createdAt?.toLocalDate()?.format(formatter)
