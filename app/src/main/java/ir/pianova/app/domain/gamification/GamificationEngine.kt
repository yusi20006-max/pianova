package ir.pianova.app.domain.gamification

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Domain rules for rewarding learning activity. State is immutable and caller-owned. */
object GamificationEngine {
    const val DAILY_PRACTICE_TARGET_MINUTES = 5
    const val DAILY_LESSON_TARGET = 1
    const val DAILY_MISSION_REWARD_XP = 15
    const val REPEATED_LESSON_COMPLETION_CAP_PER_DAY = 1

    val levels = listOf(
        SkillLevel("beginner", "نوازنده تازه‌کار", 0),
        SkillLevel("steady", "نوازنده کوشا", 100),
        SkillLevel("developing", "نوازنده در حال پیشرفت", 300),
        SkillLevel("skilled", "نوازنده ماهر", 600),
        SkillLevel("advanced", "پیانیست پیشرفته", 1000),
        SkillLevel("professional", "پیانیست حرفه‌ای", 1600)
    )

    val missions = listOf(
        DailyMissionDefinition("practice_5_minutes", "۵ دقیقه تمرین", MissionMetric.PRACTICE_MINUTES, 5),
        DailyMissionDefinition("complete_1_lesson", "تکمیل یک درس", MissionMetric.COMPLETED_LESSONS, 1)
    )

    val achievements = listOf(
        AchievementDefinition("first_note", "اولین نت", "اولین نت را نواختی"),
        AchievementDefinition("first_lesson", "اولین درس", "اولین درس را کامل کردی"),
        AchievementDefinition("practice_streak_7", "استریک ۷ روزه", "هفت روز پیاپی تمرین کردی")
    )

    fun levelFor(totalXp: Int): SkillLevel {
        require(totalXp >= 0)
        return levels.last { totalXp >= it.minimumXp }
    }

    fun nextLevel(totalXp: Int): SkillLevel? {
        require(totalXp >= 0)
        return levels.firstOrNull { it.minimumXp > totalXp }
    }

    /** Applies one activity and returns updated state plus XP/achievement deltas. */
    fun apply(state: GamificationState, activity: Activity, date: LocalDate): GamificationResult {
        require(activity.practiceMinutes >= 0)
        require(activity.lessonId == null || activity.lessonId.isNotBlank())
        require(activity.practiceMinutes == 0 || activity.type == ActivityType.PRACTICE)
        val day = date.toEpochDay()
        var updated = state
        var xpEarned = 0
        val unlocked = mutableListOf<String>()

        fun award(amount: Int) {
            xpEarned += amount
            updated = updated.copy(totalXp = updated.totalXp + amount)
        }
        fun unlock(id: String) {
            if (id !in updated.unlockedAchievementIds) {
                updated = updated.copy(unlockedAchievementIds = updated.unlockedAchievementIds + id)
                unlocked += id
            }
        }

        when (activity.type) {
            ActivityType.NOTE_PLAYED -> {
                if (!updated.hasPlayedFirstNote) {
                    updated = updated.copy(hasPlayedFirstNote = true)
                    unlock("first_note")
                }
            }
            ActivityType.EXERCISE_COMPLETED -> award(20)
            ActivityType.PRACTICE -> {
                if (activity.practiceMinutes > 0) {
                    updated = updateStreak(updated, day)
                    val totalPractice = (updated.practiceMinutesByEpochDay[day] ?: 0) + activity.practiceMinutes
                    updated = updated.copy(practiceMinutesByEpochDay = updated.practiceMinutesByEpochDay + (day to totalPractice))
                    val completedBefore = updated.completedMissionIdsByEpochDay[day].orEmpty()
                    val completedNow = mutableSetOf<String>()
                    if (totalPractice >= DAILY_PRACTICE_TARGET_MINUTES && "practice_5_minutes" !in completedBefore) {
                        completedNow += "practice_5_minutes"
                        award(DAILY_MISSION_REWARD_XP)
                    }
                    updated = addCompletedMissions(updated, day, completedNow)
                    if (totalPractice >= DAILY_PRACTICE_TARGET_MINUTES && !updated.hasPracticeMissionEverCompleted) {
                        updated = updated.copy(hasPracticeMissionEverCompleted = true)
                    }
                }
            }
            ActivityType.LESSON_COMPLETED -> {
                val lessonId = requireNotNull(activity.lessonId) { "lessonId is required for lesson completion" }
                val countToday = updated.lessonCompletionCountByEpochDay[lessonKey(day, lessonId)] ?: 0
                if (countToday < REPEATED_LESSON_COMPLETION_CAP_PER_DAY) {
                    award(50)
                    updated = updated.copy(
                        lessonCompletionCountByEpochDay = updated.lessonCompletionCountByEpochDay + (lessonKey(day, lessonId) to countToday + 1)
                    )
                }
                val completedBefore = updated.completedMissionIdsByEpochDay[day].orEmpty()
                if ("complete_1_lesson" !in completedBefore) {
                    award(DAILY_MISSION_REWARD_XP)
                    updated = addCompletedMissions(updated, day, setOf("complete_1_lesson"))
                }
                if (!updated.hasCompletedFirstLesson) {
                    updated = updated.copy(hasCompletedFirstLesson = true)
                    unlock("first_lesson")
                }
            }
            ActivityType.PERFECT_COMPLETION -> award(50)
        }

        if (updated.currentStreakDays >= 7) unlock("practice_streak_7")
        return GamificationResult(updated, xpEarned, unlocked, levelFor(state.totalXp), levelFor(updated.totalXp))
    }

    private fun updateStreak(state: GamificationState, epochDay: Long): GamificationState {
        val previous = state.lastPracticeEpochDay
        val streak = when {
            previous == null -> 1
            previous == epochDay -> state.currentStreakDays
            previous == epochDay - 1 -> state.currentStreakDays + 1
            previous < epochDay -> 1
            else -> state.currentStreakDays
        }
        val best = maxOf(state.bestStreakDays, streak)
        return state.copy(
            lastPracticeEpochDay = if (previous == null || epochDay > previous) epochDay else previous,
            currentStreakDays = streak,
            bestStreakDays = best
        )
    }

    private fun lessonDayPrefix(epochDay: Long) = "$epochDay|"

    private fun lessonKey(epochDay: Long, lessonId: String) = "$epochDay|$lessonId"

    private fun addCompletedMissions(state: GamificationState, epochDay: Long, newIds: Set<String>): GamificationState {
        val prior = state.completedMissionIdsByEpochDay[epochDay].orEmpty()
        return state.copy(completedMissionIdsByEpochDay = state.completedMissionIdsByEpochDay + (epochDay to (prior + newIds)))
    }

    fun missionProgress(state: GamificationState, date: LocalDate): List<DailyMissionProgress> {
        val day = date.toEpochDay()
        val practice = state.practiceMinutesByEpochDay[day] ?: 0
        val lessonCount = state.lessonCompletionCountByEpochDay.keys.count { it.startsWith(lessonDayPrefix(day)) }
        val completed = state.completedMissionIdsByEpochDay[day].orEmpty()
        return missions.map { mission ->
            val progress = when (mission.metric) {
                MissionMetric.PRACTICE_MINUTES -> practice
                MissionMetric.COMPLETED_LESSONS -> lessonCount
            }.coerceAtMost(mission.target)
            DailyMissionProgress(mission, progress, mission.target, mission.id in completed)
        }
    }
}

@Serializable
data class SkillLevel(val id: String, val titleFa: String, val minimumXp: Int)

@Serializable
data class DailyMissionDefinition(val id: String, val titleFa: String, val metric: MissionMetric, val target: Int)

@Serializable
enum class MissionMetric { PRACTICE_MINUTES, COMPLETED_LESSONS }

@Serializable
data class DailyMissionProgress(val mission: DailyMissionDefinition, val progress: Int, val target: Int, val completed: Boolean)

@Serializable
data class AchievementDefinition(val id: String, val titleFa: String, val descriptionFa: String)

@Serializable
enum class ActivityType { NOTE_PLAYED, EXERCISE_COMPLETED, PRACTICE, LESSON_COMPLETED, PERFECT_COMPLETION }

@Serializable
data class Activity(
    val type: ActivityType,
    val lessonId: String? = null,
    val practiceMinutes: Int = 0
)

@Serializable
data class GamificationState(
    val totalXp: Int = 0,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val lastPracticeEpochDay: Long? = null,
    val practiceMinutesByEpochDay: Map<Long, Int> = emptyMap(),
    val completedMissionIdsByEpochDay: Map<Long, Set<String>> = emptyMap(),
    val lessonCompletionCountByEpochDay: Map<String, Int> = emptyMap(),
    val unlockedAchievementIds: Set<String> = emptySet(),
    val hasPlayedFirstNote: Boolean = false,
    val hasCompletedFirstLesson: Boolean = false,
    val hasPracticeMissionEverCompleted: Boolean = false
) {
    init {
        require(totalXp >= 0)
        require(currentStreakDays >= 0 && bestStreakDays >= currentStreakDays)
        require(practiceMinutesByEpochDay.values.all { it >= 0 })
    }
}

data class GamificationResult(
    val state: GamificationState,
    val xpEarned: Int,
    val newlyUnlockedAchievementIds: List<String>,
    val previousLevel: SkillLevel,
    val currentLevel: SkillLevel
)
