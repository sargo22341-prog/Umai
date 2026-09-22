package org.opensources.umai.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Umai borrows the Claude Code palette: warm terracotta on paper-ivory in light
 * mode, on warm charcoal in dark mode.
 *
 * The accent is darkened to `#B45535` for light surfaces so white-on-accent text
 * reaches 4.89:1 and accent-on-background reaches 4.64:1 (WCAG AA); the brand
 * tone `#D97757` stays as the launcher icon colour and as the dark-mode base.
 */
internal object ClaudePalette {

    // Accent family
    val Terracotta = Color(0xFFD97757)
    val TerracottaDeep = Color(0xFFB45535)
    val TerracottaSoft = Color(0xFFE58A6B)
    val TerracottaInk = Color(0xFF461A08)
    val TerracottaVeil = Color(0xFFF7E5DC)
    val TerracottaShade = Color(0xFF7A3A22)

    // Paper (light)
    val Ivory = Color(0xFFFAF9F5)
    val IvoryRaised = Color(0xFFFFFFFF)
    val IvorySunken = Color(0xFFF2F0E8)
    val IvoryEdge = Color(0xFFEAE7DC)
    val Parchment = Color(0xFFF0EEE6)

    // Charcoal (dark)
    val Charcoal = Color(0xFF1F1E1D)
    val CharcoalRaised = Color(0xFF262624)
    val CharcoalHigh = Color(0xFF30302E)
    val CharcoalPeak = Color(0xFF3A3A37)
    val CharcoalEdge = Color(0xFF454440)

    // Ink
    val InkStrong = Color(0xFF1B1A19)
    val InkMuted = Color(0xFF66605A)
    val PaperStrong = Color(0xFFEDEAE2)
    val PaperMuted = Color(0xFFAAA59C)

    // Supporting hues drawn from the same warm family
    val Kraft = Color(0xFF7A6446)
    val KraftVeil = Color(0xFFEFE4D2)
    val KraftSoft = Color(0xFFD9BE94)
    val KraftShade = Color(0xFF52412A)

    // Status
    val ErrorLight = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFF2B8B5)
    val ErrorVeilLight = Color(0xFFF9DEDC)
    val ErrorVeilDark = Color(0xFF8C1D18)
    val ErrorInkLight = Color(0xFF410E0B)

    val Outline = Color(0xFF837D73)
    val OutlineVariantLight = Color(0xFFDAD5C8)
    val OutlineDark = Color(0xFF7A756C)
    val OutlineVariantDark = Color(0xFF43423E)
}
