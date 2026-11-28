package ir.pianova.app.core.midi

/**
 * Tracks which MIDI notes are currently held, independent of source.
 *
 * The same [PianoEvent] stream that drives audio and practice evaluation can
 * drive visual feedback. NoteOn adds a note; NoteOff removes it; repeated
 * NoteOn keeps it active. Sustain is tracked separately and never holds a note
 * key in [activeNotes] (the audio engine applies sustain; key highlighting
 * reflects physical key state). [releaseAll] clears everything, e.g. on
 * disconnect, lifecycle stop, or input-mode switch.
 */
class ActiveNoteTracker {
    private val held = mutableSetOf<Int>()
    private var sustain = false

    /** Notes currently physically held (not merely sustained). */
    val activeNotes: Set<Int> get() = held.toSet()

    val sustainActive: Boolean get() = sustain

    /** Applies [event]; returns the new snapshot of active notes. */
    fun accept(event: PianoEvent): Set<Int> {
        when (event) {
            is PianoEvent.NoteOn -> held += event.midiNote
            is PianoEvent.NoteOff -> held -= event.midiNote
            is PianoEvent.Sustain -> sustain = event.enabled
        }
        return activeNotes
    }

    fun releaseAll(): Set<Int> {
        held.clear()
        sustain = false
        return activeNotes
    }
}
