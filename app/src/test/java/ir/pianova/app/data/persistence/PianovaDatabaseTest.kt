package ir.pianova.app.data.persistence

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import ir.pianova.app.core.database.DailyMissionEntity
import ir.pianova.app.core.database.LessonProgressEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.core.database.SongProgressEntity
import ir.pianova.app.core.database.UserAchievementEntity
import ir.pianova.app.core.database.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PianovaDatabaseTest {
    private lateinit var database: PianovaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            PianovaDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun allProgressDaosPersistAndScopeRecordsByLearner() = runBlocking {
        val repository = ProgressRepository(database)
        repository.saveProfile(UserProfileEntity("learner-a", "هنرجوی اول", createdAtEpochMillis = 1, updatedAtEpochMillis = 1))
        repository.saveProfile(UserProfileEntity("learner-b", "هنرجوی دوم", createdAtEpochMillis = 2, updatedAtEpochMillis = 2))

        repository.saveLessonProgress(LessonProgressEntity("p-a", "learner-a", "lesson-1", "[\"step-1\"]", true, 10, 11))
        repository.saveLessonProgress(LessonProgressEntity("p-b", "learner-b", "lesson-1", "[\"step-2\"]", false, 12))
        repository.savePracticeSession(PracticeSessionEntity("s-a", "learner-a", "lesson-1", 20, 30, 10, 3, 2, 1))
        repository.saveAchievement(UserAchievementEntity("learner-a", "first-note", 40))
        repository.saveDailyMission(DailyMissionEntity("learner-a", "practice-five-minutes", 20000, 5, 3, false))
        repository.saveSongProgress(SongProgressEntity("learner-a", "song-1", "[\"part-a\"]", 0.85f, 50, 2))

        assertEquals(1, repository.observeLessonProgress("learner-a").first().size)
        assertEquals("step-1", repository.observeLessonProgress("learner-a").first().single().completedStepIdsJson
            .removeSurrounding("[", "]").removeSurrounding("\""))
        repository.saveLessonProgress(LessonProgressEntity("p-a-updated", "learner-a", "lesson-1", "[\"step-1\",\"step-2\"]", true, 15, 16))
        assertEquals(1, repository.observeLessonProgress("learner-a").first().size)
        assertEquals("p-a-updated", repository.observeLessonProgress("learner-a").first().single().progressId)
        assertEquals(0, repository.observeLessonProgress("learner-c").first().size)
        assertEquals(1, repository.observePracticeSessions("learner-a").first().size)
        assertEquals(1, repository.observeAchievements("learner-a").first().size)
        assertEquals(1, repository.observeDailyMissions("learner-a", 20000).first().size)
        assertEquals(1, repository.observeSongProgress("learner-a").first().size)
        assertTrue(repository.observeProfiles().first().map { it.userId }.containsAll(listOf("learner-a", "learner-b")))
    }

    @Test
    fun deleteLearnerProgressDoesNotDeleteAnotherLearner() = runBlocking {
        val repository = ProgressRepository(database)
        repository.saveProfile(UserProfileEntity("a", "الف", createdAtEpochMillis = 1, updatedAtEpochMillis = 1))
        repository.saveProfile(UserProfileEntity("b", "ب", createdAtEpochMillis = 2, updatedAtEpochMillis = 2))
        repository.saveLessonProgress(LessonProgressEntity("pa", "a", "lesson", lastAccessedAtEpochMillis = 1))
        repository.saveLessonProgress(LessonProgressEntity("pb", "b", "lesson", lastAccessedAtEpochMillis = 1))

        repository.deleteLearnerProgress("a")

        assertTrue(repository.observeProfile("a").first() == null)
        assertEquals("b", repository.observeLessonProgress("b").first().single().userId)
    }
}
