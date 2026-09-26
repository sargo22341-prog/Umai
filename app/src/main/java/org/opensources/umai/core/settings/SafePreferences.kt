package org.opensources.umai.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException

/**
 * The stored preferences, or none when the file cannot be read: a damaged
 * store costs the values it held, not the app. Any other failure is a bug and
 * surfaces.
 */
val DataStore<Preferences>.safeData: Flow<Preferences>
    get() = data.catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
