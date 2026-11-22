package ir.pianova.app.domain.family

enum class LearnerAudience(val storageValue: String) {
    CHILD("child"),
    TEEN("teen"),
    ADULT("adult"),
    UNSPECIFIED("unspecified");

    companion object {
        fun fromStorageValue(value: String?): LearnerAudience =
            values().firstOrNull { it.storageValue == value } ?: UNSPECIFIED
    }
}

data class Family(
    val familyId: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    init {
        require(familyId.isNotBlank()) { "familyId must not be blank" }
        require(createdAtEpochMillis >= 0) { "createdAtEpochMillis must not be negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis must not precede creation"
        }
    }
}

/** Exact birth date is optional; an audience category is sufficient for age-aware presentation. */
data class LearnerProfile(
    val learnerId: String,
    val familyId: String,
    val displayName: String,
    val audience: LearnerAudience,
    val birthDateEpochDay: Long? = null,
    val avatarRef: String? = null,
    val initialLevelId: String = "beginner",
    val currentLevelId: String = initialLevelId,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    init {
        require(learnerId.isNotBlank()) { "learnerId must not be blank" }
        require(familyId.isNotBlank()) { "familyId must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(initialLevelId.isNotBlank()) { "initialLevelId must not be blank" }
        require(currentLevelId.isNotBlank()) { "currentLevelId must not be blank" }
        require(createdAtEpochMillis >= 0) { "createdAtEpochMillis must not be negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis must not precede creation"
        }
    }
}

/** Family management is a context, not a guardian learner profile. */
data class FamilyManagerContext(val familyId: String) {
    init {
        require(familyId.isNotBlank()) { "familyId must not be blank" }
    }
}
