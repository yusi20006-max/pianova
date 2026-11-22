package ir.pianova.app

/**
 * Onboarding is a one-time setup state, not a proxy for learner count.
 * A family with zero learners must still be able to enter the app and add
 * profiles later from family management.
 */
internal fun shouldShowFamilySetup(onboardingComplete: Boolean): Boolean =
    !onboardingComplete
