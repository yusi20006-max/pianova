package ir.pianova.app.data.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnerSessionStoreTest {
    @Test
    fun unsafeLessonAndManagerRoutesReturnToLearning() {
        assertEquals("learning", safeLearnerDestination("lesson/lesson-01/chapter-01"))
        assertEquals("learning", safeLearnerDestination("family"))
        assertEquals("learning", safeLearnerDestination(null))
    }

    @Test
    fun safeTopLevelDestinationsAreRestored() {
        listOf("home", "learning", "practice", "library", "progress", "more", "settings").forEach {
            assertEquals(it, safeLearnerDestination(it))
        }
    }

    @Test
    fun deletedInactiveLearnerCannotRemainActive() {
        val currentProfiles = setOf("learner-a", "learner-c")
        assertNull(activeLearnerOrNull("learner-b", currentProfiles))
        assertEquals("learner-a", activeLearnerOrNull("learner-a", currentProfiles))
        assertNull(activeLearnerOrNull(null, currentProfiles))
    }
}
