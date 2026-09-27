package org.opensources.umai.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import org.opensources.umai.core.settings.RecipeDisplayOptions
import org.opensources.umai.core.settings.RecipeLayout
import org.opensources.umai.core.settings.RecipeSection
import org.opensources.umai.core.settings.ThemeMode
import org.opensources.umai.core.ui.component.BackTopAppBar

@Composable
fun AppSettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
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
        onRecipeSectionChange = viewModel::setRecipeSectionVisible,
        modifier = modifier,
        onDetectTimersChange = viewModel::setDetectTimers,
        onTimerSoundChange = viewModel::setTimerSound,
        onTimerVibrateChange = viewModel::setTimerVibrate,
    )
}

/** Stateless application settings, driven by [AppPreferences]. */
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
    onRecipeSectionChange: (RecipeSection, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onDetectTimersChange: (Boolean) -> Unit = {},
    onTimerSoundChange: (Boolean) -> Unit = {},
    onTimerVibrateChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(title = stringResource(R.string.settings_app_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            appearanceItems(preferences, onLanguageChange, onThemeChange, onLayoutChange, onDynamicColorChange)
            cookingItems(preferences, onKeepScreenOnChange, onDetectTimersChange, onTimerSoundChange, onTimerVibrateChange)
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(R.string.settings_section_recipe_page)) }
            items(RecipeSection.entries, key = { it.name }) { section ->
                SettingsSwitchRow(
                    title = stringResource(section.titleRes()),
                    summary = stringResource(section.summaryRes()),
                    checked = preferences.recipeDisplay.isVisible(section),
                    onCheckedChange = { onRecipeSectionChange(section, it) },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SettingsSectionHeader(stringResource(R.string.settings_section_about)) }
            item { AboutBlock(serverVersion) }
        }
    }
}

/** Language, theme, layout of the lists and colours. */
private fun LazyListScope.appearanceItems(
    preferences: AppPreferences,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLayoutChange: (RecipeLayout) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    item { SettingsSectionHeader(stringResource(R.string.settings_section_app)) }
    item {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries,
            selected = preferences.language,
            labelOf = { stringResource(it.labelRes()) },
            onSelect = onLanguageChange,
        )
    }
    item {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = preferences.themeMode,
            labelOf = { stringResource(it.labelRes()) },
            onSelect = onThemeChange,
        )
    }
    item {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_layout),
            options = RecipeLayout.entries,
            selected = preferences.recipeLayout,
            labelOf = { stringResource(it.labelRes()) },
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
}

/** The screen kept on, and the timers found in the steps. */
private fun LazyListScope.cookingItems(
    preferences: AppPreferences,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onDetectTimersChange: (Boolean) -> Unit,
    onTimerSoundChange: (Boolean) -> Unit,
    onTimerVibrateChange: (Boolean) -> Unit,
) {
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
    item { SettingsSectionHeader(stringResource(R.string.settings_section_cooking)) }
    item {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_keep_screen_on),
            summary = stringResource(R.string.settings_keep_screen_on_summary),
            checked = preferences.keepScreenOnWhileCooking,
            onCheckedChange = onKeepScreenOnChange,
        )
    }
    item {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_detect_timers),
            summary = stringResource(R.string.settings_detect_timers_summary),
            checked = preferences.cookingTimers.detectTimers,
            onCheckedChange = onDetectTimersChange,
        )
    }
    // How a timer calls the cook only matters when there are timers.
    item {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_timer_sound),
            summary = stringResource(R.string.settings_timer_sound_summary),
            checked = preferences.cookingTimers.sound,
            onCheckedChange = onTimerSoundChange,
            enabled = preferences.cookingTimers.detectTimers,
        )
    }
    item {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_timer_vibrate),
            summary = stringResource(R.string.settings_timer_vibrate_summary),
            checked = preferences.cookingTimers.vibrate,
            onCheckedChange = onTimerVibrateChange,
            enabled = preferences.cookingTimers.detectTimers,
        )
    }
}

@Composable
private fun AboutBlock(serverVersion: String?) {
    Column {
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

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.FRENCH -> R.string.settings_language_french
    AppLanguage.ENGLISH -> R.string.settings_language_english
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

private fun RecipeLayout.labelRes(): Int = when (this) {
    RecipeLayout.GRID -> R.string.settings_layout_grid
    RecipeLayout.LIST -> R.string.settings_layout_list
}

private fun RecipeDisplayOptions.isVisible(section: RecipeSection): Boolean = when (section) {
    RecipeSection.TIMES -> showTimes
    RecipeSection.NUTRITION -> showNutrition
    RecipeSection.SOURCE -> showSource
    RecipeSection.COMMENTS -> showComments
}

private fun RecipeSection.titleRes(): Int = when (this) {
    RecipeSection.TIMES -> R.string.settings_recipe_times
    RecipeSection.NUTRITION -> R.string.settings_recipe_nutrition
    RecipeSection.SOURCE -> R.string.settings_recipe_source
    RecipeSection.COMMENTS -> R.string.settings_recipe_comments
}

private fun RecipeSection.summaryRes(): Int = when (this) {
    RecipeSection.TIMES -> R.string.settings_recipe_times_summary
    RecipeSection.NUTRITION -> R.string.settings_recipe_nutrition_summary
    RecipeSection.SOURCE -> R.string.settings_recipe_source_summary
    RecipeSection.COMMENTS -> R.string.settings_recipe_comments_summary
}
