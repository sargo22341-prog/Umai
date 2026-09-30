package org.opensources.umai.core.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The keystore of the phone, under a key of the test's own: the app's key is never touched. */
@RunWith(AndroidJUnit4::class)
class SecretVaultTest {

    private val vault = SecretVault(keyAlias = "umai.test.vault")

    @After
    fun tearDown() = vault.clear()

    @Test
    fun aSealedTokenIsReadBackAndNeverStoredInClear() {
        val sealed = vault.seal("secret-token")

        assertNotNull(sealed)
        assertFalse(sealed.orEmpty().contains("secret-token"))
        assertEquals("secret-token", vault.unseal(sealed))
    }

    @Test
    fun nothingToSealGivesNothing() {
        assertNull(vault.seal(""))
        assertNull(vault.unseal(null))
        assertNull(vault.unseal(""))
    }

    @Test
    fun whatCannotBeReadGivesNoToken() {
        assertNull(vault.unseal("not base64 !"))
        assertNull(vault.unseal("c2hvcnQ="))
        // Sealed with a key since deleted: as after a restore on another phone.
        val sealed = vault.seal("secret-token")
        vault.clear()
        assertNull(vault.unseal(sealed))
    }
}
