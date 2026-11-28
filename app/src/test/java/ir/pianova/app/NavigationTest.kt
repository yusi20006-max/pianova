package ir.pianova.app

import ir.pianova.app.data.persistence.activeLearnerOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {

    @Test fun safeRestorableRootsRestored() {
        listOf("home", "learning", "paths", "practice", "library", "progress", "more", "settings").forEach {
            assertEquals(it, safeLearnerDestination(it))
        }
    }

    @Test fun nestedAndUnknownRoutesDoNotBreakNavStack() {
        // nested lesson/practice routes are not restored as-is; fall back safely
        assertEquals("learning", safeLearnerDestination("lesson/lesson-01/chapter-1"))
        assertEquals("learning", safeLearnerDestination("falling-notes/twinkle-twinkle"))
        assertEquals("learning", safeLearnerDestination("family-backup"))
        assertEquals("learning", safeLearnerDestination("family-reports"))
        assertEquals("learning", safeLearnerDestination("family"))
        assertEquals("learning", safeLearnerDestination(null))
        assertEquals("learning", safeLearnerDestination(""))
        assertEquals("learning", safeLearnerDestination("unknown/route"))
    }

    @Test fun noRestorableManagerRoutes() {
        // manager overlays are not NavHost destinations restored across restarts
        assertFalse(safeLearnerDestination("manager") in setOf("manager"))
    }

    @Test fun familySetupFlag() {
        assertTrue(shouldShowFamilySetup(onboardingComplete = false))
        assertFalse(shouldShowFamilySetup(onboardingComplete = true))
    }

    @Test fun activeLearnerOrNullGuardsMissingLearner() {
        assertEquals("a", activeLearnerOrNull("a", setOf("a", "b")))
        assertEquals(null, activeLearnerOrNull("z", setOf("a", "b")))
        assertEquals(null, activeLearnerOrNull(null, setOf("a")))
    }
}
