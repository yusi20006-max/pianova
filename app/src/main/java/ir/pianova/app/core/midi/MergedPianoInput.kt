package ir.pianova.app.core.midi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Combines multiple source adapters into one normalized input stream. */
class MergedPianoInput(
    inputs: List<PianoInput>,
    scope: CoroutineScope,
    extraBufferCapacity: Int = 128
) : PianoInput {
    private val mutableEvents = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = extraBufferCapacity)
    override val events = mutableEvents.asSharedFlow()

    init {
        require(extraBufferCapacity > 0)
        inputs.forEach { input ->
            scope.launch { input.events.collect { mutableEvents.emit(it) } }
        }
    }
}
