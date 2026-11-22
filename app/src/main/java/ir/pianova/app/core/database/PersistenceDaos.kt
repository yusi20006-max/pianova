package ir.pianova.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyDao {
    @Upsert suspend fun upsert(family: FamilyEntity)

    @Query("SELECT * FROM families WHERE familyId = :familyId LIMIT 1")
    fun observeById(familyId: String): Flow<FamilyEntity?>

    @Query("SELECT * FROM families WHERE familyId = :familyId LIMIT 1")
    suspend fun findById(familyId: String): FamilyEntity?
}

@Dao
interface LearnerProfileDao {
    @Upsert suspend fun upsert(profile: LearnerProfileEntity)

    @Query("SELECT * FROM learner_profiles WHERE familyId = :familyId ORDER BY createdAtEpochMillis")
    fun observeForFamily(familyId: String): Flow<List<LearnerProfileEntity>>

    @Query("SELECT * FROM learner_profiles WHERE learnerId = :learnerId LIMIT 1")
    fun observeById(learnerId: String): Flow<LearnerProfileEntity?>

    @Query("SELECT * FROM learner_profiles WHERE learnerId = :learnerId LIMIT 1")
    suspend fun findById(learnerId: String): LearnerProfileEntity?

    @Query("DELETE FROM learner_profiles WHERE learnerId = :learnerId")
    suspend fun delete(learnerId: String)
}

@Dao
interface LessonProgressDao {
    @Upsert suspend fun upsert(progress: LessonProgressEntity)
    @Query("SELECT * FROM lesson_progress WHERE userId = :learnerId ORDER BY lastAccessedAtEpochMillis DESC")
    fun observeForLearner(learnerId: String): Flow<List<LessonProgressEntity>>
    @Query("SELECT * FROM lesson_progress WHERE userId = :learnerId AND lessonId = :lessonId LIMIT 1")
    fun observeLesson(learnerId: String, lessonId: String): Flow<LessonProgressEntity?>
    @Query("DELETE FROM lesson_progress WHERE userId = :learnerId")
    suspend fun deleteForLearner(learnerId: String)
}

@Dao
interface PracticeSessionDao {
    @Upsert suspend fun upsert(session: PracticeSessionEntity)
    @Query("SELECT * FROM practice_sessions WHERE userId = :learnerId ORDER BY startedAtEpochMillis DESC")
    fun observeForLearner(learnerId: String): Flow<List<PracticeSessionEntity>>
    @Query("SELECT * FROM practice_sessions WHERE userId = :learnerId AND startedAtEpochMillis >= :from AND startedAtEpochMillis < :until ORDER BY startedAtEpochMillis")
    fun observeBetween(learnerId: String, from: Long, until: Long): Flow<List<PracticeSessionEntity>>
    @Query("DELETE FROM practice_sessions WHERE userId = :learnerId")
    suspend fun deleteForLearner(learnerId: String)
}

@Dao
interface UserAchievementDao {
    @Upsert suspend fun upsert(achievement: UserAchievementEntity)
    @Query("SELECT * FROM user_achievements WHERE userId = :learnerId ORDER BY unlockedAtEpochMillis DESC")
    fun observeForLearner(learnerId: String): Flow<List<UserAchievementEntity>>
    @Query("DELETE FROM user_achievements WHERE userId = :learnerId")
    suspend fun deleteForLearner(learnerId: String)
}

@Dao
interface DailyMissionDao {
    @Upsert suspend fun upsert(mission: DailyMissionEntity)
    @Query("SELECT * FROM daily_missions WHERE userId = :learnerId AND dateEpochDay = :dateEpochDay")
    fun observeForDay(learnerId: String, dateEpochDay: Long): Flow<List<DailyMissionEntity>>
    @Query("DELETE FROM daily_missions WHERE userId = :learnerId")
    suspend fun deleteForLearner(learnerId: String)
}

@Dao
interface SongProgressDao {
    @Upsert suspend fun upsert(progress: SongProgressEntity)
    @Query("SELECT * FROM song_progress WHERE userId = :learnerId ORDER BY lastPlayedAtEpochMillis DESC")
    fun observeForLearner(learnerId: String): Flow<List<SongProgressEntity>>
    @Query("DELETE FROM song_progress WHERE userId = :learnerId")
    suspend fun deleteForLearner(learnerId: String)
}

@Dao
interface LearnerGoalDao {
    @Upsert suspend fun upsert(goal: LearnerGoalEntity)
    @Query("SELECT * FROM learner_goals WHERE learnerId = :learnerId LIMIT 1")
    fun observeForLearner(learnerId: String): Flow<LearnerGoalEntity?>
    @Query("DELETE FROM learner_goals WHERE learnerId = :learnerId")
    suspend fun deleteForLearner(learnerId: String)
}
