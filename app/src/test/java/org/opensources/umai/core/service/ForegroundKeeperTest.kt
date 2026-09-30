package org.opensources.umai.core.service

import android.app.ForegroundServiceStartNotAllowedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundKeeperTest {

    private var starts = 0
    private var stops = 0
    private var allowed = true

    private val keeper = ForegroundKeeper(
        start = {
            if (!allowed) throw ForegroundServiceStartNotAllowedException("in the background")
            starts++
        },
        name = "TestService",
    )

    @Test
    fun `the service runs while the work needs it, and stops after`() {
        keeper.keep()
        keeper.keep()
        keeper.onForeground { stops++ }
        assertEquals(1, starts)
        assertTrue(keeper.isKeeping)

        keeper.release()

        assertEquals(1, stops)
        assertFalse(keeper.isKeeping)
    }

    @Test
    fun `a service released before it reached the foreground stops once it gets there`() {
        keeper.keep()
        keeper.release()
        assertEquals(0, stops)

        keeper.onForeground { stops++ }

        assertEquals(1, stops)
        // The next work starts it again.
        keeper.keep()
        assertEquals(2, starts)
    }

    @Test
    fun `work started in the background goes on without the service`() {
        allowed = false
        keeper.keep()

        assertEquals(0, starts)
        assertTrue(keeper.isKeeping)
        // Back in the foreground, the next request starts it.
        allowed = true
        keeper.keep()
        assertEquals(1, starts)
    }
}
