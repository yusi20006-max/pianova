# Pianova learner progress data policy

## Storage and ownership

Learner progress is stored locally in the app's Room database and remains queryable offline. Every progress query and record is scoped by the stable learner ID.

The app records:
- completed lesson-step IDs and the current in-progress step;
- lesson completion state and timestamps;
- practice-session start/end timestamps, measured foreground duration, interruption state, and activity counters;
- evaluator status, expected MIDI notes, received MIDI notes, skill ID, timestamp, and whether an answer-revealing hint was used;
- derived skill confidence, recurring note mistakes, and weekly/monthly aggregates.

## Time and accuracy

Practice duration is measured with Android's monotonic elapsed clock while the app is in the foreground. Time spent in the background is excluded. A session interrupted by leaving the foreground retains only its accumulated active duration.

Progress metrics are calculated from persisted session and evaluation records. Missing evidence is represented as an empty state or a null accuracy value, never as seeded sample metrics.

## Audio and microphone data

Pianova does not persist raw microphone recordings as part of learner progress. Evaluation evidence stores note-level results only. If microphone-based assessment is introduced later, only derived assessment results should be retained unless a separate, explicit product decision and consent flow authorizes otherwise.

## Retention and deletion

There is no automatic time-based deletion in this release. Records remain on the device until the learner profile is deleted. Learner deletion removes that learner's lesson progress, practice sessions, evaluation evidence, achievements, missions, and song progress within the same database transaction. Another learner's records are not affected.

## Scope

This policy describes the local progress data implemented for Issue #42. Cloud synchronization, family-wide reporting, parental privacy controls, and configurable retention periods are outside this issue's implementation scope.
