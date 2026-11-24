package ir.pianova.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Durable, learner-owned evidence for one evaluator outcome. */
@Entity(
    tableName = "evaluation_evidence",
    indices = [
        Index(value = ["learnerId", "occurredAtEpochMillis"]),
        Index(value = ["sessionId"]),
        Index(value = ["lessonId", "stepId"])
    ]
)
data class EvaluationEvidenceEntity(
    @PrimaryKey val evaluationId: String,
    val learnerId: String,
    val sessionId: String,
    val lessonId: String,
    val stepId: String,
    val skillId: String?,
    val occurredAtEpochMillis: Long,
    val status: String,
    /** MIDI note values encoded as comma-separated integers. */
    val expectedMidiNotesCsv: String,
    val receivedMidiNotesCsv: String,
    val answerRevealingHintUsed: Boolean
)
