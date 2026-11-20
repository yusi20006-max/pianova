package ir.pianova.app.domain.lesson

import ir.pianova.app.domain.model.MasteryCriteria
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillMasteryTest {
    private val criteria = MasteryCriteria(
        successfulIndependentAttempts = 3,
        minimumDistinctSessions = 2
    )

    @Test
    fun requiresThreeSuccessfulAttemptsAcrossAtLeastTwoSessions() {
        val attempts = listOf(
            attempt("a1", "s1"),
            attempt("a2", "s1"),
            attempt("a3", "s2")
        )
        assertTrue(attempts.hasMasteryFor("identify-white-keys", criteria))
    }

    @Test
    fun threeSuccessesInOneSessionDoNotGrantMastery() {
        val attempts = listOf(
            attempt("a1", "s1"),
            attempt("a2", "s1"),
            attempt("a3", "s1")
        )
        assertFalse(attempts.hasMasteryFor("identify-white-keys", criteria))
    }

    @Test
    fun failedOrAnswerRevealingAttemptsDoNotCount() {
        val attempts = listOf(
            attempt("a1", "s1"),
            attempt("a2", "s2", answerRevealingHintUsed = true),
            attempt("a3", "s2", successful = false),
            attempt("a4", "s3")
        )
        assertFalse(attempts.hasMasteryFor("identify-white-keys", criteria))
    }

    @Test
    fun duplicateAttemptIdsAreCountedOnlyOnce() {
        val attempts = listOf(
            attempt("same", "s1"),
            attempt("same", "s2"),
            attempt("a3", "s3")
        )
        assertFalse(attempts.hasMasteryFor("identify-white-keys", criteria))
    }

    @Test
    fun lessonMasteryRequiresEveryRequiredSkill() {
        val attempts = listOf(
            attempt("w1", "s1", skillId = "white"),
            attempt("w2", "s2", skillId = "white"),
            attempt("w3", "s2", skillId = "white"),
            attempt("b1", "s1", skillId = "black"),
            attempt("b2", "s2", skillId = "black")
        )
        assertFalse(attempts.hasLessonMastery(setOf("white", "black"), criteria))
    }

    private fun attempt(
        id: String,
        session: String,
        skillId: String = "identify-white-keys",
        successful: Boolean = true,
        answerRevealingHintUsed: Boolean = false
    ) = SkillAttempt(
        attemptId = id,
        sessionId = session,
        lessonId = "first-notes",
        skillId = skillId,
        successful = successful,
        answerRevealingHintUsed = answerRevealingHintUsed,
        occurredAtEpochMillis = 1L
    )
}
