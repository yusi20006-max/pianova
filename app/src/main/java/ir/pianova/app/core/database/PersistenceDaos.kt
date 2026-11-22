package ir.pianova.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Upsert suspend fun upsert(profile: UserProfileEntity)
    @Query("SELECT * FROM user_profiles ORDER BY createdAtEpochMillis")
    fun observeAll(): Flow<List<UserProfileEntity>>
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeById(userId: String): Flow<UserProfileEntity?>
    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun delete(userId: String)
}

@Dao
interface LessonProgressDao {
    @Upsert suspend fun upsert(progress: LessonProgressEntity)
    @Query("SELECT * FROM lesson_progress WHERE userId = :userId ORDER BY lastAccessedAtEpochMillis DESC")
    fun observeForLearner(userId: String): Flow<List<LessonProgressEntity>>
    @Query("SELECT * FROM lesson_progress WHERE userId = :userId AND lessonId = :lessonId LIMIT 1")
    fun observeLesson(userId: String, lessonId: String): Flow<LessonProgressEntity?>
    @Query("DELETE FROM lesson_progress WHERE userId = :userId")
    suspend fun deleteForLearner(userId: String)
}

@Dao
interface PracticeSessionDao {
    @Upsert suspend fun upsert(session: PracticeSessionEntity)
    @Query("SELECT * FROM practice_sessions WHERE userId = :userId ORDER BY startedAtEpochMillis DESC")
    fun observeForLearner(userId: String): Flow<List<PracticeSessionEntity>>
    @Query("SELECT * FROM practice_sessions WHERE userId = :userId AND startedAtEpochMillis >= :from AND startedAtEpochMillis < :until ORDER BY startedAtEpochMillis")
    fun observeBetween(userId: String, from: Long, until: Long): Flow<List<PracticeSessionEntity>>
    @Query("DELETE FROM practice_sessions WHERE userId = :userId")
    suspend fun deleteForLearner(userId: String)
}

@Dao
interface UserAchievementDao {
    @Upsert suspend fun upsert(achievement: UserAchievementEntity)
    @Query("SELECT * FROM user_achievements WHERE userId = :userId ORDER BY unlockedAtEpochMillis DESC")
    fun observeForLearner(userId: String): Flow<List<UserAchievementEntity>>
    @Query("DELETE FROM user_achievements WHERE userId = :userId")
    suspend fun deleteForLearner(userId: String)
}

@Dao
interface DailyMissionDao {
    @Upsert suspend fun upsert(mission: DailyMissionEntity)
    @Query("SELECT * FROM daily_missions WHERE userId = :userId AND dateEpochDay = :dateEpochDay")
    fun observeForDay(userId: String, dateEpochDay: Long): Flow<List<DailyMissionEntity>>
    @Query("DELETE FROM daily_missions WHERE userId = :userId")
    suspend fun deleteForLearner(userId: String)
}

@Dao
interface SongProgressDao {
    @Upsert suspend fun upsert(progress: SongProgressEntity)
    @Query("SELECT * FROM song_progress WHERE userId = :userId ORDER BY lastPlayedAtEpochMillis DESC")
    fun observeForLearner(userId: String): Flow<List<SongProgressEntity>>
    @Query("DELETE FROM song_progress WHERE userId = :userId")
    suspend fun deleteForLearner(userId: String)
}
