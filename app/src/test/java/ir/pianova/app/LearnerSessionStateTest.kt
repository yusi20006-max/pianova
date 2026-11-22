package ir.pianova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnerSessionStateTest {
    @Test
    fun preferencesAreNamespacedByStableLearnerId() {
        assertNotEquals(
            learnerAudiencePreferenceKey("learner-a"),
            learnerAudiencePreferenceKey("learner-b")
        )
        assertNotEquals(
            learnerRoutePreferenceKey("learner-a"),
            learnerRoutePreferenceKey("learner-b")
        )
    }

    @Test
    fun onlySafeTopLevelRoutesArePersisted() {
        assertEquals("library", learnerRouteToPersist("library"))
        assertEquals("learning", learnerRouteToPersist("learning"))
        assertNull(learnerRouteToPersist("practice"))
        assertNull(learnerRouteToPersist("lesson/first-notes/chapter-1"))
    }

    @Test
    fun unsafeOrMissingRoutesRestoreToHome() {
        assertEquals("home", learnerRouteToRestore(null))
        assertEquals("home", learnerRouteToRestore("practice"))
        assertEquals("progress", learnerRouteToRestore("progress"))
    }
}
