package ir.pianova.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.pianova.app.core.database.EvaluationEvidenceEntity
import ir.pianova.app.core.database.FamilyEntity
import ir.pianova.app.core.database.LearnerProfileEntity
import ir.pianova.app.core.database.LessonProgressEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.core.datastore.PianoSettings
import ir.pianova.app.data.persistence.ProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FamilyBackupCodecTest {
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
    fun jsonRoundTripPreservesFamilyLearnerAndProgressIds() {
        val payload = samplePayload()
        val decoded = FamilyBackupCodec.decode(FamilyBackupCodec.encode(payload, 1234))

        assertEquals(1234L, decoded.createdAtEpochMillis)
        assertEquals("family-a", decoded.payload.families.single().familyId)
        assertEquals(listOf("learner-a", "learner-b"), decoded.payload.learners.map { it.learnerId })
        assertEquals("progress-a", decoded.payload.lessonProgress.single().progressId)
        assertEquals("session-a", decoded.payload.practiceSessions.single().sessionId)
        assertEquals("evaluation-a", decoded.payload.evaluationEvidence.single().evaluationId)
    }

    @Test
    fun checksumRejectsCorruptedBackup() {
        val raw = FamilyBackupCodec.encode(samplePayload(), 1234)
        val corrupted = raw.replaceFirst("progress-a", "progress-x")

        assertFalse(runCatching { FamilyBackupCodec.decode(corrupted) }.isSuccess)
    }

    @Test
    fun unsupportedSchemaVersionIsRejected() {
        val raw = FamilyBackupCodec.encode(samplePayload(), 1234)
            .replace("\"schemaVersion\":2", "\"schemaVersion\":99")

        assertFalse(runCatching { FamilyBackupCodec.decode(raw) }.isSuccess)
    }

    @Test
    fun schemaVersionOneMigratesWithSafePreferenceDefaults() {
        val current = FamilyBackupCodec.encode(samplePayload(), 1234)
        val root = Json.parseToJsonElement(current).jsonObject
        val legacyPayload = buildJsonObject {
            root.getValue("payload").jsonObject.forEach { (key, value) ->
                if (key != "settings" && key != "preferences") put(key, value)
            }
        }
        val payloadJson = Json.encodeToString(JsonElement.serializer(), legacyPayload)
        val checksum = java.security.MessageDigest.getInstance("SHA-256")
            .digest(payloadJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val legacyEnvelope = buildJsonObject {
            put("format", JsonPrimitive(FamilyBackupCodec.FORMAT))
            put("schemaVersion", JsonPrimitive(1))
            put("createdAtEpochMillis", root.getValue("createdAtEpochMillis"))
            put("payload", legacyPayload)
            put("checksumSha256", JsonPrimitive(checksum))
        }

        val migrated = FamilyBackupCodec.decode(Json.encodeToString(JsonElement.serializer(), legacyEnvelope))

        assertEquals(FamilyBackupCodec.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(PianoSettings(), migrated.payload.settings)
        assertEquals(FamilyBackupPreferences(), migrated.payload.preferences)
        assertEquals("learner-a", migrated.payload.learners.first().learnerId)
    }

    @Test
    fun partialBackupWithOrphanedLearnerDataIsRejectedBeforeRestore() {
        val invalid = samplePayload().copy(learners = emptyList())

        assertFalse(runCatching { FamilyBackupCodec.encode(invalid, 1234) }.isSuccess)
    }

    @Test
    fun unassignedLegacyMasteryIsPreservedWithoutAssigningItToALearner() {
        val payload = samplePayload().copy(
            preferences = FamilyBackupPreferences(
                legacyMastery = mapOf(
                    "skill_attempts" to "s:[]",
                    "learner_scope_migration_decided" to "b:false"
                )
            )
        )

        val restored = FamilyBackupCodec.decode(FamilyBackupCodec.encode(payload, 1234)).payload

        assertEquals("s:[]", restored.preferences.legacyMastery["skill_attempts"])
        assertEquals("b:false", restored.preferences.legacyMastery["learner_scope_migration_decided"])
    }

    @Test
    fun backupRejectsPreferencesOutsideExplicitAllowList() {
        val invalid = samplePayload().copy(
            preferences = FamilyBackupPreferences(
                learnerSession = mapOf("api_token" to "s:must-not-be-exported")
            )
        )

        assertFalse(runCatching { FamilyBackupCodec.encode(invalid, 1234) }.isSuccess)
    }

    @Test
    fun databaseRestoreReplacesFamilyAndPreservesMultiLearnerRelationships() = runBlocking {
        val repository = ProgressRepository(database)
        database.familyDao().upsert(FamilyEntity("old-family", 1, 1))
        repository.saveLearnerProfile(
            LearnerProfileEntity("old-learner", "old-family", "قدیمی", createdAtEpochMillis = 2, updatedAtEpochMillis = 2)
        )
        repository.saveLessonProgress(LessonProgressEntity("old-progress", "old-learner", "old-lesson", lastAccessedAtEpochMillis = 3))

        FamilyBackupManager.replaceDatabase(database, samplePayload())

        assertTrue(database.familyDao().getAll().none { it.familyId == "old-family" })
        assertEquals(setOf("learner-a", "learner-b"), database.learnerProfileDao().getAll().map { it.learnerId }.toSet())
        assertEquals("family-a", database.learnerProfileDao().findById("learner-a")?.familyId)
        assertEquals("learner-a", database.lessonProgressDao().getAll().single().learnerId)
        assertEquals("evaluation-a", database.evaluationEvidenceDao().getAll().single().evaluationId)
    }

    @Test
    fun failedDatabaseReplacementRollsBackWithoutChangingExistingData() = runBlocking {
        val repository = ProgressRepository(database)
        database.familyDao().upsert(FamilyEntity("old-family", 1, 1))
        repository.saveLearnerProfile(
            LearnerProfileEntity("old-learner", "old-family", "قدیمی", createdAtEpochMillis = 2, updatedAtEpochMillis = 2)
        )
        repository.saveLessonProgress(LessonProgressEntity("old-progress", "old-learner", "old-lesson", lastAccessedAtEpochMillis = 3))

        val invalid = samplePayload().copy(families = emptyList())
        val restoreFailed = try {
            FamilyBackupManager.replaceDatabase(database, invalid)
            false
        } catch (_: Exception) {
            true
        }
        assertTrue(restoreFailed)

        assertEquals("old-family", database.familyDao().getAll().single().familyId)
        assertEquals("old-learner", database.learnerProfileDao().getAll().single().learnerId)
        assertEquals("old-progress", database.lessonProgressDao().getAll().single().progressId)
    }

    private fun samplePayload() = FamilyBackupPayload(
        families = listOf(FamilyEntity("family-a", 1, 2)),
        learners = listOf(
            LearnerProfileEntity("learner-a", "family-a", "هنرجوی اول", createdAtEpochMillis = 3, updatedAtEpochMillis = 3),
            LearnerProfileEntity("learner-b", "family-a", "هنرجوی دوم", createdAtEpochMillis = 4, updatedAtEpochMillis = 4)
        ),
        lessonProgress = listOf(
            LessonProgressEntity("progress-a", "learner-a", "lesson-a", "[\"step-1\"]", true, 10, 11)
        ),
        practiceSessions = listOf(
            PracticeSessionEntity("session-a", "learner-a", "lesson-a", 12, 20, 8, 4, 3, 1)
        ),
        achievements = emptyList(),
        dailyMissions = emptyList(),
        songProgress = emptyList(),
        learnerGoals = emptyList(),
        learningPathAssignments = emptyList(),
        evaluationEvidence = listOf(
            EvaluationEvidenceEntity("evaluation-a", "learner-a", "session-a", "lesson-a", "step-1", "pitch", 15, "CORRECT", "60", "60", false)
        ),
        settings = PianoSettings(),
        preferences = FamilyBackupPreferences()
    )
}
