package ir.pianova.app.data.lesson

import ir.pianova.app.domain.model.Chapter
import ir.pianova.app.domain.model.CompletionCriteria
import ir.pianova.app.domain.model.Lesson
import ir.pianova.app.domain.model.LessonPedagogy
import ir.pianova.app.domain.model.LessonStep
import ir.pianova.app.domain.model.MasteryCriteria
import ir.pianova.app.domain.model.ScaffoldingLevel
import ir.pianova.app.domain.model.StepScaffolding
import ir.pianova.app.domain.model.VisualGuidanceSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        try {
            val root = json.parseToJsonElement(source).jsonObject
            val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull
                ?: throw LessonFormatException("Lesson schemaVersion is missing or invalid")
            val lesson = when (version) {
                1 -> json.decodeFromString<Lesson>(source).also(::validateV1)
                2 -> json.decodeFromJsonElement<LessonV2Document>(root).toDomain().also(::validateV2)
                else -> throw LessonFormatException("Unsupported lesson schema version: $version")
            }
            return lesson
        } catch (error: LessonFormatException) {
            throw error
        } catch (error: SerializationException) {
            throw LessonFormatException("Lesson JSON is malformed or has an unsupported structure", error)
        } catch (error: IllegalArgumentException) {
            throw LessonFormatException(error.message ?: "Lesson content failed validation", error)
        }
    }

    private fun validateV1(lesson: Lesson) {
        require(lesson.schemaVersion == 1) { "Expected lesson schema version 1" }
        validateCommon(lesson)
    }

    private fun validateV2(lesson: Lesson) {
        require(lesson.schemaVersion == 2) { "Expected lesson schema version 2" }
        validateCommon(lesson)
        require(lesson.language == "fa-IR") { "Version 2 language must be an explicit locale such as fa-IR" }
        val pedagogy = requireNotNull(lesson.pedagogy) { "Version 2 pedagogy is required" }
        require(pedagogy.teacherPersonality == "adaptive") { "Unsupported teacherPersonality" }
        require(pedagogy.learningPace == "adaptive") { "Unsupported learningPace" }
        require(pedagogy.notationProgression == "gradual") { "Unsupported notationProgression" }
        val guidance = pedagogy.visualGuidance
        require(guidance.defaultLevel in 1..3) { "defaultLevel must be between 1 and 3" }
        require(guidance.suggestMoreHelpAfterConsecutiveErrors > 0) { "Error suggestion threshold must be positive" }
        require(guidance.suggestLessHelpAfterConsecutiveSuccesses > 0) { "Success suggestion threshold must be positive" }
        require(guidance.inactivitySuggestionSeconds > 0) { "Inactivity threshold must be positive" }

        val mastery = requireNotNull(lesson.masteryCriteria) { "Version 2 masteryCriteria is required" }
        require(mastery.successfulIndependentAttempts == 3) { "Mastery requires exactly three independent successful attempts" }
        require(mastery.minimumDistinctSessions == 2) { "Mastery requires at least two distinct sessions" }

        lesson.chapters.flatMap { it.steps }.forEach { step ->
            require(!step.displayText.isNullOrBlank()) { "Step ${step.id} requires displayText" }
            require(!step.narrationText.isNullOrBlank()) { "Step ${step.id} requires narrationText" }
            val skillId = step.skillId
            if (skillId != null) {
                require(skillId.isNotBlank()) { "skillId must not be blank" }
                requireNotNull(step.scaffolding) { "Assessable step ${step.id} requires three scaffolding levels" }
            }
            if (step is LessonStep.PlayNote) {
                require(step.acceptedPitchClasses.all { it in 0..11 }) {
                    "acceptedPitchClasses must contain pitch classes from 0 to 11"
                }
                if (step.skillId != null) require(step.acceptedPitchClasses.isNotEmpty()) {
                    "Assessable play_note step ${step.id} must define acceptedPitchClasses"
                }
            }
            step.scaffolding?.let { scaffold ->
                listOf(scaffold.level1, scaffold.level2, scaffold.level3).forEach { level ->
                    require(level.hintText.isNotBlank()) { "Scaffolding hintText must not be blank" }
                }
            }
        }
        val skillIds = lesson.chapters.flatMap { it.steps }.mapNotNull { it.skillId }.toSet()
        require(skillIds.isNotEmpty()) { "Version 2 lesson must define at least one assessable skill" }
    }

    private fun validateCommon(lesson: Lesson) {
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
                require(step.midiNotes.all { it in 0..127 }) { "Listen step ${step.id} contains an invalid MIDI note" }
                require(step.noteDurationMs > 0) { "Listen step ${step.id} note duration must be positive" }
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
        const val CURRENT_SCHEMA_VERSION = 2
        const val LEGACY_SCHEMA_VERSION = 1
    }
}

@Serializable
private data class LessonV2Document(
    val schemaVersion: Int,
    val id: String,
    val title: String,
    val description: String? = null,
    val language: String,
    val pedagogy: LessonPedagogy,
    val completionCriteria: CompletionCriteria = CompletionCriteria(),
    val masteryCriteria: MasteryCriteria,
    val chapters: List<LessonV2Chapter>
) {
    fun toDomain() = Lesson(
        schemaVersion = schemaVersion,
        id = id,
        title = title,
        description = description,
        chapters = chapters.map { it.toDomain() },
        completionCriteria = completionCriteria,
        language = language,
        pedagogy = pedagogy,
        masteryCriteria = masteryCriteria
    )
}

@Serializable
private data class LessonV2Chapter(
    val id: String,
    val title: String,
    val description: String? = null,
    val steps: List<LessonV2Step>
) {
    fun toDomain() = Chapter(id, title, description, steps.map { it.toDomain() })
}

@Serializable
private data class LessonV2Step(
    val type: String,
    val id: String,
    val title: String,
    val displayText: String,
    val narrationText: String,
    val skillId: String? = null,
    val scaffolding: StepScaffolding? = null,
    val body: String? = null,
    val prompt: String? = null,
    val audioAsset: String? = null,
    val midiNote: Int? = null,
    val midiNotes: List<Int>? = null,
    val durationMs: Long? = null,
    val noteDurationMs: Long? = null,
    val repetitionCount: Int? = null,
    val tempoBpm: Int? = null
) {
    fun toDomain(): LessonStep {
        return when (type) {
            "explanation" -> LessonStep.Explanation(
                id, title, body ?: throw LessonFormatException("Explanation step $id requires body"),
                displayText, narrationText, skillId, scaffolding
            )
            "listen" -> LessonStep.Listen(
                id, title, audioAsset, prompt, midiNotes.orEmpty(), noteDurationMs ?: 700L,
                displayText, narrationText, skillId, scaffolding
            )
            "play_note" -> LessonStep.PlayNote(
                id, title, midiNote ?: throw LessonFormatException("Play-note step $id requires midiNote"),
                durationMs ?: 800L, repetitionCount ?: 1, acceptedPitchClasses.orEmpty(), displayText, narrationText, skillId, scaffolding
            )
            "play_sequence" -> LessonStep.PlaySequence(
                id, title, midiNotes ?: throw LessonFormatException("Sequence step $id requires midiNotes"),
                tempoBpm ?: 80, displayText, narrationText, skillId, scaffolding
            )
            "play_chord" -> LessonStep.PlayChord(
                id, title, midiNotes ?: throw LessonFormatException("Chord step $id requires midiNotes"),
                durationMs ?: 1200L, displayText, narrationText, skillId, scaffolding
            )
            else -> throw LessonFormatException("Unknown lesson step type: $type")
        }
    }
}
