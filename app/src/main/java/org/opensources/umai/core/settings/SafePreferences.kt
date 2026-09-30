package org.opensources.umai.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import kotlin.properties.ReadOnlyProperty

/**
 * A preferences store of the app, named [name]. A damaged file is started over
 * empty: it costs the values it held, not the app.
 */
fun appPreferencesDataStore(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> =
    preferencesDataStore(name = name, corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })

/**
 * The stored preferences, or none when the file cannot be read: these stores
 * only hold what the app can do without, which falls back to its defaults.
 * A damaged file never gets here (see [appPreferencesDataStore]); any failure
 * other than an I/O one is a bug and surfaces.
 */
val DataStore<Preferences>.safeData: Flow<Preferences>
    get() = data.catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
