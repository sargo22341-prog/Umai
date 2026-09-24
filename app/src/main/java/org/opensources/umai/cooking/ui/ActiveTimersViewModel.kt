package org.opensources.umai.cooking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.opensources.umai.cooking.data.CookingTimerController
import org.opensources.umai.cooking.domain.CookingTimer
import org.opensources.umai.core.di.AppContainer

data class ActiveTimersUiState(
    val timers: List<CookingTimer> = emptyList(),
    /** The clock the timers are read against, moved on every second while one runs. */
    val now: Long = 0L,
) {
    val isEmpty: Boolean get() = timers.isEmpty()

    /** The ringing ones first, then the one ending soonest: the order they need attention in. */
    val ordered: List<CookingTimer>
        get() = timers.sortedWith(compareBy({ !(it.isRunning && it.isFinished(now)) }, { it.remainingMillis(now) }))
}

/** The timers still running once the cooking mode was left, for the pills over every screen. */
class ActiveTimersViewModel(private val timers: CookingTimerController) : ViewModel() {

    val state: StateFlow<ActiveTimersUiState> =
        combine(timers.timers, timers.ticks()) { all, now -> ActiveTimersUiState(all.timers, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ActiveTimersUiState())

    /** Silences a timer that rang, right from its pill. */
    fun dismiss(id: Int) = timers.dismiss(id)

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ActiveTimersViewModel(container.cookingTimers) }
        }
    }
}
