package ir.pianova.app.core.midi

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Adapter used by the Compose keyboard. Calls are non-suspending so pointer
 * handling stays synchronous; events are delivered through a bounded buffer.
 */
class VirtualKeyboardInput(
    extraBufferCapacity: Int = DEFAULT_BUFFER_CAPACITY,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }
) : PianoInput {
    private val mutableEvents = MutableSharedFlow<PianoEvent>(
        replay = 0,
        extraBufferCapacity = extraBufferCapacity
    )

    override val events = mutableEvents.asSharedFlow()

    init {
        require(extraBufferCapacity > 0) { "extraBufferCapacity must be positive" }
    }

    fun noteOn(midiNote: Int, velocity: Float): Boolean {
        if (midiNote !in MIDI_NOTE_RANGE || !velocity.isFinite() || velocity !in 0f..1f) {
            return false
        }
        return mutableEvents.tryEmit(
            PianoEvent.NoteOn(midiNote, velocity, clock())
        )
    }

    fun noteOff(midiNote: Int): Boolean {
        if (midiNote !in MIDI_NOTE_RANGE) return false
        return mutableEvents.tryEmit(PianoEvent.NoteOff(midiNote, clock()))
    }

    fun sustain(enabled: Boolean): Boolean =
        mutableEvents.tryEmit(PianoEvent.Sustain(enabled, clock()))

    private companion object {
        const val DEFAULT_BUFFER_CAPACITY = 64
        const val NANOS_PER_MILLI = 1_000_000L
        val MIDI_NOTE_RANGE = 0..127
    }
}
