package ir.pianova.app

import androidx.compose.runtime.staticCompositionLocalOf

val LocalActiveLearnerId = staticCompositionLocalOf<String?> { null }

internal const val ACTIVE_LEARNER_ID_KEY = "active_learner_id"

internal fun learnerAudiencePreferenceKey(learnerId: String): String =
    "lesson_audience_$learnerId"

internal fun learnerRoutePreferenceKey(learnerId: String): String =
    "last_route_$learnerId"

private val restorableLearnerRoutes = setOf(
    "home",
    "learning",
    "library",
    "progress",
    "more",
    "settings"
)

/** Only stable top-level destinations are restored; live lesson/practice sessions restart safely. */
internal fun learnerRouteToPersist(route: String?): String? =
    route?.takeIf { it in restorableLearnerRoutes }

internal fun learnerRouteToRestore(route: String?): String =
    learnerRouteToPersist(route) ?: "home"
