package org.opensources.umai.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.Proxy

/** A request to another website that led to the device's local network. */
class LocalAddressRefusedException(host: String) : IOException("$host is on the local network")

/**
 * Keeps the client for other websites off the local network. A link written
 * in a video description, a redirection or a DNS answer can point at
 * `192.168.1.1` or `localhost`: such a request fails before it is sent, rather
 * than reaching a device of the network — or being handed to Mealie's scraper,
 * which would load it from inside the network too.
 *
 * It checks the connection itself, as a network interceptor: every redirection
 * and every address a name resolves to goes through it. Behind a proxy, the
 * proxy reaches the destination and the check is its own.
 */
class PublicAddressGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val route = chain.connection()?.route()
        val address = route?.socketAddress?.address
        if (route?.proxy?.type() == Proxy.Type.DIRECT && address != null && LocalNetworkAccess.isLocal(address)) {
            throw LocalAddressRefusedException(chain.request().url.host)
        }
        return chain.proceed(chain.request())
    }
}
