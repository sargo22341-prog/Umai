package org.opensources.umai

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.TokenProvider

class UmaiApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Apply the saved language before the first screen is composed.
        container.applicationScope.launch {
            val preferences = container.preferencesRepository.preferences.first()
            container.localeController.apply(preferences.language)
        }
    }

    /**
     * Coil shares the session's OkHttp client so recipe pictures are fetched
     * with the same authentication, TLS configuration and connection pool as
     * the API calls. Coil itself has no Google Play Services dependency.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            container.sessionManager.imageClient() ?: fallbackHttpClient()
                        },
                    ),
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("umai_images").toOkioPath())
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()

    private fun fallbackHttpClient(): OkHttpClient =
        MealieClientFactory.okHttpClient(TokenProvider { null })
}
