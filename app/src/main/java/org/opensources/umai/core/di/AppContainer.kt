package org.opensources.umai.core.di

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.opensources.umai.BuildConfig
import org.opensources.umai.cooking.data.CookingTimerController
import org.opensources.umai.cooking.data.SystemTimerAlarm
import org.opensources.umai.cooking.data.SystemTimerHost
import org.opensources.umai.cooking.data.TimerNotifications
import org.opensources.umai.core.image.DeviceImageCropper
import org.opensources.umai.core.image.HttpPhotoDownloader
import org.opensources.umai.core.network.ImageUrlResolver
import org.opensources.umai.core.network.LocalNetworkAccess
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.session.AuthRepository
import org.opensources.umai.core.session.SessionManager
import org.opensources.umai.core.session.SessionStore
import org.opensources.umai.core.settings.AppPreferencesRepository
import org.opensources.umai.core.settings.LocaleController
import org.opensources.umai.home.data.RecentRecipesStore
import org.opensources.umai.llm.data.DeviceAccelerators
import org.opensources.umai.llm.data.LiteRtLmLoader
import org.opensources.umai.llm.data.LocalAiSettingsStore
import org.opensources.umai.llm.data.LocalAiWork
import org.opensources.umai.llm.data.LocalLanguageModel
import org.opensources.umai.llm.data.ModelInstaller
import org.opensources.umai.llm.data.TpuCrashGuard
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.planning.data.DishCourseStore
import org.opensources.umai.planning.data.DeviceBarcodePictures
import org.opensources.umai.planning.data.DeviceLabelPictures
import org.opensources.umai.planning.data.DevicePlanPhotos
import org.opensources.umai.planning.data.DishPoolRepository
import org.opensources.umai.planning.data.MealPlanRepository
import org.opensources.umai.planning.data.OpenFoodFactsRepository
import org.opensources.umai.planning.data.RecipeCaloriesRepository
import org.opensources.umai.planning.domain.ModelCourseClassifier
import org.opensources.umai.profile.data.ProfileRepository
import org.opensources.umai.provider.domain.ProviderRegistry
import org.opensources.umai.provider.data.ProviderMediaImporter
import org.opensources.umai.provider.data.ProviderSettingsStore
import org.opensources.umai.provider.jow.JowProvider
import org.opensources.umai.provider.marmiton.MarmitonProvider
import org.opensources.umai.provider.site750g.Site750gProvider
import org.opensources.umai.recipe.data.CalorieTagRepository
import org.opensources.umai.recipe.data.DeviceRecipeImageFiles
import org.opensources.umai.recipe.data.ImportNotifications
import org.opensources.umai.recipe.data.RecipeImportController
import org.opensources.umai.recipe.data.SystemImportHost
import org.opensources.umai.recipe.data.RecipeCommentRepository
import org.opensources.umai.recipe.data.RecipeDraftStore
import org.opensources.umai.recipe.data.RecipeEditRepository
import org.opensources.umai.recipe.data.RecipeMediaRepository
import org.opensources.umai.recipe.data.RecipeRepository
import org.opensources.umai.recipe.data.VideoStreams
import org.opensources.umai.shopping.data.ShoppingRepository
import org.opensources.umai.speech.data.SpeechModelInstaller
import org.opensources.umai.speech.data.SpeechSettingsStore
import org.opensources.umai.speech.data.WhisperTranscriber
import org.opensources.umai.youtube.data.AndroidVideoMedia
import org.opensources.umai.youtube.data.MealieRecipePages
import org.opensources.umai.youtube.data.VideoRecipeImporter
import org.opensources.umai.youtube.data.YouTubeClient
import org.opensources.umai.youtube.domain.VideoWatcher
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

    private val apiProvider: () -> MealieApi? = { sessionManager.api() }
    private val instanceKey: () -> String? = { sessionManager.instanceKey() }

    val calorieTagRepository = CalorieTagRepository(apiProvider, instanceKey)
    val recipeRepository = RecipeRepository(
        apiProvider = apiProvider,
        currentUserId = { sessionManager.activeSession()?.userId },
        calorieTags = calorieTagRepository::calorieTags,
    )
    val recipeCommentRepository = RecipeCommentRepository(apiProvider)
    val recipeMediaRepository = RecipeMediaRepository(apiProvider)
    val recipeEditRepository = RecipeEditRepository(apiProvider, recipeMediaRepository)
    val organizerRepository = OrganizerRepository(apiProvider, instanceKey)
    val mealPlanRepository = MealPlanRepository(apiProvider)
    val shoppingRepository = ShoppingRepository(apiProvider)
    private val imageCropper = DeviceImageCropper(appContext)
    val profileRepository = ProfileRepository(apiProvider, imageCropper)
    val recentRecipesStore = RecentRecipesStore(appContext)
    val recipeImageFiles = DeviceRecipeImageFiles(appContext, imageCropper)
    val recipeDraftStore = RecipeDraftStore(appContext, recipeImageFiles)
    val recipeCaloriesRepository = RecipeCaloriesRepository(apiProvider, instanceKey)
    val planPhotos = DevicePlanPhotos(appContext, imageCropper)
    val labelPictures = DeviceLabelPictures(appContext, imageCropper)

    val imageUrls = ImageUrlResolver(sessionManager::baseUrl)

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
    /** Pictures published by other websites: a recipe provider, a food database. */
    val externalPhotoDownloader = HttpPhotoDownloader(externalHttpClient)
    val providerMediaImporter = ProviderMediaImporter(
        apiProvider = apiProvider,
        registry = providerRegistry,
        media = recipeMediaRepository,
        downloader = externalPhotoDownloader,
    )

    /** Products found by their barcode, for the foods added to the plan. */
    val openFoodFacts = OpenFoodFactsRepository(
        client = externalHttpClient,
        userAgent = "umai/${BuildConfig.VERSION_NAME} (https://github.com/sargo22341-prog/umai)",
        language = localeController::appLanguage,
    )
    val barcodePictures = DeviceBarcodePictures(appContext)

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

    val importNotifications = ImportNotifications(appContext)
    val importHost = SystemImportHost(appContext, importNotifications)

    // The on-device AI, the speech recognition and YouTube: built on first use,
    // as most sessions never reach them and the device profile reads files.

    /** The phone's memory, which bounds the language models it can run. */
    val deviceMemoryBytes: Long by lazy {
        ActivityManager.MemoryInfo()
            .also { appContext.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            .totalMem
    }

    private val nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir

    private val tpuGuard by lazy {
        TpuCrashGuard(
            marker = appContext.noBackupFilesDir.resolve("tpu-loading"),
            build = "${BuildConfig.VERSION_CODE} ${Build.FINGERPRINT}",
        )
    }

    /** The chip of this phone, and whether its TPU is within the app's reach. */
    val aiDevice by lazy { DeviceAccelerators.profile(nativeLibraryDir, tpuGuard) }

    val localAiSettings = LocalAiSettingsStore(appContext)

    /** Quiet during an import, which keeps the app alive and tells what the model does itself. */
    val localAiWork = LocalAiWork(appContext, heldElsewhere = { importHost.keepsAppAlive })

    /** Picks up a download that ended while the app was away, as soon as the model is needed. */
    val modelInstaller by lazy {
        ModelInstaller(appContext, localAiSettings, aiDevice.tensorChip, applicationScope).also { it.resume() }
    }

    private val liteRtLm by lazy {
        LiteRtLmLoader(nativeLibraryDir, appContext.cacheDir.resolve("litertlm"), tpuGuard)
    }

    /** The on-device language model; every feature using it also works without it. */
    val localLanguageModel by lazy {
        LocalLanguageModel(
            installed = modelInstaller::installedModel,
            device = aiDevice,
            loader = liteRtLm,
            isSupported = liteRtLm.isAvailable,
            work = localAiWork,
            scope = applicationScope,
        )
    }

    val speechSettings = SpeechSettingsStore(appContext)
    val speechModelInstaller by lazy {
        SpeechModelInstaller(appContext, speechSettings, applicationScope).also { it.resume() }
    }

    /** Writes down the speech of a video without captions, when a Whisper model is installed. */
    private val speechTranscriber by lazy {
        WhisperTranscriber(
            modelPath = speechModelInstaller::installedPath,
            work = localAiWork,
            scope = applicationScope,
        )
    }

    /** Reads YouTube videos without an account, for the import and the cooking mode. */
    private val youTubeClient by lazy { YouTubeClient(externalHttpClient, language = localeController::appLanguage) }

    val videoStreams by lazy { VideoStreams(youTubeClient) }

    private val videoRecipeImporter by lazy {
        VideoRecipeImporter(
            youTube = youTubeClient,
            pages = MealieRecipePages(apiProvider, externalHttpClient),
            model = localLanguageModel,
            watcher = VideoWatcher(localLanguageModel, speechTranscriber, AndroidVideoMedia()),
            apiProvider = apiProvider,
            edits = recipeEditRepository,
            media = recipeMediaRepository,
            language = localeController::appLanguage,
        )
    }

    /** The recipe import, which outlives the import screen. */
    val recipeImports by lazy {
        RecipeImportController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            repository = recipeEditRepository,
            calorieTags = calorieTagRepository,
            providers = providerRegistry,
            providerSettings = providerSettings,
            mediaImporter = providerMediaImporter,
            videoImporter = videoRecipeImporter,
            host = importHost,
        )
    }

    val dishCourseStore = DishCourseStore(appContext)
    val dishPoolRepository by lazy {
        DishPoolRepository(
            apiProvider = apiProvider,
            courses = dishCourseStore,
            modelClassifier = ModelCourseClassifier(localLanguageModel),
        )
    }

    /** Re-read on every call: the user can revoke the grant from Settings. */
    val localNetworkPermission: () -> Boolean = { LocalNetworkAccess.isGranted(appContext) }

    init {
        // What the device keeps of an instance — history, courses — is only
        // good there: it goes when the app is signed in somewhere else.
        applicationScope.launch {
            sessionManager.watchInstanceChanges {
                recentRecipesStore.clear()
                dishCourseStore.clear()
            }
        }
    }
}
