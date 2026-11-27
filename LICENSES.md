# Pianova third-party notices and release licensing audit

This inventory records third-party components and bundled media. It is engineering documentation, not legal advice. Before public/commercial distribution, retain the exact license texts and complete the unresolved rights review identified below.

## Android audio

### Google Oboe
- Artifact: `com.google.oboe:oboe:1.9.0`
- Upstream: https://github.com/google/oboe
- License: Apache License 2.0
- Required distribution action: include the Apache-2.0 license text and preserve applicable notices.
- License text: `app/src/main/assets/licenses/OBOE-APACHE-2.0.txt`.
- Dependency declaration: `app/build.gradle.kts`.

### TinySoundFont
- Source: `tsf.h`, pinned upstream commit `a3621a19c6504885cd9583e6e26c35bc954fc8ec`.
- Upstream: https://github.com/schellingb/TinySoundFont
- License: MIT.
- MIT license text is retained at `app/src/main/assets/licenses/TINYSOUNDFONT-MIT.txt` and in the upstream header.

## Fonts

### Dastkhat FaNum
- Files: `dastkhat_regular.ttf`, `dastkhat_medium.ttf`, `dastkhat_bold.ttf`.
- Upstream: https://github.com/dastyar-io/dastkhat-font
- License: SIL Open Font License 1.1 (OFL-1.1).
- Downloaded at build time from immutable revision `19f4a12b56a89567b07e02500f0e4aed670e3644`; expected size and Git blob SHA-1 are checked.
- Dastkhat identifies its upstream derivatives as Vazirmatn v33.003 (OFL-1.1) and Roboto (Apache-2.0).
- OFL and third-party notices are bundled at `app/src/main/assets/licenses/DASTKHAT-OFL-1.1.txt` and `DASTKHAT-THIRD-PARTY-NOTICES.txt`.

## SoundFont

### Chaos Bank v1.9
- Asset: `app/src/main/assets/soundfonts/pianova.sf2`.
- Upstream: https://github.com/bratpeki/soundfonts
- Pinned commit: `e1dcd9da45cc7454b4e3bfae613f4020d6a0b4f7`.
- License: CC0 1.0 Universal, based on the upstream license/provenance retained at `app/src/main/assets/soundfonts/LICENSE.txt`.
- Download is pinned and size/Git blob SHA-1 verified.

## Songs and lesson content

The beginner catalog has 10 entries. Each JSON includes creator/source, rights classification, attribution, and license reference.

### Public-domain / traditional sources
- **Ode to Joy**: Beethoven, Symphony No. 9, Op. 125. The underlying composition is public domain; Pianova's simplified transcription is independently entered and no recording or modern arrangement is bundled. Source: https://imslp.org/wiki/Symphony_No.9,_Op.125_(Beethoven,_Ludwig_van).
- **Twinkle, Twinkle Little Star**: traditional melody, historically published as *Ah! vous dirai-je, maman* (1761). Pianova's transcription is independent. Historical source: https://ora.ox.ac.uk/objects/uuid:70a29850-0c0d-4abd-a501-e75224fa856a/files/ma267db096abdc84a6cc3acb344e95519.
- **Frère Jacques**, **Greensleeves**, and **London Bridge**: traditional melodies; Pianova's note transcriptions are independently entered. Traditional provenance does not establish worldwide public-domain status by itself.
- **Minuet in G major, BWV Anh. 114**: attributed in the catalog to Christian Petzold. The underlying composition is public domain; Pianova's simplified transcription is independent. Source: https://imslp.org/wiki/Minuet_in_G_major,_BWV_Anh.114_(Pezold,_Christian).
- **Prelude in C major, BWV 846**: Bach composition, public domain. Pianova uses an independently entered short educational pattern, not a modern edition engraving or recording.

### Original Pianova exercises
- `iranian-dastgah-motif.json`, `iranian-pentatonic-exercise.json`, and `beginner-five-finger.json` are original Pianova educational exercises.
- They are not represented as folk-song transcriptions.
- Their redistribution license is **not yet specified**. The project owner must choose and document the license before commercial/public distribution.

## Release licensing gate

- Third-party component licenses are identified and corresponding license/notice files are present.
- Dependency license metadata is generated from the resolved `releaseRuntimeClasspath`; CI reports are retained as artifacts.
- Public-domain/traditional song classification is documented, but territory-specific restrictions should be checked for distribution territories.
- Original Pianova exercises' redistribution license remains unresolved. Do not treat the repository's presence as a public license grant.
- The signed tag workflow creates a **draft** GitHub Release. Review licensing and artifacts before publishing it.

