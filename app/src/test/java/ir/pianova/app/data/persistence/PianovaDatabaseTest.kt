package ir.pianova.app.data.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.pianova.app.core.database.EvaluationEvidenceEntity
import ir.pianova.app.core.database.DailyMissionEntity
import ir.pianova.app.core.database.FamilyEntity
import ir.pianova.app.core.database.LearnerProfileEntity
import ir.pianova.app.core.database.LessonProgressEntity
import ir.pianova.app.core.database.MIGRATION_1_2
import ir.pianova.app.core.database.MIGRATION_2_3
import ir.pianova.app.core.database.MIGRATION_3_4
import ir.pianova.app.core.database.MIGRATION_4_5
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.core.database.SongProgressEntity
import ir.pianova.app.core.database.UserAchievementEntity
import ir.pianova.app.domain.family.Family
import ir.pianova.app.domain.family.FamilyManagerContext
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        database.familyDao().upsert(FamilyEntity("family-a", 1, 1))
        val repository = ProgressRepository(database)
        repository.saveLearnerProfile(LearnerProfileEntity("learner-a", "family-a", "هنرجوی اول", createdAtEpochMillis = 1, updatedAtEpochMillis = 1))
        repository.saveLearnerProfile(LearnerProfileEntity("learner-b", "family-a", "هنرجوی دوم", createdAtEpochMillis = 2, updatedAtEpochMillis = 2))

        repository.saveLessonProgress(LessonProgressEntity("p-a", "learner-a", "lesson-1", "[\"step-1\"]", true, 10, 11))
        repository.saveLessonProgress(LessonProgressEntity("p-b", "learner-b", "lesson-1", "[\"step-2\"]", false, 12))
        repository.savePracticeSession(PracticeSessionEntity("s-a", "learner-a", "lesson-1", 20, 30, 10, 3, 2, 1))
        repository.saveEvaluationEvidence(EvaluationEvidenceEntity("e-a", "learner-a", "s-a", "lesson-1", "step-1", "pitch", 25, "CORRECT", "60", "60", false))
        repository.saveAchievement(UserAchievementEntity("learner-a", "first-note", 40))
        repository.saveDailyMission(DailyMissionEntity("learner-a", "practice-five-minutes", 20000, 5, 3, false))
        repository.saveSongProgress(SongProgressEntity("learner-a", "song-1", "[\"part-a\"]", 0.85f, 50, 2))

        assertEquals(1, repository.observeLessonProgress("learner-a").first().size)
        assertEquals("learner-a", repository.observeLessonProgress("learner-a").first().single().learnerId)
        repository.saveLessonProgress(LessonProgressEntity("p-a-updated", "learner-a", "lesson-1", """["step-1","step-2"]""", true, 15, 16))
        assertEquals(1, repository.observeLessonProgress("learner-a").first().size)
        assertEquals("p-a-updated", repository.observeLessonProgress("learner-a").first().single().progressId)
        assertEquals(0, repository.observeLessonProgress("learner-c").first().size)
        assertEquals(1, repository.observePracticeSessions("learner-a").first().size)
        assertEquals("e-a", repository.observeEvaluationEvidence("learner-a").first().single().evaluationId)
        assertTrue(repository.observeEvaluationEvidence("learner-b").first().isEmpty())
        assertTrue(repository.observePracticeSessions("learner-b").first().isEmpty())
        assertEquals(1, repository.observeAchievements("learner-a").first().size)
        assertTrue(repository.observeAchievements("learner-b").first().isEmpty())
        assertEquals(1, repository.observeDailyMissions("learner-a", 20000).first().size)
        assertTrue(repository.observeDailyMissions("learner-b", 20000).first().isEmpty())
        assertEquals(1, repository.observeSongProgress("learner-a").first().size)
        assertTrue(repository.observeSongProgress("learner-b").first().isEmpty())
        assertEquals(
            listOf("learner-a", "learner-b"),
            repository.observeLearners("family-a").first().map { it.learnerId }
        )
    }

    @Test
    fun deleteLearnerProgressDoesNotDeleteAnotherLearner() = runBlocking {
        database.familyDao().upsert(FamilyEntity("family-a", 1, 1))
        val repository = ProgressRepository(database)
        repository.saveLearnerProfile(LearnerProfileEntity("a", "family-a", "الف", createdAtEpochMillis = 1, updatedAtEpochMillis = 1))
        repository.saveLearnerProfile(LearnerProfileEntity("b", "family-a", "ب", createdAtEpochMillis = 2, updatedAtEpochMillis = 2))
        repository.saveLessonProgress(LessonProgressEntity("pa", "a", "lesson", lastAccessedAtEpochMillis = 1))
        repository.saveLessonProgress(LessonProgressEntity("pb", "b", "lesson", lastAccessedAtEpochMillis = 1))

        repository.deleteLearnerProgress("a")

        assertNull(repository.observeLearner("a").first())
        assertEquals("b", repository.observeLessonProgress("b").first().single().learnerId)
    }

    @Test
    fun familyRepositoryValidatesOwnershipAndDeletesOnlySelectedLearner() = runBlocking {
        val repository = RoomFamilyRepository(database)
        repository.saveFamily(Family("family-a", 1, 1))
        repository.saveFamily(Family("family-b", 2, 2))

        val first = LearnerProfile("learner-a", "family-a", "هنرجوی اول", LearnerAudience.CHILD, createdAtEpochMillis = 3, updatedAtEpochMillis = 3)
        val second = LearnerProfile("learner-b", "family-a", "هنرجوی دوم", LearnerAudience.TEEN, createdAtEpochMillis = 4, updatedAtEpochMillis = 4)
        repository.saveLearner(first)
        repository.saveLearner(second)
        assertFalse(runCatching { repository.saveLearner(first.copy(familyId = "family-b")) }.isSuccess)

        assertEquals(2, repository.observeLearners("family-a").first().size)
        assertTrue(repository.observeLearners("family-b").first().isEmpty())
        assertFalse(runCatching {
            repository.saveLearner(
                LearnerProfile("orphan", "missing-family", "بدون خانواده", LearnerAudience.ADULT, createdAtEpochMillis = 5, updatedAtEpochMillis = 5)
            )
        }.isSuccess)

        val progress = ProgressRepository(database)
        assertFalse(
            runCatching {
                progress.saveLessonProgress(LessonProgressEntity("orphan", "missing-learner", "lesson-1", lastAccessedAtEpochMillis = 1))
            }.isSuccess
        )
        progress.saveLessonProgress(LessonProgressEntity("p-a", "learner-a", "lesson-1", lastAccessedAtEpochMillis = 1))
        progress.saveLessonProgress(LessonProgressEntity("p-b", "learner-b", "lesson-1", lastAccessedAtEpochMillis = 1))

        assertTrue(repository.deleteLearner("learner-a"))
        assertFalse(repository.deleteLearner("learner-a"))
        assertTrue(repository.observeLearners("family-a").first().single().learnerId == "learner-b")
        assertTrue(progress.observeLessonProgress("learner-a").first().isEmpty())
        assertEquals("learner-b", progress.observeLessonProgress("learner-b").first().single().learnerId)
    }

    @Test
    fun concurrentLearnerCreationCannotReassignStableIdsAcrossFamilies() = runBlocking {
        val familyRepository = RoomFamilyRepository(database)
        familyRepository.saveFamily(Family("family-a", 1, 1))
        familyRepository.saveFamily(Family("family-b", 2, 2))

        val domainAttempts = listOf(
            async(Dispatchers.IO) {
                runCatching {
                    familyRepository.saveLearner(
                        LearnerProfile("shared-domain-id", "family-a", "هنرجو الف", LearnerAudience.ADULT, createdAtEpochMillis = 3, updatedAtEpochMillis = 3)
                    )
                }.isSuccess
            },
            async(Dispatchers.IO) {
                runCatching {
                    familyRepository.saveLearner(
                        LearnerProfile("shared-domain-id", "family-b", "هنرجو ب", LearnerAudience.ADULT, createdAtEpochMillis = 3, updatedAtEpochMillis = 3)
                    )
                }.isSuccess
            }
        ).awaitAll()
        assertEquals(1, domainAttempts.count { it })
        assertEquals(
            1,
            familyRepository.observeLearners("family-a").first().size +
                familyRepository.observeLearners("family-b").first().size
        )

        val progressRepository = ProgressRepository(database)
        val persistenceAttempts = listOf(
            async(Dispatchers.IO) {
                runCatching {
                    progressRepository.saveLearnerProfile(
                        LearnerProfileEntity("shared-persistence-id", "family-a", "الف", createdAtEpochMillis = 4, updatedAtEpochMillis = 4)
                    )
                }.isSuccess
            },
            async(Dispatchers.IO) {
                runCatching {
                    progressRepository.saveLearnerProfile(
                        LearnerProfileEntity("shared-persistence-id", "family-b", "ب", createdAtEpochMillis = 4, updatedAtEpochMillis = 4)
                    )
                }.isSuccess
            }
        ).awaitAll()
        assertEquals(1, persistenceAttempts.count { it })
        assertEquals(
            1,
            progressRepository.observeLearners("family-a").first().count { it.learnerId == "shared-persistence-id" } +
                progressRepository.observeLearners("family-b").first().count { it.learnerId == "shared-persistence-id" }
        )
    }

    @Test
    fun familyProfileLifecyclePersistsGoalsAndDeletesOnlySelectedLearner() = runBlocking {
        val repository = RoomFamilyRepository(database)
        repository.saveFamily(Family("local-family", 1, 1))
        val first = LearnerProfile(
            "learner-one", "local-family", "هنرجوی اول", LearnerAudience.TEEN,
            avatarRef = "🎹", initialLevelId = "beginner", currentLevelId = "beginner",
            createdAtEpochMillis = 2, updatedAtEpochMillis = 2
        )
        val second = LearnerProfile(
            "learner-two", "local-family", "هنرجوی دوم", LearnerAudience.ADULT,
            createdAtEpochMillis = 3, updatedAtEpochMillis = 3
        )
        repository.saveLearner(first)
        repository.saveLearner(second)
        repository.saveGoal(first.learnerId, "یادگیری یک قطعه")
        repository.saveGoal(second.learnerId, "تمرین روزانه")
        assertEquals("یادگیری یک قطعه", repository.observeGoal(first.learnerId).first())

        repository.saveLearner(first.copy(displayName = "نام ویرایش‌شده", updatedAtEpochMillis = 4))
        assertEquals("نام ویرایش‌شده", repository.observeLearners("local-family").first().first().displayName)

        assertTrue(repository.deleteLearner(first.learnerId))
        assertNull(repository.observeGoal(first.learnerId).first())
        assertEquals("تمرین روزانه", repository.observeGoal(second.learnerId).first())
        assertEquals(listOf("learner-two"), repository.observeLearners("local-family").first().map { it.learnerId })
    }

    @Test
    fun domainModelsRejectInvalidIdentityAndTimestamps() {
        assertFalse(runCatching { Family("", 0, 0) }.isSuccess)
        assertFalse(runCatching { Family("family-a", 10, 9) }.isSuccess)
        assertFalse(
            runCatching {
                LearnerProfile("", "family-a", "هنرجو", LearnerAudience.CHILD, createdAtEpochMillis = 0, updatedAtEpochMillis = 0)
            }.isSuccess
        )
        assertFalse(
            runCatching {
                LearnerProfile("learner-a", "family-a", " ", LearnerAudience.ADULT, createdAtEpochMillis = 0, updatedAtEpochMillis = 0)
            }.isSuccess
        )
        assertEquals("family-a", FamilyManagerContext("family-a").familyId)
    }

    @Test
    fun versionOneDatabaseMigratesProfilesAndKeepsExistingProgress() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "pianova-v1-migration-test.db"
        context.deleteDatabase(databaseName)
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null)
        legacy.execSQL("CREATE TABLE user_profiles (userId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, currentLevelId TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE lesson_progress (progressId TEXT NOT NULL, userId TEXT NOT NULL, lessonId TEXT NOT NULL, completedStepIdsJson TEXT NOT NULL, isCompleted INTEGER NOT NULL, lastAccessedAtEpochMillis INTEGER NOT NULL, completedAtEpochMillis INTEGER, PRIMARY KEY(userId, lessonId))")
        legacy.execSQL("CREATE UNIQUE INDEX index_lesson_progress_progressId ON lesson_progress(progressId)")
        legacy.execSQL("CREATE TABLE practice_sessions (sessionId TEXT NOT NULL PRIMARY KEY, userId TEXT NOT NULL, lessonId TEXT, startedAtEpochMillis INTEGER NOT NULL, endedAtEpochMillis INTEGER, durationMillis INTEGER NOT NULL, notesPlayed INTEGER NOT NULL, correctNotes INTEGER NOT NULL, wrongNotes INTEGER NOT NULL)")
        legacy.execSQL("CREATE INDEX index_practice_sessions_userId ON practice_sessions(userId)")
        legacy.execSQL("CREATE INDEX index_practice_sessions_startedAtEpochMillis ON practice_sessions(startedAtEpochMillis)")
        legacy.execSQL("CREATE TABLE user_achievements (userId TEXT NOT NULL, achievementId TEXT NOT NULL, unlockedAtEpochMillis INTEGER NOT NULL, metadataJson TEXT NOT NULL, PRIMARY KEY(userId, achievementId))")
        legacy.execSQL("CREATE INDEX index_user_achievements_userId ON user_achievements(userId)")
        legacy.execSQL("CREATE TABLE daily_missions (userId TEXT NOT NULL, missionId TEXT NOT NULL, dateEpochDay INTEGER NOT NULL, targetCount INTEGER NOT NULL, progressCount INTEGER NOT NULL, isCompleted INTEGER NOT NULL, PRIMARY KEY(userId, missionId, dateEpochDay))")
        legacy.execSQL("CREATE INDEX index_daily_missions_userId ON daily_missions(userId)")
        legacy.execSQL("CREATE TABLE song_progress (userId TEXT NOT NULL, songId TEXT NOT NULL, completedSectionsJson TEXT NOT NULL, bestAccuracy REAL, lastPlayedAtEpochMillis INTEGER, playCount INTEGER NOT NULL, PRIMARY KEY(userId, songId))")
        legacy.execSQL("CREATE INDEX index_song_progress_userId ON song_progress(userId)")
        legacy.execSQL("INSERT INTO user_profiles VALUES ('legacy-learner', 'هنرجوی قبلی', 'beginner', 10, 20)")
        legacy.execSQL("INSERT INTO lesson_progress VALUES ('legacy-progress', 'legacy-learner', 'lesson-1', '[\"step-1\"]', 1, 30, 40)")
        legacy.version = 1
        legacy.close()

        val migrated = Room.databaseBuilder(context, PianovaDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val learners = migrated.learnerProfileDao().observeForFamily("family-default").first()
            assertEquals(1, learners.size)
            assertEquals("legacy-learner", learners.single().learnerId)
            assertEquals("beginner", learners.single().initialLevelId)
            assertEquals("unspecified", learners.single().audienceCategory)
            assertTrue(migrated.learningPathAssignmentDao().observeHistory("legacy-learner").first().isEmpty())
            assertEquals("legacy-progress", migrated.lessonProgressDao().observeForLearner("legacy-learner").first().single().progressId)
            assertNull(migrated.learnerGoalDao().observeForLearner("legacy-learner").first())
            assertTrue(migrated.evaluationEvidenceDao().observeForLearner("legacy-learner").first().isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }
}
