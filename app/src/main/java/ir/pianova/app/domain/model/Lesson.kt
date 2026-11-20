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
    val completionCriteria: CompletionCriteria = CompletionCriteria()
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

    @Serializable
    @SerialName("explanation")
    data class Explanation(
        override val id: String,
        override val title: String,
        val body: String
    ) : LessonStep()

    @Serializable
    @SerialName("listen")
    data class Listen(
        override val id: String,
        override val title: String,
        val audioAsset: String,
        val prompt: String? = null
    ) : LessonStep()

    @Serializable
    @SerialName("play_note")
    data class PlayNote(
        override val id: String,
        override val title: String,
        val midiNote: Int,
        val durationMs: Long = 800,
        val repetitionCount: Int = 1
    ) : LessonStep()

    @Serializable
    @SerialName("play_sequence")
    data class PlaySequence(
        override val id: String,
        override val title: String,
        val midiNotes: List<Int>,
        val tempoBpm: Int = 80
    ) : LessonStep()

    @Serializable
    @SerialName("play_chord")
    data class PlayChord(
        override val id: String,
        override val title: String,
        val midiNotes: List<Int>,
        val durationMs: Long = 1200
    ) : LessonStep()
}
