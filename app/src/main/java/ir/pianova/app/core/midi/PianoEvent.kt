package ir.pianova.app.core.midi

/**
 * Source-independent musical input events.
 *
 * Timestamps use the monotonic millisecond clock and are suitable for ordering
 * events within the current process (not for wall-clock display).
 */
sealed interface PianoEvent {
    val timestampMs: Long

    data class NoteOn(
        val midiNote: Int,
        val velocity: Float,
        override val timestampMs: Long
    ) : PianoEvent

    data class NoteOff(
        val midiNote: Int,
        override val timestampMs: Long
    ) : PianoEvent

    data class Sustain(
        val enabled: Boolean,
        override val timestampMs: Long
    ) : PianoEvent
}
