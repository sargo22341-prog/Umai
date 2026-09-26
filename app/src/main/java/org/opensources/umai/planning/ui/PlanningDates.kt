package org.opensources.umai.planning.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.opensources.umai.R
import org.opensources.umai.core.format.localizedName
import java.time.LocalDate

/** "Yesterday", "Today", "Tomorrow", then the localized day name. */
@Composable
fun LocalDate.label(today: LocalDate = LocalDate.now()): String = when (this) {
    today.minusDays(1) -> stringResource(R.string.planning_yesterday)
    today -> stringResource(R.string.planning_today)
    today.plusDays(1) -> stringResource(R.string.planning_tomorrow)
    else -> dayOfWeek.localizedName()
}
