package org.opensources.umai.recipe.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.opensources.umai.recipe.domain.RecipeDraft
import java.io.IOException

private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_drafts")

/**
 * Unfinished recipes, kept on the device only.
 *
 * Mealie stores no draft of its own, so this is one of the few pieces of state
 * Umai is allowed to own. A draft that cannot be decoded — an older shape, a
 * truncated write — is dropped rather than crashing the list.
 */
class RecipeDraftStore(context: Context, private val imageFiles: RecipeImageFiles) {

    private val dataStore = context.applicationContext.draftDataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val drafts: Flow<List<RecipeDraft>> = dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs -> decode(prefs[KeyDrafts]).sortedByDescending { it.updatedAt } }

    suspend fun draft(id: String): RecipeDraft? = drafts.first().firstOrNull { it.id == id }

    /** Inserts or replaces a draft, stamping it with the current time. */
    suspend fun save(draft: RecipeDraft) {
        val stamped = draft.copy(updatedAt = System.currentTimeMillis())
        dataStore.edit { prefs ->
            val current = decode(prefs[KeyDrafts]).filterNot { it.id == stamped.id }
            prefs[KeyDrafts] = json.encodeToString((listOf(stamped) + current).take(MAX_DRAFTS))
        }
    }

    /** Removes the draft and the picture it kept on the device. */
    suspend fun delete(id: String) {
        var removed: RecipeDraft? = null
        dataStore.edit { prefs ->
            val current = decode(prefs[KeyDrafts])
            removed = current.firstOrNull { it.id == id }
            prefs[KeyDrafts] = json.encodeToString(current.filterNot { it.id == id })
        }
        removed?.imagePath?.let(imageFiles::delete)
    }

    private fun decode(raw: String?): List<RecipeDraft> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<RecipeDraft>>(raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_DRAFTS = 50
        val KeyDrafts = stringPreferencesKey("recipe_drafts")
    }
}
