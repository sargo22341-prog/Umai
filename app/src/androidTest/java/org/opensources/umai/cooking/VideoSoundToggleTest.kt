package org.opensources.umai.cooking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.cooking.ui.VideoSoundToggle
import org.opensources.umai.core.ui.theme.UmaiTheme

/** The step video sound switch: muted first, then toggled on and off. */
@RunWith(AndroidJUnit4::class)
class VideoSoundToggleTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun togglesBetweenMutedAndSound() {
        rule.setContent {
            UmaiTheme {
                var muted by remember { mutableStateOf(true) }
                VideoSoundToggle(muted = muted, onToggle = { muted = !muted })
            }
        }
        val soundOn = context.getString(R.string.cooking_video_sound_on)
        val soundOff = context.getString(R.string.cooking_video_sound_off)

        rule.onNodeWithContentDescription(soundOn).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription(soundOff).assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription(soundOn).assertIsDisplayed()
    }
}
