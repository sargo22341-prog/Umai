package org.opensources.umai.core.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.opensources.umai.profile.data.AvatarImageSource
import org.opensources.umai.profile.data.ProfileRepository
import org.opensources.umai.recipe.data.RecipeCommentRepository
import org.opensources.umai.recipe.data.RecipeDraftStore
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.shopping.data.ShoppingRepository

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

    val recipeRepository = RecipeRepository(
        apiProvider = apiProvider,
        currentUserId = { sessionManager.activeSession()?.userId },
    )
    val recipeCommentRepository = RecipeCommentRepository(apiProvider)
    val recipeEditRepository = RecipeEditRepository(apiProvider)
    val organizerRepository = OrganizerRepository(apiProvider)
    val mealPlanRepository = MealPlanRepository(apiProvider)
    val shoppingRepository = ShoppingRepository(apiProvider)
    val profileRepository = ProfileRepository(apiProvider, AvatarImageSource(appContext))
    val recentRecipesStore = RecentRecipesStore(appContext)
    val recipeDraftStore = RecipeDraftStore(appContext)

    val imageUrls = ImageUrlResolver(sessionManager)

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
