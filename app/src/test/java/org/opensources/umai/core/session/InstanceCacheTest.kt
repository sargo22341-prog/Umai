package org.opensources.umai.core.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError

class InstanceCacheTest {

    private var key: String? = "https://a|u1"
    private var reads = 0
    private val cache = InstanceCache<String> { key }

    private suspend fun read(answer: ApiResult<String> = ApiResult.Success("read ${reads + 1}")) =
        cache.get { reads++; answer }

    @Test
    fun `a value read once is kept for the same instance and user`() = runTest {
        assertEquals(ApiResult.Success("read 1"), read())
        assertEquals(ApiResult.Success("read 1"), read())
        assertEquals(1, reads)
    }

    @Test
    fun `another instance or another user reads it again`() = runTest {
        val _ = read()
        key = "https://b|u1"
        assertEquals(ApiResult.Success("read 2"), read())
        key = "https://b|u2"
        assertEquals(ApiResult.Success("read 3"), read())
    }

    @Test
    fun `a failure is not kept`() = runTest {
        assertEquals(ApiResult.Failure(NetworkError.Timeout), read(ApiResult.Failure(NetworkError.Timeout)))
        assertEquals(ApiResult.Success("read 2"), read())
    }

    @Test
    fun `a forced refresh and a clear read it again`() = runTest {
        val _ = read()
        assertEquals(ApiResult.Success("read 2"), cache.get(forceRefresh = true) { reads++; ApiResult.Success("read 2") })
        cache.clear()
        assertEquals(ApiResult.Success("read 3"), read())
    }
}
