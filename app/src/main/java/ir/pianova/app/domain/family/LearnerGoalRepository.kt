package ir.pianova.app.domain.family

import kotlinx.coroutines.flow.Flow

interface LearnerGoalRepository {
    fun observeGoal(learnerId: String): Flow<LearnerGoal?>
    suspend fun saveGoal(goal: LearnerGoal)
    suspend fun deleteGoal(learnerId: String)
}
