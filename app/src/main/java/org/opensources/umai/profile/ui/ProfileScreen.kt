package org.opensources.umai.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.model.HouseholdStatistics
import org.opensources.umai.core.model.UserProfile
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.CropFrame
import org.opensources.umai.core.ui.component.ImagePicker
import org.opensources.umai.core.ui.component.UserAvatar
import org.opensources.umai.core.ui.component.rememberImagePickerState
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title

/**
 * The account page: who is signed in, what the household holds, the two sets of
 * settings and the ways of adding a recipe.
 */
@Composable
fun ProfileScreen(
    onOpenAppSettings: () -> Unit,
    onOpenMealieSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onImportRecipe: () -> Unit,
    onCreateRecipe: () -> Unit,
    onOpenDrafts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePicker = rememberImagePickerState()
    ImagePicker(
        state = imagePicker,
        frame = CropFrame.AVATAR,
        onImageReady = viewModel::updateAvatar,
        onCameraUnavailable = viewModel::onCameraUnavailable,
    )

    val event = state.event
    val message: String? = when (event) {
        null -> null
        ProfileEvent.AvatarUpdated -> stringResource(R.string.profile_avatar_updated)
        ProfileEvent.CameraUnavailable -> stringResource(R.string.image_camera_unavailable)
        is ProfileEvent.Failed -> "${event.error.title()}\n${event.error.message()}"
    }
    LaunchedEffect(event) {
        message?.let {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(it)
        }
        if (event != null) viewModel.consumeEvent()
    }

    ProfileScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        avatarUrl = { user -> container.imageUrls.userAvatar(user.id, user.cacheKey) },
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onPickAvatar = imagePicker::open,
        onOpenAppSettings = onOpenAppSettings,
        onOpenProviders = onOpenProviders,
        onOpenMealieSettings = onOpenMealieSettings,
        onImportRecipe = onImportRecipe,
        onCreateRecipe = onCreateRecipe,
        onOpenDrafts = onOpenDrafts,
        modifier = modifier,
    )
}

/** Stateless profile page, driven by [ProfileUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    snackbarHostState: SnackbarHostState,
    avatarUrl: (UserProfile) -> String?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onPickAvatar: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenMealieSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onImportRecipe: () -> Unit,
    onCreateRecipe: () -> Unit,
    onOpenDrafts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val error = state.error
        when {
            state.loading -> LoadingView(Modifier.padding(padding))

            error != null && !state.hasContent -> NetworkErrorView(
                error = error,
                modifier = Modifier.fillMaxSize().padding(padding),
                onRetry = onRetry,
            )

            else -> PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        IdentityHeader(
                            user = state.user,
                            avatarUrl = state.user?.let(avatarUrl),
                            uploading = state.uploadingAvatar,
                            onPickAvatar = onPickAvatar,
                        )
                    }

                    state.statistics?.let { statistics ->
                        item { StatisticsRow(statistics) }
                    }

                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    item { SectionHeader(stringResource(R.string.profile_section_add)) }

                    item {
                        NavigationRow(
                            title = stringResource(R.string.profile_import_recipe),
                            summary = stringResource(R.string.profile_import_recipe_summary),
                            icon = Icons.Outlined.Link,
                            onClick = onImportRecipe,
                        )
                        NavigationRow(
                            title = stringResource(R.string.profile_create_recipe),
                            summary = stringResource(R.string.profile_create_recipe_summary),
                            icon = Icons.Outlined.AddCircleOutline,
                            onClick = onCreateRecipe,
                        )
                        NavigationRow(
                            title = stringResource(R.string.profile_drafts),
                            summary = if (state.draftCount == 0) {
                                stringResource(R.string.profile_drafts_empty)
                            } else {
                                pluralStringResource(
                                    R.plurals.plural_drafts,
                                    state.draftCount,
                                    state.draftCount,
                                )
                            },
                            icon = Icons.Outlined.EditNote,
                            onClick = onOpenDrafts,
                        )
                    }

                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    item { SectionHeader(stringResource(R.string.profile_section_settings)) }

                    item {
                        NavigationRow(
                            title = stringResource(R.string.settings_mealie_title),
                            summary = stringResource(R.string.settings_mealie_summary),
                            icon = Icons.Outlined.Dns,
                            onClick = onOpenMealieSettings,
                        )
                        NavigationRow(
                            title = stringResource(R.string.settings_app_title),
                            summary = stringResource(R.string.settings_app_summary),
                            icon = Icons.Outlined.Tune,
                            onClick = onOpenAppSettings,
                        )
                        NavigationRow(
                            title = stringResource(R.string.providers_title),
                            summary = stringResource(R.string.providers_summary),
                            icon = Icons.Outlined.Extension,
                            onClick = onOpenProviders,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityHeader(
    user: UserProfile?,
    avatarUrl: String?,
    uploading: Boolean,
    onPickAvatar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            UserAvatar(
                url = avatarUrl,
                initials = user?.initials ?: "?",
                contentDescription = stringResource(R.string.profile_change_avatar),
                modifier = Modifier.clickable(enabled = !uploading, onClick = onPickAvatar),
                size = 84.dp,
                textStyle = 30.sp,
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (uploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = user?.displayName.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            user?.email?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            user?.let {
                Text(
                    text = stringResource(
                        R.string.profile_group_and_household,
                        it.groupName,
                        it.householdName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatisticsRow(statistics: HouseholdStatistics) {
    val cells = listOf(
        stringResource(R.string.profile_stat_recipes) to statistics.recipes,
        stringResource(R.string.profile_stat_categories) to statistics.categories,
        stringResource(R.string.profile_stat_tags) to statistics.tags,
        stringResource(R.string.profile_stat_tools) to statistics.tools,
        stringResource(R.string.profile_stat_users) to statistics.users,
    )
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(count = cells.size) { index ->
            val (label, value) = cells[index]
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = value.toString(), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NavigationRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
