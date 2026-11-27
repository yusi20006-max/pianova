package ir.pianova.app.core.midi

/**
 * Tracks notes and sustain for one physical MIDI connection.
 * Shared-pitch notes across channels are reference-counted so a source cannot
 * release another channel's held note. Disconnect always emits safe releases.
 */
class MidiConnectionEventTracker {
    private val heldByChannel = mutableMapOf<Int, MutableSet<Int>>()
    private val noteCounts = mutableMapOf<Int, Int>()
    private val sustainChannels = mutableSetOf<Int>()

    fun accept(channel: Int, event: PianoEvent): List<PianoEvent> {\n        return when (event) {
        is PianoEvent.NoteOn -> {
            val notes = heldByChannel.getOrPut(channel) { mutableSetOf() }
            if (notes.add(event.midiNote)) {
                val count = noteCounts[event.midiNote] ?: 0
                noteCounts[event.midiNote] = count + 1
                if (count == 0) listOf(event) else emptyList()
            } else listOf(event)
        }
        is PianoEvent.NoteOff -> {
            val notes = heldByChannel[channel] ?: return emptyList()
            if (!notes.remove(event.midiNote)) return emptyList()
            if (notes.isEmpty()) heldByChannel.remove(channel)
            val count = (noteCounts[event.midiNote] ?: 1) - 1
            if (count <= 0) {
                noteCounts.remove(event.midiNote)
                listOf(event)
            } else {
                noteCounts[event.midiNote] = count
                emptyList()
            }
        }
        is PianoEvent.Sustain -> {
            val before = sustainChannels.isNotEmpty()
            if (event.enabled) sustainChannels += channel else sustainChannels -= channel
            val after = sustainChannels.isNotEmpty()
            if (before != after) listOf(PianoEvent.Sustain(after, event.timestampMs)) else emptyList()
        }
    }

    fun releaseAll(timestampMs: Long): List<PianoEvent> {
        val releases = mutableListOf<PianoEvent>()
        heldByChannel.values.flatten().forEach { note ->
            val count = (noteCounts[note] ?: 1) - 1
            if (count <= 0) {
                noteCounts.remove(note)
                releases += PianoEvent.NoteOff(note, timestampMs)
            } else noteCounts[note] = count
        }
        heldByChannel.clear()
        if (sustainChannels.isNotEmpty()) releases += PianoEvent.Sustain(false, timestampMs)
        sustainChannels.clear()
        return releases
    }
}
