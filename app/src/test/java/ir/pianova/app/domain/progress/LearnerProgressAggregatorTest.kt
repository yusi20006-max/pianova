package ir.pianova.app.domain.progress

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnerProgressAggregatorTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDate.of(2026, 11, 23).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun isolatesAllMetricsByLearner() {
        val result = LearnerProgressAggregator.aggregate(
            "a",
            listOf(session("a", "sa", 60_000), session("b", "sb", 900_000)),
            listOf(evaluation("a", "ea", "CORRECT"), evaluation("b", "eb", "WRONG_NOTE")),
            emptySet(), zone, now
        )
        assertEquals(60_000L, result.practiceMillis)
        assertEquals(1, result.evaluationCount)
        assertEquals(1.0, result.accuracy!!, 0.0)
    }

    @Test fun emptyLearnerHasHonestEmptyMetrics() {
        val result = LearnerProgressAggregator.aggregate("a", emptyList(), emptyList(), emptySet(), zone, now)
        assertEquals(0, result.practiceMillis)
        assertEquals(0, result.sessionCount)
        assertNull(result.accuracy)
        assertTrue(result.skills.isEmpty())
    }

    @Test fun ignoresZeroDurationAndNonTerminalEvaluationStates() {
        val result = LearnerProgressAggregator.aggregate(
            "a", listOf(session("a", "idle", 0)),
            listOf(evaluation("a", "ready", "IN_PROGRESS")),
            emptySet(), zone, now
        )
        assertEquals(0, result.sessionCount)
        assertEquals(0, result.evaluationCount)
    }

    @Test fun interruptedSessionCountsOnlyRecordedActiveDuration() {
        val interrupted = session("a", "interrupted", 12_500).copy(isInterrupted = true)
        val result = LearnerProgressAggregator.aggregate(
            "a", listOf(interrupted), emptyList(), emptySet(), zone, now
        )
        assertEquals(12_500L, result.practiceMillis)
        assertEquals(1, result.sessionCount)
    }

    @Test fun accuracyUsesOnlyTerminalPersistedEvaluations() {
        val result = LearnerProgressAggregator.aggregate(
            "a", emptyList(),
            listOf(evaluation("a", "1", "CORRECT"), evaluation("a", "2", "WRONG_NOTE"),
                evaluation("a", "3", "EXTRA_NOTE")),
            emptySet(), zone, now
        )
        assertEquals(1.0 / 3.0, result.accuracy!!, 0.0001)
    }

    @Test fun masteryNeedsIndependentEvidenceAndConfidence() {
        val attempts = listOf(
            evaluation("a", "1", "CORRECT", "s1", "pitch"),
            evaluation("a", "2", "CORRECT", "s1", "pitch"),
            evaluation("a", "3", "CORRECT", "s2", "pitch")
        )
        val result = LearnerProgressAggregator.aggregate("a", emptyList(), attempts, emptySet(), zone, now)
        assertTrue(result.skills.single().isMastered)
        val oneSession = LearnerProgressAggregator.aggregate(
            "a", emptyList(), attempts.map { it.copy(sessionId = "same") }, emptySet(), zone, now
        )
        assertFalse(oneSession.skills.single().isMastered)
        val hinted = LearnerProgressAggregator.aggregate(
            "a", emptyList(),
            attempts.mapIndexed { index, evidence ->
                if (index == 2) evidence.copy(answerRevealingHintUsed = true) else evidence
            },
            emptySet(), zone, now
        )
        assertEquals(2, hinted.skills.single().successfulAttempts)
        assertFalse(hinted.skills.single().isMastered)
    }

    @Test fun recurringMistakesAndSkillsNeedingPracticeUsePersistedEvidence() {
        val wrongOne = evaluation("a", "wrong-1", "WRONG_NOTE", "s1", "pitch").copy(receivedMidiNotes = listOf(61))
        val wrongTwo = evaluation("a", "wrong-2", "EXTRA_NOTE", "s2", "pitch").copy(receivedMidiNotes = listOf(61))
        val correct = evaluation("a", "correct", "CORRECT", "s3", "pitch")
        val result = LearnerProgressAggregator.aggregate(
            "a", emptyList(), listOf(wrongOne, wrongTwo, correct, wrongOne),
            emptySet(), zone, now
        )
        assertEquals(3, result.evaluationCount)
        assertEquals(2, result.commonMistakes.single().occurrences)
        assertEquals(60, result.commonMistakes.single().expectedMidiNote)
        assertEquals(61, result.commonMistakes.single().receivedMidiNote)
        assertTrue(result.skills.single().needsPractice)
        assertTrue(result.strengths.isEmpty())
    }

    @Test fun weeklyBucketsRespectHalfOpenDateBoundaries() {
        val monday = LocalDate.of(2026, 11, 23).atStartOfDay(zone).toInstant().toEpochMilli()
        val result = LearnerProgressAggregator.aggregate(
            "a",
            listOf(session("a", "before", 10, monday - 1), session("a", "at", 20, monday)),
            emptyList(), emptySet(), zone, now
        )
        assertEquals(20L, result.weekly.last().practiceMillis)
    }

    private fun session(owner: String, id: String, duration: Long, start: Long = now) =
        PracticeEvidence(id, owner, start, start + duration, duration)

    private fun evaluation(
        owner: String, id: String, status: String,
        session: String = "s$id", skill: String? = null
    ) = EvaluationEvidence(id, owner, session, "lesson", "step", skill, now, status, listOf(60), listOf(60))
}
