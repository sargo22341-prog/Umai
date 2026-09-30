package org.opensources.umai.cooking.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.opensources.umai.cooking.domain.CookingTimers
import org.opensources.umai.cooking.domain.SavedTimers
import org.opensources.umai.cooking.domain.TimerRecipe
import java.time.Duration

class DeviceTimerStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("timers.preferences_pb") } }
    private var boot = 3

    private fun store() = DeviceTimerStore(dataStore) { boot }

    private val tart = TimerRecipe(slug = "tarte", name = "Tarte", servings = 6)
    private val saved = SavedTimers(
        timers = CookingTimers()
            .start(tart, stepIndex = 2, duration = Duration.ofMinutes(15), now = 1_000)
            .start(tart, stepIndex = 3, duration = Duration.ofSeconds(30), now = 2_000)
            .pause(1, now = 60_000),
        announced = setOf(2),
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `the timers come back as they were saved`() = runBlocking {
        store().save(saved)

        assertEquals(saved, store().load())
    }

    @Test
    fun `timers saved before the device restarted are dropped`() = runBlocking {
        store().save(saved)
        boot = 4

        assertEquals(SavedTimers(), store().load())
    }

    @Test
    fun `a device that does not count its starts keeps nothing`() = runBlocking {
        boot = DeviceTimerStore.UNKNOWN_BOOT
        store().save(saved)

        assertEquals(SavedTimers(), store().load())
    }

    @Test
    fun `no timer left leaves nothing in the store`() = runBlocking {
        store().save(saved)
        store().save(SavedTimers())

        assertNull(dataStore.data.first()[stringPreferencesKey("timers")])
        assertEquals(SavedTimers(), store().load())
    }

    @Test
    fun `timers saved in another form are read as none`() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("timers")] = """{"version":0}""" }

        assertEquals(SavedTimers(), store().load())
    }
}
