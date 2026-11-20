package ir.pianova.app.core.midi

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.startCoroutine
import kotlinx.coroutines.CoroutineStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VirtualKeyboardInputTest {
    @Test
    fun emitsNoteOnWithMidiVelocityAndTimestamp() = runBlocking {
        val input = VirtualKeyboardInput(clock = { 123L })
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            input.events.first()
        }

        assertEquals(true, input.noteOn(60, 0.75f))
        assertEquals(PianoEvent.NoteOn(60, 0.75f, 123L), received.await())
    }

    @Test
    fun emitsNoteOffWithTimestamp() = runBlocking {
        val input = VirtualKeyboardInput(clock = { 456L })
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            input.events.first()
        }

        assertEquals(true, input.noteOff(60))
        assertEquals(PianoEvent.NoteOff(60, 456L), received.await())
    }

    @Test
    fun emitsSustainChangesWithTimestamp() = runBlocking {
        val input = VirtualKeyboardInput(clock = { 789L })
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            input.events.first()
        }

        assertEquals(true, input.sustain(true))
        assertEquals(PianoEvent.Sustain(true, 789L), received.await())
    }

    @Test
    fun rejectsInvalidMidiNotesAndVelocity() {
        val input = VirtualKeyboardInput()
        assertFalse(input.noteOn(128, 0.5f))
        assertFalse(input.noteOn(60, 1.1f))
        assertFalse(input.noteOff(-1))
    }
}
