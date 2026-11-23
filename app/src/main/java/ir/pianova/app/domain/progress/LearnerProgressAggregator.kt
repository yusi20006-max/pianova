package ir.pianova.app.domain.progress

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Persistable outcome of one completed or interrupted evaluation attempt. */
data class EvaluationEvidence(
    val evaluationId: String,
    val learnerId: String,
    val sessionId: String,
    val lessonId: String,
    val stepId: String,
    val skillId: String?,
    val occurredAtEpochMillis: Long,
    val status: String,
    val expectedMidiNotes: List<Int>,
    val receivedMidiNotes: List<Int>,
    val answerRevealingHintUsed: Boolean = false
) {
    init {
        require(evaluationId.isNotBlank() && learnerId.isNotBlank())
        require(sessionId.isNotBlank() && lessonId.isNotBlank() && stepId.isNotBlank())
        require(occurredAtEpochMillis >= 0)
    }

    val isSuccessful: Boolean get() = status == "CORRECT"
}

/** Only foreground, user-active time is eligible for practice totals. */
data class PracticeEvidence(
    val sessionId: String,
    val learnerId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val activeDurationMillis: Long,
    val isInterrupted: Boolean = false
) {
    init {
        require(sessionId.isNotBlank() && learnerId.isNotBlank())
        require(startedAtEpochMillis >= 0 && activeDurationMillis >= 0)
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis)
    }
}

data class SkillMasteryEvidence(
    val skillId: String,
    val attempts: Int,
    val successfulAttempts: Int,
    val independentSessions: Int,
    val confidence: Double,
    val lastAssessedAtEpochMillis: Long?
) {
    val isMastered: Boolean
        get() = attempts >= 3 && successfulAttempts >= 3 &&
            independentSessions >= 2 && confidence >= 0.8
}

data class PeriodProgress(
    val periodStartEpochDay: Long,
    val practiceMillis: Long,
    val sessionCount: Int,
    val completedEvaluations: Int,
    val correctEvaluations: Int
) {
    val accuracy: Double?
        get() = if (completedEvaluations == 0) null
        else correctEvaluations.toDouble() / completedEvaluations
}

data class LearnerProgressSnapshot(
    val learnerId: String,
    val practiceMillis: Long,
    val sessionCount: Int,
    val completedLessonCount: Int,
    val evaluationCount: Int,
    val accuracy: Double?,
    val skills: List<SkillMasteryEvidence>,
    val weekly: List<PeriodProgress>,
    val monthly: List<PeriodProgress>
)

/**
 * Pure aggregation over records already filtered to one learner by the caller.
 * No placeholder values are generated; absent evidence remains null or empty.
 */
object LearnerProgressAggregator {
    fun aggregate(
        learnerId: String,
        sessions: List<PracticeEvidence>,
        evaluations: List<EvaluationEvidence>,
        completedLessonIds: Set<String>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMillis: Long = System.currentTimeMillis()
    ): LearnerProgressSnapshot {
        require(learnerId.isNotBlank())
        val learnerSessions = sessions.filter { it.learnerId == learnerId }
        val learnerEvaluations = evaluations.filter { it.learnerId == learnerId }
        val validSessions = learnerSessions.filter { it.activeDurationMillis > 0 }
        val completed = learnerEvaluations.filter { it.status in TERMINAL_STATUSES }
        val correct = completed.count { it.isSuccessful }
        val skills = learnerEvaluations.filter { !it.skillId.isNullOrBlank() }
            .groupBy { it.skillId!! }
            .map { (skillId, records) ->
                val valid = records.distinctBy { it.evaluationId }
                val successful = valid.filter { it.isSuccessful && !it.answerRevealingHintUsed }
                val denominator = valid.size
                SkillMasteryEvidence(
                    skillId = skillId,
                    attempts = valid.size,
                    successfulAttempts = successful.size,
                    independentSessions = successful.map { it.sessionId }.distinct().size,
                    confidence = if (denominator == 0) 0.0 else successful.size.toDouble() / denominator,
                    lastAssessedAtEpochMillis = valid.maxOfOrNull { it.occurredAtEpochMillis }
                )
            }.sortedBy { it.skillId }

        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val weekly = periodBuckets(validSessions, completed, today, zoneId, weekly = true)
        val monthly = periodBuckets(validSessions, completed, today, zoneId, weekly = false)

        return LearnerProgressSnapshot(
            learnerId = learnerId,
            practiceMillis = validSessions.sumOf { it.activeDurationMillis },
            sessionCount = validSessions.size,
            completedLessonCount = completedLessonIds.size,
            evaluationCount = completed.size,
            accuracy = if (completed.isEmpty()) null else correct.toDouble() / completed.size,
            skills = skills,
            weekly = weekly,
            monthly = monthly
        )
    }

    private fun periodBuckets(
        sessions: List<PracticeEvidence>,
        evaluations: List<EvaluationEvidence>,
        today: LocalDate,
        zoneId: ZoneId,
        weekly: Boolean
    ): List<PeriodProgress> {
        val starts = (0L..(if (weekly) 7L else 5L)).map { offset ->
            if (weekly) today.minusWeeks(offset).with(java.time.DayOfWeek.MONDAY)
            else today.minusMonths(offset).withDayOfMonth(1)
        }.distinct().sorted()
        return starts.map { start ->
            val end = if (weekly) start.plusWeeks(1) else start.plusMonths(1)
            val from = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val until = end.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val periodSessions = sessions.filter {
                it.startedAtEpochMillis >= from && it.startedAtEpochMillis < until && it.activeDurationMillis > 0
            }
            val periodEvaluations = evaluations.filter {
                it.occurredAtEpochMillis >= from && it.occurredAtEpochMillis < until &&
                    it.status in TERMINAL_STATUSES
            }
            PeriodProgress(
                periodStartEpochDay = start.toEpochDay(),
                practiceMillis = periodSessions.sumOf { it.activeDurationMillis },
                sessionCount = periodSessions.size,
                completedEvaluations = periodEvaluations.size,
                correctEvaluations = periodEvaluations.count { it.isSuccessful }
            )
        }
    }

    private val TERMINAL_STATUSES = setOf("CORRECT", "WRONG_NOTE", "MISSING_NOTE", "EXTRA_NOTE")
}
