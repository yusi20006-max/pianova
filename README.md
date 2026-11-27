# Pianova (پیانوا)

Pianova is an offline-first, interactive piano-learning companion designed to support learners at different ages and skill levels.

پیانوا یک همراه تعاملی و آفلاین برای یادگیری پیانو است که برای همراهی نورا و نیکان در مسیر یادگیری این ساز شکل گرفته و برای هنرجویان با سن‌ها و سطح‌های مختلف طراحی می‌شود.

## What Pianova offers

- Interactive lessons and piano practice.
- Independent learner profiles for family use.
- Flexible learning paths for children, teenagers, and adults.
- Practice tools, progress tracking, and learner-specific reports.
- Local-first operation and family backup/restore.

## Intended piano hardware workflow

Pianova is intended to work alongside the family's Bluetooth-capable digital piano as compatible hardware support is implemented. Bluetooth audio routing and Bluetooth MIDI are separate capabilities and must be supported based on the piano's actual profiles.

**Bluetooth MIDI and Bluetooth audio routing support are implemented.** BLE MIDI input uses the shared lesson input contract, while Bluetooth audio output is selected through Android's system media route. Physical Kinix PJ-88D verification remains pending. Microphone-based acoustic practice feedback is also under implementation, with explicit permission and local-only processing.

## Privacy and data

Family profiles and learning progress are stored locally on the device in the current scope. Cloud accounts, remote authentication, and multi-device sync are not currently part of the family learning system.

## Development

See [SPEC.md](SPEC.md) for project requirements and technical direction. See [LICENSES.md](LICENSES.md) for licensing information.
