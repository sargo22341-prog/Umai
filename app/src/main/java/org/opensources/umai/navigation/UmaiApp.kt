package org.opensources.umai.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import org.opensources.umai.cooking.domain.CookingStepRequest
import org.opensources.umai.cooking.ui.ActiveTimerPills
import org.opensources.umai.cooking.ui.ActiveTimersViewModel
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.session.ServerSession
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.recipe.domain.ImportRequest
import org.opensources.umai.setup.ui.SetupRoute

/**
 * Root of the UI. Until an instance is configured (or after its token has been
 * refused) the setup screen replaces the whole navigation graph, so the user
 * never reaches an empty Home.
 *
 * [sharedUrl] is a recipe page shared to the app from another one: it opens the
 * import as soon as an instance is available, then [onSharedUrlHandled] clears it.
 * [cookingRequest] likewise opens the cooking mode a timer notification asks for,
 * and [importRequest] the import, or the recipe it created, an import notification does.
 */
@Composable
fun UmaiApp(
    modifier: Modifier = Modifier,
    sharedUrl: String? = null,
    onSharedUrlHandled: () -> Unit = {},
    cookingRequest: CookingStepRequest? = null,
    onCookingRequestHandled: () -> Unit = {},
    importRequest: ImportRequest? = null,
    onImportRequestHandled: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val sessionState by container.sessionManager.state.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Loading -> Box(modifier = modifier.fillMaxSize()) { LoadingView() }
        SessionState.NotConfigured, is SessionState.Expired -> SetupRoute(modifier = modifier)
        is SessionState.Active -> MainNavigation(
            session = state.session,
            sharedUrl = sharedUrl,
            onSharedUrlHandled = onSharedUrlHandled,
            cookingRequest = cookingRequest,
            onCookingRequestHandled = onCookingRequestHandled,
            importRequest = importRequest,
            onImportRequestHandled = onImportRequestHandled,
            modifier = modifier,
        )
    }
}

@Composable
private fun MainNavigation(
    session: ServerSession,
    sharedUrl: String?,
    onSharedUrlHandled: () -> Unit,
    cookingRequest: CookingStepRequest?,
    onCookingRequestHandled: () -> Unit,
    importRequest: ImportRequest?,
    onImportRequestHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    OpenSharedUrl(navController, sharedUrl, onSharedUrlHandled)
    OpenCookingRequest(navController, cookingRequest, onCookingRequestHandled)
    OpenImportRequest(navController, importRequest, onImportRequestHandled)

    var notice by rememberTransientNotice()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!destination.isFullScreen()) {
                UmaiBottomBar(
                    selected = TopLevelTab.entries.firstOrNull { it.matches(destination) },
                    searchSelected = destination?.hasRoute(SearchDestination::class) == true ||
                        destination?.hasRoute(OrganizerSearchDestination::class) == true,
                    onSelect = { navController.switchTab(it.route) },
                    onSearch = { navController.switchTab(SearchDestination) },
                    profile = container.profileTabInfo(session),
                )
            }
        },
    ) { padding ->
        // The tab bar already sits over the system navigation bar: the screens below must
        // not leave room for it a second time. Full-screen destinations keep that inset.
        val bottomBarPadding = PaddingValues(bottom = padding.calculateBottomPadding())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottomBarPadding)
                .consumeWindowInsets(bottomBarPadding),
        ) {
            AppNavHost(navController = navController, showNotice = { notice = it })
            FloatingPills(
                navController = navController,
                destination = destination,
                notice = notice,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** The last notice shown, which goes away by itself after [NOTICE_MILLIS]. */
@Composable
private fun rememberTransientNotice(): MutableState<AppNotice?> {
    val notice = remember { mutableStateOf<AppNotice?>(null) }
    LaunchedEffect(notice.value) {
        if (notice.value != null) {
            delay(NOTICE_MILLIS)
            notice.value = null
        }
    }
    return notice
}

@Composable
private fun OpenSharedUrl(navController: NavHostController, sharedUrl: String?, onHandled: () -> Unit) {
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            navController.navigate(RecipeImportDestination(sharedUrl))
            onHandled()
        }
    }
}

@Composable
private fun OpenCookingRequest(navController: NavHostController, request: CookingStepRequest?, onHandled: () -> Unit) {
    LaunchedEffect(request) {
        if (request != null) {
            navController.openCooking(request.slug, request.servings, request.step)
            onHandled()
        }
    }
}

@Composable
private fun OpenImportRequest(navController: NavHostController, request: ImportRequest?, onHandled: () -> Unit) {
    LaunchedEffect(request) {
        when (request) {
            null -> return@LaunchedEffect
            ImportRequest.OpenImport -> navController.openImport()
            is ImportRequest.OpenRecipe -> navController.navigate(RecipeDestination(request.slug))
        }
        onHandled()
    }
}

/** The running timers and the last notice, floating over every screen. */
@Composable
private fun FloatingPills(
    navController: NavHostController,
    destination: NavDestination?,
    notice: AppNotice?,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val activeTimers: ActiveTimersViewModel = viewModel(factory = ActiveTimersViewModel.factory(container))
    val timers by activeTimers.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Above the tab bar when there is one, above the system bar otherwise.
            .then(if (destination.isFullScreen()) Modifier.navigationBarsPadding() else Modifier)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The cooking mode shows its timers itself; every other screen gets
        // them as pills, on the left, clear of the buttons on the right.
        if (destination?.hasRoute(CookingDestination::class) != true) {
            ActiveTimerPills(
                state = timers,
                onOpen = { navController.openCooking(it.recipe.slug, it.recipe.servings, it.stepIndex) },
                onStop = activeTimers::dismiss,
                modifier = Modifier.align(Alignment.Start),
            )
        }
        NoticePill(
            message = notice?.let { stringResource(it.messageRes) },
            success = notice?.success ?: true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

private fun TopLevelTab.matches(destination: NavDestination?): Boolean = when (this) {
    TopLevelTab.HOME -> destination?.hasRoute(HomeDestination::class) == true
    TopLevelTab.PLANNING -> destination?.hasRoute(PlanningDestination::class) == true
    TopLevelTab.SHOPPING -> destination?.hasRoute(ShoppingDestination::class) == true
    TopLevelTab.PROFILE -> destination?.hasRoute(ProfileDestination::class) == true
}

/**
 * Readers and forms own the whole screen: the tab bar would only steal vertical
 * space and show no tab as selected.
 */
private fun NavDestination?.isFullScreen(): Boolean = this != null && FullScreenDestinations.any { hasRoute(it) }

private const val NOTICE_MILLIS = 2_500L

private fun AppContainer.profileTabInfo(session: ServerSession): ProfileTabInfo {
    val name = session.userDisplayName?.takeIf { it.isNotBlank() }
        ?: session.username?.takeIf { it.isNotBlank() }
    return ProfileTabInfo(
        displayName = name,
        avatarUrl = session.userId?.let { imageUrls.userAvatar(it, session.avatarCacheKey) },
        initials = name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
    )
}
