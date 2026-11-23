package ir.pianova.app.data.persistence

import androidx.room.withTransaction
import ir.pianova.app.core.database.EvaluationEvidenceEntity
import ir.pianova.app.core.database.DailyMissionDao
import ir.pianova.app.core.database.DailyMissionEntity
import ir.pianova.app.core.database.LearnerProfileDao
import ir.pianova.app.core.database.LearnerProfileEntity
import ir.pianova.app.core.database.LessonProgressDao
import ir.pianova.app.core.database.LessonProgressEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionDao
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.core.database.SongProgressDao
import ir.pianova.app.core.database.SongProgressEntity
import ir.pianova.app.core.database.UserAchievementDao
import ir.pianova.app.core.database.UserAchievementEntity
import ir.pianova.app.domain.progress.EvaluationEvidence
import ir.pianova.app.domain.progress.LearnerProgressAggregator
import ir.pianova.app.domain.progress.LearnerProgressSnapshot
import ir.pianova.app.domain.progress.PracticeEvidence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

/** All learner-owned progress queries require an explicit stable learnerId. */
class ProgressRepository(private val database: PianovaDatabase) {
    private val profiles: LearnerProfileDao = database.learnerProfileDao()
    private val lessons: LessonProgressDao = database.lessonProgressDao()
    private val sessions: PracticeSessionDao = database.practiceSessionDao()
    private val achievements: UserAchievementDao = database.userAchievementDao()
    private val missions: DailyMissionDao = database.dailyMissionDao()
    private val songs: SongProgressDao = database.songProgressDao()
    private val evaluationEvidence = database.evaluationEvidenceDao()

    fun observeLearners(familyId: String): Flow<List<LearnerProfileEntity>> =
        profiles.observeForFamily(familyId)
    fun observeLearner(learnerId: String): Flow<LearnerProfileEntity?> =
        profiles.observeById(learnerId)
    suspend fun saveLearnerProfile(profile: LearnerProfileEntity) {
        database.withTransaction {
            require(database.familyDao().findById(profile.familyId) != null) {
                "Family does not exist"
            }
            val existing = profiles.findById(profile.learnerId)
            require(existing == null || existing.familyId == profile.familyId) {
                "A learner cannot be reassigned to another family"
            }
            profiles.upsert(profile)
        }
    }

    fun observeLessonProgress(learnerId: String): Flow<List<LessonProgressEntity>> =
        lessons.observeForLearner(learnerId)
    fun observeLesson(learnerId: String, lessonId: String): Flow<LessonProgressEntity?> =
        lessons.observeLesson(learnerId, lessonId)
    suspend fun saveLessonProgress(progress: LessonProgressEntity) {
        requireLearner(progress.learnerId)
        lessons.upsert(progress)
    }

    fun observePracticeSessions(learnerId: String): Flow<List<PracticeSessionEntity>> =
        sessions.observeForLearner(learnerId)
    fun observePracticeSessionsBetween(learnerId: String, from: Long, until: Long): Flow<List<PracticeSessionEntity>> =
        sessions.observeBetween(learnerId, from, until)
    suspend fun savePracticeSession(session: PracticeSessionEntity) {
        requireLearner(session.learnerId)
        sessions.upsert(session)
    }

    fun observeEvaluationEvidence(learnerId: String): Flow<List<EvaluationEvidenceEntity>> =
        evaluationEvidence.observeForLearner(learnerId)

    suspend fun saveEvaluationEvidence(evidence: EvaluationEvidenceEntity) {
        requireLearner(evidence.learnerId)
        require(evidence.evaluationId.isNotBlank() && evidence.sessionId.isNotBlank())
        require(evidence.lessonId.isNotBlank() && evidence.stepId.isNotBlank())
        require(evidence.occurredAtEpochMillis >= 0)
        evaluationEvidence.upsert(evidence)
    }

    fun observeLearnerProgress(
        learnerId: String,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<LearnerProgressSnapshot> = combine(
        sessions.observeForLearner(learnerId),
        lessons.observeForLearner(learnerId),
        evaluationEvidence.observeForLearner(learnerId)
    ) { practiceRows, lessonRows, evaluationRows ->
        LearnerProgressAggregator.aggregate(
            learnerId = learnerId,
            sessions = practiceRows.map { row ->
                PracticeEvidence(
                    sessionId = row.sessionId,
                    learnerId = row.learnerId,
                    startedAtEpochMillis = row.startedAtEpochMillis,
                    endedAtEpochMillis = row.endedAtEpochMillis,
                    activeDurationMillis = row.durationMillis.coerceAtLeast(0)
                )
            },
            evaluations = evaluationRows.map { row ->
                EvaluationEvidence(
                    evaluationId = row.evaluationId,
                    learnerId = row.learnerId,
                    sessionId = row.sessionId,
                    lessonId = row.lessonId,
                    stepId = row.stepId,
                    skillId = row.skillId,
                    occurredAtEpochMillis = row.occurredAtEpochMillis,
                    status = row.status,
                    expectedMidiNotes = row.expectedMidiNotesCsv.decodeMidiNotes(),
                    receivedMidiNotes = row.receivedMidiNotesCsv.decodeMidiNotes(),
                    answerRevealingHintUsed = row.answerRevealingHintUsed
                )
            },
            completedLessonIds = lessonRows.filter { it.isCompleted }.map { it.lessonId }.toSet(),
            zoneId = zoneId
        )
    }

    private fun String.decodeMidiNotes(): List<Int> =
        split(',').mapNotNull { it.trim().toIntOrNull() }

    fun observeAchievements(learnerId: String): Flow<List<UserAchievementEntity>> =
        achievements.observeForLearner(learnerId)
    suspend fun saveAchievement(achievement: UserAchievementEntity) {
        requireLearner(achievement.learnerId)
        achievements.upsert(achievement)
    }

    fun observeDailyMissions(learnerId: String, dateEpochDay: Long): Flow<List<DailyMissionEntity>> =
        missions.observeForDay(learnerId, dateEpochDay)
    suspend fun saveDailyMission(mission: DailyMissionEntity) {
        requireLearner(mission.learnerId)
        missions.upsert(mission)
    }

    fun observeSongProgress(learnerId: String): Flow<List<SongProgressEntity>> =
        songs.observeForLearner(learnerId)
    suspend fun saveSongProgress(progress: SongProgressEntity) {
        requireLearner(progress.learnerId)
        songs.upsert(progress)
    }

    private suspend fun requireLearner(learnerId: String) {
        require(learnerId.isNotBlank())
        require(profiles.findById(learnerId) != null) { "Learner does not exist" }
    }

    suspend fun deleteLearnerProgress(learnerId: String) {
        require(learnerId.isNotBlank())
        database.withTransaction {
            lessons.deleteForLearner(learnerId)
            sessions.deleteForLearner(learnerId)
            evaluationEvidence.deleteForLearner(learnerId)
            achievements.deleteForLearner(learnerId)
            missions.deleteForLearner(learnerId)
            songs.deleteForLearner(learnerId)
            profiles.delete(learnerId)
        }
    }
}
