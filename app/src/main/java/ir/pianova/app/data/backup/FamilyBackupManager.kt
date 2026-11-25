package ir.pianova.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import ir.pianova.app.core.database.*
import ir.pianova.app.core.datastore.PianoSettings
import ir.pianova.app.core.datastore.SettingsDataStore
import ir.pianova.app.domain.lesson.SkillAttempt
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.content
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

@Serializable
data class FamilyBackupPreferences(
    val learnerSession: Map<String, String> = emptyMap(),
    val family: Map<String, String> = emptyMap(),
    val legacy: Map<String, String> = emptyMap(),
    val lessonMastery: Map<String, String> = emptyMap()
)

@Serializable
data class FamilyBackupPayload(
    val families: List<FamilyEntity>,
    val learners: List<LearnerProfileEntity>,
    val lessonProgress: List<LessonProgressEntity>,
    val practiceSessions: List<PracticeSessionEntity>,
    val achievements: List<UserAchievementEntity>,
    val dailyMissions: List<DailyMissionEntity>,
    val songProgress: List<SongProgressEntity>,
    val learnerGoals: List<LearnerGoalEntity>,
    val learningPathAssignments: List<LearningPathAssignmentEntity>,
    val evaluationEvidence: List<EvaluationEvidenceEntity>,
    val settings: PianoSettings = PianoSettings(),
    val preferences: FamilyBackupPreferences = FamilyBackupPreferences()
)

@Serializable
data class FamilyBackupEnvelope(
    val format: String,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val payload: FamilyBackupPayload,
    val checksumSha256: String
)

data class FamilyBackupPreview(
    val createdAtEpochMillis: Long,
    val familyCount: Int,
    val learnerCount: Int,
    val lessonRecordCount: Int,
    val practiceSessionCount: Int,
    val evaluationCount: Int
)

/** Strict, versioned JSON format. Only explicitly modeled local data is exported. */
object FamilyBackupCodec {
    const val FORMAT = "pianova-family-backup"
    const val CURRENT_SCHEMA_VERSION = 2
    const val MAX_BACKUP_BYTES = 25 * 1024 * 1024

    private val sessionKeysForValidation = setOf("last_active_learner_id", "legacy_audience_assigned")

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = true
        isLenient = false
    }

    fun encode(payload: FamilyBackupPayload, createdAtEpochMillis: Long = System.currentTimeMillis()): String {
        require(createdAtEpochMillis >= 0) { "Invalid backup timestamp" }
        validate(payload)
        val payloadJson = json.encodeToString(payload)
        val envelope = FamilyBackupEnvelope(
            format = FORMAT,
            schemaVersion = CURRENT_SCHEMA_VERSION,
            createdAtEpochMillis = createdAtEpochMillis,
            payload = payload,
            checksumSha256 = sha256(payloadJson)
        )
        val encoded = json.encodeToString(envelope)
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup file exceeds the supported size limit" }
        return encoded
    }

    fun decode(raw: String): FamilyBackupEnvelope {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup file is too large" }
        val rootElement = runCatching { json.parseToJsonElement(raw) }
            .getOrElse { throw IllegalArgumentException("Backup JSON is invalid or incomplete", it) }
        val root = runCatching { rootElement.jsonObject }
            .getOrElse { throw IllegalArgumentException("Backup envelope must be a JSON object") }
        val format = root["format"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Backup format marker is missing")
        require(format == FORMAT) { "This file is not a Pianova family backup" }
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Backup schema version is missing")
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
            "Unsupported backup schema version: $schemaVersion"
        }
        val payloadElement = root["payload"]
            ?: throw IllegalArgumentException("Backup payload is missing")
        val storedChecksum = root["checksumSha256"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Backup integrity checksum is missing")
        val actualChecksum = sha256(json.encodeToString(JsonElement.serializer(), payloadElement))
        require(actualChecksum.equals(storedChecksum, ignoreCase = true)) {
            "Backup integrity check failed"
        }
        val parsed = runCatching { json.decodeFromString<FamilyBackupEnvelope>(raw) }
            .getOrElse { throw IllegalArgumentException("Backup payload is invalid or incomplete", it) }
        require(parsed.createdAtEpochMillis >= 0) { "Backup timestamp is invalid" }
        // Version 1 did not persist DataStore or learner preference snapshots.
        // Missing fields use safe defaults; all record IDs and relationships remain unchanged.
        val migrated = when (schemaVersion) {
            1 -> parsed.copy(schemaVersion = CURRENT_SCHEMA_VERSION)
            CURRENT_SCHEMA_VERSION -> parsed
            else -> throw IllegalArgumentException("Unsupported backup schema version: $schemaVersion")
        }
        validate(migrated.payload)
        return migrated
    }

    private fun validate(payload: FamilyBackupPayload) {
        require(payload.families.isNotEmpty()) { "Backup does not contain family metadata" }
        val familyIds = payload.families.map { it.familyId }
        val learnerIds = payload.learners.map { it.learnerId }
        require(familyIds.all(String::isNotBlank) && familyIds.distinct().size == familyIds.size) {
            "Family identifiers are missing or duplicated"
        }
        require(learnerIds.all(String::isNotBlank) && learnerIds.distinct().size == learnerIds.size) {
            "Learner identifiers are missing or duplicated"
        }
        require(payload.learners.all { it.familyId in familyIds }) {
            "Backup contains a learner without its family"
        }
        require(payload.lessonProgress.all { it.learnerId in learnerIds }) {
            "Backup contains lesson progress for an unknown learner"
        }
        require(payload.practiceSessions.all { it.learnerId in learnerIds }) {
            "Backup contains a practice session for an unknown learner"
        }
        require(payload.achievements.all { it.learnerId in learnerIds }) {
            "Backup contains an achievement for an unknown learner"
        }
        require(payload.dailyMissions.all { it.learnerId in learnerIds }) {
            "Backup contains a daily mission for an unknown learner"
        }
        require(payload.songProgress.all { it.learnerId in learnerIds }) {
            "Backup contains song progress for an unknown learner"
        }
        require(payload.learnerGoals.all { it.learnerId in learnerIds }) {
            "Backup contains a goal for an unknown learner"
        }
        require(payload.learningPathAssignments.all { it.learnerId in learnerIds }) {
            "Backup contains a learning-path assignment for an unknown learner"
        }
        require(payload.evaluationEvidence.all { it.learnerId in learnerIds }) {
            "Backup contains evaluation evidence for an unknown learner"
        }
        require(payload.lessonProgress.map { it.learnerId to it.lessonId }.distinct().size == payload.lessonProgress.size) {
            "Backup contains duplicate lesson-progress records"
        }
        require(payload.practiceSessions.map { it.sessionId }.distinct().size == payload.practiceSessions.size) {
            "Backup contains duplicate practice-session identifiers"
        }
        require(payload.lessonProgress.map { it.progressId }.distinct().size == payload.lessonProgress.size) {
            "Backup contains duplicate lesson-progress identifiers"
        }
        require(payload.achievements.map { it.learnerId to it.achievementId }.distinct().size == payload.achievements.size) {
            "Backup contains duplicate achievements"
        }
        require(payload.dailyMissions.map { Triple(it.learnerId, it.missionId, it.dateEpochDay) }.distinct().size == payload.dailyMissions.size) {
            "Backup contains duplicate daily missions"
        }
        require(payload.songProgress.map { it.learnerId to it.songId }.distinct().size == payload.songProgress.size) {
            "Backup contains duplicate song-progress records"
        }
        require(payload.learningPathAssignments.map { it.assignmentId }.distinct().size == payload.learningPathAssignments.size) {
            "Backup contains duplicate learning-path assignments"
        }
        require(payload.evaluationEvidence.map { it.evaluationId }.distinct().size == payload.evaluationEvidence.size) {
            "Backup contains duplicate evaluation identifiers"
        }
        require(payload.learnerGoals.map { it.learnerId }.distinct().size == payload.learnerGoals.size) {
            "Backup contains duplicate learner goals"
        }
        require(payload.settings.volume in 0f..1f) { "Backup volume setting is invalid" }
        require(payload.settings.noteLabelMode in setOf("latin", "persian", "both")) {
            "Backup note-label setting is invalid"
        }
        require(payload.settings.defaultOctave in 0..8 && payload.settings.instrument.isNotBlank()) {
            "Backup piano settings are invalid"
        }
        val activeLearner = payload.preferences.learnerSession["last_active_learner_id"]
        require(activeLearner == null || activeLearner.removePrefix("s:") in learnerIds) {
            "Backup active-learner preference refers to a missing learner"
        }
        require(payload.preferences.lessonMastery.keys.all { it in learnerIds }) {
            "Backup mastery data refers to an unknown learner"
        }
        val allowedSessionKey = { key: String ->
            key in sessionKeysForValidation || listOf("audience.", "destination.", "volume.", "label_mode.", "first_midi.")
                .any(key::startsWith)
        }
        require(payload.preferences.learnerSession.keys.all(allowedSessionKey)) {
            "Backup contains an unsupported learner preference"
        }
        require(payload.preferences.family.keys.all { it == "onboarding_complete" }) {
            "Backup contains an unsupported family preference"
        }
        require(payload.preferences.legacy.keys.all { it == "lesson_audience" }) {
            "Backup contains an unsupported legacy preference"
        }
        require(payload.preferences.learnerSession.keys
            .filter { it.startsWith("audience.") || it.startsWith("destination.") || it.startsWith("volume.") ||
                it.startsWith("label_mode.") || it.startsWith("first_midi.") }
            .all { key -> learnerIds.any { key.endsWith(".$it") } }) {
            "Backup contains a preference for an unknown learner"
        }
        require(payload.preferences.learnerSession.values.all { it.length >= 2 && it[1] == ':' && it[0] in "sbilf" } &&
            payload.preferences.family.values.all { it.length >= 2 && it[1] == ':' && it[0] in "sbilf" } &&
            payload.preferences.legacy.values.all { it.length >= 2 && it[1] == ':' && it[0] in "sbilf" }) {
            "Backup contains an invalid preference value"
        }
        require(payload.preferences.learnerSession["last_active_learner_id"]?.startsWith("s:") != false) {
            "Backup active-learner preference has an invalid type"
        }
        require(payload.preferences.learnerSession["legacy_audience_assigned"]?.startsWith("b:") != false) {
            "Backup legacy-audience preference has an invalid type"
        }
        require(payload.preferences.family["onboarding_complete"]?.startsWith("b:") != false) {
            "Backup onboarding preference has an invalid type"
        }
        require(payload.preferences.legacy["lesson_audience"]?.startsWith("s:") != false) {
            "Backup legacy audience preference has an invalid type"
        }
        payload.preferences.learnerSession.forEach { (key, value) ->
            val raw = value.substring(2)
            when {
                key.startsWith("audience.") -> require(value.startsWith("s:") && raw in setOf("children", "teens", "adults")) {
                    "Backup contains an invalid learner audience"
                }
                key.startsWith("destination.") -> require(value.startsWith("s:") &&
                    raw in setOf("home", "learning", "paths", "practice", "library", "progress", "more", "settings")) {
                    "Backup contains an invalid learner destination"
                }
                key.startsWith("volume.") -> require(value.startsWith("f:") &&
                    raw.toFloatOrNull()?.let { it.isFinite() && it in 0.05f..1f } == true) {
                    "Backup contains an invalid learner volume"
                }
                key.startsWith("label_mode.") -> require(value.startsWith("i:") && raw.toIntOrNull()?.let { it in 0..2 } == true) {
                    "Backup contains an invalid note-label preference"
                }
                key.startsWith("first_midi.") -> require(value.startsWith("i:") && raw.toIntOrNull()?.let { it in 21..84 } == true) {
                    "Backup contains an invalid keyboard range preference"
                }
            }
        }
        require(payload.preferences.legacy["lesson_audience"]?.removePrefix("s:") in setOf(null, "children", "teens", "adults")) {
            "Backup contains an invalid legacy audience"
        }
        require(payload.preferences.lessonMastery.values.all { raw ->
            runCatching { Json.decodeFromString(ListSerializer(SkillAttempt.serializer()), raw) }.isSuccess
        }) {
            "Backup contains invalid learner mastery data"
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

object FamilyBackupManager {
    private val sessionKeys = setOf("last_active_learner_id", "legacy_audience_assigned")

    suspend fun exportToUri(context: Context, database: PianovaDatabase, uri: Uri) {
        val payload = snapshot(context, database)
        val bytes = FamilyBackupCodec.encode(payload).toByteArray(Charsets.UTF_8)
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Cannot open the selected backup destination")
        output.use { it.write(bytes); it.flush() }
    }

    suspend fun previewFromUri(context: Context, uri: Uri): FamilyBackupPreview {
        val envelope = FamilyBackupCodec.decode(readText(context, uri))
        return envelope.toPreview()
    }

    suspend fun restoreFromUri(context: Context, database: PianovaDatabase, uri: Uri) {
        val envelope = FamilyBackupCodec.decode(readText(context, uri))
        val incoming = envelope.payload
        val previous = snapshot(context, database)
        val previousLearnerIds = previous.learners.map { it.learnerId }
        val affectedLearnerIds = (previousLearnerIds + incoming.learners.map { it.learnerId }).distinct()
        try {
            replaceDatabase(database, incoming)
            SettingsDataStore(context).replace(incoming.settings)
            replacePreferences(context, incoming.preferences, affectedLearnerIds)
        } catch (failure: Throwable) {
            val rollbackFailure = runCatching {
                replaceDatabase(database, previous)
                SettingsDataStore(context).replace(previous.settings)
                replacePreferences(context, previous.preferences, affectedLearnerIds)
            }.exceptionOrNull()
            if (rollbackFailure != null) {
                throw IllegalStateException(
                    "Restore failed and automatic rollback also failed. Keep the original backup file.",
                    failure
                ).also { it.addSuppressed(rollbackFailure) }
            }
            if (failure is CancellationException) throw failure
            throw IllegalStateException("Restore failed. Existing family data was restored.", failure)
        }
    }

    private suspend fun snapshot(context: Context, database: PianovaDatabase): FamilyBackupPayload {
        val records = database.withTransaction {
            FamilyBackupPayload(
                families = database.familyDao().getAll(),
                learners = database.learnerProfileDao().getAll(),
                lessonProgress = database.lessonProgressDao().getAll(),
                practiceSessions = database.practiceSessionDao().getAll(),
                achievements = database.userAchievementDao().getAll(),
                dailyMissions = database.dailyMissionDao().getAll(),
                songProgress = database.songProgressDao().getAll(),
                learnerGoals = database.learnerGoalDao().getAll(),
                learningPathAssignments = database.learningPathAssignmentDao().getAll(),
                evaluationEvidence = database.evaluationEvidenceDao().getAll(),
                settings = PianoSettings(),
                preferences = FamilyBackupPreferences()
            )
        }
        return records.copy(
            settings = SettingsDataStore(context).currentSettings(),
            preferences = capturePreferences(context, records.learners.map { it.learnerId })
        )
    }

    internal suspend fun replaceDatabase(database: PianovaDatabase, payload: FamilyBackupPayload) {
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            listOf(
                "evaluation_evidence",
                "learning_path_assignments",
                "learner_goals",
                "lesson_progress",
                "practice_sessions",
                "user_achievements",
                "daily_missions",
                "song_progress",
                "learner_profiles",
                "families"
            ).forEach { table -> sql.execSQL("DELETE FROM $table") }

            payload.families.forEach { database.familyDao().upsert(it) }
            payload.learners.forEach { database.learnerProfileDao().upsert(it) }
            payload.lessonProgress.forEach { database.lessonProgressDao().upsert(it) }
            payload.practiceSessions.forEach { database.practiceSessionDao().upsert(it) }
            payload.achievements.forEach { database.userAchievementDao().upsert(it) }
            payload.dailyMissions.forEach { database.dailyMissionDao().upsert(it) }
            payload.songProgress.forEach { database.songProgressDao().upsert(it) }
            payload.learnerGoals.forEach { database.learnerGoalDao().upsert(it) }
            payload.learningPathAssignments.forEach { database.learningPathAssignmentDao().insert(it) }
            payload.evaluationEvidence.forEach { database.evaluationEvidenceDao().upsert(it) }
        }
    }

    private fun capturePreferences(context: Context, learnerIds: List<String>): FamilyBackupPreferences {
        val app = context.applicationContext
        val session = app.getSharedPreferences("pianova_learner_sessions", Context.MODE_PRIVATE)
        val family = app.getSharedPreferences("pianova_family", Context.MODE_PRIVATE)
        val legacy = app.getSharedPreferences("pianova_preferences", Context.MODE_PRIVATE)
        val sessionValues = session.all.filterKeys { key ->
            key in sessionKeys || listOf("audience.", "destination.", "volume.", "label_mode.", "first_midi.")
                .any(key::startsWith)
        }.mapValues { (_, value) -> encodePreference(value) }
        val familyValues = family.all.filterKeys { it == "onboarding_complete" }
            .mapValues { (_, value) -> encodePreference(value) }
        val legacyValues = legacy.all.filterKeys { it == "lesson_audience" }
            .mapValues { (_, value) -> encodePreference(value) }
        val mastery = learnerIds.associateNotNull { learnerId ->
            val value = app.getSharedPreferences(
                "pianova_lesson_mastery.$learnerId", Context.MODE_PRIVATE
            ).getString("skill_attempts", null)
            value?.let { learnerId to it }
        }
        return FamilyBackupPreferences(sessionValues, familyValues, legacyValues, mastery)
    }

    private fun replacePreferences(
        context: Context,
        snapshot: FamilyBackupPreferences,
        affectedLearnerIds: List<String>
    ) {
        val app = context.applicationContext
        val session = app.getSharedPreferences("pianova_learner_sessions", Context.MODE_PRIVATE)
        val family = app.getSharedPreferences("pianova_family", Context.MODE_PRIVATE)
        val legacy = app.getSharedPreferences("pianova_preferences", Context.MODE_PRIVATE)
        check(session.edit().clear().commit()) { "Could not restore learner preferences" }
        check(family.edit().clear().commit()) { "Could not restore family preferences" }
        check(legacy.edit().clear().commit()) { "Could not restore legacy preferences" }
        snapshot.learnerSession.forEach { (key, value) -> putPreference(session, key, value) }
        snapshot.family.forEach { (key, value) -> putPreference(family, key, value) }
        snapshot.legacy.forEach { (key, value) -> putPreference(legacy, key, value) }
        affectedLearnerIds.forEach { learnerId ->
            val preferences = app.getSharedPreferences(
                "pianova_lesson_mastery.$learnerId", Context.MODE_PRIVATE
            )
            check(preferences.edit().clear().commit()) { "Could not clear previous mastery data" }
            snapshot.lessonMastery[learnerId]?.let { raw ->
                check(preferences.edit().putString("skill_attempts", raw).commit()) {
                    "Could not restore mastery data"
                }
            }
        }
    }

    private fun encodePreference(value: Any?): String = when (value) {
        is String -> "s:$value"
        is Boolean -> "b:$value"
        is Int -> "i:$value"
        is Long -> "l:$value"
        is Float -> "f:$value"
        else -> throw IllegalArgumentException("Unsupported preference value")
    }

    private fun putPreference(preferences: android.content.SharedPreferences, key: String, encoded: String) {
        val separator = encoded.indexOf(':')
        require(separator == 1) { "Invalid preference value" }
        val value = encoded.substring(2)
        val editor = preferences.edit()
        when (encoded[0]) {
            's' -> editor.putString(key, value)
            'b' -> editor.putBoolean(key, value.toBooleanStrict())
            'i' -> editor.putInt(key, value.toInt())
            'l' -> editor.putLong(key, value.toLong())
            'f' -> editor.putFloat(key, value.toFloat())
            else -> throw IllegalArgumentException("Unknown preference value type")
        }
        check(editor.commit()) { "Could not restore a preference" }
    }

    private fun readText(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open the selected backup file")
        val bytes = input.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= FamilyBackupCodec.MAX_BACKUP_BYTES) { "Backup file is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun FamilyBackupEnvelope.toPreview() = FamilyBackupPreview(
        createdAtEpochMillis = createdAtEpochMillis,
        familyCount = payload.families.size,
        learnerCount = payload.learners.size,
        lessonRecordCount = payload.lessonProgress.size,
        practiceSessionCount = payload.practiceSessions.size,
        evaluationCount = payload.evaluationEvidence.size
    )

    private inline fun <K, V : Any, R : Any> Iterable<K>.associateNotNull(
        transform: (K) -> Pair<R, V>?
    ): Map<R, V> = mapNotNull(transform).toMap()
}
