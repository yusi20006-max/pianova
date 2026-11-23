package ir.pianova.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FamilyEntity::class,
        LearnerProfileEntity::class,
        LessonProgressEntity::class,
        PracticeSessionEntity::class,
        UserAchievementEntity::class,
        DailyMissionEntity::class,
        SongProgressEntity::class,
        LearnerGoalEntity::class,
        LearningPathAssignmentEntity::class,
        EvaluationEvidenceEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class PianovaDatabase : RoomDatabase() {
    abstract fun familyDao(): FamilyDao
    abstract fun learnerProfileDao(): LearnerProfileDao
    abstract fun lessonProgressDao(): LessonProgressDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun userAchievementDao(): UserAchievementDao
    abstract fun dailyMissionDao(): DailyMissionDao
    abstract fun songProgressDao(): SongProgressDao
    abstract fun learnerGoalDao(): LearnerGoalDao
    abstract fun learningPathAssignmentDao(): LearningPathAssignmentDao
    abstract fun evaluationEvidenceDao(): EvaluationEvidenceDao

    companion object {
        @Volatile private var INSTANCE: PianovaDatabase? = null

        fun getInstance(context: Context): PianovaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PianovaDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
            }

        private const val DATABASE_NAME = "pianova.db"
    }
}
