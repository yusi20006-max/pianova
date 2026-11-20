package ir.pianova.app.data.lesson

import ir.pianova.app.domain.model.Chapter
import ir.pianova.app.domain.model.Lesson
import ir.pianova.app.domain.model.LessonStep
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class LessonFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class JsonLessonParser(
    private val json: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }
) {
    fun parse(source: String): Lesson {
        val lesson = try {
            json.decodeFromString<Lesson>(source)
        } catch (error: SerializationException) {
            throw LessonFormatException("Lesson JSON is malformed or has an unsupported structure", error)
        } catch (error: IllegalArgumentException) {
            if (error is LessonFormatException) throw error
            throw LessonFormatException("Lesson JSON could not be decoded", error)
        }

        try {
            validate(lesson)
        } catch (error: IllegalArgumentException) {
            throw LessonFormatException(error.message ?: "Lesson content failed validation", error)
        }
        return lesson
    }

    private fun validate(lesson: Lesson) {
        require(lesson.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported lesson schema version: ${lesson.schemaVersion}"
        }
        require(lesson.id.isNotBlank()) { "Lesson id must not be blank" }
        require(lesson.title.isNotBlank()) { "Lesson title must not be blank" }
        require(lesson.chapters.isNotEmpty()) { "Lesson must contain at least one chapter" }
        require(lesson.completionCriteria.minimumAccuracyPercent in 0..100) {
            "minimumAccuracyPercent must be between 0 and 100"
        }
        require(lesson.chapters.map(Chapter::id).distinct().size == lesson.chapters.size) {
            "Chapter ids must be unique within a lesson"
        }

        lesson.chapters.forEach { chapter ->
            require(chapter.id.isNotBlank()) { "Chapter id must not be blank" }
            require(chapter.title.isNotBlank()) { "Chapter title must not be blank" }
            require(chapter.steps.isNotEmpty()) { "Chapter ${chapter.id} must contain at least one step" }
            require(chapter.steps.map(LessonStep::id).distinct().size == chapter.steps.size) {
                "Step ids must be unique within chapter ${chapter.id}"
            }
            chapter.steps.forEach(::validateStep)
        }
    }

    private fun validateStep(step: LessonStep) {
        require(step.id.isNotBlank()) { "Step id must not be blank" }
        require(step.title.isNotBlank()) { "Step title must not be blank" }
        when (step) {
            is LessonStep.Explanation -> require(step.body.isNotBlank()) {
                "Explanation step ${step.id} must have a body"
            }
            is LessonStep.Listen -> {
                require(step.audioAsset?.isNotBlank() == true || step.midiNotes.isNotEmpty()) {
                    "Listen step ${step.id} must specify an audio asset or reference MIDI notes"
                }
                require(step.midiNotes.all { it in 0..127 }) {
                    "Listen step ${step.id} contains an invalid MIDI note"
                }
                require(step.noteDurationMs > 0) {
                    "Listen step ${step.id} note duration must be positive"
                }
            }
            is LessonStep.PlayNote -> {
                require(step.midiNote in 0..127) { "MIDI note must be between 0 and 127" }
                require(step.durationMs > 0) { "Note duration must be positive" }
                require(step.repetitionCount in 1..100) { "Repetition count must be between 1 and 100" }
            }
            is LessonStep.PlaySequence -> {
                require(step.midiNotes.isNotEmpty()) { "Sequence must contain notes" }
                require(step.midiNotes.all { it in 0..127 }) { "Sequence contains an invalid MIDI note" }
                require(step.tempoBpm in 20..300) { "Tempo must be between 20 and 300 BPM" }
            }
            is LessonStep.PlayChord -> {
                require(step.midiNotes.size >= 2) { "A chord must contain at least two notes" }
                require(step.midiNotes.all { it in 0..127 }) { "Chord contains an invalid MIDI note" }
                require(step.durationMs > 0) { "Chord duration must be positive" }
            }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
