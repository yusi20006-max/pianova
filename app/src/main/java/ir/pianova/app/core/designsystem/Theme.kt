package ir.pianova.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** User-approved Learning Journey palette. */
val PianovaIvory = Color(0xFFF5F1E8)
val PianovaWhite = Color(0xFFFFFFFF)
val PianovaSage = Color(0xFFDCE9D9)
val PianovaGreen = Color(0xFF557A62)
val PianovaInk = Color(0xFF233D32)
val PianovaMuted = Color(0xFF52605A)
val PianovaBorder = Color(0xFFD9D4C8)
val PianovaFuture = Color(0xFFE8E2D6)
val PianovaGold = Color(0xFFB88735)

private val PianovaColorScheme = lightColorScheme(
    primary = PianovaGreen,
    onPrimary = Color.White,
    primaryContainer = PianovaSage,
    onPrimaryContainer = PianovaInk,
    secondary = PianovaMuted,
    onSecondary = Color.White,
    secondaryContainer = PianovaFuture,
    onSecondaryContainer = PianovaInk,
    tertiary = PianovaGold,
    onTertiary = Color.White,
    background = PianovaIvory,
    onBackground = PianovaInk,
    surface = PianovaWhite,
    onSurface = PianovaInk,
    surfaceVariant = PianovaFuture,
    onSurfaceVariant = PianovaMuted,
    outline = PianovaBorder,
    outlineVariant = PianovaBorder,
    error = Color(0xFFB3261E),
    onError = Color.White
)

/** Material 3 light theme with the bundled Dastkhat Persian typeface. */
@Composable
fun PianovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PianovaColorScheme,
        typography = PianovaTypography,
        content = content
    )
}
