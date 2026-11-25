package ir.pianova.app.domain.progress

import ir.pianova.app.domain.family.LearnerProfile
import java.time.DayOfWeek
import java.time.LocalDate

data class LearnerActivityEvidence(
    val learnerId: String,
    val occurredAtEpochMillis: Long,
    val activityType: ActivityType
) {
    init {
        require(learnerId.isNotBlank())
        require(occurredAtEpochMillis >= 0)
    }
}

enum class ActivityType {
    PRACTICE,
    EVALUATION,
    LESSON
}

data class FamilyLearnerReport(
    val learner: LearnerProfile,
    val progress: LearnerProgressSnapshot,
    val goal: String?,
    val recentActivity: LearnerActivityEvidence?
)

data class FamilyProgressOverview(
    val reports: List<FamilyLearnerReport>,
    val totalPracticeMillis: Long,
    val totalSessionCount: Int,
    val totalCompletedLessonCount: Int
)

object FamilyProgressReportFactory {
    fun create(
        learner: LearnerProfile,
        progress: LearnerProgressSnapshot,
        goal: String?,
        activityEvidence: List<LearnerActivityEvidence>
    ): FamilyLearnerReport {
        require(progress.learnerId == learner.learnerId) {
            "Progress snapshot must belong to the reported learner"
        }
        val recent = activityEvidence
            .asSequence()
            .filter { it.learnerId == learner.learnerId }
            .maxByOrNull { it.occurredAtEpochMillis }
        return FamilyLearnerReport(
            learner = learner,
            progress = progress,
            goal = goal?.trim()?.takeIf { it.isNotEmpty() },
            recentActivity = recent
        )
    }
}

object FamilyProgressOverviewFactory {
    fun aggregate(reports: List<FamilyLearnerReport>): FamilyProgressOverview =
        FamilyProgressOverview(
            reports = reports.toList(),
            totalPracticeMillis = reports.sumOf { it.progress.practiceMillis },
            totalSessionCount = reports.sumOf { it.progress.sessionCount },
            totalCompletedLessonCount = reports.sumOf { it.progress.completedLessonCount }
        )
}

object CompletedPeriodSelector {
    fun weeks(periods: List<PeriodProgress>, today: LocalDate): List<PeriodProgress> {
        val currentWeekStart = today.with(DayOfWeek.MONDAY)
        return periods.filter { LocalDate.ofEpochDay(it.periodStartEpochDay).isBefore(currentWeekStart) }
    }

    fun months(periods: List<PeriodProgress>, today: LocalDate): List<PeriodProgress> {
        val currentMonthStart = today.withDayOfMonth(1)
        return periods.filter { LocalDate.ofEpochDay(it.periodStartEpochDay).isBefore(currentMonthStart) }
    }
}
