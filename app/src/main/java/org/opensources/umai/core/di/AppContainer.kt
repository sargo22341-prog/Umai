package org.opensources.umai.core.di

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import org.opensources.umai.cooking.data.CookingTimerController
import org.opensources.umai.cooking.data.SystemTimerAlarm
import org.opensources.umai.cooking.data.SystemTimerHost
import org.opensources.umai.cooking.data.TimerNotifications
import org.opensources.umai.core.image.DeviceImageCropper
import org.opensources.umai.core.network.LocalNetworkAccess
import org.opensources.umai.core.network.MealieMedia
import org.opensources.umai.core.session.AuthRepository
import org.opensources.umai.core.session.SessionManager
import org.opensources.umai.core.session.SessionStore
import org.opensources.umai.core.settings.AppPreferencesRepository
import org.opensources.umai.core.settings.LocaleController
import org.opensources.umai.home.data.RecentRecipesStore
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.profile.data.ProfileRepository
import org.opensources.umai.provider.ProviderRegistry
import org.opensources.umai.provider.data.HttpPhotoDownloader
import org.opensources.umai.provider.data.ProviderMediaImporter
import org.opensources.umai.provider.data.ProviderSettingsStore
import org.opensources.umai.provider.jow.JowProvider
import org.opensources.umai.provider.marmiton.MarmitonProvider
import org.opensources.umai.provider.site750g.Site750gProvider
import org.opensources.umai.recipe.data.CalorieTagRepository
import org.opensources.umai.recipe.data.DeviceRecipeImageFiles
import org.opensources.umai.recipe.data.RecipeCommentRepository
import org.opensources.umai.recipe.data.RecipeDraftStore
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.shopping.data.ShoppingRepository
import java.util.concurrent.TimeUnit

/**
 * Hand-written dependency graph.
 *
 * The app has a single module and a handful of long-lived objects, so a manual
 * container stays easier to read than a DI framework and adds no annotation
 * processing to the build.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferencesRepository = AppPreferencesRepository(appContext)
    val localeController = LocaleController(appContext)

    private val sessionStore = SessionStore(appContext)

    val sessionManager = SessionManager(
        store = sessionStore,
        scope = applicationScope,
        acceptLanguage = { localeController.acceptLanguage() },
    )

    val authRepository = AuthRepository(sessionManager)

    private val apiProvider: () -> org.opensources.umai.core.network.api.MealieApi? =
        { sessionManager.api() }

    val calorieTagRepository = CalorieTagRepository(apiProvider)
    val recipeRepository = RecipeRepository(
        apiProvider = apiProvider,
        currentUserId = { sessionManager.activeSession()?.userId },
        calorieTags = calorieTagRepository::calorieTags,
    )
    val recipeCommentRepository = RecipeCommentRepository(apiProvider)
    val recipeMediaRepository = RecipeMediaRepository(apiProvider)
    val recipeEditRepository = RecipeEditRepository(apiProvider, recipeMediaRepository)
    val organizerRepository = OrganizerRepository(apiProvider)
    val mealPlanRepository = MealPlanRepository(apiProvider)
    val shoppingRepository = ShoppingRepository(apiProvider)
    private val imageCropper = DeviceImageCropper(appContext)
    val profileRepository = ProfileRepository(apiProvider, imageCropper)
    val recentRecipesStore = RecentRecipesStore(appContext)
    val recipeImageFiles = DeviceRecipeImageFiles(appContext, imageCropper)
    val recipeDraftStore = RecipeDraftStore(appContext, recipeImageFiles)

    val imageUrls = ImageUrlResolver(sessionManager)

    /**
     * For requests to other websites (a recipe provider, a video host): no
     * Mealie credentials, and the platform's own trust settings.
     */
    private val externalHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Removing a provider is removing its line here, and its package. */
    val providerRegistry = ProviderRegistry(listOf(JowProvider, Site750gProvider, MarmitonProvider))
    val providerSettings = ProviderSettingsStore(appContext)
    val providerMediaImporter = ProviderMediaImporter(
        apiProvider = apiProvider,
        registry = providerRegistry,
        media = recipeMediaRepository,
        downloader = HttpPhotoDownloader(externalHttpClient),
    )

    /**
     * Monotonic, and counting while the device sleeps: the cooking timers are
     * read against it.
     */
    val timerClock: () -> Long = SystemClock::elapsedRealtime
    val timerNotifications = TimerNotifications(appContext)
    val timerHost = SystemTimerHost(appContext, timerNotifications)

    /** The cooking timers, which outlive the cooking mode and the app's screens. */
    val cookingTimers = CookingTimerController(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        alarm = SystemTimerAlarm(appContext),
        host = timerHost,
        options = preferencesRepository.preferences.map { it.cookingTimers },
        clock = timerClock,
    )

    /** Re-read on every call: the user can revoke the grant from Settings. */
    val localNetworkPermission: () -> Boolean = { LocalNetworkAccess.isGranted(appContext) }
}

/**
 * Builds media URLs for the instance that is currently configured, so screens
 * never have to know the server address.
 */
class ImageUrlResolver(private val sessionManager: SessionManager) {

    fun thumbnail(recipeId: String, imageToken: String?): String? =
        url(recipeId, imageToken, MealieMedia.ImageSize.SMALL)

    fun medium(recipeId: String, imageToken: String?): String? =
        url(recipeId, imageToken, MealieMedia.ImageSize.MEDIUM)

    fun original(recipeId: String, imageToken: String?): String? =
        url(recipeId, imageToken, MealieMedia.ImageSize.ORIGINAL)

    fun recipeAsset(recipeId: String, fileName: String, version: String?): String? =
        sessionManager.baseUrl()?.let { MealieMedia.recipeAsset(it, recipeId, fileName, version) }

    fun stepImage(recipeId: String, source: String): String? =
        sessionManager.baseUrl()?.let { MealieMedia.resolveStepImage(it, recipeId, source) }

    fun userAvatar(userId: String, cacheKey: String?): String? =
        sessionManager.baseUrl()?.let { MealieMedia.userImage(it, userId, cacheKey) }

    private fun url(
        recipeId: String,
        imageToken: String?,
        size: MealieMedia.ImageSize,
    ): String? {
        if (imageToken == null) return null
        val base = sessionManager.baseUrl() ?: return null
        return MealieMedia.recipeImage(base, recipeId, size, imageToken)
    }
}
