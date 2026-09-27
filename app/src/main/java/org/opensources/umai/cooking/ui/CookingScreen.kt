package org.opensources.umai.cooking.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.opensources.umai.R
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.component.EmptyView
import org.opensources.umai.core.ui.component.KeepScreenOn
import org.opensources.umai.core.ui.component.LoadingView
import org.opensources.umai.core.ui.component.NetworkErrorView
import org.opensources.umai.core.ui.component.message
import org.opensources.umai.core.ui.component.title
import org.opensources.umai.recipe.domain.StepClip
import java.time.Duration

/**
 * Step-by-step cooking mode: one step fills the screen, navigation is reduced
 * to two large touch targets, and the screen is kept awake while the mode is
 * open so the phone can be put down on the worktop.
 *
 * [step] is the step to open on. [onOpenTimer] opens the cooking mode of a
 * timer started from another recipe; this recipe's timers open their step here.
 */
@Composable
fun CookingRoute(
    slug: String,
    servings: Int,
    step: Int,
    onExit: () -> Unit,
    onCooked: () -> Unit,
    onOpenTimer: (CookingTimer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: CookingViewModel = viewModel(
        factory = CookingViewModel.factory(container, slug, servings, step),
        key = "cooking-$slug",
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notifications = rememberNotificationAccess(onAllowed = viewModel::onNotificationsAllowed)

    LaunchedEffect(state.markedCooked) {
        if (state.markedCooked) onCooked()
    }

    val cookedSubject = stringResource(R.string.cooking_timeline_subject)
    val recipeId = state.recipe?.id.orEmpty()
    CookingScreen(
        state = state,
        clip = state.clip,
        onExit = onExit,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onGoToStep = viewModel::goToStep,
        onRetry = viewModel::load,
        onMarkCooked = { viewModel.markCooked(cookedSubject) },
        onDismissMarkError = viewModel::dismissMarkError,
        stepImageUrl = { source -> container.imageUrls.stepImage(recipeId, source) },
        stepPhotoUrl = { file -> container.imageUrls.recipeAsset(recipeId, file, state.recipe?.mediaVersion) },
        modifier = modifier,
        onStartTimer = { duration ->
            viewModel.startTimer(duration)
            if (!notifications.allowed) notifications.ask()
        },
        onPauseTimer = viewModel::pauseTimer,
        onResumeTimer = viewModel::resumeTimer,
        onDismissTimer = viewModel::dismissTimer,
        onOpenTimer = { timer ->
            if (timer.recipe.slug == slug) viewModel.goToStep(timer.stepIndex) else onOpenTimer(timer)
        },
        notificationsAllowed = notifications.allowed,
    )
}

/** Whether the timer notifications may show, and how to ask for them. */
private class NotificationAccess(val allowed: Boolean, val ask: () -> Unit)

/**
 * Timers ring without notifications; these only show them outside the app.
 * They are asked for with the first timer, and read again on every return, as
 * the reader may allow them from the system settings meanwhile.
 */
@Composable
private fun rememberNotificationAccess(onAllowed: () -> Unit): NotificationAccess {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.notificationsAllowed()) }
    LifecycleResumeEffect(Unit) {
        allowed = context.notificationsAllowed()
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (granted) onAllowed()
    }
    return NotificationAccess(allowed) { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

private fun Context.notificationsAllowed(): Boolean =
    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Stateless cooking mode, driven by [CookingUiState]. */
@Composable
fun CookingScreen(
    state: CookingUiState,
    clip: StepClip?,
    onExit: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGoToStep: (Int) -> Unit,
    onRetry: () -> Unit,
    onMarkCooked: () -> Unit,
    onDismissMarkError: () -> Unit,
    stepImageUrl: (String) -> String?,
    stepPhotoUrl: (String) -> String?,
    modifier: Modifier = Modifier,
    onStartTimer: (Duration) -> Unit = {},
    onPauseTimer: (Int) -> Unit = {},
    onResumeTimer: (Int) -> Unit = {},
    onDismissTimer: (Int) -> Unit = {},
    onOpenTimer: (CookingTimer) -> Unit = {},
    notificationsAllowed: Boolean = true,
    videoContent: @Composable (StepClip, Int) -> Unit = { stepClip, number -> StepVideoPlayer(stepClip, number) },
) {
    var stepListVisible by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    KeepScreenOn(enabled = state.keepScreenOn)
    if (finishing) {
        FinishDialog(
            marking = state.markingCooked,
            error = state.markError,
            onMarkCooked = onMarkCooked,
            onLeave = onExit,
            onDismiss = onDismissMarkError,
            onClose = { finishing = false },
        )
    }
    if (stepListVisible) {
        StepListSheet(
            steps = state.steps.map { it.title ?: it.text },
            currentStep = state.currentStep,
            onSelect = {
                onGoToStep(it)
                stepListVisible = false
            },
            onDismiss = { stepListVisible = false },
        )
    }
    val timers = TimerActions(onOpenTimer, onPauseTimer, onResumeTimer, onDismissTimer)
    Scaffold(
        modifier = modifier,
        topBar = { CookingTopBar(state, onExit = onExit, onShowSteps = { stepListVisible = true }) },
        bottomBar = { CookingBottomBar(state, notificationsAllowed, timers, onPrevious, onNext, onFinish = { finishing = true }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            CookingBody(state, clip, onRetry, stepImageUrl, stepPhotoUrl, onStartTimer, videoContent)
        }
    }
}

/** What the timer panel does with a timer. */
private class TimerActions(
    val onOpen: (CookingTimer) -> Unit,
    val onPause: (Int) -> Unit,
    val onResume: (Int) -> Unit,
    val onDismiss: (Int) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CookingTopBar(state: CookingUiState, onExit: () -> Unit, onShowSteps: () -> Unit) {
    TopAppBar(
        title = { Text(text = state.recipe?.name ?: stringResource(R.string.cooking_title), maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = stringResource(R.string.cooking_exit))
            }
        },
        actions = {
            if (state.stepCount > 0) {
                IconButton(onClick = onShowSteps) {
                    Icon(Icons.Outlined.FormatListNumbered, contentDescription = stringResource(R.string.cooking_steps))
                }
            }
        },
    )
}

@Composable
private fun CookingBottomBar(
    state: CookingUiState,
    notificationsAllowed: Boolean,
    timers: TimerActions,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Column {
        // Leaving the cooking mode leaves its timers running: they live on in the
        // notifications and on the timer pills of the other screens.
        if (state.timers.timers.isNotEmpty()) {
            TimersPanel(
                timers = state.timers.timers,
                now = state.now,
                recipeSlug = state.recipe?.slug,
                notificationsAllowed = notificationsAllowed,
                onOpen = timers.onOpen,
                onPause = timers.onPause,
                onResume = timers.onResume,
                onDismiss = timers.onDismiss,
            )
        }
        if (state.stepCount > 0) {
            CookingControls(
                hasPrevious = state.hasPrevious,
                isLastStep = state.isLastStep,
                onPrevious = onPrevious,
                onNext = onNext,
                onFinish = onFinish,
            )
        }
    }
}

@Composable
private fun CookingBody(
    state: CookingUiState,
    clip: StepClip?,
    onRetry: () -> Unit,
    stepImageUrl: (String) -> String?,
    stepPhotoUrl: (String) -> String?,
    onStartTimer: (Duration) -> Unit,
    videoContent: @Composable (StepClip, Int) -> Unit,
) {
    val error = state.error
    when {
        state.loading -> LoadingView()
        error != null -> NetworkErrorView(error = error, modifier = Modifier.fillMaxSize(), onRetry = onRetry)
        state.stepCount == 0 -> EmptyView(
            title = stringResource(R.string.cooking_title),
            message = stringResource(R.string.cooking_no_steps),
            modifier = Modifier.fillMaxSize(),
        )
        else -> StepContent(
            stepIndex = state.currentStep,
            stepCount = state.stepCount,
            title = state.step?.title,
            text = state.step?.text.orEmpty(),
            media = {
                val number = state.currentStep + 1
                clip?.let { videoContent(it, number) }
                state.step?.photo?.let { file -> StepImage(url = stepPhotoUrl(file), stepNumber = number) }
                state.step?.images.orEmpty().forEach { source -> StepImage(url = stepImageUrl(source), stepNumber = number) }
            },
            ingredients = state.ingredientsForStep,
            scale = state.scale,
            durations = state.stepDurations,
            onStartTimer = onStartTimer,
        )
    }
}

/**
 * Offered at the end of the recipe: record it as cooked in Mealie, or just
 * leave. [onClose] is called before leaving or dismissing it.
 */
@Composable
private fun FinishDialog(
    marking: Boolean,
    error: NetworkError?,
    onMarkCooked: () -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!marking) {
                onClose()
                onDismiss()
            }
        },
        title = { Text(stringResource(R.string.cooking_done_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cooking_done_message))
                Text(stringResource(R.string.cooking_mark_cooked_summary), style = MaterialTheme.typography.bodySmall)
                error?.let {
                    Text(
                        text = "${it.title()}\n${it.message()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onMarkCooked, enabled = !marking) {
                if (marking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.cooking_mark_cooked))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClose()
                    onLeave()
                },
                enabled = !marking,
            ) {
                Text(stringResource(R.string.cooking_leave))
            }
        },
    )
}

@Composable
private fun CookingControls(
    hasPrevious: Boolean,
    isLastStep: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = hasPrevious,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.cooking_previous))
            }
            Button(
                onClick = if (isLastStep) onFinish else onNext,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            ) {
                Text(stringResource(if (isLastStep) R.string.cooking_finish else R.string.cooking_next))
                if (!isLastStep) {
                    Spacer(Modifier.size(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}
