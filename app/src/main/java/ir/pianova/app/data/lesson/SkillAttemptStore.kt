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

        /**
         * The previous app stored mastery attempts without an owner. Assign them
         * only when exactly one learner exists; otherwise leave them quarantined.
         */
        @Synchronized
        fun migrateLegacyAttempts(context: Context, learnerIds: List<String>) {
            val distinctIds = learnerIds.distinct()
            if (distinctIds.isEmpty()) return

            val appContext = context.applicationContext
            val legacy = appContext.getSharedPreferences("pianova_lesson_mastery", Context.MODE_PRIVATE)
            if (legacy.getBoolean(MIGRATION_DECIDED_KEY, false)) return

            if (distinctIds.size == 1) {
                val raw = legacy.getString(ATTEMPTS_KEY, null)
                if (raw != null) {
                    val target = appContext.getSharedPreferences(
                        preferencesName(distinctIds.single()), Context.MODE_PRIVATE
                    )
                    val serializer = ListSerializer(SkillAttempt.serializer())
                    val oldAttempts: List<SkillAttempt>? = runCatching {
                        jsonForMigration.decodeFromString(serializer, raw)
                    }.getOrNull()
                    if (oldAttempts != null) {
                        val existing: List<SkillAttempt> = target.getString(ATTEMPTS_KEY, null)?.let { saved ->
                            runCatching { jsonForMigration.decodeFromString(serializer, saved) }.getOrNull()
                        }.orEmpty()
                        val merged = (existing + oldAttempts).distinctBy(SkillAttempt::attemptId)
                        target.edit()
                            .putString(ATTEMPTS_KEY, jsonForMigration.encodeToString(serializer, merged))
                            .commit()
                        legacy.edit().remove(ATTEMPTS_KEY).putBoolean(MIGRATION_DECIDED_KEY, true).commit()
                        return
                    }
                }
            }

            // With multiple learners, ownership is ambiguous. Keep the legacy
            // payload untouched and mark the decision so it cannot leak later.
            legacy.edit().putBoolean(MIGRATION_DECIDED_KEY, true).commit()
        }

        private val jsonForMigration = Json { ignoreUnknownKeys = false }
        private const val MIGRATION_DECIDED_KEY = "learner_scope_migration_decided"

        fun clearForLearner(context: Context, learnerId: String) {
            require(learnerId.isNotBlank())
            context.applicationContext.getSharedPreferences(
                preferencesName(learnerId),
                Context.MODE_PRIVATE
            ).edit().clear().commit()
        }
    }
}
