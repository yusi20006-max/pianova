package ir.pianova.app.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EvaluationEvidenceDao {
    @Query("SELECT * FROM evaluation_evidence")
    suspend fun getAll(): List<EvaluationEvidenceEntity>

    @Upsert suspend fun upsert(evidence: EvaluationEvidenceEntity)

    @Upsert suspend fun upsertAll(evidence: List<EvaluationEvidenceEntity>)

    @Query("SELECT * FROM evaluation_evidence WHERE learnerId = :learnerId ORDER BY occurredAtEpochMillis DESC")
    fun observeForLearner(learnerId: String): Flow<List<EvaluationEvidenceEntity>>

    @Query("SELECT * FROM evaluation_evidence WHERE learnerId = :learnerId AND occurredAtEpochMillis >= :from AND occurredAtEpochMillis < :until ORDER BY occurredAtEpochMillis")
    fun observeBetween(learnerId: String, from: Long, until: Long): Flow<List<EvaluationEvidenceEntity>>

    @Query("DELETE FROM evaluation_evidence WHERE learnerId = :learnerId")
    suspend fun deleteForLearner(learnerId: String)
}
