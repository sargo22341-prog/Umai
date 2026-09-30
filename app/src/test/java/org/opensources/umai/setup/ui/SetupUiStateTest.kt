package org.opensources.umai.setup.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupUiStateTest {

    @Test
    fun `the password and the API token never show in the text of the state`() {
        val text = SetupUiState(url = "https://mealie.example", username = "marie", password = "hunter2", apiToken = "tok-123").toString()

        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("tok-123"))
        assertTrue(text.contains("marie"))
    }
}
