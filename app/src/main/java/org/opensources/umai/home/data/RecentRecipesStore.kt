package org.opensources.umai.home.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_recent")

/**
 * Mealie has no "recently viewed" endpoint, so the history of opened recipes is
 * kept on the device. Only slugs are stored; the recipes themselves are still
 * read from the instance.
 */
class RecentRecipesStore(context: Context) {

    private val dataStore = context.applicationContext.recentDataStore

    val slugs: Flow<List<String>> = dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            prefs[KeySlugs]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        }

    suspend fun remember(slug: String) {
        if (slug.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KeySlugs]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(slug) + current.filterNot { it == slug }).take(MAX_ENTRIES)
            prefs[KeySlugs] = updated.joinToString(SEPARATOR)
        }
    }

    /** Mealie derives the slug from the name: a renamed recipe keeps its place. */
    suspend fun rename(oldSlug: String, newSlug: String) {
        if (oldSlug == newSlug || newSlug.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[KeySlugs]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
            prefs[KeySlugs] = current.map { if (it == oldSlug) newSlug else it }.distinct().joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KeySlugs) }
    }

    private companion object {
        const val MAX_ENTRIES = 12
        const val SEPARATOR = "\u001F"
        val KeySlugs = stringPreferencesKey("recent_slugs")
    }
}
