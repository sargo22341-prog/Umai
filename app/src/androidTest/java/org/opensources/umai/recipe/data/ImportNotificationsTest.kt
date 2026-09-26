package org.opensources.umai.recipe.data

import android.app.Notification
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.recipe.domain.ImportPhase
import org.opensources.umai.recipe.domain.RecipeImportRun
import org.opensources.umai.youtube.domain.WatchProgress

/** What the system shows of an import running outside its screen. */
@RunWith(AndroidJUnit4::class)
class ImportNotificationsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifications = ImportNotifications(context)

    private fun Notification.text(key: String) = extras.getCharSequence(key)?.toString()

    @Test
    fun aRunningImportShowsItsPhaseAndTheSiteItComesFrom() {
        val shown = notifications.running(RecipeImportRun(url = "https://youtu.be/abc", isVideo = true, phase = ImportPhase.UNDERSTANDING))

        assertEquals(context.getString(R.string.import_notification_running), shown.text(Notification.EXTRA_TITLE))
        assertEquals(context.getString(R.string.import_understanding), shown.text(Notification.EXTRA_TEXT))
        assertEquals("youtu.be", shown.text(Notification.EXTRA_SUB_TEXT))
        assertTrue(shown.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(shown.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test
    fun listeningToAVideoShowsHowFarItIs() {
        val run = RecipeImportRun(
            url = "https://youtu.be/abc",
            isVideo = true,
            phase = ImportPhase.WATCHING,
            watchProgress = WatchProgress(seeing = false, done = 2, total = 5),
        )

        val shown = notifications.running(run)

        assertEquals(context.getString(R.string.import_watching_sound), shown.text(Notification.EXTRA_TEXT))
        assertEquals(5, shown.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(2, shown.extras.getInt(Notification.EXTRA_PROGRESS))
    }

    @Test
    fun onlyTheAppsOwnImportIntentsAreRead() {
        val component = Intent().setClassName(context, "org.opensources.umai.MainActivity")

        assertEquals(ImportRequest.OpenImport, ImportNotifications.request(Intent(component).setAction("org.opensources.umai.action.OPEN_IMPORT")))
        assertEquals(
            ImportRequest.OpenRecipe("tarte"),
            ImportNotifications.request(
                Intent(component).setAction("org.opensources.umai.action.OPEN_IMPORTED_RECIPE").putExtra("slug", "tarte"),
            ),
        )
        // A recipe to open needs its address; any other intent is not an import's.
        assertNull(ImportNotifications.request(Intent(component).setAction("org.opensources.umai.action.OPEN_IMPORTED_RECIPE")))
        assertNull(ImportNotifications.request(Intent(Intent.ACTION_MAIN)))
    }
}
