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
        SongProgressEntity::class
    ],
    version = 2,
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

    companion object {
        @Volatile private var INSTANCE: PianovaDatabase? = null

        fun getInstance(context: Context): PianovaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PianovaDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }

        private const val DATABASE_NAME = "pianova.db"
    }
}
