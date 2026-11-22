package ir.pianova.app.data.persistence

import androidx.room.withTransaction
import ir.pianova.app.core.database.FamilyEntity
import ir.pianova.app.core.database.LearnerProfileEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.domain.family.Family
import ir.pianova.app.domain.family.FamilyRepository
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFamilyRepository(private val database: PianovaDatabase) : FamilyRepository {
    private val families = database.familyDao()
    private val learners = database.learnerProfileDao()

    override fun observeFamily(familyId: String): Flow<Family?> =
        families.observeById(familyId).map { it?.toDomain() }

    override fun observeLearners(familyId: String): Flow<List<LearnerProfile>> =
        learners.observeForFamily(familyId).map { profiles -> profiles.map { it.toDomain() } }

    override suspend fun saveFamily(family: Family) {
        families.upsert(family.toEntity())
    }

    override suspend fun saveLearner(learner: LearnerProfile) {
        require(families.findById(learner.familyId) != null) {
            "Family \${learner.familyId} does not exist"
        }
        learners.upsert(learner.toEntity())
    }

    override suspend fun deleteLearner(learnerId: String): Boolean {
        require(learnerId.isNotBlank())
        return database.withTransaction {
            if (learners.findById(learnerId) == null) return@withTransaction false
            database.lessonProgressDao().deleteForLearner(learnerId)
            database.practiceSessionDao().deleteForLearner(learnerId)
            database.userAchievementDao().deleteForLearner(learnerId)
            database.dailyMissionDao().deleteForLearner(learnerId)
            database.songProgressDao().deleteForLearner(learnerId)
            learners.delete(learnerId)
            true
        }
    }
}

private fun Family.toEntity() = FamilyEntity(
    familyId = familyId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun FamilyEntity.toDomain() = Family(
    familyId = familyId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun LearnerProfile.toEntity() = LearnerProfileEntity(
    learnerId = learnerId,
    familyId = familyId,
    displayName = displayName,
    birthDateEpochDay = birthDateEpochDay,
    audienceCategory = audience.storageValue,
    avatarRef = avatarRef,
    initialLevelId = initialLevelId,
    currentLevelId = currentLevelId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun LearnerProfileEntity.toDomain() = LearnerProfile(
    learnerId = learnerId,
    familyId = familyId,
    displayName = displayName,
    audience = LearnerAudience.fromStorageValue(audienceCategory),
    birthDateEpochDay = birthDateEpochDay,
    avatarRef = avatarRef,
    initialLevelId = initialLevelId,
    currentLevelId = currentLevelId,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)
