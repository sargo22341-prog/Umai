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

    /**
     * Swaps [previous] for [token] after a refresh, unless the session changed
     * meanwhile (signed out, another instance): a token must never land on a
     * session it was not issued for. Answers whether the token was replaced.
     */
    suspend fun replaceToken(previous: String, token: String): Boolean

    suspend fun signOut()
}
