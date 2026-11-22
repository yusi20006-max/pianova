package ir.pianova.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyOnboardingGateTest {
    @Test
    fun completedOnboardingDoesNotRestartWhenTheLastLearnerIsDeleted() {
        assertFalse(shouldShowFamilySetup(onboardingComplete = true))
    }

    @Test
    fun interruptedOnboardingCanResumeWithAnEmptyFamily() {
        assertTrue(shouldShowFamilySetup(onboardingComplete = false))
    }
}
