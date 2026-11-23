package ir.pianova.app.data.lesson

import ir.pianova.app.domain.model.LessonAudience
import ir.pianova.app.domain.model.Lesson
import ir.pianova.app.domain.model.Chapter
import ir.pianova.app.domain.model.LessonStep
import ir.pianova.app.domain.model.AudienceContent
import ir.pianova.app.domain.model.AudienceCopy
import ir.pianova.app.core.voice.LessonNarrationText
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
    fun adultVoiceUsesAdultNarrationInsteadOfChildNarration() {
        val step = LessonStep.PlayNote(
            id = "adult-voice",
            title = "عنوان پایه",
            midiNote = 60,
            displayText = "متن پایه",
            narrationText = "گویندگی پایه",
            audienceContent = AudienceContent(
                adults = AudienceCopy(narrationText = "گویندگی بزرگسال")
            )
        )
        assertEquals(
            "گویندگی بزرگسال",
            LessonNarrationText.forStep(step, interactive = true, audience = LessonAudience.ADULTS)
        )
        assertEquals(
            "گویندگی پایه",
            LessonNarrationText.forStep(step, interactive = true, audience = LessonAudience.CHILDREN)
        )
    }

    @Test
    fun adultProfileFallsBackToLegacyCopyWhenNoAdultOverrideExists() {
        val step = LessonStep.PlayNote(
            id = "note",
            title = "نت",
            midiNote = 60,
            displayText = "متن پایه",
            narrationText = "گویندگی پایه"
        )
        val lesson = Lesson(
            schemaVersion = 2,
            id = "fallback",
            title = "پایه",
            chapters = listOf(Chapter(id = "chapter", title = "فصل", steps = listOf(step)))
        )
        assertEquals("پایه", lesson.titleFor(LessonAudience.ADULTS))
        assertEquals("متن پایه", step.displayTextFor(LessonAudience.ADULTS))
        assertEquals("گویندگی پایه", step.narrationTextFor(LessonAudience.ADULTS))
    }

    @Test
    fun teenModeUsesTeenCopyWhenAvailableAndFallsBackToAdultCopyOtherwise() {
        val adultFallback = LessonStep.PlayNote(
            id = "teen-fallback", title = "عنوان", midiNote = 60,
            narrationText = "گویندگی پایه",
            audienceContent = AudienceContent(adults = AudienceCopy(narrationText = "گویندگی خنثی بزرگسال"))
        )
        assertEquals("گویندگی خنثی بزرگسال", adultFallback.narrationTextFor(LessonAudience.TEENS))
        val teenSpecific = adultFallback.copy(
            audienceContent = AudienceContent(
                adults = AudienceCopy(narrationText = "گویندگی بزرگسال"),
                teens = AudienceCopy(narrationText = "گویندگی نوجوان")
            )
        )
        assertEquals("گویندگی نوجوان", teenSpecific.narrationTextFor(LessonAudience.TEENS))
    }
}
