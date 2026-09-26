package org.opensources.umai.home.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.opensources.umai.core.settings.safeData

private val Context.recentDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_recent")

/** The history of opened recipes, as the screens that rename or delete a recipe keep it right. */
interface RecentRecipes {
    /** Mealie derives the slug from the name: a renamed recipe keeps its place. */
    suspend fun rename(oldSlug: String, newSlug: String)

    /** A deleted recipe leaves the history: its slug would lead nowhere. */
    suspend fun forget(slug: String)
}

/**
 * Mealie has no "recently viewed" endpoint, so the history of opened recipes is
 * kept on the device. Only slugs are stored; the recipes themselves are still
 * read from the instance.
 */
class RecentRecipesStore(context: Context) : RecentRecipes {

    private val dataStore = context.applicationContext.recentDataStore

    val slugs: Flow<List<String>> = dataStore.safeData.map { slugsOf(it) }

    suspend fun remember(slug: String) {
        if (slug.isBlank()) return
        dataStore.edit { prefs ->
            val updated = (listOf(slug) + slugsOf(prefs).filterNot { it == slug }).take(MAX_ENTRIES)
            prefs[KeySlugs] = updated.joinToString(SEPARATOR)
        }
    }

    override suspend fun rename(oldSlug: String, newSlug: String) {
        if (oldSlug == newSlug || newSlug.isBlank()) return
        dataStore.edit { prefs ->
            prefs[KeySlugs] = slugsOf(prefs).map { if (it == oldSlug) newSlug else it }.distinct().joinToString(SEPARATOR)
        }
    }

    override suspend fun forget(slug: String) {
        dataStore.edit { prefs ->
            prefs[KeySlugs] = slugsOf(prefs).filterNot { it == slug }.joinToString(SEPARATOR)
        }
    }

    /** The history belongs to the instance it was read from. */
    suspend fun clear() {
        dataStore.edit { it.remove(KeySlugs) }
    }

    private fun slugsOf(prefs: Preferences): List<String> =
        prefs[KeySlugs]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val MAX_ENTRIES = 12
        const val SEPARATOR = "\u001F"
        val KeySlugs = stringPreferencesKey("recent_slugs")
    }
}
