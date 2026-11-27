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

    init {
        scope.launch { for (event in queue) eventFlow.emit(event) }
        scope.launch {
            midiInput.events.collect { if (modeFlow.value == PracticeInputMode.MIDI) queue.trySend(it) }
        }
        scope.launch {
            microphoneInput.events.collect { if (modeFlow.value == PracticeInputMode.MICROPHONE) queue.trySend(it) }
        }
    }

    fun setMode(mode: PracticeInputMode) {
        modeFlow.value = mode
    }

    override fun close() {
        queue.close()
    }
}
