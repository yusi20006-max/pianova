package ir.pianova.app.core.midi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergedPianoInputTest {

    class FakePianoInput : PianoInput {
        private val flow = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = 256)
        override val events: SharedFlow<PianoEvent> = flow
        suspend fun emit(e: PianoEvent) = flow.emit(e)
    }

    @Test fun mergesVirtualAndPhysicalSources() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val touch = FakePianoInput(); val usb = FakePianoInput(); val bt = FakePianoInput()
        val merged = MergedPianoInput(listOf(touch, usb, bt), scope)
        val out = mutableListOf<PianoEvent>()
        val job = scope.launch { merged.events.collect { out += it } }

        touch.emit(PianoEvent.NoteOn(60, 0.8f, 1)); usb.emit(PianoEvent.NoteOn(64, 0.9f, 2)); bt.emit(PianoEvent.NoteOn(67, 1f, 3))
        delay(50)
        assertTrue(out.any { it is PianoEvent.NoteOn && it.midiNote == 60 })
        assertTrue(out.any { it is PianoEvent.NoteOn && it.midiNote == 64 })
        assertTrue(out.any { it is PianoEvent.NoteOn && it.midiNote == 67 })

        // note released by same source -> NoteOff emitted
        usb.emit(PianoEvent.NoteOff(64, 4)); delay(50)
        assertTrue(out.any { it is PianoEvent.NoteOff && it.midiNote == 64 })
        job.cancel()
    }

    @Test fun sameNoteOnTwoSourcesReleasedOnlyAfterBothRelease() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val a = FakePianoInput(); val b = FakePianoInput()
        val merged = MergedPianoInput(listOf(a, b), scope)
        val out = mutableListOf<PianoEvent>()
        val job = scope.launch { merged.events.collect { out += it } }
        a.emit(PianoEvent.NoteOn(60, 1f, 1)); b.emit(PianoEvent.NoteOn(60, 1f, 2)); delay(50)
        assertEquals(1, out.count { it is PianoEvent.NoteOn && it.midiNote == 60 }) // one NoteOn

        a.emit(PianoEvent.NoteOff(60, 3)); delay(50)
        assertTrue(out.none { it is PianoEvent.NoteOff && it.midiNote == 60 }) // still held by b
        b.emit(PianoEvent.NoteOff(60, 4)); delay(50)
        assertTrue(out.any { it is PianoEvent.NoteOff && it.midiNote == 60 }) // now released
        job.cancel()
    }
}
