package ir.pianova.app.core.midi

import ir.pianova.app.core.audio.AcousticPianoInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class PracticeInputMode { MIDI, MICROPHONE }

/** Routes practice evaluation to one selected input mode to avoid duplicate notes. */
class PracticeInputRouter(
    private val midiInput: PianoInput,
    private val microphoneInput: AcousticPianoInput,
    scope: CoroutineScope
) : PianoInput, AutoCloseable {
    private val eventFlow = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = 128)
    override val events = eventFlow.asSharedFlow()
    private val queue = Channel<PianoEvent>(Channel.UNLIMITED)
    private val modeFlow = MutableStateFlow(PracticeInputMode.MIDI)
    val mode: StateFlow<PracticeInputMode> = modeFlow
    private val lock = Any()
    private val midiHeld = mutableSetOf<Int>()
    private val microphoneHeld = mutableSetOf<Int>()
    private var midiSustain = false
    private var microphoneSustain = false
    private var enabled = true
    val isEnabled: Boolean get() = synchronized(lock) { enabled }

    init {
        scope.launch { for (event in queue) eventFlow.emit(event) }
        scope.launch {
            midiInput.events.collect { event ->
                synchronized(lock) {
                    if (!enabled) return@synchronized
                    if (modeFlow.value != PracticeInputMode.MIDI) return@synchronized
                    track(midiHeld, event)
                    if (event is PianoEvent.Sustain) midiSustain = event.enabled
                    queue.trySend(event)
                }
            }
        }
        scope.launch {
            microphoneInput.events.collect { event ->
                synchronized(lock) {
                    if (!enabled) return@synchronized
                    if (modeFlow.value != PracticeInputMode.MICROPHONE) return@synchronized
                    track(microphoneHeld, event)
                    if (event is PianoEvent.Sustain) microphoneSustain = event.enabled
                    queue.trySend(event)
                }
            }
        }
    }

    /**
     * Enables/disables forwarding of practice events without tearing down
     * subscriptions. Disabling releases any held notes/sustain so nothing is
     * left stuck, and is used to suspend practice evaluation during reference
     * playback.
     */
    fun setEnabled(value: Boolean) {
        synchronized(lock) {
            if (enabled == value) return
            enabled = value
            if (!value) releaseHeldLocked()
        }
    }

    private fun releaseHeldLocked() {
        val now = System.nanoTime() / 1_000_000L
        midiHeld.toList().forEach { queue.trySend(PianoEvent.NoteOff(it, now)) }
        microphoneHeld.toList().forEach { queue.trySend(PianoEvent.NoteOff(it, now)) }
        midiHeld.clear()
        microphoneHeld.clear()
        if (midiSustain || microphoneSustain) queue.trySend(PianoEvent.Sustain(false, now))
        midiSustain = false
        microphoneSustain = false
    }

    fun setMode(mode: PracticeInputMode) {
        synchronized(lock) {
            if (modeFlow.value == mode) return
            val now = System.nanoTime() / 1_000_000L
            val activeNotes = if (modeFlow.value == PracticeInputMode.MIDI) midiHeld else microphoneHeld
            activeNotes.toList().forEach { queue.trySend(PianoEvent.NoteOff(it, now)) }
            activeNotes.clear()
            val sustain = if (modeFlow.value == PracticeInputMode.MIDI) midiSustain else microphoneSustain
            if (sustain) queue.trySend(PianoEvent.Sustain(false, now))
            if (modeFlow.value == PracticeInputMode.MIDI) midiSustain = false else microphoneSustain = false
            modeFlow.value = mode
        }
    }

    private fun track(notes: MutableSet<Int>, event: PianoEvent) {
        when (event) {
            is PianoEvent.NoteOn -> notes += event.midiNote
            is PianoEvent.NoteOff -> notes -= event.midiNote
            is PianoEvent.Sustain -> Unit
        }
    }

    override fun close() {
        synchronized(lock) {
            releaseHeldLocked()
            enabled = false
        }
        queue.close()
    }
}
