package org.opensources.umai.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.opensources.umai.core.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = ClaudePalette.TerracottaDeep,
    onPrimary = Color.White,
    primaryContainer = ClaudePalette.TerracottaVeil,
    onPrimaryContainer = ClaudePalette.TerracottaInk,
    inversePrimary = ClaudePalette.TerracottaSoft,

    secondary = ClaudePalette.Kraft,
    onSecondary = Color.White,
    secondaryContainer = ClaudePalette.KraftVeil,
    onSecondaryContainer = ClaudePalette.KraftShade,

    tertiary = ClaudePalette.TerracottaShade,
    onTertiary = Color.White,
    tertiaryContainer = ClaudePalette.Parchment,
    onTertiaryContainer = ClaudePalette.InkStrong,

    background = ClaudePalette.Ivory,
    onBackground = ClaudePalette.InkStrong,
    surface = ClaudePalette.Ivory,
    onSurface = ClaudePalette.InkStrong,
    surfaceVariant = ClaudePalette.Parchment,
    onSurfaceVariant = ClaudePalette.InkMuted,
    surfaceTint = ClaudePalette.TerracottaDeep,

    surfaceContainerLowest = ClaudePalette.IvoryRaised,
    surfaceContainerLow = Color(0xFFF7F5EF),
    surfaceContainer = ClaudePalette.IvorySunken,
    surfaceContainerHigh = ClaudePalette.IvoryEdge,
    surfaceContainerHighest = Color(0xFFE4E0D4),

    inverseSurface = ClaudePalette.Charcoal,
    inverseOnSurface = ClaudePalette.PaperStrong,

    outline = ClaudePalette.Outline,
    outlineVariant = ClaudePalette.OutlineVariantLight,

    error = ClaudePalette.ErrorLight,
    onError = Color.White,
    errorContainer = ClaudePalette.ErrorVeilLight,
    onErrorContainer = ClaudePalette.ErrorInkLight,

    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = ClaudePalette.TerracottaSoft,
    onPrimary = Color(0xFF3A1406),
    primaryContainer = ClaudePalette.TerracottaShade,
    onPrimaryContainer = Color(0xFFFFDBCD),
    inversePrimary = ClaudePalette.TerracottaDeep,

    secondary = ClaudePalette.KraftSoft,
    onSecondary = Color(0xFF3A2C16),
    secondaryContainer = ClaudePalette.KraftShade,
    onSecondaryContainer = ClaudePalette.KraftVeil,

    tertiary = ClaudePalette.Terracotta,
    onTertiary = Color(0xFF3A1406),
    tertiaryContainer = Color(0xFF52301F),
    onTertiaryContainer = ClaudePalette.PaperStrong,

    background = ClaudePalette.Charcoal,
    onBackground = ClaudePalette.PaperStrong,
    surface = ClaudePalette.Charcoal,
    onSurface = ClaudePalette.PaperStrong,
    surfaceVariant = ClaudePalette.CharcoalHigh,
    onSurfaceVariant = ClaudePalette.PaperMuted,
    surfaceTint = ClaudePalette.TerracottaSoft,

    surfaceContainerLowest = Color(0xFF171615),
    surfaceContainerLow = Color(0xFF211F1E),
    surfaceContainer = ClaudePalette.CharcoalRaised,
    surfaceContainerHigh = ClaudePalette.CharcoalHigh,
    surfaceContainerHighest = ClaudePalette.CharcoalPeak,

    inverseSurface = ClaudePalette.Ivory,
    inverseOnSurface = ClaudePalette.InkStrong,

    outline = ClaudePalette.OutlineDark,
    outlineVariant = ClaudePalette.OutlineVariantDark,

    error = ClaudePalette.ErrorDark,
    onError = Color(0xFF601410),
    errorContainer = ClaudePalette.ErrorVeilDark,
    onErrorContainer = Color(0xFFF9DEDC),

    scrim = Color(0xFF000000),
)

/**
 * Dynamic colour is part of AOSP, not of Google Play Services, so offering it
 * as an opt-in keeps the app usable on GrapheneOS. It stays off by default
 * because the Claude palette is the app's identity.
 */
@Composable
fun UmaiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = UmaiTypography,
        shapes = UmaiShapes,
        content = content,
    )
}
