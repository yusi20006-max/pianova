package ir.pianova.app.data.lesson

import ir.pianova.app.domain.model.LessonStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledLessonContentTest {
    private val parser = JsonLessonParser()

    @Test
    fun allFiveBundledPersianLessonsParseAndContainEveryStepKind() {
        val directory = listOf(
            File("src/main/assets/lessons"),
            File("app/src/main/assets/lessons")
        ).firstOrNull(File::isDirectory) ?: error("Bundled lesson assets directory was not found")

        val files = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .sortedBy(File::getName)

        assertEquals("Exactly five beginner lessons must be bundled", 5, files.size)

        val lessons = files.map { file ->
            parser.parse(file.readText(Charsets.UTF_8)).also { lesson ->
                assertEquals("Asset filename and lesson id must match", file.nameWithoutExtension, lesson.id)
            }
        }
        assertEquals(
            setOf("first-notes", "lesson-02-middle-c", "lesson-03-do-re-mi", "lesson-04-fa-sol-la-si", "lesson-05-first-melody"),
            lessons.map { it.id }.toSet()
        )

        lessons.forEach { lesson ->
            val steps = lesson.chapters.flatMap { it.steps }
            assertTrue("${lesson.id} needs an explanation", steps.any { it is LessonStep.Explanation })
            assertTrue("${lesson.id} needs a listen step", steps.any { it is LessonStep.Listen })
            assertTrue(
                "${lesson.id} needs an interactive play step",
                steps.any { it is LessonStep.PlayNote || it is LessonStep.PlaySequence || it is LessonStep.PlayChord }
            )
        }
    }

    @Test
    fun firstFiveLessonsTeachTheRequiredNotesAndSevenNoteMelody() {
        val directory = listOf(
            File("src/main/assets/lessons"),
            File("app/src/main/assets/lessons")
        ).firstOrNull(File::isDirectory) ?: error("Bundled lesson assets directory was not found")

        fun lesson(id: String) = parser.parse(
            File(directory, "$id.json").readText(Charsets.UTF_8)
        )

        val keyboard = lesson("first-notes")
        val blackKeySequences = keyboard.chapters.flatMap { it.steps }
            .filterIsInstance<LessonStep.PlaySequence>()
            .map { it.midiNotes }
        assertTrue(listOf(61, 63) in blackKeySequences)
        assertTrue(listOf(66, 68, 70) in blackKeySequences)
        assertTrue(listOf(60, 62, 64, 65, 67, 69, 71) in blackKeySequences)

        val middleC = lesson("lesson-02-middle-c")
        assertTrue(middleC.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.PlayNote>().any { it.midiNote == 60 })

        val doReMi = lesson("lesson-03-do-re-mi")
        assertTrue(doReMi.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.PlaySequence>().any { it.midiNotes == listOf(60, 62, 64) })

        val faSolLaSi = lesson("lesson-04-fa-sol-la-si")
        assertTrue(faSolLaSi.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.PlaySequence>().any { it.midiNotes == listOf(65, 67, 69, 71) })

        val melody = lesson("lesson-05-first-melody")
        assertTrue(melody.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.PlaySequence>().any {
            it.midiNotes == listOf(60, 62, 64, 67, 64, 62, 60) && it.midiNotes.size == 7
        })
    }
}
