package ir.pianova.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Moves the single-user v1 profile table into the family/learner model.
 * Existing user IDs are retained as learner IDs and all existing progress
 * rows keep their original ownership column values.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS families (
                familyId TEXT NOT NULL PRIMARY KEY,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO families (familyId, createdAtEpochMillis, updatedAtEpochMillis)
            VALUES ('family-default', 0, 0)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learner_profiles (
                learnerId TEXT NOT NULL PRIMARY KEY,
                familyId TEXT NOT NULL,
                displayName TEXT NOT NULL,
                birthDateEpochDay INTEGER,
                audienceCategory TEXT NOT NULL,
                avatarRef TEXT,
                initialLevelId TEXT NOT NULL,
                currentLevelId TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(familyId) REFERENCES families(familyId)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO learner_profiles (
                learnerId, familyId, displayName, birthDateEpochDay,
                audienceCategory, avatarRef, initialLevelId, currentLevelId,
                createdAtEpochMillis, updatedAtEpochMillis
            )
            SELECT
                userId, 'family-default', displayName, NULL,
                'unspecified', NULL, currentLevelId, currentLevelId,
                createdAtEpochMillis, updatedAtEpochMillis
            FROM user_profiles
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learner_profiles_familyId ON learner_profiles(familyId)")
        db.execSQL("DROP TABLE user_profiles")
    }
}

/** Adds optional learner goals without mixing mutable goals into identity records. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learner_goals (
                learnerId TEXT NOT NULL PRIMARY KEY,
                goalText TEXT NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(learnerId) REFERENCES learner_profiles(learnerId)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }
}


/** Adds local, learner-owned learning-path selection history. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learning_path_assignments (
                assignmentId TEXT NOT NULL PRIMARY KEY,
                learnerId TEXT NOT NULL,
                pathId TEXT NOT NULL,
                levelId TEXT NOT NULL,
                startedAtEpochMillis INTEGER NOT NULL,
                endedAtEpochMillis INTEGER,
                isActive INTEGER NOT NULL,
                FOREIGN KEY(learnerId) REFERENCES learner_profiles(learnerId)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_path_assignments_learnerId ON learning_path_assignments(learnerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_path_assignments_learnerId_isActive ON learning_path_assignments(learnerId, isActive)")
    }
}


/** Stores durable per-attempt evaluation evidence without changing existing progress rows. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN wasInterrupted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE lesson_progress ADD COLUMN currentStepId TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS evaluation_evidence (
                evaluationId TEXT NOT NULL PRIMARY KEY,
                learnerId TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                lessonId TEXT NOT NULL,
                stepId TEXT NOT NULL,
                skillId TEXT,
                occurredAtEpochMillis INTEGER NOT NULL,
                status TEXT NOT NULL,
                expectedMidiNotesCsv TEXT NOT NULL,
                receivedMidiNotesCsv TEXT NOT NULL,
                answerRevealingHintUsed INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_evaluation_evidence_learnerId_occurredAtEpochMillis ON evaluation_evidence(learnerId, occurredAtEpochMillis)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_evaluation_evidence_sessionId ON evaluation_evidence(sessionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_evaluation_evidence_lessonId_stepId ON evaluation_evidence(lessonId, stepId)")
    }
}
