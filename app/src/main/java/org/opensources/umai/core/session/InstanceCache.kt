package org.opensources.umai.core.session

import org.opensources.umai.core.network.ApiResult

/**
 * A value read from Mealie, kept for as long as the app stays signed in to the
 * same place: asked for under another instance or user ([instanceKey]), it is
 * read again rather than served from the previous one.
 */
class InstanceCache<T : Any>(private val instanceKey: () -> String?) {

    private class Entry<T>(val key: String?, val value: T)

    @Volatile
    private var entry: Entry<T>? = null

    /** The kept value, or the one [load] reads — kept when it succeeds. */
    suspend fun get(forceRefresh: Boolean = false, load: suspend () -> ApiResult<T>): ApiResult<T> {
        val key = instanceKey()
        entry?.takeIf { !forceRefresh && it.key == key }?.let { return ApiResult.Success(it.value) }
        return load().also { if (it is ApiResult.Success) entry = Entry(key, it.value) }
    }

    fun clear() {
        entry = null
    }
}
