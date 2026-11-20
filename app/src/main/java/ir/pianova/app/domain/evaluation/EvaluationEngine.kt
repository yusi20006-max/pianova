package ir.pianova.app.domain.evaluation

import ir.pianova.app.core.midi.PianoEvent
import ir.pianova.app.core.midi.PianoInput
import ir.pianova.app.domain.model.LessonStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class EvaluationStatus {
    READY,
    IN_PROGRESS,
    CORRECT,
    WRONG_NOTE,
    MISSING_NOTE,
    EXTRA_NOTE
}

data class EvaluationState(
    val status: EvaluationStatus,
    val expectedNotes: List<Int>,
    val receivedNotes: List<Int>,
    val completedUnits: Int,
    val totalUnits: Int,
    val feedbackFa: String,
    val isComplete: Boolean = false,
    val isFinished: Boolean = false
)

/**
 * Starts one evaluation session against the normalized PianoInput stream.
 *
 * Timing is deliberately not used as a pass/fail condition. Beginners can
 * take as long as they need unless a future lesson step explicitly opts in
 * to rhythm evaluation.
 */
class EvaluationEngine(
    private val input: PianoInput
) {
    fun start(
        step: LessonStep,
        scope: CoroutineScope
    ): EvaluationSession {
        require(step is LessonStep.PlayNote ||
            step is LessonStep.PlaySequence ||
            step is LessonStep.PlayChord) {
            "Only playable lesson steps can be evaluated"
        }

        val session = EvaluationSession(step)
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            input.events.collect(session::accept)
        }
        session.attach(job)
        return session
    }
}

class EvaluationSession internal constructor(
    private val step: LessonStep
) {
    private val _state = MutableStateFlow(initialState(step))
    val state: StateFlow<EvaluationState> = _state.asStateFlow()

    private val heldNotes = mutableSetOf<Int>()
    private val receivedNotes = mutableListOf<Int>()
    private val chordNotesSeen = mutableSetOf<Int>()
    private var sequenceIndex = 0
    private var repetitionsCompleted = 0
    private var collectorJob: Job? = null

    internal fun attach(job: Job) {
        collectorJob = job
    }

    @Synchronized
    internal fun accept(event: PianoEvent) {
        if (_state.value.isFinished || _state.value.isComplete) return

        when (event) {
            is PianoEvent.NoteOn -> onNoteOn(event.midiNote)
            is PianoEvent.NoteOff -> {
                heldNotes.remove(event.midiNote)
                if (step is LessonStep.PlayChord) {
                    val expected = step.midiNotes.distinct()
                    val chordHeld = expected.all { it in heldNotes }
                    val hasExtraHeld = heldNotes.any { it !in expected }
                    if (chordHeld && !hasExtraHeld) {
                        publish(
                            status = EvaluationStatus.CORRECT,
                            expected = emptyList(),
                            feedback = "آفرین، آکورد را درست نواختی!",
                            complete = true,
                            completed = expected.size,
                            total = expected.size
                        )
                    }
                }
            }
            is PianoEvent.Sustain -> Unit
        }
    }

    /**
     * Ends the attempt explicitly. An unfinished attempt is reported as
     * MissingNote; no timeout or tempo penalty is applied.
     */
    fun finish(): EvaluationState {
        val result = synchronized(this) {
            val current = _state.value
            if (current.isComplete || current.isFinished) {
                current
            } else {
                val missing = remainingNotes()
                current.copy(
                    status = EvaluationStatus.MISSING_NOTE,
                    expectedNotes = missing,
                    receivedNotes = receivedNotes.toList(),
                    completedUnits = completedUnits(),
                    feedbackFa = "چند نت جا مانده؛ دوباره تلاش کن.",
                    isComplete = false,
                    isFinished = true
                ).also { _state.value = it }
            }
        }
        collectorJob?.cancel()
        return result
    }

    /**
     * Stops collecting when the caller abandons an attempt, without
     * converting an unfinished attempt into a MissingNote result.
     */
    fun cancel() {
        synchronized(this) {
            if (!_state.value.isFinished) {
                _state.value = _state.value.copy(isFinished = true)
            }
        }
        collectorJob?.cancel()
    }

    private fun onNoteOn(note: Int) {
        if (!heldNotes.add(note)) return
        receivedNotes += note

        when (step) {
            is LessonStep.PlayNote -> evaluateSingleNote(step, note)
            is LessonStep.PlaySequence -> evaluateSequence(step, note)
            is LessonStep.PlayChord -> evaluateChord(step, note)
            else -> Unit
        }
    }

    private fun evaluateSingleNote(target: LessonStep.PlayNote, note: Int) {
        val matchesTarget = if (target.acceptedPitchClasses.isNotEmpty()) {
            note.mod(12) in target.acceptedPitchClasses
        } else {
            note == target.midiNote
        }
        if (!matchesTarget) {
            publish(
                status = EvaluationStatus.WRONG_NOTE,
                expected = listOf(target.midiNote),
                feedback = "این نت درست نیست؛ دوباره امتحان کن."
            )
            return
        }

        repetitionsCompleted++
        val complete = repetitionsCompleted >= target.repetitionCount
        publish(
            status = if (complete) EvaluationStatus.CORRECT else EvaluationStatus.IN_PROGRESS,
            expected = if (complete) emptyList() else listOf(target.midiNote),
            feedback = if (complete) "آفرین، درست نواختی!" else "درست بود؛ یک بار دیگر بنواز.",
            complete = complete
        )
    }

    private fun evaluateSequence(target: LessonStep.PlaySequence, note: Int) {
        val expected = target.midiNotes.getOrNull(sequenceIndex)
        if (note == expected) {
            sequenceIndex++
            val complete = sequenceIndex == target.midiNotes.size
            publish(
                status = if (complete) EvaluationStatus.CORRECT else EvaluationStatus.IN_PROGRESS,
                expected = if (complete) emptyList() else listOf(target.midiNotes[sequenceIndex]),
                feedback = if (complete) "آفرین، ملودی را درست نواختی!" else "درست بود؛ ادامه بده.",
                complete = complete
            )
        } else {
            val isPartOfTarget = note in target.midiNotes
            publish(
                status = if (isPartOfTarget) EvaluationStatus.WRONG_NOTE else EvaluationStatus.EXTRA_NOTE,
                expected = listOfNotNull(expected),
                feedback = if (isPartOfTarget) {
                    "ترتیب نت‌ها را دوباره بررسی کن."
                } else {
                    "یک نت اضافه نواخته شد؛ دوباره امتحان کن."
                }
            )
        }
    }

    private fun evaluateChord(target: LessonStep.PlayChord, note: Int) {
        val expected = target.midiNotes.distinct()
        if (note !in expected) {
            publish(
                status = EvaluationStatus.EXTRA_NOTE,
                expected = expected.filterNot { it in heldNotes },
                feedback = "این نت در آکورد نیست؛ دوباره امتحان کن."
            )
            return
        }

        chordNotesSeen += note
        val allChordNotesHeld = expected.all { it in heldNotes }
        val hasExtraHeld = heldNotes.any { it !in expected }
        val complete = allChordNotesHeld && !hasExtraHeld
        val hasExtra = allChordNotesHeld && hasExtraHeld
        publish(
            status = when {
                complete -> EvaluationStatus.CORRECT
                hasExtra -> EvaluationStatus.EXTRA_NOTE
                else -> EvaluationStatus.IN_PROGRESS
            },
            expected = expected.filterNot { it in heldNotes },
            feedback = when {
                complete -> "آفرین، آکورد را درست نواختی!"
                hasExtra -> "نت اضافه را رها کن تا آکورد بررسی شود."
                else -> "خوب است؛ بقیه نت‌های آکورد را هم بنواز."
            },
            complete = complete,
            completed = chordNotesSeen.size,
            total = expected.size
        )
    }

    private fun remainingNotes(): List<Int> = when (step) {
        is LessonStep.PlayNote -> List((step.repetitionCount - repetitionsCompleted).coerceAtLeast(0)) {
            step.midiNote
        }
        is LessonStep.PlaySequence -> step.midiNotes.drop(sequenceIndex)
        is LessonStep.PlayChord -> step.midiNotes.distinct().filterNot { it in heldNotes }
        else -> emptyList()
    }

    private fun completedUnits(): Int = when (step) {
        is LessonStep.PlayNote -> repetitionsCompleted
        is LessonStep.PlaySequence -> sequenceIndex
        is LessonStep.PlayChord -> chordNotesSeen.size
        else -> 0
    }

    private fun totalUnits(): Int = when (step) {
        is LessonStep.PlayNote -> step.repetitionCount
        is LessonStep.PlaySequence -> step.midiNotes.size
        is LessonStep.PlayChord -> step.midiNotes.distinct().size
        else -> 0
    }

    private fun publish(
        status: EvaluationStatus,
        expected: List<Int>,
        feedback: String,
        complete: Boolean = false,
        completed: Int = completedUnits(),
        total: Int = totalUnits()
    ) {
        _state.value = EvaluationState(
            status = status,
            expectedNotes = expected,
            receivedNotes = receivedNotes.toList(),
            completedUnits = completed,
            totalUnits = total,
            feedbackFa = feedback,
            isComplete = complete,
            isFinished = complete
        )
        // A successful session is terminal. Stop collecting immediately so
        // the parent scope is not kept alive by an idle SharedFlow collector.
        if (complete) collectorJob?.cancel()
    }

    companion object {
        private fun initialState(step: LessonStep): EvaluationState {
            val expected = when (step) {
                is LessonStep.PlayNote -> listOf(step.midiNote)
                is LessonStep.PlaySequence -> step.midiNotes
                is LessonStep.PlayChord -> step.midiNotes.distinct()
                else -> emptyList()
            }
            val total = when (step) {
                is LessonStep.PlayNote -> step.repetitionCount
                is LessonStep.PlaySequence -> step.midiNotes.size
                is LessonStep.PlayChord -> step.midiNotes.distinct().size
                else -> 0
            }
            return EvaluationState(
                status = EvaluationStatus.READY,
                expectedNotes = expected,
                receivedNotes = emptyList(),
                completedUnits = 0,
                totalUnits = total,
                feedbackFa = "آماده‌ای؟ شروع کن."
            )
        }
    }
}
