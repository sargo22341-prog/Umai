package org.opensources.umai.recipe.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.ui.component.BackTopAppBar
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.VideoStream
import java.io.File

/**
 * Edits an existing recipe. The tabs at the top lead straight to any section
 * of the recipe — the same sections as the creation form.
 *
 * [onSaved] receives the slug of the saved recipe, which changes when the
 * recipe is renamed; [onBack] leaves without anything saved; [onDeleted] is
 * called once the recipe no longer exists on Mealie.
 */
@Composable
fun RecipeEditRoute(
    slug: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: RecipeEditViewModel =
        viewModel(factory = RecipeEditViewModel.factory(container, slug), key = "recipe-edit-$slug")
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.savedSlug) {
        state.savedSlug?.let(onSaved)
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }

    // Anything saved already — the text, when only the picture failed — must
    // reach the recipe page even if the rest is abandoned.
    val leave = { state.committedSlug?.let(onSaved) ?: onBack() }
    val requestLeave = { if (state.hasChanges) confirmDiscard = true else leave() }

    BackHandler(onBack = requestLeave)

    if (confirmDiscard) {
        DiscardDialog(
            onDiscard = {
                confirmDiscard = false
                leave()
            },
            onDismiss = { confirmDiscard = false },
        )
    }

    RecipeEditScreen(
        state = state,
        actions = remember(viewModel) { RecipeFormActions(viewModel) },
        videoActions = remember(viewModel) { VideoChapterActions(viewModel) },
        currentImageUrl = state.recipe?.let { container.imageUrls.original(it.recipeId, it.imageToken) },
        stepPhotoUrl = { step ->
            step.photoPath?.let { Uri.fromFile(File(it)).toString() }
                ?: state.recipe?.let { recipe ->
                    step.photoFile?.let { container.imageUrls.recipeAsset(recipe.recipeId, it, recipe.mediaVersion) }
                }
        },
        onBack = requestLeave,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onDismissError = viewModel::dismissSaveError,
        onDelete = viewModel::delete,
        onDismissDeleteError = viewModel::dismissDeleteError,
        modifier = modifier,
    )
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_discard_title)) },
        text = { Text(stringResource(R.string.edit_discard_message)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.edit_discard_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_keep_editing)) } },
    )
}

/**
 * Stateless editor, driven by [RecipeEditUiState]. [videoPlayer] plays the
 * recipe video in the video section; a test puts a still stand-in there.
 */
@Composable
fun RecipeEditScreen(
    state: RecipeEditUiState,
    actions: RecipeFormActions,
    videoActions: VideoChapterActions,
    currentImageUrl: String?,
    stepPhotoUrl: (DraftStep) -> String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onDelete: () -> Unit,
    onDismissDeleteError: () -> Unit,
    modifier: Modifier = Modifier,
    videoPlayer: @Composable (VideoStream, ChapterPlayerState) -> Unit = { stream, player -> ChapterVideoPlayer(stream, player) },
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        DeleteRecipeDialog(
            name = state.savedName,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                EditTopBar(state, onBack = onBack, onSave = onSave, onDelete = { confirmDelete = true })
                if (state.recipe != null) {
                    SectionTabs(sections = state.sections, selected = state.section, onSelect = actions.onShowSection)
                }
            }
        },
    ) { padding ->
        val loadError = state.loadError
        when {
            state.loading -> LoadingView(Modifier.padding(padding))
            loadError != null -> NetworkErrorView(loadError, Modifier.fillMaxSize().padding(padding), onRetry = onRetry)
            else -> SlidingSections(state.section, Modifier.fillMaxSize().padding(padding).imePadding()) { section ->
                val banners = @Composable { EditBanners(state, onDismissError, onDismissDeleteError) }
                // The player stays in view while the steps scroll under it.
                if (section == RecipeFormSection.VIDEO) {
                    VideoSection(
                        draft = state.draft,
                        video = VideoSectionState(state.videoStream, state.videoStreamLoading, state.videoStreamFailed),
                        actions = videoActions,
                        videoPlayer = videoPlayer,
                        banners = banners,
                    )
                } else {
                    FormSection(section, state, currentImageUrl, stepPhotoUrl, actions, onDismissError, onDismissDeleteError)
                }
            }
        }
    }
}

/**
 * Slides from one section to the next the way the tabs are laid out: towards a
 * later section from the end edge, towards an earlier one from the start.
 */
@Composable
private fun SlidingSections(
    section: RecipeFormSection,
    modifier: Modifier,
    content: @Composable (RecipeFormSection) -> Unit,
) {
    AnimatedContent(
        targetState = section,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(tween(SECTION_MILLIS)) { it / 4 * direction } + fadeIn(tween(SECTION_MILLIS)))
                .togetherWith(
                    slideOutHorizontally(tween(SECTION_MILLIS)) { -it / 4 * direction } +
                        fadeOut(tween(SECTION_MILLIS / 2)),
                )
        },
        label = "editSection",
    ) { shown -> content(shown) }
}

@Composable
private fun EditTopBar(state: RecipeEditUiState, onBack: () -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    BackTopAppBar(
        title = stringResource(R.string.edit_title),
        onBack = onBack,
        actions = {
            if (state.saving || state.deleting) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 16.dp).size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = onSave, enabled = state.canSave) {
                    Text(stringResource(R.string.edit_save))
                }
            }
            if (state.recipe != null) EditMenu(enabled = state.canDelete, onDelete = onDelete)
        },
    )
}

/** One section of the form but the video, which keeps its player in view: it scrolls whole. */
@Composable
private fun FormSection(
    section: RecipeFormSection,
    state: RecipeEditUiState,
    currentImageUrl: String?,
    stepPhotoUrl: (DraftStep) -> String?,
    actions: RecipeFormActions,
    onDismissError: () -> Unit,
    onDismissDeleteError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditBanners(state, onDismissError, onDismissDeleteError)
        when (section) {
            RecipeFormSection.BASICS -> BasicsSection(state.draft, actions)
            RecipeFormSection.IMAGE -> ImageSection(
                imageUrl = state.newImagePath?.let { Uri.fromFile(File(it)).toString() } ?: currentImageUrl,
                processing = state.processingImage,
                canRemove = state.newImagePath != null,
                failed = state.imageFailed,
                actions = actions,
            )
            RecipeFormSection.INGREDIENTS -> IngredientsSection(state.draft, actions)
            RecipeFormSection.INSTRUCTIONS -> InstructionsSection(state.draft, state.steps, stepPhotoUrl, actions)
            RecipeFormSection.ORGANIZERS -> OrganizersSection(
                draft = state.draft,
                categories = state.categories,
                tags = state.tags,
                loading = state.loadingOrganizers,
                actions = actions,
            )
            // Laid out by the caller, outside this scrolling column.
            RecipeFormSection.VIDEO -> Unit
        }
        Spacer(Modifier.size(12.dp))
    }
}

/** What went wrong with the last save or deletion, above the section. */
@Composable
private fun EditBanners(state: RecipeEditUiState, onDismissError: () -> Unit, onDismissDeleteError: () -> Unit) {
    state.saveError?.let { error ->
        ErrorBanner(message = "${error.title()}\n${error.message()}", onDismiss = onDismissError)
    }
    state.deleteError?.let { error ->
        ErrorBanner(
            message = "${stringResource(R.string.edit_delete_failed)}\n${error.title()}\n${error.message()}",
            onDismiss = onDismissDeleteError,
        )
    }
}

/** The actions on the recipe as a whole, behind the ⋮ of the top bar. */
@Composable
private fun EditMenu(enabled: Boolean, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.edit_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Deleting cannot be undone, and removes the recipe for everyone on the instance: it is confirmed by name. */
@Composable
private fun DeleteRecipeDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.edit_delete_title, name)) },
        text = { Text(stringResource(R.string.edit_delete_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.edit_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** The menu of the editor: one tab per section, scrollable at large font sizes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionTabs(sections: List<RecipeFormSection>, selected: RecipeFormSection, onSelect: (RecipeFormSection) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = sections.indexOf(selected).coerceAtLeast(0), edgePadding = 12.dp) {
        sections.forEach { section ->
            Tab(
                selected = section == selected,
                onClick = { onSelect(section) },
                text = { Text(stringResource(section.labelRes())) },
            )
        }
    }
}

private const val SECTION_MILLIS = 250
