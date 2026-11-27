package ir.pianova.app.core.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiConnectionEventTrackerTest {
    @Test
    fun sharedPitchAcrossChannelsIsReleasedOnlyAfterLastChannel() {
        val tracker = MidiConnectionEventTracker()
        assertEquals(listOf(PianoEvent.NoteOn(60, 0.8f, 1)), tracker.accept(0, PianoEvent.NoteOn(60, 0.8f, 1)))
        assertEquals(emptyList<PianoEvent>(), tracker.accept(1, PianoEvent.NoteOn(60, 0.7f, 2)))
        assertEquals(emptyList<PianoEvent>(), tracker.accept(0, PianoEvent.NoteOff(60, 3)))
        assertEquals(listOf(PianoEvent.NoteOff(60, 4)), tracker.accept(1, PianoEvent.NoteOff(60, 4)))
    }

    @Test
    fun disconnectReleasesHeldNotesAndSustain() {
        val tracker = MidiConnectionEventTracker()
        tracker.accept(0, PianoEvent.NoteOn(64, 1f, 10))
        tracker.accept(2, PianoEvent.NoteOn(67, 0.5f, 11))
        tracker.accept(2, PianoEvent.Sustain(true, 12))
        assertEquals(
            listOf(PianoEvent.NoteOff(64, 99), PianoEvent.NoteOff(67, 99), PianoEvent.Sustain(false, 99)),
            tracker.releaseAll(99)
        )
        assertEquals(emptyList<PianoEvent>(), tracker.releaseAll(100))
    }

    @Test
    fun sustainIsAggregatedAcrossChannels() {
        val tracker = MidiConnectionEventTracker()
        assertEquals(listOf(PianoEvent.Sustain(true, 1)), tracker.accept(0, PianoEvent.Sustain(true, 1)))
        assertEquals(emptyList<PianoEvent>(), tracker.accept(1, PianoEvent.Sustain(true, 2)))
        assertEquals(emptyList<PianoEvent>(), tracker.accept(0, PianoEvent.Sustain(false, 3)))
        assertEquals(listOf(PianoEvent.Sustain(false, 4)), tracker.accept(1, PianoEvent.Sustain(false, 4)))
    }
}
