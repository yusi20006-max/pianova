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
        ).firstOrNull { it.isDirectory } ?: error("Bundled lesson assets directory was not found")

        val files = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .sortedBy { it.name }

        assertTrue("The five starter lessons must be bundled", files.size >= 5)

        val lessons = files.map { file ->
            parser.parse(file.readText(Charsets.UTF_8)).also { lesson ->
                assertEquals("Asset filename and lesson id must match", file.nameWithoutExtension, lesson.id)
            }
        }
        assertEquals(
            true,
            lessons.map { it.id }.toSet().containsAll(
                setOf("first-notes", "lesson-02-middle-c", "lesson-03-do-re-mi", "lesson-04-fa-sol-la-si", "lesson-05-first-melody")
            )
        )

        assertEquals("فصل اول: آشنایی با پیانو", lessons.first { it.id == "first-notes" }.chapters.single().title)
        assertEquals("فصل اول: آشنایی با پیانو", lessons.first { it.id == "lesson-02-middle-c" }.chapters.single().title)
        listOf("lesson-03-do-re-mi", "lesson-04-fa-sol-la-si", "lesson-05-first-melody").forEach { id ->
            assertEquals("فصل دوم: نت‌های موسیقی", lessons.first { it.id == id }.chapters.single().title)
        }

        lessons.forEach { lesson ->
            assertEquals(2, lesson.schemaVersion)
            val steps = lesson.chapters.flatMap { it.steps }
            assertTrue("${lesson.id} needs explicit display text on every step", steps.all { !it.displayText.isNullOrBlank() })
            assertTrue("${lesson.id} needs explicit narration text on every step", steps.all { !it.narrationText.isNullOrBlank() })
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
        val keyboardSteps = keyboard.chapters.flatMap { it.steps }
        assertEquals(2, keyboard.schemaVersion)
        assertEquals(5, keyboardSteps.size)
        assertEquals(
            setOf("identify-white-keys", "identify-black-keys"),
            keyboardSteps.mapNotNull { it.skillId }.toSet()
        )
        assertTrue(keyboardSteps.filterIsInstance<LessonStep.Listen>().any {
            it.midiNotes == listOf(60, 62, 64, 65, 67, 69, 71)
        })
        assertTrue(keyboardSteps.filterIsInstance<LessonStep.Listen>().any {
            it.midiNotes == listOf(61, 63, 66, 68, 70)
        })

        val middleC = lesson("lesson-02-middle-c")
        val middleCSteps = middleC.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.PlayNote>()
        assertTrue(middleCSteps.any { it.midiNote == 60 })
        assertTrue(middleCSteps.all { it.skillId == "find-middle-c" && it.acceptedMidiNotes == listOf(60) })

        val doReMi = lesson("lesson-03-do-re-mi")
        assertTrue(doReMi.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.PlaySequence>().all { it.midiNotes == listOf(60, 62, 64, 62, 60) })
        assertTrue(doReMi.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.Listen>().all { it.midiNotes == listOf(60, 62, 64, 62, 60) })

        val faSolLaSi = lesson("lesson-04-fa-sol-la-si")
        assertTrue(faSolLaSi.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.PlaySequence>().all { it.midiNotes == listOf(65, 67, 69, 71, 69, 67, 65) })
        assertTrue(faSolLaSi.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.Listen>().all { it.midiNotes == listOf(65, 67, 69, 71, 69, 67, 65) })

        val melody = lesson("lesson-05-first-melody")
        assertTrue(melody.chapters.flatMap { it.steps }.filterIsInstance<LessonStep.PlaySequence>().any {
            it.midiNotes == listOf(60, 62, 64, 67, 64, 62, 60) && it.midiNotes.size == 7
        })
    }
}
