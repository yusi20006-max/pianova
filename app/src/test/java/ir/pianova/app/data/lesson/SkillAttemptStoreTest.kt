package ir.pianova.app.data.lesson

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.pianova.app.domain.lesson.SkillAttempt
import ir.pianova.app.domain.model.MasteryCriteria
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    }

    @After
    fun tearDown() {
        SkillAttemptStore.clearForLearner(context, "learner-a")
        SkillAttemptStore.clearForLearner(context, "learner-b")
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
