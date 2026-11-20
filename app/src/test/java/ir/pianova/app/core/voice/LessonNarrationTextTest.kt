package ir.pianova.app.core.voice

import ir.pianova.app.domain.model.LessonStep
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonNarrationTextTest {
    @Test
    fun explanationNarrationIncludesTitleAndPersianBody() {
        val step = LessonStep.Explanation("one", "کلیدهای پیانو", "کلیدهای سفید و سیاه را ببین.")
        val spoken = LessonNarrationText.forStep(step, interactive = true)
        assertTrue(spoken.contains("کلیدهای پیانو"))
        assertTrue(spoken.contains("کلیدهای سفید و سیاه را ببین"))
    }

    @Test
    fun playableStepsProduceActionablePersianInstructions() {
        val note = LessonStep.PlayNote("two", "نت دو", 60)
        val sequence = LessonStep.PlaySequence("three", "گام", listOf(60, 62, 64))
        val chord = LessonStep.PlayChord("four", "آکورد", listOf(60, 64, 67))
        assertTrue(LessonNarrationText.forStep(note, true).contains("نت هدف را بنواز: دو"))
        assertTrue(LessonNarrationText.forStep(sequence, false).contains("به ترتیب بنواز"))
        assertTrue(LessonNarrationText.forStep(chord, true).contains("هم‌زمان"))
    }

    @Test
    fun listenUsesPromptAndFallbackWhenPromptMissing() {
        val provided = LessonStep.Listen("five", "گوش کن", prompt = "به نت دو گوش بده")
        val fallback = LessonStep.Listen("six", "گوش کن")
        assertTrue(LessonNarrationText.forStep(provided, true).contains("به نت دو گوش بده"))
        assertTrue(LessonNarrationText.forStep(fallback, true).contains("به صدای نت‌ها"))
    }

    @Test
    fun versionTwoUsesOnlyItsExplicitNarrationText() {
        val step = LessonStep.PlayNote(
            id = "v2-step",
            title = "عنوان داخلی",
            midiNote = 60,
            displayText = "متن نمایشی مستقل",
            narrationText = "متن مخصوص گویندگی"
        )
        val spoken = LessonNarrationText.forStep(step, interactive = true)
        assertTrue(spoken == "متن مخصوص گویندگی")
    }

    @Test
    fun feedbackIsTrimmed() {
        assertTrue(LessonNarrationText.feedback("  درست بود  ") == "درست بود")
    }
}
