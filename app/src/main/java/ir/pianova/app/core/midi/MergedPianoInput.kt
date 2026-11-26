package ir.pianova.app.core.midi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Combines input sources while reference-counting held notes and sustain.
 * A note remains active until every source that pressed it releases it.
 * The unbounded internal queue prevents backpressure from dropping NoteOff events.
 */
class MergedPianoInput(
    inputs: List<PianoInput>,
    scope: CoroutineScope,
    extraBufferCapacity: Int = 128
) : PianoInput {
    private val mutableEvents = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = extraBufferCapacity)
    override val events = mutableEvents.asSharedFlow()
    private val eventQueue = Channel<PianoEvent>(Channel.UNLIMITED)
    private val lock = Any()
    private val heldBySource = inputs.map { mutableSetOf<Int>() }
    private val noteCounts = mutableMapOf<Int, Int>()
    private val sustainSources = mutableSetOf<Int>()

    init {
        require(extraBufferCapacity > 0)
        scope.launch {
            for (event in eventQueue) mutableEvents.emit(event)
        }
        inputs.forEachIndexed { sourceIndex, input ->
            scope.launch {
                input.events.collect { event -> handle(sourceIndex, event) }
            }
        }
    }

    private fun enqueue(event: PianoEvent) {
        eventQueue.trySend(event)
    }

    private fun handle(source: Int, event: PianoEvent) {
        synchronized(lock) {
            when (event) {
                is PianoEvent.NoteOn -> {
                    val sourceNotes = heldBySource[source]
                    if (sourceNotes.add(event.midiNote)) {
                        val count = noteCounts[event.midiNote] ?: 0
                        noteCounts[event.midiNote] = count + 1
                        if (count == 0) enqueue(event)
                    } else {
                        // Preserve repeated NoteOn velocity updates from the active source.
                        enqueue(event)
                    }
                }
                is PianoEvent.NoteOff -> {
                    if (heldBySource[source].remove(event.midiNote)) {
                        val count = (noteCounts[event.midiNote] ?: 1) - 1
                        if (count <= 0) {
                            noteCounts.remove(event.midiNote)
                            enqueue(event)
                        } else noteCounts[event.midiNote] = count
                    }
                }
                is PianoEvent.Sustain -> {
                    if (event.enabled) sustainSources += source else sustainSources -= source
                    enqueue(PianoEvent.Sustain(sustainSources.isNotEmpty(), event.timestampMs))
                }
            }
        }
    }
}
