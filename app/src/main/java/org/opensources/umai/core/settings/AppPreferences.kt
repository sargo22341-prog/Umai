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

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val dynamicColor: Boolean = false,
    val recipeLayout: RecipeLayout = RecipeLayout.GRID,
    val keepScreenOnWhileCooking: Boolean = true,
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
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KeyTheme] = mode.name }

    suspend fun setLanguage(language: AppLanguage) = edit { it[KeyLanguage] = language.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KeyDynamicColor] = enabled }

    suspend fun setRecipeLayout(layout: RecipeLayout) = edit { it[KeyLayout] = layout.name }

    suspend fun setKeepScreenOnWhileCooking(enabled: Boolean) = edit { it[KeyKeepScreenOn] = enabled }

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
    }
}
