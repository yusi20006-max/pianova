package ir.pianova.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Pianova brand palette (SPEC §6 — Visual Direction). */
val DarkNavy = Color(0xFF0F172A)      // #0F172A background
val DeepPurple = Color(0xFF6D28D9)    // #6D28D9 primary accents
val ElectricBlue = Color(0xFF38BDF8)  // #38BDF8 highlights
val Gold = Color(0xFFF59E0B)          // #F59E0B badges / rewards

private val Slate50 = Color(0xFFF8FAFC)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate500 = Color(0xFF64748B)
private val Slate700 = Color(0xFF334155)
private val Slate800 = Color(0xFF1E293B)
private val Slate900 = Color(0xFF0F172A)
private val ErrorRed = Color(0xFFF87171)

/**
 * Dark-first Material 3 color scheme. Pianova is a dark-themed app by design,
 * so the dark scheme is the only scheme required for Phase 0 / v1.0.
 */
private val PianovaColorScheme = darkColorScheme(
    primary = DeepPurple,
    onPrimary = Color.White,
    primaryContainer = DeepPurple,
    onPrimaryContainer = Color.White,
    secondary = ElectricBlue,
    onSecondary = Slate900,
    secondaryContainer = Slate800,
    onSecondaryContainer = ElectricBlue,
    tertiary = Gold,
    onTertiary = Slate900,
    tertiaryContainer = Slate800,
    onTertiaryContainer = Gold,
    background = DarkNavy,
    onBackground = Slate50,
    surface = DarkNavy,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate200,
    surfaceContainerLow = Slate800,
    surfaceContainerHigh = Slate700,
    outline = Slate700,
    outlineVariant = Slate500,
    error = ErrorRed,
    onError = Slate900
)

/**
 * App theme: Material 3, dark color scheme (Dark Navy / Deep Purple /
 * Electric Blue / Gold) with Dastkhat typography for all Persian and Latin text.
 */
@Composable
fun PianovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PianovaColorScheme,
        typography = PianovaTypography,
        content = content
    )
}
