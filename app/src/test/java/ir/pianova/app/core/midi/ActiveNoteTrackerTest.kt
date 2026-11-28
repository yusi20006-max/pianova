package ir.pianova.app.core.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveNoteTrackerTest {
    @Test fun noteOnAddsNoteOffRemoves() {
        val t = ActiveNoteTracker()
        assertTrue(t.accept(PianoEvent.NoteOn(60, 0.8f, 1)).contains(60))
        assertTrue(t.accept(PianoEvent.NoteOn(64, 0.8f, 2)).contains(64))
        assertEquals(setOf(60, 64), t.activeNotes)
        assertEquals(setOf(64), t.accept(PianoEvent.NoteOff(60, 3)))
    }

    @Test fun repeatedNoteOnKeepsActive() {
        val t = ActiveNoteTracker()
        t.accept(PianoEvent.NoteOn(60, 0.5f, 1))
        t.accept(PianoEvent.NoteOn(60, 0.9f, 2))
        assertEquals(setOf(60), t.activeNotes)
        assertEquals(emptySet<Int>(), t.accept(PianoEvent.NoteOff(60, 3)))
    }

    @Test fun sustainTrackedSeparatelyFromKeys() {
        val t = ActiveNoteTracker()
        t.accept(PianoEvent.NoteOn(60, 1f, 1))
        t.accept(PianoEvent.Sustain(true, 2))
        assertTrue(t.sustainActive)
        assertEquals(setOf(60), t.activeNotes)
        t.accept(PianoEvent.Sustain(false, 3))
        assertFalse(t.sustainActive)
    }

    @Test fun releaseAllClearsEverything_disconnect() {
        val t = ActiveNoteTracker()
        t.accept(PianoEvent.NoteOn(60, 1f, 1)); t.accept(PianoEvent.NoteOn(72, 1f, 1)); t.accept(PianoEvent.Sustain(true, 1))
        assertEquals(emptySet<Int>(), t.releaseAll())
        assertFalse(t.sustainActive)
    }

    @Test fun strayNoteOffIsSafe() {
        val t = ActiveNoteTracker()
        assertEquals(emptySet<Int>(), t.accept(PianoEvent.NoteOff(99, 1)))
    }
}
