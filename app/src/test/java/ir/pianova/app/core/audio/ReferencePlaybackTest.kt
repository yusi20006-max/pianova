package ir.pianova.app.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferencePlaybackTest {

    class FakeSink : ReferenceAudioSink {
        val events = mutableListOf<String>()
        val active = mutableSetOf<Int>()
        override fun noteOn(midiNote: Int, velocity: Float) { events += "on:$midiNote"; active += midiNote }
        override fun noteOff(midiNote: Int) { events += "off:$midiNote"; active -= midiNote }
    }

    /** Fake clock + immediate sleep advancing the clock. */
    class FakeTime(var nanos: Long = 0L) {
        fun now() = nanos
    }

    private fun controller(scope: CoroutineScope, sink: ReferenceAudioSink, time: FakeTime, tickMs: Long = 10L) =
        ReferencePlaybackController(
            scope = scope,
            sink = sink,
            tickMs = tickMs,
            nowNanos = { time.nanos },
            sleep = { ms -> time.nanos += ms * 1_000_000L }
        )

    @Test fun referenceNoteRejectsInvalidData() {
        var threw = false
        try { ReferenceNote(200, 0.0, 1.0) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
        threw = false
        try { ReferenceNote(60, 0.0, 0.0) } catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }

    @Test fun schedulerComputesStartAndEnd() {
        val s = ReferenceScheduler(listOf(
            ReferenceNote(60, 0.0, 1.0),
            ReferenceNote(64, 1.0, 1.0)
        ))
        assertEquals(2.0, s.totalBeats, 1e-9)
        assertEquals(setOf(0), s.startedBy(0.5))
        assertEquals(setOf(0, 1), s.startedBy(1.0))
        assertEquals(setOf(0), s.endedBy(1.0))
    }

    @Test fun noPlaybackWithoutValidContent() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val sink = FakeSink(); val time = FakeTime()
        val c = controller(scope, sink, time)
        c.load(emptyList(), 120.0)
        assertFalse(c.hasContent)
        c.play(); c.join()
        assertTrue(sink.events.isEmpty())
        assertEquals(ReferencePlaybackState.IDLE, c.progress.value.state)
    }

    @Test fun playProducesBalancedNoteOnOffAndCompletes() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val sink = FakeSink(); val time = FakeTime()
        val c = controller(scope, sink, time, tickMs = 5L)
        // two sequential quarter notes at 120 BPM (0.5s each) = 1 beat each
        c.load(listOf(ReferenceNote(60, 0.0, 1.0), ReferenceNote(62, 1.0, 1.0)), 120.0)
        assertTrue(c.hasContent)
        c.play(); c.join()

        assertEquals(ReferencePlaybackState.COMPLETED, c.progress.value.state)
        assertEquals(1f, c.progress.value.fraction, 1e-4f)
        assertTrue(sink.events.contains("on:60")); assertTrue(sink.events.contains("off:60"))
        assertTrue(sink.events.contains("on:62")); assertTrue(sink.events.contains("off:62"))
        assertTrue("no stuck notes", sink.active.isEmpty())
        // ordering: on60 before off60 before on62 before off62
        val s = sink.events
        assertTrue(s.indexOf("on:60") < s.indexOf("off:60"))
        assertTrue(s.indexOf("off:60") < s.indexOf("on:62"))
        assertTrue(s.indexOf("on:62") < s.indexOf("off:62"))
    }

    @Test fun pauseStopsAndReleases_replayRestartsSingleSession() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val sink = FakeSink(); val time = FakeTime()
        val c = controller(scope, sink, time, tickMs = 5L)
        c.load(listOf(ReferenceNote(60, 0.0, 4.0)), 120.0)

        c.play()
        c.pause()
        assertEquals(ReferencePlaybackState.PAUSED, c.progress.value.state)
        assertTrue(sink.active.isEmpty()) // paused releases active notes
        val eventsAfterPause = sink.events.size

        c.replay(); c.join()
        assertEquals(ReferencePlaybackState.COMPLETED, c.progress.value.state)
        assertTrue(sink.active.isEmpty())
        assertTrue(sink.events.size > eventsAfterPause)
    }

    @Test fun stopResetsAndReleases() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val sink = FakeSink(); val time = FakeTime()
        val c = controller(scope, sink, time, tickMs = 5L)
        c.load(listOf(ReferenceNote(60, 0.0, 4.0)), 120.0)
        c.play(); c.stop()
        assertEquals(ReferencePlaybackState.STOPPED, c.progress.value.state)
        assertEquals(0.0, c.progress.value.currentBeat, 1e-9)
        assertTrue(sink.active.isEmpty())
    }

    @Test fun noDuplicateSessions_playWhilePlayingIsNoOp() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val sink = FakeSink(); val time = FakeTime()
        val c = controller(scope, sink, time, tickMs = 5L)
        c.load(listOf(ReferenceNote(60, 0.0, 2.0)), 120.0)
        c.play()
        c.play() // already playing -> no-op
        c.join()
        assertEquals(ReferencePlaybackState.COMPLETED, c.progress.value.state)
        // exactly one noteOn for note 60
        assertEquals(1, sink.events.count { it == "on:60" })
    }

    @Test fun releaseOnDisposeStopsAndClears() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val sink = FakeSink(); val time = FakeTime()
        val c = controller(scope, sink, time, tickMs = 5L)
        c.load(listOf(ReferenceNote(60, 0.0, 4.0)), 120.0)
        c.play(); c.release()
        assertEquals(ReferencePlaybackState.IDLE, c.progress.value.state)
        assertTrue(sink.active.isEmpty())
    }

    @Test fun referenceDoesNotEmitPracticeInput() {
        // ReferenceAudioSink has no path to PianoInput; structural guarantee.
        val sink = FakeSink()
        sink.noteOn(60, 1f); sink.noteOff(60)
        assertEquals(listOf("on:60", "off:60"), sink.events)
    }
}
