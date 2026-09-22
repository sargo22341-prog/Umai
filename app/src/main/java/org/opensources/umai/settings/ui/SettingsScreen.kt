package org.opensources.umai.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.BuildConfig
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.format.localizedName
import org.opensources.umai.core.session.AuthMode
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.settings.AppLanguage
import org.opensources.umai.core.settings.AppPreferences
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.core.settings.ThemeMode
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsScreen(
        preferences = preferences,
        session = session,
        state = state,
        onCheckConnection = viewModel::checkConnection,
        onSignOut = viewModel::signOut,
        onLanguageChange = viewModel::setLanguage,
        onThemeChange = viewModel::setTheme,
        onLayoutChange = viewModel::setLayout,
        onDynamicColorChange = viewModel::setDynamicColor,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        modifier = modifier,
    )
}

/** Stateless settings, driven by the preferences and the session state. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    session: SessionState,
    state: SettingsUiState,
    onCheckConnection: () -> Unit,
    onSignOut: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLayoutChange: (RecipeLayout) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_connection)) }

            item {
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
                        state.checkError?.let { error ->
                            Text("${error.title()} — ${error.message()}")
                        }
                    },
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onCheckConnection,
                        modifier = Modifier.weight(1f),
                        enabled = state.connectionCheck != ConnectionCheck.CHECKING,
                    ) {
                        Text(stringResource(R.string.settings_check_connection))
                    }
                    OutlinedButton(
                        onClick = { signOutDialogVisible = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_change_instance))
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.settings_section_app)) }

            item {
                ChoiceRow(
                    title = stringResource(R.string.settings_language),
                    options = AppLanguage.entries,
                    selected = preferences.language,
                    labelOf = {
                        stringResource(
                            when (it) {
                                AppLanguage.SYSTEM -> R.string.settings_language_system
                                AppLanguage.FRENCH -> R.string.settings_language_french
                                AppLanguage.ENGLISH -> R.string.settings_language_english
                            },
                        )
                    },
                    onSelect = onLanguageChange,
                )
            }

            item {
                ChoiceRow(
                    title = stringResource(R.string.settings_theme),
                    options = ThemeMode.entries,
                    selected = preferences.themeMode,
                    labelOf = {
                        stringResource(
                            when (it) {
                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                ThemeMode.DARK -> R.string.settings_theme_dark
                            },
                        )
                    },
                    onSelect = onThemeChange,
                )
            }

            item {
                ChoiceRow(
                    title = stringResource(R.string.settings_layout),
                    options = RecipeLayout.entries,
                    selected = preferences.recipeLayout,
                    labelOf = {
                        stringResource(
                            when (it) {
                                RecipeLayout.GRID -> R.string.settings_layout_grid
                                RecipeLayout.LIST -> R.string.settings_layout_list
                            },
                        )
                    },
                    onSelect = onLayoutChange,
                )
            }

            item {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = preferences.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }

            item {
                SwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    summary = stringResource(R.string.settings_keep_screen_on_summary),
                    checked = preferences.keepScreenOnWhileCooking,
                    onCheckedChange = onKeepScreenOnChange,
                )
            }

            state.household?.let { household ->
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_section_mealie)) }
                item {
                    ListItem(
                        headlineContent = { Text(dayName(household.firstDayOfWeek)) },
                        overlineContent = { Text(stringResource(R.string.settings_first_day_of_week)) },
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (household.showNutrition) R.string.value_yes else R.string.value_no,
                                ),
                            )
                        },
                        overlineContent = { Text(stringResource(R.string.settings_show_nutrition)) },
                    )
                    Text(
                        text = stringResource(R.string.settings_mealie_preferences_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.settings_section_about)) }

            item {
                ListItem(
                    headlineContent = { Text(BuildConfig.VERSION_NAME) },
                    overlineContent = { Text(stringResource(R.string.settings_app_version)) },
                )
                (session as? SessionState.Active)?.session?.serverVersion?.let { version ->
                    ListItem(
                        headlineContent = { Text(version) },
                        overlineContent = { Text(stringResource(R.string.settings_server_version)) },
                    )
                }
                Text(
                    text = stringResource(R.string.settings_about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(labelOf(option)) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Mealie stores the first day of the week as 0 = Monday. */
@Composable
private fun dayName(index: Int): String =
    java.time.DayOfWeek.of(((index % 7) + 7) % 7 + 1).localizedName()
