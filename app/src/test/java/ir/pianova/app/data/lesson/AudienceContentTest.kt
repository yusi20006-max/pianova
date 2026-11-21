package ir.pianova.app.data.lesson

import ir.pianova.app.domain.model.LessonAudience
import ir.pianova.app.domain.model.displayTextFor
import ir.pianova.app.domain.model.narrationTextFor
import ir.pianova.app.domain.model.titleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudienceContentTest {
    private val parser = JsonLessonParser()

    @Test
    fun allFiveBundledLessonsProvideIndependentChildAndAdultCopy() {
        val directory = listOf(
            File("src/main/assets/lessons"),
            File("app/src/main/assets/lessons")
        ).firstOrNull { it.isDirectory } ?: error("Bundled lesson assets directory was not found")
        val expected = setOf(
            "first-notes", "lesson-02-middle-c", "lesson-03-do-re-mi",
            "lesson-04-fa-sol-la-si", "lesson-05-first-melody"
        )
        val files = directory.listFiles().orEmpty()
            .filter { it.isFile && it.extension == "json" && it.nameWithoutExtension in expected }
        assertEquals(5, files.size)

        files.forEach { file ->
            val lesson = parser.parse(file.readText(Charsets.UTF_8))
            assertNotNull(lesson.audienceContent?.children)
            assertNotNull(lesson.audienceContent?.adults)
            assertTrue(lesson.titleFor(LessonAudience.CHILDREN).isNotBlank())
            assertTrue(lesson.titleFor(LessonAudience.ADULTS).isNotBlank())
            lesson.chapters.flatMap { it.steps }.forEach { step ->
                assertTrue(step.displayTextFor(LessonAudience.CHILDREN).isNotBlank())
                assertTrue(step.displayTextFor(LessonAudience.ADULTS).isNotBlank())
                assertTrue(!step.narrationTextFor(LessonAudience.CHILDREN).isNullOrBlank())
                assertTrue(!step.narrationTextFor(LessonAudience.ADULTS).isNullOrBlank())
            }
        }
    }

    @Test
    fun adultProfileFallsBackToLegacyCopyWhenNoAdultOverrideExists() {
        val lesson = parser.parse("""
            {
              "schemaVersion": 2, "id": "fallback", "title": "پایه",
              "language": "fa-IR",
              "pedagogy": {
                "teacherPersonality": "adaptive", "learningPace": "adaptive",
                "notationProgression": "gradual", "waitModeByDefault": true,
                "visualGuidance": {
                  "automaticSuggestionsEnabled": true, "manualSelectionEnabled": true,
                  "defaultLevel": 2, "suggestMoreHelpAfterConsecutiveErrors": 2,
                  "suggestLessHelpAfterConsecutiveSuccesses": 2, "inactivitySuggestionSeconds": 8
                }
              },
              "masteryCriteria": {"successfulIndependentAttempts": 3, "minimumDistinctSessions": 2},
              "chapters": [{
                "id": "chapter", "title": "فصل", "steps": [{
                  "type": "play_note", "id": "note", "title": "نت",
                  "displayText": "متن پایه", "narrationText": "گویندگی پایه", "midiNote": 60
                }]
              }]
            }
        """.trimIndent())
        val step = lesson.chapters.single().steps.single()
        assertEquals("پایه", lesson.titleFor(LessonAudience.ADULTS))
        assertEquals("متن پایه", step.displayTextFor(LessonAudience.ADULTS))
        assertEquals("گویندگی پایه", step.narrationTextFor(LessonAudience.ADULTS))
    }
}
