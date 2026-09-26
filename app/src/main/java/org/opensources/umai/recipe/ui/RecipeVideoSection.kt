package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Start
import androidx.compose.material.icons.automirrored.outlined.KeyboardTab
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.recipe.domain.DraftChapter
import org.opensources.umai.recipe.domain.RecipeDraft
import org.opensources.umai.recipe.domain.VideoChapters
import org.opensources.umai.recipe.domain.VideoStream
import org.opensources.umai.recipe.domain.VideoTime

/** The callbacks of the video section. */
@Immutable
class VideoChapterActions(
    val onStartChange: (Int, Double?) -> Unit,
    val onEndChange: (Int, Double?) -> Unit,
    val onRetryVideo: () -> Unit,
) {
    constructor(editing: VideoChapterEditing) : this(
        onStartChange = editing::setChapterStart,
        onEndChange = editing::setChapterEnd,
        onRetryVideo = editing::loadVideoStream,
    )
}

/** What the video section shows of the recipe video. */
data class VideoSectionState(
    val stream: VideoStream?,
    val loading: Boolean,
    val failed: Boolean,
)

/**
 * Places each step in the recipe video, for the cooking mode to play: the
 * player stays at the top, the steps scroll below it. A time is typed, or
 * taken from where the player is; a step without a start is not in the video.
 * [banners] shows the errors of the editor above the steps.
 */
@Composable
internal fun VideoSection(
    draft: RecipeDraft,
    video: VideoSectionState,
    actions: VideoChapterActions,
    videoPlayer: @Composable (VideoStream, ChapterPlayerState) -> Unit,
    banners: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = remember { ChapterPlayerState() }
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            when {
                video.stream != null -> videoPlayer(video.stream, player)
                video.loading -> VideoPlaceholder {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(stringResource(R.string.edit_video_loading), style = MaterialTheme.typography.bodyMedium)
                }
                else -> VideoPlaceholder {
                    Text(stringResource(R.string.edit_video_unavailable), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = actions.onRetryVideo) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            banners()
            Text(
                text = stringResource(R.string.edit_video_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val canUsePlayer = video.stream != null
            ChapterRow(
                title = stringResource(R.string.recipe_ingredients),
                subtitle = null,
                chapter = draft.video?.ingredients,
                stepIndex = VideoChapters.INGREDIENTS,
                player = player,
                canUsePlayer = canUsePlayer,
                actions = actions,
            )
            // The file numbers the steps as they are written: an empty step is not one.
            draft.steps.withIndex()
                .filter { (_, step) -> step.text.isNotBlank() || step.title.isNotBlank() }
                .forEachIndexed { position, (index, step) ->
                    ChapterRow(
                        title = stringResource(R.string.create_step_label, position + 1),
                        subtitle = step.title.ifBlank { step.text }.trim(),
                        chapter = step.chapter,
                        stepIndex = index,
                        player = player,
                        canUsePlayer = canUsePlayer,
                        actions = actions,
                    )
                }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun VideoPlaceholder(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

/** One step: where it starts and ends in the video. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterRow(
    title: String,
    subtitle: String?,
    chapter: DraftChapter?,
    stepIndex: Int,
    player: ChapterPlayerState,
    canUsePlayer: Boolean,
    actions: VideoChapterActions,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                verticalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ChapterTimeField(
                    value = chapter?.start,
                    onValueChange = { actions.onStartChange(stepIndex, it) },
                    label = stringResource(R.string.edit_video_start),
                    placeholder = stringResource(R.string.edit_video_not_placed),
                    error = null,
                )
                IconButton(
                    onClick = { actions.onStartChange(stepIndex, player.positionSeconds) },
                    enabled = canUsePlayer,
                ) {
                    Icon(Icons.Outlined.Start, contentDescription = stringResource(R.string.edit_video_start_here, title))
                }
                IconButton(
                    onClick = { chapter?.let { player.playFrom(it.start) } },
                    enabled = canUsePlayer && chapter != null,
                ) {
                    Icon(Icons.Outlined.PlayCircle, contentDescription = stringResource(R.string.edit_video_play_step, title))
                }
                IconButton(onClick = { actions.onStartChange(stepIndex, null) }, enabled = chapter != null) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.edit_video_remove, title))
                }
            }
            if (chapter != null) {
                FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                    ChapterTimeField(
                        value = chapter.end,
                        onValueChange = { actions.onEndChange(stepIndex, it) },
                        label = stringResource(R.string.edit_video_end),
                        placeholder = stringResource(R.string.edit_video_end_next),
                        error = stringResource(R.string.edit_video_end_before_start).takeUnless { chapter.isValid },
                    )
                    IconButton(
                        onClick = { actions.onEndChange(stepIndex, player.positionSeconds) },
                        enabled = canUsePlayer,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardTab, contentDescription = stringResource(R.string.edit_video_end_here, title))
                    }
                    IconButton(onClick = { actions.onEndChange(stepIndex, null) }, enabled = chapter.end != null) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.edit_video_end_clear, title))
                    }
                }
            }
        }
    }
}

/**
 * A time typed as `1:25`. What is typed stays as typed while it is being
 * written; the field only follows the value when it changes from elsewhere,
 * such as the player. An empty field means no time.
 */
@Composable
private fun ChapterTimeField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    label: String,
    placeholder: String,
    error: String?,
) {
    var text by rememberSaveable { mutableStateOf(value?.let(VideoTime::format).orEmpty()) }
    LaunchedEffect(value) {
        val typed = if (text.isBlank()) null else VideoTime.parse(text)
        if (typed != value) text = value?.let(VideoTime::format).orEmpty()
    }
    val unreadable = text.isNotBlank() && VideoTime.parse(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            if (typed.isBlank()) onValueChange(null) else VideoTime.parse(typed)?.let(onValueChange)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = unreadable || error != null,
        supportingText = when {
            unreadable -> { { Text(stringResource(R.string.edit_video_time_format)) } }
            error != null -> { { Text(error) } }
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.widthIn(min = 120.dp, max = 200.dp),
    )
}
