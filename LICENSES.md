# Pianova third-party notices and release licensing audit

This file records third-party components and bundled media currently present in the repository. It is not a substitute for checking the exact upstream license files at each pinned revision before publishing a release.

## Android audio

### Google Oboe
- Artifact: `com.google.oboe:oboe:1.9.0`
- Upstream: https://github.com/google/oboe
- License: Apache License 2.0
- Required distribution action: include the Apache-2.0 license text and preserve applicable notices.
- License text: `app/src/main/assets/licenses/OBOE-APACHE-2.0.txt`.
- Repository dependency declaration: `app/build.gradle.kts`.

### TinySoundFont
- Source: `tsf.h`, downloaded from the pinned upstream commit `a3621a19c6504885cd9583e6e26c35bc954fc8ec`.
- Upstream: https://github.com/schellingb/TinySoundFont
- License: MIT.
- License text is retained at `app/src/main/assets/licenses/TINYSOUNDFONT-MIT.txt` and in the upstream header.

## Fonts

### Dastkhat FaNum
- Files: `dastkhat_regular.ttf`, `dastkhat_medium.ttf`, `dastkhat_bold.ttf`.
- Upstream: https://github.com/dastyar-io/dastkhat-font
- License: SIL Open Font License 1.1 (OFL-1.1).
- The font is downloaded at build time by `downloadDastkhatFonts`; the font binaries are not committed.
- Exact upstream revision: `19f4a12b56a89567b07e02500f0e4aed670e3644`.
- The three TTF files are pinned by immutable commit URL and verified by expected byte size and upstream Git blob SHA-1 before use.
- Dastkhat identifies itself as a derivative of Vazirmatn v33.003 (OFL-1.1); its Latin glyphs derive from Roboto (Apache-2.0).
- Exact upstream license and notices are bundled at `app/src/main/assets/licenses/DASTKHAT-OFL-1.1.txt` and `app/src/main/assets/licenses/DASTKHAT-THIRD-PARTY-NOTICES.txt`.

## SoundFont

### Chaos Bank v1.9
- Bundled asset: `app/src/main/assets/soundfonts/pianova.sf2`.
- Upstream: https://github.com/bratpeki/soundfonts
- Pinned upstream commit: `e1dcd9da45cc7454b4e3bfae613f4020d6a0b4f7`.
- Declared license: Creative Commons CC0 1.0 Universal.
- Provenance and license statement: `app/src/main/assets/soundfonts/LICENSE.txt`.
- The asset is downloaded at build time and checked against its expected size and Git blob SHA-1.

## Songs and lesson content

The offline beginner catalog is stored in `app/src/main/assets/songs/`. Each JSON file carries its own creator, source, rights classification, attribution, and license reference.

### Public-domain compositions and traditional melodies

- **Ode to Joy**: Ludwig van Beethoven, Symphony No. 9, Op. 125. The underlying composition is public domain; IMSLP lists the autograph manuscript and historical editions as public domain: https://imslp.org/wiki/Symphony_No.9,_Op.125_(Beethoven,_Ludwig_van). Pianova entered a simplified note transcription; no recording or modern arrangement is bundled.
- **Twinkle, Twinkle Little Star**: traditional melody published as *Ah! vous dirai-je, maman* in 1761. Historical publication evidence is summarized in the Oxford Research Archive: https://ora.ox.ac.uk/objects/uuid:70a29850-0c0d-4abd-a501-e75224fa856a/files/ma267db096abdc84a6cc3acb344e95519. Pianova entered its own beginner transcription.
- **Frère Jacques**, **Greensleeves**, and **London Bridge**: traditional melodies. The catalog contains only Pianova-entered note transcriptions, not third-party arrangements, lyrics, or recordings. Their traditional provenance is documented in each asset; public-domain status must still be assessed for the target distribution territory.
- **Minuet in G major, BWV Anh. 114**: Christian Petzold. IMSLP identifies Petzold as composer and includes historical manuscript and editions marked public domain: https://imslp.org/wiki/Minuet_in_G_major,_BWV_Anh.114_(Pezold,_Christian). Pianova entered a simplified transcription.
- **Prelude in C major, BWV 846**: Johann Sebastian Bach. The underlying composition is public domain; Pianova's asset is a short independently entered educational pattern, not a modern edition engraving or recording.

### Original Pianova exercises

- `iranian-dastgah-motif.json` and `iranian-pentatonic-exercise.json` are original educational exercises created for Pianova with a Persian musical character. They are explicitly **not** represented as traditional folk songs or transcriptions of named compositions.
- `beginner-five-finger.json` is an original technical exercise created for Pianova.
- These original works contain no third-party melody or recording. Their external redistribution license remains subject to the Pianova project owner's licensing decision; do not treat the repository URL as a public license grant.

### Continuing policy

A public-domain composition does not make a modern recording, edition, arrangement, or engraving public domain. Pianova stores its own note transcriptions and does not bundle third-party recordings. Before adding a new song, verify melody provenance, transcription/arrangement provenance, recording rights (if any), and territory-specific restrictions.

## Release audit status

| Component | Current evidence | Status |
|---|---|---|
| Oboe 1.9.0 | Upstream Apache-2.0 license text bundled in app assets | Identified; license text bundled |
| TinySoundFont | MIT text retained in repository | Identified |
| Dastkhat FaNum | Pinned upstream commit; three TTF files size/SHA-1 verified; OFL and third-party notices bundled | Identified and reproducibly pinned |
| Chaos Bank v1.9 | Pinned upstream README lists the exact file under CC0; source commit and asset Git blob SHA-1 are pinned | CC0 evidence verified at pinned revision |
| Songs | Offline catalog with 10 JSON assets; source/attribution metadata in each asset and this section | Public-domain source evidence recorded for historical compositions; territorial status and original-exercise redistribution license remain explicit review items |
| Resolved Release runtime dependencies | `releaseRuntimeClasspath`: 117 resolved modules. Graph: 16 first-level entries (14 declared runtime libraries, Compose BOM, Kotlin stdlib) and 101 transitive modules. License metadata: 110 Apache Software License 2.0, 6 Apache License 2.0, 1 Oboe metadata entry verified against the exact 1.9.0 upstream tag as Apache-2.0. No missing license names or license URLs. `com.google.guava:listenablefuture:1.0` has no project URL in its POM; its upstream project is identified at https://github.com/google/guava/tree/released-all-futures/listenablefuture1. | Resolved graph and license metadata reviewed; no UNKNOWN/NEEDS REVIEW license entries. All 117 module declarations resolve to Apache-2.0. |

## Release gate

The current release dependency and packaged-asset inventory has been reviewed as documented above. The dependency report is based on resolved Gradle runtime components and their published license metadata, with Oboe independently checked against its exact 1.9.0 upstream tag and the missing Guava ListenableFuture project URL resolved to its upstream source. Bundled fonts, native audio code, and SoundFont assets are pinned and have corresponding license texts or notices. This is an engineering inventory, not legal advice. Re-run the inventory and review whenever release dependencies or packaged assets change.