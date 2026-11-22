package ir.pianova.app.domain.family

data class LearnerGoal(
    val learnerId: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    init {
        require(learnerId.isNotBlank()) { "learnerId must not be blank" }
        require(title.isNotBlank()) { "goal title must not be blank" }
        require(createdAtEpochMillis >= 0) { "createdAtEpochMillis must not be negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis must not precede creation"
        }
    }
}
