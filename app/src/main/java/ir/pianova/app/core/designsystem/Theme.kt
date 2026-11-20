package ir.pianova.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
 * so the dark scheme is the only scheme required for Phase 0.
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

private val PianovaTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
)

/**
 * App theme: Material 3, dark color scheme (Dark Navy / Deep Purple /
 * Electric Blue / Gold). The Vazirmatn font family is introduced together
 * with the full UI in Phase 1.
 */
@Composable
fun PianovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PianovaColorScheme,
        typography = PianovaTypography,
        content = content
    )
}
