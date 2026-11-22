package ir.pianova.app.data.lesson

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.pianova.app.domain.lesson.SkillAttempt
import ir.pianova.app.domain.model.MasteryCriteria
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkillAttemptStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SkillAttemptStore.clearForLearner(context, "learner-a")
        SkillAttemptStore.clearForLearner(context, "learner-b")
        context.getSharedPreferences("pianova_lesson_mastery", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        SkillAttemptStore.clearForLearner(context, "learner-a")
        SkillAttemptStore.clearForLearner(context, "learner-b")
        context.getSharedPreferences("pianova_lesson_mastery", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun legacyMasteryMigratesWhenExactlyOneLearnerExists() {
        val criteria = MasteryCriteria(successfulIndependentAttempts = 3, minimumDistinctSessions = 2)
        val legacyAttempts = listOf(
            SkillAttempt("old-1", "session-1", "lesson-1", "skill-c4", true, false, 1),
            SkillAttempt("old-2", "session-1", "lesson-1", "skill-c4", true, false, 2),
            SkillAttempt("old-3", "session-2", "lesson-1", "skill-c4", true, false, 3)
        )
        val legacy = context.getSharedPreferences("pianova_lesson_mastery", Context.MODE_PRIVATE)
        legacy.edit().putString(
            "skill_attempts",
            Json.encodeToString(ListSerializer(SkillAttempt.serializer()), legacyAttempts)
        ).commit()

        SkillAttemptStore.migrateLegacyAttempts(context, listOf("learner-a"))

        assertTrue(SkillAttemptStore(context, "learner-a").hasMasteryFor("lesson-1", "skill-c4", criteria))
        assertNull(legacy.getString("skill_attempts", null))
    }

    @Test
    fun legacyMasteryRemainsQuarantinedWhenMultipleLearnersExist() {
        val legacyAttempts = listOf(
            SkillAttempt("old-1", "session-1", "lesson-1", "skill-c4", true, false, 1)
        )
        val raw = Json.encodeToString(ListSerializer(SkillAttempt.serializer()), legacyAttempts)
        val legacy = context.getSharedPreferences("pianova_lesson_mastery", Context.MODE_PRIVATE)
        legacy.edit().putString("skill_attempts", raw).commit()

        SkillAttemptStore.migrateLegacyAttempts(context, listOf("learner-a", "learner-b"))

        assertFalse(SkillAttemptStore(context, "learner-a").hasMasteryFor("lesson-1", "skill-c4", MasteryCriteria()))
        assertFalse(SkillAttemptStore(context, "learner-b").hasMasteryFor("lesson-1", "skill-c4", MasteryCriteria()))
        assertEquals(raw, legacy.getString("skill_attempts", null))
    }

    @Test
    fun masteryAttemptsAreIsolatedBetweenLearners() {
        val first = SkillAttemptStore(context, "learner-a")
        val second = SkillAttemptStore(context, "learner-b")
        val criteria = MasteryCriteria(successfulIndependentAttempts = 3, minimumDistinctSessions = 2)

        listOf(
            SkillAttempt("a1", "session-1", "lesson-1", "skill-c4", true, false, 1),
            SkillAttempt("a2", "session-1", "lesson-1", "skill-c4", true, false, 2),
            SkillAttempt("a3", "session-2", "lesson-1", "skill-c4", true, false, 3)
        ).forEach(first::record)

        assertTrue(first.hasMasteryFor("lesson-1", "skill-c4", criteria))
        assertFalse(second.hasMasteryFor("lesson-1", "skill-c4", criteria))
        assertTrue(second.masteredSkillIds("lesson-1", setOf("skill-c4"), criteria).isEmpty())
    }
}
