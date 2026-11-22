package ir.pianova.app.data.lesson

import android.content.Context
import ir.pianova.app.domain.lesson.SkillAttempt
import ir.pianova.app.domain.lesson.hasMasteryFor
import ir.pianova.app.domain.model.MasteryCriteria
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local mastery-attempt storage isolated to a single learner profile. */
class SkillAttemptStore(context: Context, learnerId: String) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName(learnerId),
        Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = false }

    init {
        require(learnerId.isNotBlank()) { "learnerId must not be blank" }
    }

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
        private const val PREFERENCES_PREFIX = "pianova_lesson_mastery_"
        private const val LEGACY_PREFERENCES_NAME = "pianova_lesson_mastery"
        private const val ATTEMPTS_KEY = "skill_attempts"
        private const val MIGRATION_DECIDED_KEY = "learner_scope_migration_decided"

        private fun preferencesName(learnerId: String): String {
            require(learnerId.isNotBlank()) { "learnerId must not be blank" }
            return PREFERENCES_PREFIX + learnerId
        }

        /**
         * Old installations stored attempts globally. Assign them only when
         * exactly one learner exists. With multiple learners, keep the legacy
         * payload quarantined rather than leaking it into another profile.
         */
        @Synchronized
        fun migrateLegacyAttempts(context: Context, learnerIds: List<String>) {
            val distinctLearnerIds = learnerIds.distinct()
            if (distinctLearnerIds.isEmpty()) return
            val legacy = context.applicationContext.getSharedPreferences(
                LEGACY_PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            if (legacy.getBoolean(MIGRATION_DECIDED_KEY, false)) return

            if (distinctLearnerIds.size == 1) {
                val learnerId = distinctLearnerIds.single()
                val raw = legacy.getString(ATTEMPTS_KEY, null)
                if (raw != null) {
                    val target = context.applicationContext.getSharedPreferences(
                        preferencesName(learnerId),
                        Context.MODE_PRIVATE
                    )
                    if (target.getString(ATTEMPTS_KEY, null) == null) {
                        target.edit().putString(ATTEMPTS_KEY, raw).apply()
                    }
                    legacy.edit().remove(ATTEMPTS_KEY).putBoolean(MIGRATION_DECIDED_KEY, true).apply()
                    return
                }
            }

            legacy.edit().putBoolean(MIGRATION_DECIDED_KEY, true).apply()
        }
    }
}
