package ir.pianova.app.data.persistence

import androidx.room.withTransaction
import ir.pianova.app.core.database.DailyMissionDao
import ir.pianova.app.core.database.DailyMissionEntity
import ir.pianova.app.core.database.LearnerProfileDao
import ir.pianova.app.core.database.LearnerProfileEntity
import ir.pianova.app.core.database.LessonProgressDao
import ir.pianova.app.core.database.LessonProgressEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionDao
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.core.database.SongProgressDao
import ir.pianova.app.core.database.SongProgressEntity
import ir.pianova.app.core.database.UserAchievementDao
import ir.pianova.app.core.database.UserAchievementEntity
import kotlinx.coroutines.flow.Flow

/** All learner-owned progress queries require an explicit stable learnerId. */
class ProgressRepository(private val database: PianovaDatabase) {
    private val profiles: LearnerProfileDao = database.learnerProfileDao()
    private val lessons: LessonProgressDao = database.lessonProgressDao()
    private val sessions: PracticeSessionDao = database.practiceSessionDao()
    private val achievements: UserAchievementDao = database.userAchievementDao()
    private val missions: DailyMissionDao = database.dailyMissionDao()
    private val songs: SongProgressDao = database.songProgressDao()

    fun observeLearners(familyId: String): Flow<List<LearnerProfileEntity>> =
        profiles.observeForFamily(familyId)
    fun observeLearner(learnerId: String): Flow<LearnerProfileEntity?> =
        profiles.observeById(learnerId)
    suspend fun saveLearnerProfile(profile: LearnerProfileEntity) {
        require(database.familyDao().findById(profile.familyId) != null) {
            "Family \${profile.familyId} does not exist"
        }
        profiles.upsert(profile)
    }

    fun observeLessonProgress(learnerId: String): Flow<List<LessonProgressEntity>> =
        lessons.observeForLearner(learnerId)
    fun observeLesson(learnerId: String, lessonId: String): Flow<LessonProgressEntity?> =
        lessons.observeLesson(learnerId, lessonId)
    suspend fun saveLessonProgress(progress: LessonProgressEntity) = lessons.upsert(progress)

    fun observePracticeSessions(learnerId: String): Flow<List<PracticeSessionEntity>> =
        sessions.observeForLearner(learnerId)
    fun observePracticeSessionsBetween(learnerId: String, from: Long, until: Long): Flow<List<PracticeSessionEntity>> =
        sessions.observeBetween(learnerId, from, until)
    suspend fun savePracticeSession(session: PracticeSessionEntity) = sessions.upsert(session)

    fun observeAchievements(learnerId: String): Flow<List<UserAchievementEntity>> =
        achievements.observeForLearner(learnerId)
    suspend fun saveAchievement(achievement: UserAchievementEntity) = achievements.upsert(achievement)

    fun observeDailyMissions(learnerId: String, dateEpochDay: Long): Flow<List<DailyMissionEntity>> =
        missions.observeForDay(learnerId, dateEpochDay)
    suspend fun saveDailyMission(mission: DailyMissionEntity) = missions.upsert(mission)

    fun observeSongProgress(learnerId: String): Flow<List<SongProgressEntity>> =
        songs.observeForLearner(learnerId)
    suspend fun saveSongProgress(progress: SongProgressEntity) = songs.upsert(progress)

    suspend fun deleteLearnerProgress(learnerId: String) {
        require(learnerId.isNotBlank())
        database.withTransaction {
            lessons.deleteForLearner(learnerId)
            sessions.deleteForLearner(learnerId)
            achievements.deleteForLearner(learnerId)
            missions.deleteForLearner(learnerId)
            songs.deleteForLearner(learnerId)
            profiles.delete(learnerId)
        }
    }
}
