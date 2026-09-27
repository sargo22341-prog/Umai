package org.opensources.umai.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the Mealie access token with an AES-GCM key held in the Android
 * Keystore, so the token never touches disk in clear text. The key material
 * itself stays inside the keystore and cannot be exported by the app.
 */
class SecretVault(private val keyAlias: String = DEFAULT_ALIAS) {

    /** Returns `Base64(iv || ciphertext)`, or `null` when the token is blank. */
    fun seal(plainText: String): String? {
        if (plainText.isEmpty()) return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    /**
     * Returns `null` when the payload cannot be decrypted, which happens after
     * a device restore or if the keystore entry was invalidated. Callers treat
     * that as "no session" and ask the user to sign in again.
     */
    fun unseal(sealed: String?): String? {
        if (sealed.isNullOrEmpty()) return null
        val raw = try {
            Base64.decode(sealed, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (raw.size <= IV_LENGTH) return null
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val cipherText = raw.copyOfRange(IV_LENGTH, raw.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            // A key invalidated or replaced: the token can no longer be read.
            null
        } catch (_: IOException) {
            // The keystore itself could not be loaded.
            null
        } catch (_: ProviderException) {
            // Thrown by the keystore daemon for a key it lost.
            null
        }
    }

    /**
     * Deletes the key. A key the keystore refuses to delete is left behind on
     * purpose: the token it sealed is already erased, and it only seals the next one.
     */
    fun clear() {
        try {
            keyStore().deleteEntry(keyAlias)
        } catch (_: GeneralSecurityException) {
            // Left behind, as said above.
        } catch (_: IOException) {
            // The keystore could not even be loaded: the key is left behind, as said above.
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val DEFAULT_ALIAS = "umai.session.v1"
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
