package ir.pianova.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local learner profile. IDs are stable and are the ownership key for all
 * learner-scoped records. Family relationships are added by the family model.
 */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val currentLevelId: String = "beginner",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "lesson_progress",
    primaryKeys = ["userId", "lessonId"],
    indices = [Index(value = ["progressId"], unique = true)]
)
data class LessonProgressEntity(
    val progressId: String,
    val userId: String,
    val lessonId: String,
    /** JSON array of completed step IDs, kept schema-neutral for lesson JSON evolution. */
    val completedStepIdsJson: String = "[]",
    val isCompleted: Boolean = false,
    val lastAccessedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null
)

@Entity(tableName = "practice_sessions", indices = [Index("userId"), Index("startedAtEpochMillis")])
data class PracticeSessionEntity(
    @PrimaryKey val sessionId: String,
    val userId: String,
    val lessonId: String? = null,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val durationMillis: Long = 0,
    val notesPlayed: Int = 0,
    val correctNotes: Int = 0,
    val wrongNotes: Int = 0
)

@Entity(
    tableName = "user_achievements",
    primaryKeys = ["userId", "achievementId"],
    indices = [Index("userId")]
)
data class UserAchievementEntity(
    val userId: String,
    val achievementId: String,
    val unlockedAtEpochMillis: Long,
    val metadataJson: String = "{}"
)

@Entity(
    tableName = "daily_missions",
    primaryKeys = ["userId", "missionId", "dateEpochDay"],
    indices = [Index("userId")]
)
data class DailyMissionEntity(
    val userId: String,
    val missionId: String,
    val dateEpochDay: Long,
    val targetCount: Int,
    val progressCount: Int = 0,
    val isCompleted: Boolean = false
)

@Entity(
    tableName = "song_progress",
    primaryKeys = ["userId", "songId"],
    indices = [Index("userId")]
)
data class SongProgressEntity(
    val userId: String,
    val songId: String,
    val completedSectionsJson: String = "[]",
    val bestAccuracy: Float? = null,
    val lastPlayedAtEpochMillis: Long? = null,
    val playCount: Int = 0
)
