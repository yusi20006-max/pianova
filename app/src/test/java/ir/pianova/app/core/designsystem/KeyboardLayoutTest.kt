package ir.pianova.app.core.designsystem

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardLayoutTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun defaultsAreUsableAndLarge() {
        val d = KeyboardLayout.DEFAULT.normalized()
        assertEquals(KeyboardLayout.DEFAULT_KEYBOARD_HEIGHT_DP, d.keyboardHeightDp)
        assertTrue(d.keyboardHeightDp >= KeyboardLayout.MIN_KEYBOARD_HEIGHT_DP)
        assertEquals(KeyboardLayout.DEFAULT_VISIBLE_NOTES, d.visibleNotes)
    }

    @Test fun normalizedClampsCorruptValues() {
        val n = KeyboardLayout(keyboardHeightDp = -5, offsetYDp = 9999, offsetXDp = -9999, visibleNotes = 1000).normalized()
        assertEquals(KeyboardLayout.MIN_KEYBOARD_HEIGHT_DP, n.keyboardHeightDp)
        assertEquals(KeyboardLayout.MAX_OFFSET_DP, n.offsetYDp)
        assertEquals(KeyboardLayout.MIN_OFFSET_DP, n.offsetXDp)
        assertEquals(KeyboardLayout.MAX_VISIBLE_NOTES, n.visibleNotes)
    }

    @Test fun saveLoadResetPersistence() {
        val ctx = context
        KeyboardLayoutStore.reset(ctx)
        assertEquals(KeyboardLayout.DEFAULT.normalized(), KeyboardLayoutStore.load(ctx))

        val custom = KeyboardLayout(keyboardHeightDp = 260, offsetYDp = 30, offsetXDp = -10, visibleNotes = 36).normalized()
        KeyboardLayoutStore.save(ctx, custom)
        assertEquals(custom, KeyboardLayoutStore.load(ctx))

        KeyboardLayoutStore.reset(ctx)
        assertEquals(KeyboardLayout.DEFAULT.normalized(), KeyboardLayoutStore.load(ctx))
    }

    @Test fun autoPlayPreferencePersisted() {
        val ctx = context
        ReferencePlaybackPreferences.setAutoPlayEnabled(ctx, true)
        assertTrue(ReferencePlaybackPreferences.isAutoPlayEnabled(ctx))
        ReferencePlaybackPreferences.setAutoPlayEnabled(ctx, false)
        assertFalse(ReferencePlaybackPreferences.isAutoPlayEnabled(ctx))
    }
}
