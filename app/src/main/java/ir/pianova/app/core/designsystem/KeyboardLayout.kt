package ir.pianova.app.core.designsystem

import android.content.Context
import android.content.SharedPreferences

/**
 * Adjustable practice-keyboard layout, persisted across sessions.
 *
 * Kept deliberately simple: height, vertical/horizontal offset, visible note
 * count (octave/range), and reset-to-default. Visible-range start (first MIDI
 * note) is persisted separately per learner in `LearnerSessionStore`.
 */
data class KeyboardLayout(
    val keyboardHeightDp: Int = DEFAULT_KEYBOARD_HEIGHT_DP,
    val offsetYDp: Int = DEFAULT_OFFSET_Y_DP,
    val offsetXDp: Int = DEFAULT_OFFSET_X_DP,
    val visibleNotes: Int = DEFAULT_VISIBLE_NOTES
) {
    init {
        // Normalize defensively so corrupt persisted values can't break layout.
    }

    fun normalized(): KeyboardLayout = KeyboardLayout(
        keyboardHeightDp = keyboardHeightDp.coerceIn(MIN_KEYBOARD_HEIGHT_DP, MAX_KEYBOARD_HEIGHT_DP),
        offsetYDp = offsetYDp.coerceIn(MIN_OFFSET_DP, MAX_OFFSET_DP),
        offsetXDp = offsetXDp.coerceIn(MIN_OFFSET_DP, MAX_OFFSET_DP),
        visibleNotes = visibleNotes.coerceIn(MIN_VISIBLE_NOTES, MAX_VISIBLE_NOTES)
    )

    companion object {
        const val DEFAULT_KEYBOARD_HEIGHT_DP = 200
        const val MIN_KEYBOARD_HEIGHT_DP = 120
        const val MAX_KEYBOARD_HEIGHT_DP = 420
        const val DEFAULT_OFFSET_Y_DP = 0
        const val DEFAULT_OFFSET_X_DP = 0
        const val MIN_OFFSET_DP = -120
        const val MAX_OFFSET_DP = 120
        const val DEFAULT_VISIBLE_NOTES = 24
        const val MIN_VISIBLE_NOTES = 12
        const val MAX_VISIBLE_NOTES = 48

        val DEFAULT = KeyboardLayout()

        // Octave step helpers for range adjustment.
        const val OCTAVE = 12
    }
}

/** Persistence for [KeyboardLayout] in `pianova_settings`. */
object KeyboardLayoutStore {
    private const val PREFS = "pianova_settings"
    private const val KEY_HEIGHT = "keyboard_height_dp"
    private const val KEY_OFFSET_Y = "keyboard_offset_y_dp"
    private const val KEY_OFFSET_X = "keyboard_offset_x_dp"
    private const val KEY_VISIBLE_NOTES = "keyboard_visible_notes"

    fun load(context: Context): KeyboardLayout {
        val p: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return KeyboardLayout(
            keyboardHeightDp = p.getInt(KEY_HEIGHT, KeyboardLayout.DEFAULT_KEYBOARD_HEIGHT_DP),
            offsetYDp = p.getInt(KEY_OFFSET_Y, KeyboardLayout.DEFAULT_OFFSET_Y_DP),
            offsetXDp = p.getInt(KEY_OFFSET_X, KeyboardLayout.DEFAULT_OFFSET_X_DP),
            visibleNotes = p.getInt(KEY_VISIBLE_NOTES, KeyboardLayout.DEFAULT_VISIBLE_NOTES)
        ).normalized()
    }

    fun save(context: Context, layout: KeyboardLayout) {
        val n = layout.normalized()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HEIGHT, n.keyboardHeightDp)
            .putInt(KEY_OFFSET_Y, n.offsetYDp)
            .putInt(KEY_OFFSET_X, n.offsetXDp)
            .putInt(KEY_VISIBLE_NOTES, n.visibleNotes)
            .apply()
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_HEIGHT)
            .remove(KEY_OFFSET_Y)
            .remove(KEY_OFFSET_X)
            .remove(KEY_VISIBLE_NOTES)
            .apply()
    }
}

/** Reference-playback auto-play preference, persisted in `pianova_settings`. */
object ReferencePlaybackPreferences {
    private const val PREFS = "pianova_settings"
    private const val KEY_AUTO_PLAY = "reference_auto_play"
    const val DEFAULT_AUTO_PLAY = false

    fun isAutoPlayEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_PLAY, DEFAULT_AUTO_PLAY)

    fun setAutoPlayEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_PLAY, enabled).apply()
    }
}
