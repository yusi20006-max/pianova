# Pianova Song Assets

All files in this directory are UTF-8 JSON documents using schema version 1.

## Schema

- `id`: stable, unique identifier.
- `title`, `composer`: display metadata.
- `category`: `iranian`, `classical`, `international`, or `practice`.
- `difficulty`: `beginner`, `easy`, or `intermediate`.
- `tempo`: quarter-note beats per minute.
- `beatsPerBar`: meter numerator used by the practice renderer.
- `notes`: ordered events with `midiNote` (21–108), `startBeat`, `duration`, and `hand` (`right`, `left`, or `both`).
- `practiceSections`: named ranges in beats.
- `license`: material type, license name, attribution, license URL, and optional source URL/notes.

## Content and rights policy

1. Include only original Pianova exercises or compositions whose underlying composition is verified public domain / otherwise properly licensed.
2. A public-domain composition does not make a modern recording, edition, arrangement, or engraving public domain. Pianova stores its own note transcription and does not bundle third-party recordings.
3. Keep attribution and source evidence inside each song document. Do not mark a song public domain solely because it is traditional or old.
4. Iranian-category entries in this initial catalog are original Pianova exercises with Persian musical character, not claims about anonymous traditional melodies. A future named folk song requires a documented provenance and rights review.
5. Before adding a new song, verify melody provenance, arrangement/transcription provenance, recording rights (if any), and territory-specific restrictions.

The initial catalog contains two original Iranian-character exercises, two public-domain classical/international melodies, and two original beginner exercises. The category is a pedagogical grouping, not a claim that all entries have the same legal status.
