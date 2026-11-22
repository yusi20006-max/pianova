package ir.pianova.app.data.persistence

import android.content.Context
import ir.pianova.app.domain.model.LessonAudience

/** Device-local settings scoped to a stable learner ID. */
class LearnerSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME, Context.MODE_PRIVATE
    )

    fun lastActiveLearnerId(): String? = preferences.getString(KEY_LAST_ACTIVE_LEARNER, null)

    fun rememberActiveLearner(learnerId: String) {
        require(learnerId.isNotBlank())
        preferences.edit().putString(KEY_LAST_ACTIVE_LEARNER, learnerId).commit()
    }

    fun forgetActiveLearner(learnerId: String) {
        if (lastActiveLearnerId() == learnerId) {
            preferences.edit().remove(KEY_LAST_ACTIVE_LEARNER).commit()
        }
    }

    fun clearForLearner(learnerId: String) {
        require(learnerId.isNotBlank())
        preferences.edit()
            .remove(audienceKey(learnerId))
            .remove(destinationKey(learnerId))
            .remove("volume.$learnerId")
            .remove("label_mode.$learnerId")
            .remove("first_midi.$learnerId")
            .apply {
                if (lastActiveLearnerId() == learnerId) remove(KEY_LAST_ACTIVE_LEARNER)
            }
            .commit()
    }

    fun audienceFor(learnerId: String, fallback: LessonAudience): LessonAudience {
        require(learnerId.isNotBlank())
        return LessonAudience.fromStorageValue(preferences.getString(audienceKey(learnerId), fallback.storageValue))
    }

    fun saveAudience(learnerId: String, audience: LessonAudience) {
        require(learnerId.isNotBlank())
        preferences.edit().putString(audienceKey(learnerId), audience.storageValue).commit()
    }

    fun volumeFor(learnerId: String): Float =
        preferences.getFloat("volume.$learnerId", 0.8f).coerceIn(0.05f, 1f)

    fun saveVolume(learnerId: String, volume: Float) {
        require(learnerId.isNotBlank() && volume in 0.05f..1f)
        preferences.edit().putFloat("volume.$learnerId", volume).apply()
    }

    fun labelModeFor(learnerId: String): Int =
        preferences.getInt("label_mode.$learnerId", 1).coerceIn(0, 2)

    fun saveLabelMode(learnerId: String, mode: Int) {
        require(learnerId.isNotBlank() && mode in 0..2)
        preferences.edit().putInt("label_mode.$learnerId", mode).apply()
    }

    fun firstMidiFor(learnerId: String): Int =
        preferences.getInt("first_midi.$learnerId", 60).coerceIn(21, 84)

    fun saveFirstMidi(learnerId: String, firstMidi: Int) {
        require(learnerId.isNotBlank() && firstMidi in 21..84)
        preferences.edit().putInt("first_midi.$learnerId", firstMidi).apply()
    }

    fun destinationFor(learnerId: String): String {
        require(learnerId.isNotBlank())
        return safeLearnerDestination(preferences.getString(destinationKey(learnerId), null))
    }

    fun saveDestination(learnerId: String, route: String) {
        require(learnerId.isNotBlank())
        preferences.edit().putString(destinationKey(learnerId), safeLearnerDestination(route)).commit()
    }

    private fun audienceKey(learnerId: String) = "audience.$learnerId"
    private fun destinationKey(learnerId: String) = "destination.$learnerId"

    companion object {
        private const val PREFERENCES_NAME = "pianova_learner_sessions"
        private const val KEY_LAST_ACTIVE_LEARNER = "last_active_learner_id"
    }
}

/** Lesson evaluation is ephemeral; manager routes are never restored as learner sessions. */
internal fun safeLearnerDestination(route: String?): String = when (route) {
    "home", "learning", "practice", "library", "progress", "more", "settings" -> route
    else -> "learning"
}

internal fun activeLearnerOrNull(activeLearnerId: String?, learnerIds: Set<String>): String? =
    activeLearnerId?.takeIf { it in learnerIds }
