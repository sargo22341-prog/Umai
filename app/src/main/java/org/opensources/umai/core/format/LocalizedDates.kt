package org.opensources.umai.core.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale as JavaLocale

/**
 * Locale-aware date helpers for composables.
 *
 * `Locale.getDefault()` is read once per process, so a composable that calls it
 * keeps showing the old language after the user switches it in the settings.
 * Compose's [Locale.current] is observable, so these helpers recompose when the
 * language changes.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): JavaLocale = Locale.current.platformLocale

/** Localized full day name, capitalized ("Lundi", "Monday"). */
@Composable
@ReadOnlyComposable
fun DayOfWeek.localizedName(): String {
    val locale = currentLocale()
    return getDisplayName(TextStyle.FULL, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/** Date formatter that follows the language chosen in the app. */
@Composable
fun rememberDateFormatter(style: FormatStyle): DateTimeFormatter {
    val locale = currentLocale()
    return remember(locale, style) {
        DateTimeFormatter.ofLocalizedDate(style).withLocale(locale)
    }
}
