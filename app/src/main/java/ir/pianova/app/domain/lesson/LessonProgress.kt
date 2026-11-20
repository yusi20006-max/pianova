package ir.pianova.app.domain.lesson

import ir.pianova.app.domain.model.Chapter
import ir.pianova.app.domain.model.Lesson
import ir.pianova.app.domain.model.LessonStep

data class LessonStepEntry(
    val chapterId: String,
    val chapterTitle: String,
    val step: LessonStep,
    val index: Int
)

/** Flattens chapter content while retaining its chapter identity and global order. */
fun Lesson.flattenedSteps(): List<LessonStepEntry> =
    chapters.flatMap { chapter ->
        chapter.steps.map { step ->
            LessonStepEntry(
                chapterId = chapter.id,
                chapterTitle = chapter.title,
                step = step,
                index = 0
            )
        }
    }.mapIndexed { index, entry -> entry.copy(index = index) }

/** MIDI notes represented by a step, used for keyboard guidance and playback. */
fun LessonStep.targetMidiNotes(): List<Int> = when (this) {
    is LessonStep.Explanation -> emptyList()
    is LessonStep.Listen -> midiNotes
    is LessonStep.PlayNote -> listOf(midiNote)
    is LessonStep.PlaySequence -> midiNotes
    is LessonStep.PlayChord -> midiNotes.distinct()
}

/** The nearest upcoming playable note, used to preview keys during explanations. */
fun List<LessonStepEntry>.previewNotesAt(index: Int): List<Int> =
    drop(index).firstNotNullOfOrNull { entry ->
        entry.step.targetMidiNotes().takeIf { it.isNotEmpty() }
    }.orEmpty()
