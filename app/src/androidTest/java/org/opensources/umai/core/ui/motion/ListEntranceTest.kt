package org.opensources.umai.core.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListEntranceTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun itemLeftWhileRising_isShownWhenScrolledBackAfterTheEntrance() {
        lateinit var listState: LazyListState
        rule.mainClock.autoAdvance = false
        rule.setContent {
            listState = rememberLazyListState()
            val entrance = rememberListEntrance()
            LazyColumn(state = listState) {
                items(count = 3) { index ->
                    Box(
                        modifier = Modifier
                            .testTag("item-$index")
                            .fillParentMaxSize()
                            .listEntrance(entrance, index)
                            .background(Color.Red),
                    )
                }
            }
        }
        // The first item starts rising, then leaves the screen before the end of its rise.
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread { listState.requestScrollToItem(1) }
        rule.mainClock.advanceTimeByFrame()
        // The entrance window is measured on the real clock.
        Thread.sleep(ENTRANCE_WINDOW_ELAPSED_MS)

        rule.runOnUiThread { listState.requestScrollToItem(0) }
        rule.mainClock.advanceTimeBy(SETTLE_MS)

        val pixels = rule.onNodeWithTag("item-0").captureToImage().toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 2])
    }

    private companion object {
        const val ENTRANCE_WINDOW_ELAPSED_MS = 700L
        const val SETTLE_MS = 1_000L
    }
}
