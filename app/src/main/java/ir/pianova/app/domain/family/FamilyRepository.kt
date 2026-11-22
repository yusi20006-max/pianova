package ir.pianova.app.domain.family

import kotlinx.coroutines.flow.Flow

interface FamilyRepository {
    fun observeLocalFamily(): Flow<Family?>
    fun observeFamily(familyId: String): Flow<Family?>
    fun observeLearners(familyId: String): Flow<List<LearnerProfile>>
    suspend fun saveFamily(family: Family)
    suspend fun saveLearner(learner: LearnerProfile)
    /** Deletes only this learner and the learner-owned records attached to it. */
    suspend fun deleteLearner(learnerId: String): Boolean
}
