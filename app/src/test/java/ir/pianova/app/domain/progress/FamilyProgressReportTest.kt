package ir.pianova.app.domain.progress

import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyProgressReportTest {
    @Test
    fun emptyFamilyOverviewHasNoInventedMetrics() {
        val overview = FamilyProgressOverviewFactory.aggregate(emptyList())

        assertTrue(overview.reports.isEmpty())
        assertEquals(0L, overview.totalPracticeMillis)
        assertEquals(0, overview.totalSessionCount)
        assertEquals(0, overview.totalCompletedLessonCount)
    }

    @Test
    fun singleLearnerReportKeepsItsGoalAndLatestActivity() {
        val learner = learner("learner-a")
        val snapshot = snapshot("learner-a", practiceMillis = 120_000, sessions = 2, lessons = 1)
        val report = FamilyProgressReportFactory.create(
            learner,
            snapshot,
            "تمرین روزانه",
            listOf(
                LearnerActivityEvidence("learner-a", 100, ActivityType.PRACTICE),
                LearnerActivityEvidence("learner-a", 200, ActivityType.EVALUATION)
            )
        )

        val overview = FamilyProgressOverviewFactory.aggregate(listOf(report))

        assertEquals(1, overview.reports.size)
        assertEquals(120_000L, overview.totalPracticeMillis)
        assertEquals(2, overview.totalSessionCount)
        assertEquals("تمرین روزانه", report.goal)
        assertEquals(ActivityType.EVALUATION, report.recentActivity?.activityType)
        assertEquals(200L, report.recentActivity?.occurredAtEpochMillis)
    }

    @Test
    fun multipleLearnersRemainSeparateAndAreNotRanked() {
        val first = FamilyProgressReportFactory.create(
            learner("learner-a"),
            snapshot("learner-a", practiceMillis = 60_000, sessions = 1, lessons = 1),
            null,
            listOf(LearnerActivityEvidence("learner-a", 100, ActivityType.PRACTICE))
        )
        val second = FamilyProgressReportFactory.create(
            learner("learner-b"),
            snapshot("learner-b", practiceMillis = 300_000, sessions = 3, lessons = 2),
            null,
            listOf(LearnerActivityEvidence("learner-b", 200, ActivityType.LESSON))
        )

        val overview = FamilyProgressOverviewFactory.aggregate(listOf(first, second))

        assertEquals(listOf("learner-a", "learner-b"), overview.reports.map { it.learner.learnerId })
        assertEquals(360_000L, overview.totalPracticeMillis)
        assertEquals(4, overview.totalSessionCount)
        assertEquals(3, overview.totalCompletedLessonCount)
        assertEquals(60_000L, overview.reports[0].progress.practiceMillis)
        assertEquals(300_000L, overview.reports[1].progress.practiceMillis)
    }

    @Test
    fun activityFromAnotherLearnerCannotAppearInReport() {
        val report = FamilyProgressReportFactory.create(
            learner("learner-a"),
            snapshot("learner-a", practiceMillis = 0, sessions = 0, lessons = 0),
            "هدف",
            listOf(LearnerActivityEvidence("learner-b", 999, ActivityType.EVALUATION))
        )

        assertNull(report.recentActivity)
    }

    @Test(expected = IllegalArgumentException::class)
    fun snapshotForAnotherLearnerIsRejected() {
        FamilyProgressReportFactory.create(
            learner("learner-a"),
            snapshot("learner-b", practiceMillis = 0, sessions = 0, lessons = 0),
            null,
            emptyList()
        )
    }

    private fun learner(id: String) = LearnerProfile(
        learnerId = id,
        familyId = "family-a",
        displayName = id,
        audience = LearnerAudience.ADULT,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1
    )

    private fun snapshot(id: String, practiceMillis: Long, sessions: Int, lessons: Int) =
        LearnerProgressSnapshot(
            learnerId = id,
            practiceMillis = practiceMillis,
            sessionCount = sessions,
            completedLessonCount = lessons,
            evaluationCount = 0,
            accuracy = null,
            skills = emptyList(),
            weekly = emptyList(),
            monthly = emptyList()
        )
}
