package ir.pianova.app.domain.evaluation

import ir.pianova.app.core.midi.PianoEvent
import ir.pianova.app.core.midi.PianoInput
import ir.pianova.app.domain.model.LessonStep
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationEngineTest {
    @Test
    fun evaluatesSingleNoteAndRepetitionCount() = runBlocking {
        val input = FakePianoInput()
        val session = EvaluationEngine(input).start(
            LessonStep.PlayNote("repeat-c", "دو را بنواز", midiNote = 60, repetitionCount = 2),
            this
        )
        yield()

        input.emit(PianoEvent.NoteOn(60, 0.8f, 1L))
        yield()
        assertEquals(EvaluationStatus.IN_PROGRESS, session.state.value.status)
        assertEquals(1, session.state.value.completedUnits)

        input.emit(PianoEvent.NoteOff(60, 2L))
        input.emit(PianoEvent.NoteOn(60, 0.8f, 3L))
        val result = awaitStatus(session, EvaluationStatus.CORRECT)
        assertTrue(result.isComplete)
        assertEquals(2, result.completedUnits)
    }

    @Test
    fun reportsWrongNoteThenRecoversOnCorrectNote() = runBlocking {
        val input = FakePianoInput()
        val session = EvaluationEngine(input).start(
            LessonStep.PlayNote("middle-c", "دو وسط", 60),
            this
        )
        yield()

        input.emit(PianoEvent.NoteOn(62, 0.8f, 1L))
        val wrong = awaitStatus(session, EvaluationStatus.WRONG_NOTE)
        assertEquals("این نت درست نیست؛ دوباره امتحان کن.", wrong.feedbackFa)

        input.emit(PianoEvent.NoteOff(62, 2L))
        input.emit(PianoEvent.NoteOn(60, 0.8f, 3L))
        assertTrue(awaitStatus(session, EvaluationStatus.CORRECT).isComplete)
    }

    @Test
    fun evaluatesSequenceInOrderWithoutTimingPenalty() = runBlocking {
        val input = FakePianoInput()
        val session = EvaluationEngine(input).start(
            LessonStep.PlaySequence("scale", "سه نت", listOf(60, 62, 64), tempoBpm = 80),
            this
        )
        yield()

        input.emit(PianoEvent.NoteOn(60, 0.8f, 10L))
        awaitStatus(session, EvaluationStatus.IN_PROGRESS)
        input.emit(PianoEvent.NoteOff(60, 20L))
        input.emit(PianoEvent.NoteOn(62, 0.8f, 5_000L))
        awaitStatus(session, EvaluationStatus.IN_PROGRESS)
        input.emit(PianoEvent.NoteOff(62, 5_100L))
        input.emit(PianoEvent.NoteOn(64, 0.8f, 30_000L))

        val result = awaitStatus(session, EvaluationStatus.CORRECT)
        assertEquals(3, result.completedUnits)
    }

    @Test
    fun distinguishesWrongSequenceNoteFromExtraNote() = runBlocking {
        val input = FakePianoInput()
        val session = EvaluationEngine(input).start(
            LessonStep.PlaySequence("melody", "ملودی", listOf(60, 62)),
            this
        )
        yield()

        input.emit(PianoEvent.NoteOn(62, 0.8f, 1L))
        assertEquals(EvaluationStatus.WRONG_NOTE, awaitStatus(session, EvaluationStatus.WRONG_NOTE).status)
        input.emit(PianoEvent.NoteOff(62, 2L))
        input.emit(PianoEvent.NoteOn(70, 0.8f, 3L))
        assertEquals(EvaluationStatus.EXTRA_NOTE, awaitStatus(session, EvaluationStatus.EXTRA_NOTE).status)
    }

    @Test
    fun evaluatesChordWhenAllNotesAreHeldTogether() = runBlocking {
        val input = FakePianoInput()
        val session = EvaluationEngine(input).start(
            LessonStep.PlayChord("c-major", "آکورد دو ماژور", listOf(60, 64, 67)),
            this
        )
        yield()

        input.emit(PianoEvent.NoteOn(60, 0.8f, 1L))
        awaitStatus(session, EvaluationStatus.IN_PROGRESS)
        input.emit(PianoEvent.NoteOn(64, 0.8f, 2L))
        awaitStatus(session, EvaluationStatus.IN_PROGRESS)
        input.emit(PianoEvent.NoteOn(67, 0.8f, 3L))

        val result = awaitStatus(session, EvaluationStatus.CORRECT)
        assertTrue(result.isComplete)
        assertEquals(3, result.completedUnits)
    }

    @Test
    fun reportsExtraNoteDuringChord() = runBlocking {
        val input = FakePianoInput()
        val session = EvaluationEngine(input).start(
            LessonStep.PlayChord("c-major", "آکورد دو ماژور", listOf(60, 64, 67)),
            this
        )
        yield()

        input.emit(PianoEvent.NoteOn(72, 0.8f, 1L))
        assertEquals(EvaluationStatus.EXTRA_NOTE, awaitStatus(session, EvaluationStatus.EXTRA_NOTE).status)
    }

    @Test
    fun chordDoesNotPassUntilExtraHeldNoteIsReleased() = runBlocking {
        val input = FakePianoInput()
        val session = EvaluationEngine(input).start(
            LessonStep.PlayChord("c-major", "آکورد دو ماژور", listOf(60, 64, 67)),
            this
        )
        yield()

        input.emit(PianoEvent.NoteOn(72, 0.8f, 1L))
        awaitStatus(session, EvaluationStatus.EXTRA_NOTE)
        input.emit(PianoEvent.NoteOn(60, 0.8f, 2L))
        input.emit(PianoEvent.NoteOn(64, 0.8f, 3L))
        input.emit(PianoEvent.NoteOn(67, 0.8f, 4L))
        assertEquals(EvaluationStatus.EXTRA_NOTE, session.state.value.status)

        input.emit(PianoEvent.NoteOff(72, 5L))
        assertTrue(awaitStatus(session, EvaluationStatus.CORRECT).isComplete)
    }

    @Test
    fun finishReportsMissingNotes() = runBlocking {
        val input = FakePianoInput()
        val session = EvaluationEngine(input).start(
            LessonStep.PlaySequence("short", "ملودی کوتاه", listOf(60, 62, 64)),
            this
        )
        yield()
        input.emit(PianoEvent.NoteOn(60, 0.8f, 1L))
        awaitStatus(session, EvaluationStatus.IN_PROGRESS)

        val result = session.finish()
        assertEquals(EvaluationStatus.MISSING_NOTE, result.status)
        assertEquals(listOf(62, 64), result.expectedNotes)
        assertTrue(result.isFinished)
        assertEquals("چند نت جا مانده؛ دوباره تلاش کن.", result.feedbackFa)
    }

    private suspend fun awaitStatus(
        session: EvaluationSession,
        status: EvaluationStatus
    ): EvaluationState = withTimeout(1_000) {
        session.state.first { it.status == status }
    }

    private class FakePianoInput : PianoInput {
        private val mutableEvents = MutableSharedFlow<PianoEvent>(extraBufferCapacity = 16)
        override val events: SharedFlow<PianoEvent> = mutableEvents

        suspend fun emit(event: PianoEvent) {
            mutableEvents.emit(event)
        }
    }
}
