package org.opensources.umai.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** `SYSTEM` follows the device language, falling back to English. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en"),
}

/** Density of the recipe grid on Home and Search. */
enum class RecipeLayout { GRID, LIST }

/**
 * Which optional sections the recipe page shows. Everything is visible by
 * default; hiding a section only changes this device, never the recipe.
 */
data class RecipeDisplayOptions(
    val showTimes: Boolean = true,
    val showNutrition: Boolean = true,
    val showSource: Boolean = true,
    val showComments: Boolean = true,
)

/**
 * How the cooking mode handles the durations written in the steps: whether it
 * offers a timer for each, and how a timer that reaches zero calls the cook.
 */
data class CookingTimerOptions(
    val detectTimers: Boolean = true,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
)

/** One of the sections [RecipeDisplayOptions] can hide. */
enum class RecipeSection { TIMES, NUTRITION, SOURCE, COMMENTS }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val dynamicColor: Boolean = false,
    val recipeLayout: RecipeLayout = RecipeLayout.GRID,
    val keepScreenOnWhileCooking: Boolean = true,
    val recipeDisplay: RecipeDisplayOptions = RecipeDisplayOptions(),
    val cookingTimers: CookingTimerOptions = CookingTimerOptions(),
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_settings")

class AppPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            AppPreferences(
                themeMode = prefs[KeyTheme].toEnum(ThemeMode.SYSTEM),
                language = prefs[KeyLanguage].toEnum(AppLanguage.SYSTEM),
                dynamicColor = prefs[KeyDynamicColor] ?: false,
                recipeLayout = prefs[KeyLayout].toEnum(RecipeLayout.GRID),
                keepScreenOnWhileCooking = prefs[KeyKeepScreenOn] ?: true,
                recipeDisplay = RecipeDisplayOptions(
                    showTimes = prefs[KeyShowTimes] ?: true,
                    showNutrition = prefs[KeyShowNutrition] ?: true,
                    showSource = prefs[KeyShowSource] ?: true,
                    showComments = prefs[KeyShowComments] ?: true,
                ),
                cookingTimers = CookingTimerOptions(
                    detectTimers = prefs[KeyDetectTimers] ?: true,
                    sound = prefs[KeyTimerSound] ?: true,
                    vibrate = prefs[KeyTimerVibrate] ?: true,
                ),
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KeyTheme] = mode.name }

    suspend fun setLanguage(language: AppLanguage) = edit { it[KeyLanguage] = language.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KeyDynamicColor] = enabled }

    suspend fun setRecipeLayout(layout: RecipeLayout) = edit { it[KeyLayout] = layout.name }

    suspend fun setKeepScreenOnWhileCooking(enabled: Boolean) = edit { it[KeyKeepScreenOn] = enabled }

    suspend fun setDetectTimers(enabled: Boolean) = edit { it[KeyDetectTimers] = enabled }

    suspend fun setTimerSound(enabled: Boolean) = edit { it[KeyTimerSound] = enabled }

    suspend fun setTimerVibrate(enabled: Boolean) = edit { it[KeyTimerVibrate] = enabled }

    suspend fun setRecipeSectionVisible(section: RecipeSection, visible: Boolean) = edit {
        it[section.key()] = visible
    }

    private fun RecipeSection.key() = when (this) {
        RecipeSection.TIMES -> KeyShowTimes
        RecipeSection.NUTRITION -> KeyShowNutrition
        RecipeSection.SOURCE -> KeyShowSource
        RecipeSection.COMMENTS -> KeyShowComments
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        val KeyTheme = stringPreferencesKey("theme_mode")
        val KeyLanguage = stringPreferencesKey("language")
        val KeyDynamicColor = booleanPreferencesKey("dynamic_color")
        val KeyLayout = stringPreferencesKey("recipe_layout")
        val KeyKeepScreenOn = booleanPreferencesKey("keep_screen_on_cooking")
        val KeyShowTimes = booleanPreferencesKey("recipe_show_times")
        val KeyShowNutrition = booleanPreferencesKey("recipe_show_nutrition")
        val KeyShowSource = booleanPreferencesKey("recipe_show_source")
        val KeyShowComments = booleanPreferencesKey("recipe_show_comments")
        val KeyDetectTimers = booleanPreferencesKey("cooking_detect_timers")
        val KeyTimerSound = booleanPreferencesKey("cooking_timer_sound")
        val KeyTimerVibrate = booleanPreferencesKey("cooking_timer_vibrate")
    }
}
