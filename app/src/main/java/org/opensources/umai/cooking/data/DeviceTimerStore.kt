package org.opensources.umai.cooking.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.SavedTimers
import org.opensources.umai.cooking.domain.TimerRecipe
import org.opensources.umai.cooking.domain.TimerStore
import org.opensources.umai.core.settings.appPreferencesDataStore
import org.opensources.umai.core.settings.safeData
import java.io.IOException
import java.time.Duration

private val Context.timerDataStore: DataStore<Preferences> by appPreferencesDataStore("umai_timers")

/**
 * Keeps the timers in a small store of the device. Their times are read from
 * a clock that starts again with the device ([CookingTimerController]): what
 * was saved before a restart no longer means anything, and is dropped — the
 * system forgets the wake-up alarm on a restart too.
 */
class DeviceTimerStore(
    private val dataStore: DataStore<Preferences>,
    /** How many times the device started: the clock of the timers starts again each time. */
    private val bootCount: () -> Int,
) : TimerStore {

    /** The store of the app's timers. */
    constructor(context: Context) : this(
        dataStore = context.applicationContext.timerDataStore,
        bootCount = {
            Settings.Global.getInt(context.applicationContext.contentResolver, Settings.Global.BOOT_COUNT, UNKNOWN_BOOT)
        },
    )

    override suspend fun load(): SavedTimers {
        val text = dataStore.safeData.first()[KeyTimers] ?: return SavedTimers()
        val saved = try {
            json.decodeFromString(Snapshot.serializer(), text)
        } catch (_: SerializationException) {
            // Written by a version that saved them otherwise: as if none had been.
            return SavedTimers()
        }
        val boot = bootCount()
        return if (boot != UNKNOWN_BOOT && saved.bootCount == boot) saved.toDomain() else SavedTimers()
    }

    override suspend fun save(saved: SavedTimers) {
        try {
            dataStore.edit { preferences ->
                if (saved.timers.isEmpty) {
                    preferences.remove(KeyTimers)
                } else {
                    preferences[KeyTimers] = json.encodeToString(Snapshot.serializer(), saved.toSnapshot(bootCount()))
                }
            }
        } catch (cause: IOException) {
            // The timers still run and ring; only surviving the death of the app is lost.
            Log.w(TAG, "The timers could not be saved", cause)
        }
    }

    @Serializable
    private data class Snapshot(
        val bootCount: Int,
        val nextId: Int,
        val announced: Set<Int>,
        val timers: List<SavedTimer>,
    )

    @Serializable
    private data class SavedTimer(
        val id: Int,
        val slug: String,
        val name: String,
        val servings: Int,
        val stepIndex: Int,
        val durationMillis: Long,
        val endsAt: Long?,
        val pausedRemaining: Long?,
    )

    private fun SavedTimers.toSnapshot(bootCount: Int) = Snapshot(
        bootCount = bootCount,
        nextId = timers.nextId,
        announced = announced,
        timers = timers.timers.map { timer ->
            SavedTimer(
                id = timer.id,
                slug = timer.recipe.slug,
                name = timer.recipe.name,
                servings = timer.recipe.servings,
                stepIndex = timer.stepIndex,
                durationMillis = timer.duration.toMillis(),
                endsAt = timer.endsAt,
                pausedRemaining = timer.pausedRemaining,
            )
        },
    )

    private fun Snapshot.toDomain() = SavedTimers(
        timers = CookingTimers(
            timers = timers.map { timer ->
                CookingTimer(
                    id = timer.id,
                    recipe = TimerRecipe(slug = timer.slug, name = timer.name, servings = timer.servings),
                    stepIndex = timer.stepIndex,
                    duration = Duration.ofMillis(timer.durationMillis),
                    endsAt = timer.endsAt,
                    pausedRemaining = timer.pausedRemaining,
                )
            },
            nextId = nextId,
        ),
        announced = announced,
    )

    internal companion object {
        private const val TAG = "UmaiTimers"

        /** What a device that does not count its starts answers: nothing saved holds there. */
        const val UNKNOWN_BOOT = -1
        private val KeyTimers = stringPreferencesKey("timers")
        private val json = Json { ignoreUnknownKeys = true }
    }
}
