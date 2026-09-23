package org.opensources.umai.settings.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.BuildConfig
import org.opensources.umai.R
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.settings.AppLanguage
import org.opensources.umai.core.settings.AppPreferences
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.core.settings.ThemeMode

@Composable
fun AppSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: AppSettingsViewModel = viewModel(factory = AppSettingsViewModel.factory(container))
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()

    AppSettingsScreen(
        preferences = preferences,
        serverVersion = (session as? SessionState.Active)?.session?.serverVersion,
        onBack = onBack,
        onLanguageChange = viewModel::setLanguage,
        onThemeChange = viewModel::setTheme,
        onLayoutChange = viewModel::setLayout,
        onDynamicColorChange = viewModel::setDynamicColor,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        modifier = modifier,
    )
}

/** Stateless application settings, driven by [AppPreferences]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    preferences: AppPreferences,
    serverVersion: String?,
    onBack: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLayoutChange: (RecipeLayout) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_app_title)) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SettingsSectionHeader(stringResource(R.string.settings_section_app)) }

            item {
                SettingsChoiceRow(
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
                SettingsChoiceRow(
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
                SettingsChoiceRow(
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
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = preferences.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }

            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    summary = stringResource(R.string.settings_keep_screen_on_summary),
                    checked = preferences.keepScreenOnWhileCooking,
                    onCheckedChange = onKeepScreenOnChange,
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(R.string.settings_section_about)) }

            item {
                ListItem(
                    headlineContent = { Text(BuildConfig.VERSION_NAME) },
                    overlineContent = { Text(stringResource(R.string.settings_app_version)) },
                )
                serverVersion?.let { version ->
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
