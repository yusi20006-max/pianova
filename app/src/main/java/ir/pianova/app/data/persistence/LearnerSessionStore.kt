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
