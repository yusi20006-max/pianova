# Privacy and Child Data in Pianova

## Local-only data model

Pianova stores family profiles, learner display names, audience category, learning goals, lesson progress, practice sessions, evaluation evidence, achievements, daily missions, song progress, and learner-scoped settings locally on the device.

This scope adds no account server, cloud sync, or network request for profile creation or progress persistence. The Android manifest does not request network, microphone, or storage permissions. Android automatic app backup is disabled and backup rules exclude app data from cloud backup and device-transfer backup. Manual family backup/restore is user-initiated and uses Android's Storage Access Framework to read or write a selected JSON file.

## Minimal profile information

Use a display name, audience category (child, teen, adult, or unspecified), and optional avatar. Pianova does not require an exact date of birth for age-aware learning presentation. Learning goals are optional.

## Learner separation

Progress and learner-owned records are keyed by stable learner ID. Learner screens and repositories query the active learner's ID; family reports and family management are separate family-manager surfaces. Deleting a learner removes only that learner's records and settings and leaves other learners and family data intact.

## Family manager and learner switching

Family management is separate from learner mode. Learner switching remains free and does not require PIN, password, or biometric authentication. This is an intentional product decision, not an authentication or access-control boundary.

Anyone who can use the device while Pianova is open or the device is unlocked can switch profiles and may view family reports or manage profiles. Pianova does not claim to protect one learner's data from another person with unrestricted access to the unlocked device. Use Android's device lock and supervise access to the device.

## Deletion and restore

Deleting a learner requires a confirmation dialog describing the data affected and stating that deletion cannot be undone. It deletes only the selected learner and related records. Family backup restore previews the backup and asks for confirmation before replacing current family data. The explicit JSON backup is optional; users should create a backup before destructive operations if they want a recovery copy.

## Backups and transfer

Android automatic backup is disabled and explicit backup rules exclude databases, files, shared preferences, and app data for cloud backup and device-transfer. Users can create a family backup JSON through the in-app backup flow and choose where to save it. A chosen backup file may be copied, shared, or stored elsewhere by the user, outside Pianova's control.

## Limitations

- Local storage is not a separate account or a multi-user security boundary.
- No PIN, password, or biometric gate is implemented for learner switching.
- Pianova does not encrypt the manual backup JSON; treat it as sensitive and store it safely.
- Deleting a learner from Pianova does not erase previously exported backup files or copies made outside the app.
- Device lock, operating-system backups, file sharing, and access by people using the unlocked device are outside Pianova's app-level control.
