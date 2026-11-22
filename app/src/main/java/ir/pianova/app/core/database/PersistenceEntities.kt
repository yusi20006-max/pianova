package ir.pianova.app.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey val familyId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

/**
 * The former user_profiles table is evolved into the learner profile table.
 * The migration preserves existing userId values as stable learner IDs.
 */
@Entity(
    tableName = "learner_profiles",
    foreignKeys = [
        ForeignKey(
            entity = FamilyEntity::class,
            parentColumns = ["familyId"],
            childColumns = ["familyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("familyId")]
)
data class LearnerProfileEntity(
    @PrimaryKey val learnerId: String,
    val familyId: String,
    val displayName: String,
    val birthDateEpochDay: Long? = null,
    val audienceCategory: String = "unspecified",
    val avatarRef: String? = null,
    val initialLevelId: String = "beginner",
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
    @ColumnInfo(name = "userId") val learnerId: String,
    val lessonId: String,
    val completedStepIdsJson: String = "[]",
    val isCompleted: Boolean = false,
    val lastAccessedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null
)

@Entity(
    tableName = "practice_sessions",
    indices = [Index("userId"), Index("startedAtEpochMillis")]
)
data class PracticeSessionEntity(
    @PrimaryKey val sessionId: String,
    @ColumnInfo(name = "userId") val learnerId: String,
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
    @ColumnInfo(name = "userId") val learnerId: String,
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
    @ColumnInfo(name = "userId") val learnerId: String,
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
    @ColumnInfo(name = "userId") val learnerId: String,
    val songId: String,
    val completedSectionsJson: String = "[]",
    val bestAccuracy: Float? = null,
    val lastPlayedAtEpochMillis: Long? = null,
    val playCount: Int = 0
)
