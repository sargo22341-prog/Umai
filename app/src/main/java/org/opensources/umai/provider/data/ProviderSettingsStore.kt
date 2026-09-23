package org.opensources.umai.provider.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.providerDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_providers")

/** Where the choices made on the page of a provider are kept. */
interface ProviderSettings {
    /** Whether a recipe imported from the provider also gets its video and step photos. */
    fun importsMedia(providerId: String): Flow<Boolean>

    suspend fun setImportsMedia(providerId: String, enabled: Boolean)
}

suspend fun ProviderSettings.importsMediaNow(providerId: String): Boolean = importsMedia(providerId).first()

/** Kept on the device only: Mealie has no notion of the app's providers. */
class ProviderSettingsStore(context: Context) : ProviderSettings {

    private val dataStore = context.applicationContext.providerDataStore

    override fun importsMedia(providerId: String): Flow<Boolean> = dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { it[mediaKey(providerId)] ?: true }

    override suspend fun setImportsMedia(providerId: String, enabled: Boolean) {
        dataStore.edit { it[mediaKey(providerId)] = enabled }
    }

    private fun mediaKey(providerId: String) = booleanPreferencesKey("${providerId}_import_media")
}
