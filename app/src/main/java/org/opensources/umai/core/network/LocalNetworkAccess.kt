package org.opensources.umai.core.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.DnsResolver
import android.net.InetAddresses
import android.os.CancellationSignal
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import kotlin.coroutines.resume

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

    /** A lookup the system resolver has not answered by then is taken as failed. */
    private const val LOOKUP_TIMEOUT_MS = 10_000L

    /**
     * Whether reaching [baseUrl] needs the permission, not granted yet. It is
     * read again on every call: the user can revoke the grant from Settings.
     */
    suspend fun isNeededFor(context: Context, baseUrl: String): Boolean =
        context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED && isLocalInstance(context, baseUrl)

    /**
     * Resolves [baseUrl] and reports whether it points at the local network.
     * A host that cannot be resolved in time is reported as non-local: the
     * connection attempt will then surface the real DNS error instead of a
     * permission one.
     */
    private suspend fun isLocalInstance(context: Context, baseUrl: String): Boolean {
        val host = baseUrl.toHttpUrlOrNull()?.host ?: return false
        if (InetAddresses.isNumericAddress(host)) return isLocal(InetAddresses.parseNumericAddress(host))
        return withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { resolve(context, host) }.orEmpty().any(::isLocal)
    }

    /** Private ranges, link-local, loopback, the unspecified address and IPv6 unique local addresses. */
    fun isLocal(address: InetAddress): Boolean =
        address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress ||
            address.isAnyLocalAddress || address.isUniqueLocalIpv6()

    private fun InetAddress.isUniqueLocalIpv6(): Boolean {
        val bytes = address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }

    /** The addresses of [host], empty when it has none; cancelling the caller cancels the lookup. */
    private suspend fun resolve(context: Context, host: String): List<InetAddress> =
        suspendCancellableCoroutine { continuation -> query(context, host, continuation) }

    private fun query(context: Context, host: String, continuation: CancellableContinuation<List<InetAddress>>) {
        val cancel = CancellationSignal()
        continuation.invokeOnCancellation { cancel.cancel() }
        // The replies are watched on the main looper; the callback only resumes the caller.
        DnsResolver(context, null).query(
            null,
            host,
            DnsResolver.FLAG_EMPTY,
            Runnable::run,
            cancel,
            object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(answer: List<InetAddress>, rcode: Int) = continuation.resume(answer)

                // An unknown host or a failed lookup: the connection attempt will tell which.
                override fun onError(error: DnsResolver.DnsException) = continuation.resume(emptyList())
            },
        )
    }
}
