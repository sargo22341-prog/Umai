package org.opensources.umai.navigation

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A flash between screens is what lies behind them showing through. Halfway through a transition,
 * the two screens must be side by side and opaque: never blended, never the background.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTransitionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private lateinit var backDispatcher: OnBackPressedDispatcher

    @Before
    fun setUp() {
        composeRule.setContent {
            navController = rememberNavController()
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            NavHost(
                navController = navController,
                startDestination = FIRST,
                // A colour no screen uses: any gap or transparency shows it.
                modifier = Modifier.fillMaxSize().background(Color.Magenta),
                enterTransition = { screenEnter() },
                exitTransition = { screenExit() },
                popEnterTransition = { screenPopEnter() },
                popExitTransition = { screenPopExit() },
                predictivePopEnterTransition = { screenPopEnter() },
                predictivePopExitTransition = { screenPopExit() },
            ) {
                composable(FIRST) { Box(Modifier.fillMaxSize().background(Color.Red)) }
                composable(SECOND) { Box(Modifier.fillMaxSize().background(Color.Blue)) }
            }
        }
    }

    @Test
    fun screensStayOpaqueAndEdgeToEdgeGoingForwardAndBack() {
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread { navController.navigate(SECOND) }
        assertScreensSideBySideHalfway()
        composeRule.mainClock.advanceTimeBy(SCREEN_TRANSITION_MILLIS * 2L)

        composeRule.runOnUiThread { navController.popBackStack() }
        assertScreensSideBySideHalfway()
    }

    /** Without its own transitions, the back gesture would fade and shrink the screen instead. */
    @Test
    fun backGestureSlidesTheScreensLikeGoingBack() {
        composeRule.runOnUiThread { navController.navigate(SECOND) }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnUiThread {
            backDispatcher.dispatchOnBackStarted(backEvent(progress = 0f))
            backDispatcher.dispatchOnBackProgressed(backEvent(progress = 0.5f))
        }
        assertScreensSideBySideHalfway()
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    /** Going forward the first screen leaves on the left; going back it returns from the left. */
    private fun assertScreensSideBySideHalfway() {
        composeRule.mainClock.advanceTimeBy(SCREEN_TRANSITION_MILLIS / 2L)
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 2] }

        val boundary = row.indexOfFirst { it != Color.Red }
        assertTrue("the first screen is visible and opaque on the left", boundary > 0)
        assertEquals("the second screen is visible and opaque on the right", Color.Blue, row.last())
        // At most one pixel of seam, from rounding the two offsets.
        assertTrue("nothing shows between or through the screens", row.drop(boundary).count { it != Color.Blue } <= 1)
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
    }
}
