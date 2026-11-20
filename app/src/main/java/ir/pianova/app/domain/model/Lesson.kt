package ir.pianova.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Lesson(
    val schemaVersion: Int,
    val id: String,
    val title: String,
    val description: String? = null,
    val chapters: List<Chapter>,
    val completionCriteria: CompletionCriteria = CompletionCriteria(),
    val language: String? = null,
    val pedagogy: LessonPedagogy? = null,
    val masteryCriteria: MasteryCriteria? = null
)

@Serializable
data class LessonPedagogy(
    val teacherPersonality: String = "adaptive",
    val learningPace: String = "adaptive",
    val notationProgression: String = "gradual",
    val waitModeByDefault: Boolean = true,
    val visualGuidance: VisualGuidanceSettings = VisualGuidanceSettings()
)

@Serializable
data class VisualGuidanceSettings(
    val automaticSuggestionsEnabled: Boolean = true,
    val manualSelectionEnabled: Boolean = true,
    val defaultLevel: Int = 2,
    val suggestMoreHelpAfterConsecutiveErrors: Int = 2,
    val suggestLessHelpAfterConsecutiveSuccesses: Int = 2,
    val inactivitySuggestionSeconds: Int = 8
)

@Serializable
data class MasteryCriteria(
    val successfulIndependentAttempts: Int = 3,
    val minimumDistinctSessions: Int = 2
)

@Serializable
data class StepScaffolding(
    val level1: ScaffoldingLevel,
    val level2: ScaffoldingLevel,
    val level3: ScaffoldingLevel
)

@Serializable
data class ScaffoldingLevel(
    val hintText: String,
    val revealsAnswer: Boolean = false
)

@Serializable
data class Chapter(
    val id: String,
    val title: String,
    val description: String? = null,
    val steps: List<LessonStep>
)

@Serializable
data class CompletionCriteria(
    val minimumAccuracyPercent: Int = 80,
    val requireAllSteps: Boolean = true
)

@Serializable
sealed class LessonStep {
    abstract val id: String
    abstract val title: String
    abstract val displayText: String?
    abstract val narrationText: String?
    abstract val skillId: String?
    abstract val scaffolding: StepScaffolding?

    @Serializable
    @SerialName("explanation")
    data class Explanation(
        override val id: String,
        override val title: String,
        val body: String,
        override val displayText: String? = null,
        override val narrationText: String? = null,
        override val skillId: String? = null,
        override val scaffolding: StepScaffolding? = null
    ) : LessonStep()

    @Serializable
    @SerialName("listen")
    data class Listen(
        override val id: String,
        override val title: String,
        val audioAsset: String? = null,
        val prompt: String? = null,
        val midiNotes: List<Int> = emptyList(),
        val noteDurationMs: Long = 700,
        override val displayText: String? = null,
        override val narrationText: String? = null,
        override val skillId: String? = null,
        override val scaffolding: StepScaffolding? = null
    ) : LessonStep()

    @Serializable
    @SerialName("play_note")
    data class PlayNote(
        override val id: String,
        override val title: String,
        val midiNote: Int,
        val durationMs: Long = 800,
        val repetitionCount: Int = 1,
        override val displayText: String? = null,
        override val narrationText: String? = null,
        override val skillId: String? = null,
        override val scaffolding: StepScaffolding? = null
    ) : LessonStep()

    @Serializable
    @SerialName("play_sequence")
    data class PlaySequence(
        override val id: String,
        override val title: String,
        val midiNotes: List<Int>,
        val tempoBpm: Int = 80,
        override val displayText: String? = null,
        override val narrationText: String? = null,
        override val skillId: String? = null,
        override val scaffolding: StepScaffolding? = null
    ) : LessonStep()

    @Serializable
    @SerialName("play_chord")
    data class PlayChord(
        override val id: String,
        override val title: String,
        val midiNotes: List<Int>,
        val durationMs: Long = 1200,
        override val displayText: String? = null,
        override val narrationText: String? = null,
        override val skillId: String? = null,
        override val scaffolding: StepScaffolding? = null
    ) : LessonStep()
}
