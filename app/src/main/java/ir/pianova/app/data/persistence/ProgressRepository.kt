package ir.pianova.app.data.persistence

import ir.pianova.app.core.database.DailyMissionDao
import ir.pianova.app.core.database.DailyMissionEntity
import ir.pianova.app.core.database.LessonProgressDao
import ir.pianova.app.core.database.LessonProgressEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionDao
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.core.database.SongProgressDao
import ir.pianova.app.core.database.SongProgressEntity
import ir.pianova.app.core.database.UserAchievementDao
import ir.pianova.app.core.database.UserAchievementEntity
import ir.pianova.app.core.database.UserProfileDao
import ir.pianova.app.core.database.UserProfileEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Single persistence boundary for learner progress. Every learner-owned query
 * requires userId so callers cannot accidentally request an unscoped record set.
 */
class ProgressRepository(private val database: PianovaDatabase) {
    private val profiles: UserProfileDao = database.userProfileDao()
    private val lessons: LessonProgressDao = database.lessonProgressDao()
    private val sessions: PracticeSessionDao = database.practiceSessionDao()
    private val achievements: UserAchievementDao = database.userAchievementDao()
    private val missions: DailyMissionDao = database.dailyMissionDao()
    private val songs: SongProgressDao = database.songProgressDao()

    fun observeProfiles(): Flow<List<UserProfileEntity>> = profiles.observeAll()
    fun observeProfile(userId: String): Flow<UserProfileEntity?> = profiles.observeById(userId)
    suspend fun saveProfile(profile: UserProfileEntity) = profiles.upsert(profile)

    fun observeLessonProgress(userId: String): Flow<List<LessonProgressEntity>> =
        lessons.observeForLearner(userId)
    fun observeLesson(userId: String, lessonId: String): Flow<LessonProgressEntity?> =
        lessons.observeLesson(userId, lessonId)
    suspend fun saveLessonProgress(progress: LessonProgressEntity) = lessons.upsert(progress)

    fun observePracticeSessions(userId: String): Flow<List<PracticeSessionEntity>> =
        sessions.observeForLearner(userId)
    fun observePracticeSessionsBetween(userId: String, from: Long, until: Long): Flow<List<PracticeSessionEntity>> =
        sessions.observeBetween(userId, from, until)
    suspend fun savePracticeSession(session: PracticeSessionEntity) = sessions.upsert(session)

    fun observeAchievements(userId: String): Flow<List<UserAchievementEntity>> =
        achievements.observeForLearner(userId)
    suspend fun saveAchievement(achievement: UserAchievementEntity) = achievements.upsert(achievement)

    fun observeDailyMissions(userId: String, dateEpochDay: Long): Flow<List<DailyMissionEntity>> =
        missions.observeForDay(userId, dateEpochDay)
    suspend fun saveDailyMission(mission: DailyMissionEntity) = missions.upsert(mission)

    fun observeSongProgress(userId: String): Flow<List<SongProgressEntity>> =
        songs.observeForLearner(userId)
    suspend fun saveSongProgress(progress: SongProgressEntity) = songs.upsert(progress)

    suspend fun deleteLearnerProgress(userId: String) {
        require(userId.isNotBlank())
        database.withTransaction {
            lessons.deleteForLearner(userId)
            sessions.deleteForLearner(userId)
            achievements.deleteForLearner(userId)
            missions.deleteForLearner(userId)
            songs.deleteForLearner(userId)
            profiles.delete(userId)
        }
    }
}
