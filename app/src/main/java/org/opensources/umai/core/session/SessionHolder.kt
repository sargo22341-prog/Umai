package org.opensources.umai.core.session

import org.opensources.umai.core.network.api.MealieApi

/**
 * The slice of the session that [AuthRepository] needs: read the current
 * session and replace it. Keeping it to an interface lets the sign-in logic be
 * tested without a DataStore or the Android keystore.
 */
interface SessionHolder {
    fun activeSession(): ServerSession?
    fun api(): MealieApi?
    suspend fun activate(session: ServerSession)
    suspend fun updateToken(token: String)
    suspend fun signOut()
}
