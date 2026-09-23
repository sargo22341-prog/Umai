package org.opensources.umai.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import org.opensources.umai.core.format.localizedName
import org.opensources.umai.core.session.AuthMode
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import java.time.DayOfWeek

@Composable
fun MealieSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: MealieSettingsViewModel =
        viewModel(factory = MealieSettingsViewModel.factory(container))
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    MealieSettingsScreen(
        session = session,
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onCheckConnection = viewModel::checkConnection,
        onSignOut = viewModel::signOut,
        onFirstDayChange = viewModel::setFirstDayOfWeek,
        onShowNutritionChange = viewModel::setShowNutrition,
        onShowAssetsChange = viewModel::setShowAssets,
        onDisableCommentsChange = viewModel::setDisableComments,
        onRecipePublicChange = viewModel::setRecipePublic,
        onPrivateHouseholdChange = viewModel::setPrivateHousehold,
        onDismissSaveError = viewModel::dismissSaveError,
        modifier = modifier,
    )
}

/** Stateless Mealie settings, driven by [MealieSettingsUiState]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealieSettingsScreen(
    session: SessionState,
    state: MealieSettingsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCheckConnection: () -> Unit,
    onSignOut: () -> Unit,
    onFirstDayChange: (DayOfWeek) -> Unit,
    onShowNutritionChange: (Boolean) -> Unit,
    onShowAssetsChange: (Boolean) -> Unit,
    onDisableCommentsChange: (Boolean) -> Unit,
    onRecipePublicChange: (Boolean) -> Unit,
    onPrivateHouseholdChange: (Boolean) -> Unit,
    onDismissSaveError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var signOutDialogVisible by remember { mutableStateOf(false) }

    if (signOutDialogVisible) {
        AlertDialog(
            onDismissRequest = { signOutDialogVisible = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        signOutDialogVisible = false
                        onSignOut()
                    },
                ) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { signOutDialogVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_mealie_title)) },
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
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item { SettingsSectionHeader(stringResource(R.string.settings_section_connection)) }
                item { ConnectionBlock(session = session, state = state) }
                item {
                    ConnectionActions(
                        checking = state.connectionCheck == ConnectionCheck.CHECKING,
                        onCheckConnection = onCheckConnection,
                        onChangeInstance = { signOutDialogVisible = true },
                    )
                }

                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader(stringResource(R.string.settings_section_household)) }

                if (!state.canManageHousehold) {
                    item { ReadOnlyNotice() }
                }

                state.saveError?.let { error ->
                    item {
                        SaveErrorNotice(
                            message = "${error.title()} — ${error.message()}",
                            onDismiss = onDismissSaveError,
                        )
                    }
                }

                val household = state.household
                if (household == null) {
                    item {
                        Text(
                            text = stringResource(
                                if (state.loading) {
                                    R.string.loading
                                } else {
                                    R.string.settings_household_unavailable
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    item {
                        SettingsChoiceRow(
                            title = stringResource(R.string.settings_first_day_of_week),
                            options = FirstDayOptions,
                            selected = household.firstDay,
                            labelOf = { it.localizedName() },
                            onSelect = onFirstDayChange,
                            enabled = state.householdEditable,
                        )
                    }
                    item {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_show_nutrition),
                            summary = stringResource(R.string.settings_show_nutrition_summary),
                            checked = household.recipeShowNutrition,
                            onCheckedChange = onShowNutritionChange,
                            enabled = state.householdEditable,
                        )
                    }
                    item {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_show_assets),
                            summary = stringResource(R.string.settings_show_assets_summary),
                            checked = household.recipeShowAssets,
                            onCheckedChange = onShowAssetsChange,
                            enabled = state.householdEditable,
                        )
                    }
                    item {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_disable_comments),
                            summary = stringResource(R.string.settings_disable_comments_summary),
                            checked = household.recipeDisableComments,
                            onCheckedChange = onDisableCommentsChange,
                            enabled = state.householdEditable,
                        )
                    }
                    item {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_recipe_public),
                            summary = stringResource(R.string.settings_recipe_public_summary),
                            checked = household.recipePublic,
                            onCheckedChange = onRecipePublicChange,
                            enabled = state.householdEditable,
                        )
                    }
                    item {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_private_household),
                            summary = stringResource(R.string.settings_private_household_summary),
                            checked = household.privateHousehold,
                            onCheckedChange = onPrivateHouseholdChange,
                            enabled = state.householdEditable,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBlock(session: SessionState, state: MealieSettingsUiState) {
    val active = session as? SessionState.Active
    ListItem(
        headlineContent = {
            Text(active?.session?.baseUrl ?: stringResource(R.string.value_unknown))
        },
        overlineContent = { Text(stringResource(R.string.settings_instance)) },
        leadingContent = { Icon(Icons.Outlined.Dns, contentDescription = null) },
    )
    ListItem(
        headlineContent = {
            Text(
                active?.session?.userDisplayName
                    ?: active?.session?.username
                    ?: stringResource(R.string.value_unknown),
            )
        },
        overlineContent = { Text(stringResource(R.string.settings_account)) },
        supportingContent = {
            Text(
                stringResource(
                    when (active?.session?.authMode) {
                        AuthMode.API_TOKEN -> R.string.settings_auth_token
                        else -> R.string.settings_auth_password
                    },
                ),
            )
        },
        leadingContent = { Icon(Icons.Outlined.Person, contentDescription = null) },
    )
    ListItem(
        headlineContent = {
            Text(
                stringResource(
                    when {
                        state.connectionCheck == ConnectionCheck.CHECKING ->
                            R.string.settings_status_checking
                        session is SessionState.Expired ||
                            state.connectionCheck == ConnectionCheck.FAILED ->
                            R.string.settings_status_expired
                        else -> R.string.settings_status_connected
                    },
                ),
            )
        },
        overlineContent = { Text(stringResource(R.string.settings_status)) },
        supportingContent = {
            state.checkError?.let { error -> Text("${error.title()} — ${error.message()}") }
        },
    )
}

@Composable
private fun ConnectionActions(
    checking: Boolean,
    onCheckConnection: () -> Unit,
    onChangeInstance: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCheckConnection,
            modifier = Modifier.weight(1f),
            enabled = !checking,
        ) {
            Text(stringResource(R.string.settings_check_connection))
        }
        OutlinedButton(onClick = onChangeInstance, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_change_instance))
        }
    }
}

/** Mealie only lets a household manager change these preferences. */
@Composable
private fun ReadOnlyNotice() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_household_read_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SaveErrorNotice(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

/** Mealie's own picker offers the seven days, starting on Sunday. */
private val FirstDayOptions = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)
