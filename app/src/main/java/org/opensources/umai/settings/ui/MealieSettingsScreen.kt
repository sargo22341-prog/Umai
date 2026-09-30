package org.opensources.umai.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.opensources.umai.core.model.HouseholdPreferences
import org.opensources.umai.core.session.AuthMode
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.ui.component.BackTopAppBar
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import java.time.DayOfWeek

@Composable
fun MealieSettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
        onSyncCalorieTags = viewModel::syncCalorieTags,
        modifier = modifier,
    )
}

/** Stateless Mealie settings, driven by [MealieSettingsUiState]. */
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
    onSyncCalorieTags: () -> Unit = {},
) {
    var signOutDialogVisible by remember { mutableStateOf(false) }
    if (signOutDialogVisible) {
        SignOutDialog(onDismiss = { signOutDialogVisible = false }, onSignOut = onSignOut)
    }
    val household = HouseholdActions(
        onFirstDayChange = onFirstDayChange,
        onShowNutritionChange = onShowNutritionChange,
        onShowAssetsChange = onShowAssetsChange,
        onDisableCommentsChange = onDisableCommentsChange,
        onRecipePublicChange = onRecipePublicChange,
        onPrivateHouseholdChange = onPrivateHouseholdChange,
    )

    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(title = stringResource(R.string.settings_mealie_title), onBack = onBack) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SettingsSectionHeader(stringResource(R.string.settings_section_connection)) }
                item { ConnectionBlock(session = session, state = state) }
                item {
                    ConnectionActions(
                        checking = state.connectionCheck == ConnectionCheck.CHECKING,
                        onCheckConnection = onCheckConnection,
                        onChangeInstance = { signOutDialogVisible = true },
                    )
                }
                householdItems(state, household, onDismissSaveError)
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { SettingsSectionHeader(stringResource(R.string.settings_section_recipes)) }
                item { CalorieSyncBlock(sync = state.calorieSync, onSync = onSyncCalorieTags) }
            }
        }
    }
}

@Composable
private fun SignOutDialog(onDismiss: () -> Unit, onSignOut: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sign_out_title)) },
        text = { Text(stringResource(R.string.settings_sign_out_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onSignOut()
                },
            ) { Text(stringResource(R.string.settings_sign_out)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** What each preference of the household changes. */
private class HouseholdActions(
    val onFirstDayChange: (DayOfWeek) -> Unit,
    val onShowNutritionChange: (Boolean) -> Unit,
    val onShowAssetsChange: (Boolean) -> Unit,
    val onDisableCommentsChange: (Boolean) -> Unit,
    val onRecipePublicChange: (Boolean) -> Unit,
    val onPrivateHouseholdChange: (Boolean) -> Unit,
)

/** The preferences of the household, stored on Mealie and shared by its members. */
private fun LazyListScope.householdItems(
    state: MealieSettingsUiState,
    actions: HouseholdActions,
    onDismissSaveError: () -> Unit,
) {
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
    item { SettingsSectionHeader(stringResource(R.string.settings_section_household)) }
    item {
        Text(
            text = stringResource(R.string.settings_household_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    if (!state.canManageHousehold) item { ReadOnlyNotice() }
    state.saveError?.let { error ->
        item { SaveErrorNotice(message = "${error.title()} — ${error.message()}", onDismiss = onDismissSaveError) }
    }
    val household = state.household
    if (household == null) {
        item {
            Text(
                text = stringResource(if (state.loading) R.string.loading else R.string.settings_household_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    } else {
        item { HouseholdPreferenceRows(household, editable = state.householdEditable, actions = actions) }
    }
}

@Composable
private fun HouseholdPreferenceRows(household: HouseholdPreferences, editable: Boolean, actions: HouseholdActions) {
    Column {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_first_day_of_week),
            options = FirstDayOptions,
            selected = household.firstDay,
            labelOf = { it.localizedName() },
            onSelect = actions.onFirstDayChange,
            enabled = editable,
            scopeNote = stringResource(R.string.settings_scope_first_day),
        )
        HouseholdSwitch(
            R.string.settings_show_nutrition, R.string.settings_show_nutrition_summary, R.string.settings_scope_nutrition,
            checked = household.recipeShowNutrition, editable = editable, onChange = actions.onShowNutritionChange,
        )
        HouseholdSwitch(
            R.string.settings_show_assets, R.string.settings_show_assets_summary, R.string.settings_scope_server_only,
            checked = household.recipeShowAssets, editable = editable, onChange = actions.onShowAssetsChange,
        )
        HouseholdSwitch(
            R.string.settings_disable_comments, R.string.settings_disable_comments_summary, R.string.settings_scope_comments,
            checked = household.recipeDisableComments, editable = editable, onChange = actions.onDisableCommentsChange,
        )
        HouseholdSwitch(
            R.string.settings_recipe_public, R.string.settings_recipe_public_summary, R.string.settings_scope_server_only,
            checked = household.recipePublic, editable = editable, onChange = actions.onRecipePublicChange,
        )
        HouseholdSwitch(
            R.string.settings_private_household, R.string.settings_private_household_summary, R.string.settings_scope_server_only,
            checked = household.privateHousehold, editable = editable, onChange = actions.onPrivateHouseholdChange,
        )
    }
}

@Composable
private fun HouseholdSwitch(
    @StringRes title: Int,
    @StringRes summary: Int,
    @StringRes scope: Int,
    checked: Boolean,
    editable: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        title = stringResource(title),
        summary = stringResource(summary),
        checked = checked,
        onCheckedChange = onChange,
        enabled = editable,
        scopeNote = stringResource(scope),
    )
}

@Composable
private fun CalorieSyncBlock(sync: CalorieSync, onSync: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(R.string.settings_calorie_tags), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.settings_calorie_tags_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onSync, enabled = !sync.running) {
            Text(stringResource(if (sync.running) R.string.settings_calorie_tags_running else R.string.settings_calorie_tags_action))
        }
        if (sync.running && sync.total > 0) {
            LinearProgressIndicator(progress = { sync.processed.toFloat() / sync.total }, modifier = Modifier.fillMaxWidth())
        }
        sync.error?.let { error ->
            Text(text = "${error.title()}\n${error.message()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (sync.finished && sync.error == null) {
            Text(
                text = stringResource(R.string.settings_calorie_tags_done, sync.total, sync.changed, sync.failed),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (sync.total > 0 && !sync.complete) {
            Text(
                text = stringResource(R.string.settings_calorie_tags_incomplete, sync.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                        AuthMode.PASSWORD, null -> R.string.settings_auth_password
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
