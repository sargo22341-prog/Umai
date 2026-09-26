package org.opensources.umai.speech.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.opensources.umai.speech.domain.SpeechModel
import org.opensources.umai.speech.domain.SpeechModelCatalog
import java.io.IOException

private val Context.speechDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_speech")

/** A speech model being downloaded by the system's download manager. */
data class PendingSpeechModel(val downloadId: Long, val model: SpeechModel)

data class SpeechSettings(
    /** The model downloaded and checked, ready to be run. */
    val installed: SpeechModel? = null,
    val pending: PendingSpeechModel? = null,
)

/** Kept on the device only: the model is a file of this phone, unknown to Mealie. */
class SpeechSettingsStore(context: Context) {

    private val dataStore = context.applicationContext.speechDataStore

    val settings: Flow<SpeechSettings> = dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            SpeechSettings(
                installed = SpeechModelCatalog.byId(prefs[KeyInstalled]),
                pending = prefs[KeyPendingDownload]?.let { id ->
                    SpeechModelCatalog.byId(prefs[KeyPendingModel])?.let { PendingSpeechModel(id, it) }
                },
            )
        }

    suspend fun current(): SpeechSettings = settings.first()

    suspend fun setInstalled(model: SpeechModel?) = dataStore.edit { prefs ->
        if (model == null) prefs.remove(KeyInstalled) else prefs[KeyInstalled] = model.id
    }

    suspend fun setPending(pending: PendingSpeechModel?) = dataStore.edit { prefs ->
        if (pending == null) {
            prefs.remove(KeyPendingDownload)
            prefs.remove(KeyPendingModel)
        } else {
            prefs[KeyPendingDownload] = pending.downloadId
            prefs[KeyPendingModel] = pending.model.id
        }
    }

    private companion object {
        val KeyInstalled = stringPreferencesKey("installed_model")
        val KeyPendingDownload = longPreferencesKey("pending_download")
        val KeyPendingModel = stringPreferencesKey("pending_model")
    }
}
