package org.opensources.umai.core.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress

/**
 * Android 17 gates connections to the local network behind the
 * `ACCESS_LOCAL_NETWORK` runtime permission. A Mealie instance is very often
 * on the user's LAN, so without it every request to `mealie.home` or
 * `192.168.x.y` fails with a connect timeout and no other explanation.
 *
 * The permission is only asked for when the instance really resolves to a
 * local address; an instance published on the internet never triggers a prompt.
 */
object LocalNetworkAccess {

    const val PERMISSION: String = Manifest.permission.ACCESS_LOCAL_NETWORK

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Resolves [baseUrl] and reports whether it points at the local network.
     * A host that cannot be resolved is reported as non-local: the connection
     * attempt will then surface the real DNS error instead of a permission one.
     */
    suspend fun isLocalInstance(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val host = baseUrl.toHttpUrlOrNull()?.host ?: return@withContext false
        runCatching { InetAddress.getAllByName(host).any { it.isLocal() } }.getOrDefault(false)
    }

    /** Private ranges, link-local, loopback and IPv6 unique local addresses. */
    private fun InetAddress.isLocal(): Boolean =
        isSiteLocalAddress || isLinkLocalAddress || isLoopbackAddress || isAnyLocalAddress ||
            isUniqueLocalIpv6()

    private fun InetAddress.isUniqueLocalIpv6(): Boolean {
        val bytes = address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
