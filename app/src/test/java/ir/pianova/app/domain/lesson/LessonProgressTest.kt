package ir.pianova.app.domain.lesson

import ir.pianova.app.domain.model.Chapter
import ir.pianova.app.domain.model.Lesson
import ir.pianova.app.domain.model.LessonStep
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonProgressTest {
    @Test
    fun flattenedStepsPreserveChapterAndStepOrder() {
        val lesson = Lesson(
            schemaVersion = 1,
            id = "sample",
            title = "نمونه",
            chapters = listOf(
                Chapter("one", "فصل اول", steps = listOf(
                    LessonStep.Explanation("intro", "معرفی", "متن")
                )),
                Chapter("two", "فصل دوم", steps = listOf(
                    LessonStep.PlayNote("c4", "دو", 60),
                    LessonStep.PlayNote("d4", "ر", 62)
                ))
            )
        )

        val entries = lesson.flattenedSteps()

        assertEquals(listOf(0, 1, 2), entries.map { it.index })
        assertEquals(listOf("one", "two", "two"), entries.map { it.chapterId })
        assertEquals(listOf("intro", "c4", "d4"), entries.map { it.step.id })
    }

    @Test
    fun explanationPreviewsNextPlayableStepAndListenNotes() {
        val entries = listOf(
            LessonStepEntry("one", "فصل اول", LessonStep.Explanation("intro", "معرفی", "متن"), 0),
            LessonStepEntry("one", "فصل اول", LessonStep.Listen("listen", "گوش کن", midiNotes = listOf(60)), 1),
            LessonStepEntry("one", "فصل اول", LessonStep.PlayNote("play", "بنواز", 60), 2)
        )

        assertEquals(listOf(60), entries.previewNotesAt(0))
        assertEquals(listOf(60), entries.previewNotesAt(1))
        assertEquals(listOf(60), entries[2].step.targetMidiNotes())
    }
}
