package ir.pianova.app.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG contrast checks for shared theme tokens (readability regression guard). */
class ThemeContrastTest {

    private fun srgb(c: Color, comp: (Color) -> Float): Double {
        val v = comp(c).toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * srgb(c) { it.red } + 0.7152 * srgb(c) { it.green } + 0.0722 * srgb(c) { it.blue }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertReadable(fg: Color, bg: Color, minRatio: Double = 4.5, label: String) {
        val ratio = contrast(fg, bg)
        assertTrue("$label contrast ${"%.2f".format(ratio)} < $minRatio", ratio >= minRatio)
    }

    @Test fun primaryTextReadableOnLightSurfaces() {
        assertReadable(PianovaInk, PianovaWhite, label = "ink/white")
        assertReadable(PianovaInk, PianovaSage, label = "ink/sage")
        assertReadable(PianovaInk, PianovaFuture, label = "ink/future")
        assertReadable(PianovaInk, PianovaIvory, label = "ink/ivory")
    }

    @Test fun secondaryTextReadableOnLightSurfaces() {
        assertReadable(PianovaMuted, PianovaWhite, label = "muted/white")
        assertReadable(PianovaMuted, PianovaSage, label = "muted/sage")
        assertReadable(PianovaMuted, PianovaFuture, label = "muted/future")
        assertReadable(PianovaMuted, PianovaIvory, label = "muted/ivory")
    }

    @Test fun accentTextReadable() {
        assertReadable(PianovaGreen, PianovaWhite, label = "green/white")
    }

    @Test fun aboutScreenUsesDarkTextNotBorderTokens() {
        // Regression: About previously used `outline`/`outlineVariant` (light border) for text.
        // Theme keeps border tokens separate; verify border tokens are NOT used as readable text colors here.
        val surface = PianovaWhite
        assertReadable(PianovaInk, surface, label = "about title/ink")
        assertReadable(PianovaMuted, surface, label = "about secondary/muted")
        assertReadable(PianovaGreen, surface, label = "about link/green")
        // border token would fail as text — documents why it must not be used for text
        assertTrue(contrast(PianovaBorder, surface) < 4.5)
    }
}
