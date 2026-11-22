package ir.pianova.app.data.persistence

import androidx.room.withTransaction
import ir.pianova.app.core.database.LearnerGoalDao
import ir.pianova.app.core.database.LearnerGoalEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.domain.family.LearnerGoal
import ir.pianova.app.domain.family.LearnerGoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLearnerGoalRepository(private val database: PianovaDatabase) : LearnerGoalRepository {
    private val goals: LearnerGoalDao = database.learnerGoalDao()

    override fun observeGoal(learnerId: String): Flow<LearnerGoal?> =
        goals.observeForLearner(learnerId).map { it?.toDomain() }

    override suspend fun saveGoal(goal: LearnerGoal) {
        database.withTransaction {
            require(database.learnerProfileDao().findById(goal.learnerId) != null) {
                "Learner does not exist"
            }
            goals.upsert(goal.toEntity())
        }
    }

    override suspend fun deleteGoal(learnerId: String) {
        require(learnerId.isNotBlank())
        goals.deleteForLearner(learnerId)
    }
}

private fun LearnerGoal.toEntity() = LearnerGoalEntity(
    learnerId = learnerId,
    title = title,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun LearnerGoalEntity.toDomain() = LearnerGoal(
    learnerId = learnerId,
    title = title,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)
