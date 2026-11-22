# Family and learner domain model

Issue #38 introduces a local family container and independent learner profiles.

## Ownership model

- A Family owns one or more LearnerProfile records.
- learnerId is the stable owner key for lesson progress, practice sessions, achievements, daily missions, and song progress.
- Family management is represented by FamilyManagerContext; a guardian does not need a learner profile.
- A learner may independently be an adult. The model does not assume that the family manager is a learner.

## Profile data and privacy

A learner profile stores a display name, audience category, optional avatar reference, initial/current level, and timestamps. An exact birth date is optional. The audience category is enough for age-aware presentation, and the legacy migration uses unspecified rather than guessing an age.

Learning paths, goals, preferences, and progress are not embedded in the identity model. They remain separate learner-scoped concepts for later issues.

## Persistence and migration

Room schema version 2 adds families and replaces the former user_profiles table with learner_profiles. Migration 1 to 2 creates a local default family, preserves each previous userId as the same learnerId, copies the existing level into both initial and current level, and marks the audience as unspecified. Existing progress tables retain their ownership values; the Kotlin property is now named learnerId while its stored column remains userId for compatibility.

Deleting a learner runs in one Room transaction and removes only that learner's lesson progress, practice sessions, achievements, daily missions, song progress, and profile. Other learners and the family remain intact.

No account server, cloud sync, authentication, or network dependency is added.
