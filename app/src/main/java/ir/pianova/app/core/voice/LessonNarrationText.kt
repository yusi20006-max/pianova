package ir.pianova.app.core.voice

import ir.pianova.app.domain.model.LessonStep

object LessonNarrationText {
    fun forStep(step: LessonStep, interactive: Boolean): String {
        val title = step.title.trim()
        val instruction = when (step) {
            is LessonStep.Explanation -> step.body
            is LessonStep.Listen -> step.prompt ?: "به صدای نت‌ها با دقت گوش کن."
            is LessonStep.PlayNote -> "نت هدف را بنواز: ${noteName(step.midiNote)}."
            is LessonStep.PlaySequence -> "نت‌ها را به ترتیب بنواز."
            is LessonStep.PlayChord -> "همه نت‌ها را هم‌زمان بنواز و نگه دار."
        }.trim()
        return if (interactive) "$title. $instruction" else "$title. $instruction"
    }

    fun feedback(text: String): String = text.trim()

    private fun noteName(midi: Int): String {
        val names = listOf("دو", "دو دیز", "ر", "ر دیز", "می", "فا", "فا دیز", "سل", "سل دیز", "لا", "لا دیز", "سی")
        return names[midi.mod(12)]
    }
}
