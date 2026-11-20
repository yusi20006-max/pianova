package ir.pianova.app.domain.lesson

import ir.pianova.app.domain.model.MasteryCriteria

/**
 * A completed evaluation attempt. Persistence is deliberately supplied by the
 * caller so this rule can be tested independently of any storage technology.
 */
data class SkillAttempt(
    val attemptId: String,
    val sessionId: String,
    val skillId: String,
    val successful: Boolean,
    val answerRevealingHintUsed: Boolean,
    val occurredAtEpochMillis: Long
)

fun Iterable<SkillAttempt>.hasMasteryFor(
    skillId: String,
    criteria: MasteryCriteria
): Boolean {
    require(skillId.isNotBlank()) { "skillId must not be blank" }
    val independentSuccesses = asSequence()
        .filter { it.skillId == skillId && it.successful && !it.answerRevealingHintUsed }
        .filter { it.attemptId.isNotBlank() && it.sessionId.isNotBlank() }
        .distinctBy(SkillAttempt::attemptId)
        .toList()

    return independentSuccesses.size >= criteria.successfulIndependentAttempts &&
        independentSuccesses.map(SkillAttempt::sessionId).distinct().size >= criteria.minimumDistinctSessions
}

fun Iterable<SkillAttempt>.hasLessonMastery(
    requiredSkillIds: Set<String>,
    criteria: MasteryCriteria
): Boolean = requiredSkillIds.isNotEmpty() &&
    requiredSkillIds.all { skillId -> hasMasteryFor(skillId, criteria) }
