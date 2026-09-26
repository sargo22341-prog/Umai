package org.opensources.umai.speech.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.opensources.umai.core.download.DownloadRecord
import org.opensources.umai.core.download.PendingDownload
import org.opensources.umai.core.settings.safeData
import org.opensources.umai.speech.domain.SpeechModel
import org.opensources.umai.speech.domain.SpeechModelCatalog

private val Context.speechDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_speech")

data class SpeechSettings(
    /** The model downloaded and checked, ready to be run. */
    val installed: SpeechModel? = null,
    val pending: PendingDownload<SpeechModel>? = null,
)

/** Kept on the device only: the model is a file of this phone, unknown to Mealie. */
class SpeechSettingsStore(context: Context) : DownloadRecord<SpeechModel> {

    private val dataStore = context.applicationContext.speechDataStore

    val settings: Flow<SpeechSettings> = dataStore.safeData
        .map { prefs ->
            SpeechSettings(
                installed = SpeechModelCatalog.byId(prefs[KeyInstalled]),
                pending = prefs[KeyPendingDownload]?.let { id ->
                    SpeechModelCatalog.byId(prefs[KeyPendingModel])?.let { PendingDownload(listOf(id), it) }
                },
            )
        }

    suspend fun current(): SpeechSettings = settings.first()

    override suspend fun installed(): SpeechModel? = current().installed

    override suspend fun pending(): PendingDownload<SpeechModel>? = current().pending

    override suspend fun setInstalled(model: SpeechModel?) {
        dataStore.edit { prefs ->
            if (model == null) prefs.remove(KeyInstalled) else prefs[KeyInstalled] = model.id
        }
    }

    /** A speech model is a single file: one download. */
    override suspend fun setPending(pending: PendingDownload<SpeechModel>?) {
        dataStore.edit { prefs ->
            if (pending == null) {
                prefs.remove(KeyPendingDownload)
                prefs.remove(KeyPendingModel)
            } else {
                prefs[KeyPendingDownload] = pending.downloadIds.single()
                prefs[KeyPendingModel] = pending.model.id
            }
        }
    }

    private companion object {
        val KeyInstalled = stringPreferencesKey("installed_model")
        val KeyPendingDownload = longPreferencesKey("pending_download")
        val KeyPendingModel = stringPreferencesKey("pending_model")
    }
}
