# Pianova third-party notices and release licensing audit

This file records third-party components and bundled media currently present in the repository. It is not a substitute for checking the exact upstream license files at each pinned revision before publishing a release.

## Android audio

### Google Oboe
- Artifact: `com.google.oboe:oboe:1.9.0`
- Upstream: https://github.com/google/oboe
- License: Apache License 2.0
- Required distribution action: include the Apache-2.0 license text and preserve applicable notices.
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
- The font is downloaded at build time by `downloadDastkhatFonts`; the font binaries are not currently committed.
- Dastkhat identifies itself as a derivative of Vazirmatn (OFL-1.1); its Latin glyphs derive from Roboto (Apache-2.0). Preserve the upstream OFL and third-party notices when redistributing the font files.
- Upstream license and notices must be retrieved from the same source revision used for the font download before a production release. The current Gradle URL points at the mutable `main` branch, so the font source is not yet reproducibly pinned.

## SoundFont

### Chaos Bank v1.9
- Bundled asset: `app/src/main/assets/soundfonts/pianova.sf2`.
- Upstream: https://github.com/bratpeki/soundfonts
- Pinned upstream commit: `e1dcd9da45cc7454b4e3bfae613f4020d6a0b4f7`.
- Declared license: Creative Commons CC0 1.0 Universal.
- Provenance and license statement: `app/src/main/assets/soundfonts/LICENSE.txt`.
- The asset is downloaded at build time and checked against its expected size and Git blob SHA-1.

## Songs and lesson content

- No standalone song-library assets are present in the current repository tree at this audit point.
- The five bundled interactive lesson JSON files are original Pianova lesson content, not third-party song recordings or score editions.
- Before adding any third-party song, arrangement, MIDI, recording, or score, record its creator, exact source, license, attribution text, and any restrictions here. Public-domain status must be assessed for the specific arrangement/recording as well as the underlying composition.

## Release audit status

| Component | Current evidence | Status |
|---|---|---|
| Oboe 1.9.0 | Upstream Apache-2.0 license; project notice exists | License identified; include full notice in release bundle |
| TinySoundFont | MIT text retained in repository | Identified |
| Dastkhat FaNum | Upstream states OFL-1.1; derivative notices disclosed | License identified; pin font revision and bundle exact notices |
| Chaos Bank v1.9 | Project provenance file declares CC0-1.0 and pins source commit | Identified; verify upstream license evidence at pinned revision |
| Songs | No song assets currently present | No third-party song license review applicable yet |
| Other transitive dependencies | Not yet inventoried from the resolved release runtime graph | Pending automated dependency/license inventory |

## Release gate

Do not describe the application as fully license-audited until the exact release dependency graph, all packaged assets, and the upstream license texts at pinned revisions have been reviewed.