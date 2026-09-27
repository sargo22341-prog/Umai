package org.opensources.umai.llm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.opensources.umai.core.download.DownloadRecord
import org.opensources.umai.core.download.PendingDownload
import org.opensources.umai.core.settings.safeData
import org.opensources.umai.llm.domain.LocalModel
import org.opensources.umai.llm.domain.LocalModelCatalog

private val Context.localAiDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_local_ai")

data class LocalAiSettings(
    /** The local AI can be turned off without deleting the model. */
    val enabled: Boolean = true,
    /** The model downloaded and checked, ready to be run. */
    val installed: LocalModel? = null,
    val pending: PendingDownload<LocalModel>? = null,
)

/** Kept on the device only: the model is a file of this phone, unknown to Mealie. */
class LocalAiSettingsStore(context: Context) : DownloadRecord<LocalModel> {

    private val dataStore = context.applicationContext.localAiDataStore

    val settings: Flow<LocalAiSettings> = dataStore.safeData
        .map { prefs ->
            LocalAiSettings(
                enabled = prefs[KeyEnabled] ?: true,
                installed = model(prefs[KeyInstalledId], prefs[KeyInstalledUrl]),
                pending = prefs[KeyPendingDownloads]?.let { ids ->
                    model(prefs[KeyPendingId], prefs[KeyPendingUrl])?.let { model ->
                        PendingDownload(ids.split(',').mapNotNull(String::toLongOrNull), model)
                    }
                },
            )
        }

    suspend fun current(): LocalAiSettings = settings.first()

    override suspend fun installed(): LocalModel? = current().installed

    override suspend fun pending(): PendingDownload<LocalModel>? = current().pending

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KeyEnabled] = enabled }
    }

    override suspend fun setPending(pending: PendingDownload<LocalModel>?) {
        dataStore.edit { prefs ->
            if (pending == null) {
                prefs.remove(KeyPendingDownloads)
                prefs.remove(KeyPendingId)
                prefs.remove(KeyPendingUrl)
            } else {
                prefs[KeyPendingDownloads] = pending.downloadIds.joinToString(",")
                prefs.putModel(KeyPendingId, KeyPendingUrl, pending.model)
            }
        }
    }

    override suspend fun setInstalled(model: LocalModel?) {
        dataStore.edit { prefs ->
            if (model == null) {
                prefs.remove(KeyInstalledId)
                prefs.remove(KeyInstalledUrl)
            } else {
                prefs.putModel(KeyInstalledId, KeyInstalledUrl, model)
            }
        }
    }

    private fun MutablePreferences.putModel(
        idKey: Preferences.Key<String>,
        urlKey: Preferences.Key<String>,
        model: LocalModel,
    ) {
        this[idKey] = model.id
        if (model.isCustom) this[urlKey] = model.files.first().url else remove(urlKey)
    }

    private fun model(id: String?, url: String?): LocalModel? = when (id) {
        null -> null
        LocalModel.CUSTOM_ID -> url?.let(LocalModel::custom)
        else -> LocalModelCatalog.byId(id)
    }

    private companion object {
        val KeyEnabled = booleanPreferencesKey("enabled")
        val KeyInstalledId = stringPreferencesKey("installed_model")
        val KeyInstalledUrl = stringPreferencesKey("installed_url")
        val KeyPendingDownloads = stringPreferencesKey("pending_downloads")
        val KeyPendingId = stringPreferencesKey("pending_model")
        val KeyPendingUrl = stringPreferencesKey("pending_url")
    }
}
