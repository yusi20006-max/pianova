package ir.pianova.app.domain.gamification

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GamificationEngineTest {
    @Test fun levelsUseThresholdsAndReturnProgression() {
        assertEquals("beginner", GamificationEngine.levelFor(0).id)
        assertEquals("steady", GamificationEngine.levelFor(100).id)
        assertEquals("developing", GamificationEngine.levelFor(300).id)
        assertEquals("skilled", GamificationEngine.levelFor(600).id)
        assertEquals("advanced", GamificationEngine.levelFor(1000).id)
        assertEquals("professional", GamificationEngine.levelFor(1600).id)
        assertEquals(100, GamificationEngine.nextLevel(0)?.minimumXp)
        assertNull(GamificationEngine.nextLevel(1600))
    }

    @Test fun activityRewardsAreCorrect() {
        val date = LocalDate.of(2026, 1, 1)
        assertEquals(20, GamificationEngine.apply(GamificationState(), Activity(ActivityType.EXERCISE_COMPLETED), date).xpEarned)
        assertEquals(50, GamificationEngine.apply(GamificationState(), Activity(ActivityType.PERFECT_COMPLETION), date).xpEarned)
        assertEquals(50, GamificationEngine.apply(GamificationState(), Activity(ActivityType.LESSON_COMPLETED, lessonId="lesson-a"), date).xpEarned + 15)
    }

    @Test fun lessonCompletionRewardHasDailyAntiFarmingCapAndMissionRewardOnce() {
        val date = LocalDate.of(2026, 2, 1)
        val first = GamificationEngine.apply(GamificationState(), Activity(ActivityType.LESSON_COMPLETED, lessonId="l1"), date)
        assertEquals(65, first.xpEarned)
        val repeatSame = GamificationEngine.apply(first.state, Activity(ActivityType.LESSON_COMPLETED, lessonId="l1"), date)
        assertEquals(0, repeatSame.xpEarned)
        val anotherLesson = GamificationEngine.apply(repeatSame.state, Activity(ActivityType.LESSON_COMPLETED, lessonId="l2"), date)
        assertEquals(50, anotherLesson.xpEarned)
        val nextDay = GamificationEngine.apply(anotherLesson.state, Activity(ActivityType.LESSON_COMPLETED, lessonId="l1"), date.plusDays(1))
        assertEquals(65, nextDay.xpEarned)
    }

    @Test fun fivePracticeMinutesCompletesMissionAndAddsRewardOnlyOnce() {
        val day = LocalDate.of(2026, 3, 2)
        val first = GamificationEngine.apply(GamificationState(), Activity(ActivityType.PRACTICE, practiceMinutes=3), day)
        assertEquals(0, first.xpEarned)
        val second = GamificationEngine.apply(first.state, Activity(ActivityType.PRACTICE, practiceMinutes=2), day)
        assertEquals(15, second.xpEarned)
        assertTrue(GamificationEngine.missionProgress(second.state, day).first().completed)
        val morePractice = GamificationEngine.apply(second.state, Activity(ActivityType.PRACTICE, practiceMinutes=20), day)
        assertEquals(0, morePractice.xpEarned)
        assertEquals(25, GamificationEngine.missionProgress(morePractice.state, day).first().progress)
    }

    @Test fun streakCountsConsecutiveDaysAndResetsAfterGap() {
        var state = GamificationState()
        val start = LocalDate.of(2026, 4, 1)
        for (day in 0..6) {
            state = GamificationEngine.apply(state, Activity(ActivityType.PRACTICE, practiceMinutes=1), start.plusDays(day.toLong())).state
        }
        assertEquals(7, state.currentStreakDays)
        assertEquals(7, state.bestStreakDays)
        assertTrue("practice_streak_7" in state.unlockedAchievementIds)
        val afterGap = GamificationEngine.apply(state, Activity(ActivityType.PRACTICE, practiceMinutes=1), start.plusDays(9))
        assertEquals(1, afterGap.state.currentStreakDays)
        assertEquals(7, afterGap.state.bestStreakDays)
    }

    @Test fun firstNoteAndFirstLessonAchievementsUnlockOnce() {
        val date = LocalDate.of(2026, 5, 1)
        val firstNote = GamificationEngine.apply(GamificationState(), Activity(ActivityType.NOTE_PLAYED), date)
        assertEquals(listOf("first_note"), firstNote.newlyUnlockedAchievementIds)
        val repeatedNote = GamificationEngine.apply(firstNote.state, Activity(ActivityType.NOTE_PLAYED), date)
        assertTrue(repeatedNote.newlyUnlockedAchievementIds.isEmpty())
        val lesson = GamificationEngine.apply(repeatedNote.state, Activity(ActivityType.LESSON_COMPLETED, lessonId="lesson"), date)
        assertEquals(listOf("first_lesson"), lesson.newlyUnlockedAchievementIds)
    }

    @Test fun levelUpIsReportedOnReward() {
        val state = GamificationState(totalXp=90)
        val result = GamificationEngine.apply(state, Activity(ActivityType.EXERCISE_COMPLETED), LocalDate.now())
        assertEquals("beginner", result.previousLevel.id)
        assertEquals("steady", result.currentLevel.id)
    }
}
