package ir.pianova.app.data.lesson

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.pianova.app.domain.lesson.SkillAttempt
import ir.pianova.app.domain.model.MasteryCriteria
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkillAttemptStoreTest {
    private lateinit var context: Context
    private val criteria = MasteryCriteria(
        successfulIndependentAttempts = 3,
        minimumDistinctSessions = 2
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun attemptsAndMasteryAreIsolatedByLearner() {
        val learnerA = SkillAttemptStore(context, "learner-a")
        val learnerB = SkillAttemptStore(context, "learner-b")
        listOf(attempt("a1", "session-1"), attempt("a2", "session-1"), attempt("a3", "session-2"))
            .forEach(learnerA::record)

        assertTrue(learnerA.hasMasteryFor("first-notes", "identify-keys", criteria))
        assertFalse(learnerB.hasMasteryFor("first-notes", "identify-keys", criteria))
    }

    @Test
    fun legacyAttemptsMoveToTheOnlyLearner() {
        writeLegacyAttempts()

        SkillAttemptStore.migrateLegacyAttempts(context, listOf("legacy"))
        val migrated = SkillAttemptStore(context, "legacy")

        assertTrue(migrated.hasMasteryFor("first-notes", "identify-keys", criteria))
        assertNull(context.getSharedPreferences("pianova_lesson_mastery", Context.MODE_PRIVATE)
            .getString("skill_attempts", null))
    }

    @Test
    fun legacyAttemptsAreNotSharedWhenMultipleLearnersExist() {
        writeLegacyAttempts()

        SkillAttemptStore.migrateLegacyAttempts(context, listOf("learner-a", "learner-b"))

        assertFalse(SkillAttemptStore(context, "learner-a").hasMasteryFor("first-notes", "identify-keys", criteria))
        assertFalse(SkillAttemptStore(context, "learner-b").hasMasteryFor("first-notes", "identify-keys", criteria))
        assertTrue(
            context.getSharedPreferences("pianova_lesson_mastery", Context.MODE_PRIVATE)
                .contains("skill_attempts")
        )
    }

    private fun writeLegacyAttempts() {
        val attempts = listOf(
            attempt("legacy-1", "session-1"),
            attempt("legacy-2", "session-1"),
            attempt("legacy-3", "session-2")
        )
        val encoded = Json.encodeToString(ListSerializer(SkillAttempt.serializer()), attempts)
        context.getSharedPreferences("pianova_lesson_mastery", Context.MODE_PRIVATE)
            .edit()
            .putString("skill_attempts", encoded)
            .commit()
    }

    private fun attempt(id: String, sessionId: String) = SkillAttempt(
        attemptId = id,
        sessionId = sessionId,
        lessonId = "first-notes",
        skillId = "identify-keys",
        successful = true,
        answerRevealingHintUsed = false,
        occurredAtEpochMillis = 1L
    )

    private fun clearPreferences() {
        listOf(
            "pianova_lesson_mastery",
            "pianova_lesson_mastery_learner-a",
            "pianova_lesson_mastery_learner-b",
            "pianova_lesson_mastery_legacy"
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
