package ir.pianova.app.core.midi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeInputRouterTest {

    class FakePianoInput : PianoInput {
        private val flow = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = 256)
        override val events: SharedFlow<PianoEvent> = flow
        suspend fun emit(e: PianoEvent) = flow.emit(e)
    }

    private fun router(midi: PianoInput, mic: PianoInput, scope: CoroutineScope) =
        PracticeInputRouter(midi, mic, scope)

    @Test fun routesMidiNoteOnOffAndSustainInMidiMode() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val midi = FakePianoInput(); val mic = FakePianoInput()
        val r = router(midi, mic, scope)
        val out = mutableListOf<PianoEvent>()
        val job = scope.launch { r.events.collect { out += it } }
        yield()

        midi.emit(PianoEvent.NoteOn(60, 0.9f, 1)); yield()
        midi.emit(PianoEvent.NoteOff(60, 2)); yield()
        midi.emit(PianoEvent.Sustain(true, 3)); yield()
        midi.emit(PianoEvent.Sustain(false, 4)); yield()

        assertTrue(out.any { it is PianoEvent.NoteOn && it.midiNote == 60 && it.velocity == 0.9f })
        assertTrue(out.any { it is PianoEvent.NoteOff && it.midiNote == 60 })
        assertTrue(out.any { it is PianoEvent.Sustain && it.enabled })
        assertTrue(out.any { it is PianoEvent.Sustain && !it.enabled })
        job.cancel(); r.close()
    }

    @Test fun microphoneEventsIgnoredInMidiMode_andMidiIgnoredInMicMode() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val midi = FakePianoInput(); val mic = FakePianoInput()
        val r = router(midi, mic, scope)
        val out = mutableListOf<PianoEvent>()
        val job = scope.launch { r.events.collect { out += it } }
        yield()

        mic.emit(PianoEvent.NoteOn(60, 1f, 1)); yield()   // ignored in MIDI mode
        assertTrue(out.isEmpty())

        r.setMode(PracticeInputMode.MICROPHONE); yield()
        mic.emit(PianoEvent.NoteOn(62, 1f, 2)); yield()
        assertTrue(out.any { it is PianoEvent.NoteOn && it.midiNote == 62 })
        midi.emit(PianoEvent.NoteOn(64, 1f, 3)); yield()  // ignored in MIC mode
        assertTrue(out.none { it is PianoEvent.NoteOn && it.midiNote == 64 })
        job.cancel(); r.close()
    }

    @Test fun switchingModeReleasesHeldNotes() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val midi = FakePianoInput(); val mic = FakePianoInput()
        val r = router(midi, mic, scope)
        val out = mutableListOf<PianoEvent>()
        val job = scope.launch { r.events.collect { out += it } }
        yield()
        midi.emit(PianoEvent.NoteOn(60, 1f, 1)); yield()
        r.setMode(PracticeInputMode.MICROPHONE); yield()
        assertTrue(out.any { it is PianoEvent.NoteOff && it.midiNote == 60 })
        job.cancel(); r.close()
    }

    @Test fun setEnabledFalseSuspendsAndReleases_thenResumes() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val midi = FakePianoInput(); val mic = FakePianoInput()
        val r = router(midi, mic, scope)
        val out = mutableListOf<PianoEvent>()
        val job = scope.launch { r.events.collect { out += it } }
        yield()
        midi.emit(PianoEvent.NoteOn(60, 1f, 1)); yield()

        r.setEnabled(false); yield()   // used during reference playback
        assertTrue(out.any { it is PianoEvent.NoteOff && it.midiNote == 60 })
        val before = out.size
        midi.emit(PianoEvent.NoteOn(62, 1f, 2)); yield()
        assertEquals(before, out.size) // no events while disabled

        r.setEnabled(true); yield()
        midi.emit(PianoEvent.NoteOn(64, 1f, 3)); yield()
        assertTrue(out.any { it is PianoEvent.NoteOn && it.midiNote == 64 })
        job.cancel(); r.close()
    }

    @Test fun disconnectReleaseProducesNoteOffsAndCloseReleases() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val midi = FakePianoInput(); val mic = FakePianoInput()
        val r = router(midi, mic, scope)
        val out = mutableListOf<PianoEvent>()
        val job = scope.launch { r.events.collect { out += it } }
        yield()
        midi.emit(PianoEvent.NoteOn(60, 1f, 1)); yield()
        midi.emit(PianoEvent.NoteOff(60, 2)); yield()       // normal release
        assertTrue(out.any { it is PianoEvent.NoteOff && it.midiNote == 60 })

        // reconnection: emit NoteOn again -> works
        midi.emit(PianoEvent.NoteOn(60, 1f, 3)); yield()
        assertTrue(out.takeLast(1).any { it is PianoEvent.NoteOn && it.midiNote == 60 })

        // close() releases held notes
        val before = out.size
        r.close(); yield()
        assertTrue(out.drop(before).any { it is PianoEvent.NoteOff && it.midiNote == 60 })
        job.cancel()
    }
}
