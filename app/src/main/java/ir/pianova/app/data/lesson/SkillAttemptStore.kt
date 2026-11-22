package ir.pianova.app.data.lesson

import android.content.Context
import ir.pianova.app.domain.lesson.SkillAttempt
import ir.pianova.app.domain.lesson.hasMasteryFor
import ir.pianova.app.domain.model.MasteryCriteria
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Small, local-only attempt store for the JSON v2 cross-session mastery rule.
 * This intentionally does not introduce or expand the Room/DataStore work in #13.
 */
class SkillAttemptStore(context: Context, learnerId: String) {
    init { require(learnerId.isNotBlank()) { "learnerId must not be blank" } }

    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName(learnerId),
        Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = false }

    @Synchronized
    fun record(attempt: SkillAttempt) {
        val attempts = readAttempts()
        if (attempts.none { it.attemptId == attempt.attemptId }) {
            preferences.edit()
                .putString(ATTEMPTS_KEY, json.encodeToString(ListSerializer(SkillAttempt.serializer()), attempts + attempt))
                .apply()
        }
    }

    @Synchronized
    fun hasMasteryFor(lessonId: String, skillId: String, criteria: MasteryCriteria): Boolean =
        readAttempts()
            .asSequence()
            .filter { it.lessonId == lessonId }
            .toList()
            .hasMasteryFor(skillId, criteria)

    @Synchronized
    fun masteredSkillIds(lessonId: String, skillIds: Set<String>, criteria: MasteryCriteria): Set<String> =
        skillIds.filterTo(mutableSetOf()) { skillId -> hasMasteryFor(lessonId, skillId, criteria) }

    private fun readAttempts(): List<SkillAttempt> {
        val raw = preferences.getString(ATTEMPTS_KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SkillAttempt.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFERENCES_PREFIX = "pianova_lesson_mastery."
        private const val ATTEMPTS_KEY = "skill_attempts"

        private fun preferencesName(learnerId: String) = PREFERENCES_PREFIX + learnerId

        fun clearForLearner(context: Context, learnerId: String) {
            require(learnerId.isNotBlank())
            context.applicationContext.getSharedPreferences(
                preferencesName(learnerId),
                Context.MODE_PRIVATE
            ).edit().clear().commit()
        }
    }
}
