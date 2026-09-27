package org.opensources.umai.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.opensources.umai.core.settings.safeData

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "umai_session")

/**
 * Persists the configured instance. The access token is stored sealed by
 * [SecretVault]; the password is never persisted in any form.
 */
class SessionStore(context: Context, private val vault: SecretVault = SecretVault()) {

    private val dataStore = context.applicationContext.sessionDataStore

    val stored: Flow<StoredSession?> = dataStore.safeData
        .map { prefs ->
            val baseUrl = prefs[KeyBaseUrl] ?: return@map null
            StoredSession(
                baseUrl = baseUrl,
                token = vault.unseal(prefs[KeySealedToken]),
                authMode = prefs[KeyAuthMode]?.let { name -> AuthMode.entries.firstOrNull { it.name == name } }
                    ?: AuthMode.PASSWORD,
                username = prefs[KeyUsername],
                userId = prefs[KeyUserId],
                userDisplayName = prefs[KeyUserDisplayName],
                serverVersion = prefs[KeyServerVersion],
                isAdmin = prefs[KeyIsAdmin] == true,
                avatarCacheKey = prefs[KeyAvatarCacheKey],
                tokenRejected = prefs[KeyTokenRejected] == true,
            )
        }

    /** Where the stored session signs in, and as whom (see [instanceKeyOf]); the token is left sealed. */
    val instanceKeys: Flow<String?> = dataStore.safeData
        .map { prefs -> prefs[KeyBaseUrl]?.let { instanceKeyOf(it, prefs[KeyUserId]) } }

    suspend fun save(session: ServerSession) {
        val sealed = vault.seal(session.token)
        dataStore.edit { prefs ->
            prefs[KeyBaseUrl] = session.baseUrl
            if (sealed != null) prefs[KeySealedToken] = sealed else prefs.remove(KeySealedToken)
            prefs[KeyAuthMode] = session.authMode.name
            session.username?.let { prefs[KeyUsername] = it } ?: prefs.remove(KeyUsername)
            session.userId?.let { prefs[KeyUserId] = it } ?: prefs.remove(KeyUserId)
            session.userDisplayName?.let { prefs[KeyUserDisplayName] = it }
                ?: prefs.remove(KeyUserDisplayName)
            session.serverVersion?.let { prefs[KeyServerVersion] = it } ?: prefs.remove(KeyServerVersion)
            prefs[KeyIsAdmin] = session.isAdmin
            session.avatarCacheKey?.let { prefs[KeyAvatarCacheKey] = it }
                ?: prefs.remove(KeyAvatarCacheKey)
            prefs[KeyTokenRejected] = false
        }
    }

    /**
     * Replaces the token after a silent refresh, leaving the rest untouched —
     * unless the stored session is no longer the one of [baseUrl], or was
     * rejected meanwhile.
     */
    suspend fun updateToken(baseUrl: String, token: String) {
        val sealed = vault.seal(token) ?: return
        dataStore.edit { prefs ->
            if (prefs[KeyBaseUrl] == baseUrl && prefs[KeyTokenRejected] != true) prefs[KeySealedToken] = sealed
        }
    }

    /** Marks the session as needing a new sign-in without forgetting the instance. */
    suspend fun markTokenRejected() {
        dataStore.edit { prefs ->
            prefs[KeyTokenRejected] = true
            prefs.remove(KeySealedToken)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            val owner = prefs[KeyDataOwner]
            prefs.clear()
            // The data kept on the device still comes from that place.
            owner?.let { prefs[KeyDataOwner] = it }
        }
        vault.clear()
    }

    /**
     * Records that the data kept on the device now comes from [owner], an
     * instance and a user, and answers whether it came from another one until
     * now. A first claim takes over what is there.
     */
    suspend fun claimLocalData(owner: String): Boolean {
        var changed = false
        dataStore.edit { prefs ->
            changed = prefs[KeyDataOwner]?.let { it != owner } ?: false
            prefs[KeyDataOwner] = owner
        }
        return changed
    }

    data class StoredSession(
        val baseUrl: String,
        val token: String?,
        val authMode: AuthMode,
        val username: String?,
        val userId: String?,
        val userDisplayName: String?,
        val serverVersion: String?,
        val isAdmin: Boolean,
        val avatarCacheKey: String?,
        val tokenRejected: Boolean,
    )

    private companion object {
        val KeyBaseUrl = stringPreferencesKey("base_url")
        val KeySealedToken = stringPreferencesKey("sealed_token")
        val KeyAuthMode = stringPreferencesKey("auth_mode")
        val KeyUsername = stringPreferencesKey("username")
        val KeyUserId = stringPreferencesKey("user_id")
        val KeyUserDisplayName = stringPreferencesKey("user_display_name")
        val KeyServerVersion = stringPreferencesKey("server_version")
        val KeyIsAdmin = booleanPreferencesKey("user_is_admin")
        val KeyAvatarCacheKey = stringPreferencesKey("user_avatar_cache_key")
        val KeyTokenRejected = booleanPreferencesKey("token_rejected")
        val KeyDataOwner = stringPreferencesKey("local_data_owner")
    }
}

/** An instance and a user, as one key: what was read from Mealie holds only under the same one. */
internal fun instanceKeyOf(baseUrl: String, userId: String?): String = "$baseUrl|${userId.orEmpty()}"
