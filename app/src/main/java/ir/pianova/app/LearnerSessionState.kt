package ir.pianova.app

import androidx.compose.runtime.staticCompositionLocalOf

val LocalActiveLearnerId = staticCompositionLocalOf<String?> { null }

internal const val ACTIVE_LEARNER_ID_KEY = "active_learner_id"
private const val LEGACY_AUDIENCE_MIGRATED_KEY = "legacy_lesson_audience_migrated"

internal fun ensureLearnerAudiencePreference(
    preferences: android.content.SharedPreferences,
    learnerId: String,
    defaultValue: String = "children"
): String {
    val key = learnerAudiencePreferenceKey(learnerId)
    preferences.getString(key, null)?.let { return it }

    val legacyAlreadyAssigned = preferences.getBoolean(LEGACY_AUDIENCE_MIGRATED_KEY, false)
    val value = if (legacyAlreadyAssigned) {
        defaultValue
    } else {
        preferences.getString("lesson_audience", defaultValue) ?: defaultValue
    }
    preferences.edit()
        .putString(key, value)
        .putBoolean(LEGACY_AUDIENCE_MIGRATED_KEY, true)
        .apply()
    return value
}

internal fun learnerAudiencePreferenceKey(learnerId: String): String =
    "lesson_audience_$learnerId"

internal fun learnerRoutePreferenceKey(learnerId: String): String =
    "last_route_$learnerId"

internal fun learnerPianoVolumePreferenceKey(learnerId: String): String =
    "volume_$learnerId"

internal fun learnerPianoRangePreferenceKey(learnerId: String): String =
    "first_midi_$learnerId"

internal fun learnerPianoLabelModePreferenceKey(learnerId: String): String =
    "label_mode_$learnerId"

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
