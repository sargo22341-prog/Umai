package org.opensources.umai.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LocalNetworkAccessTest {

    // Literal addresses: InetAddress reads them without any DNS lookup.
    private fun isLocal(address: String) = LocalNetworkAccess.isLocal(InetAddress.getByName(address))

    @Test
    fun `private, loopback, link-local and unique local addresses are local`() {
        listOf("10.0.0.2", "172.16.4.1", "192.168.1.1", "127.0.0.1", "169.254.1.1", "0.0.0.0", "::1", "fe80::1", "fd12:3456::1")
            .forEach { assertTrue(it, isLocal(it)) }
    }

    @Test
    fun `public addresses are not`() {
        listOf("8.8.8.8", "172.32.0.1", "2001:4860:4860::8888").forEach { assertFalse(it, isLocal(it)) }
    }
}
