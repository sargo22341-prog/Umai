package org.opensources.umai.llm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A TPU that crashed the app is kept aside, for that build of the app and the system only. */
class TpuCrashGuardTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a load that returns leaves no trace`() {
        val marker = folder.root.resolve("tpu-loading")

        val result = TpuCrashGuard(marker, "1 fingerprint").loading { "loaded" }

        assertEquals("loaded", result)
        assertFalse(marker.exists())
        assertFalse(TpuCrashGuard(marker, "1 fingerprint").crashedBefore)
    }

    @Test
    fun `a load that never returned keeps the TPU aside until the app or the system changes`() {
        val marker = folder.root.resolve("tpu-loading")
        // What a native crash in the middle of loading leaves behind.
        marker.writeText("1 fingerprint")

        assertTrue(TpuCrashGuard(marker, "1 fingerprint").crashedBefore)
        assertFalse(TpuCrashGuard(marker, "2 fingerprint").crashedBefore)
        assertFalse(TpuCrashGuard(marker, "1 other-system").crashedBefore)
    }
}
