# Third-party audio components

## TinySoundFont

- Project: https://github.com/schellingb/TinySoundFont
- Source file: `tsf.h`
- License: MIT
- Pinned source commit: `a3621a19c6504885cd9583e6e26c35bc954fc8ec`
- The header is downloaded by `downloadAudioDependencies` and verified against
  its upstream Git blob SHA before native compilation.

The MIT license text is included at the top of the upstream `tsf.h` header.
The upstream license is also available at:
https://github.com/schellingb/TinySoundFont/blob/main/LICENSE

## Google Oboe

- Project: https://github.com/google/oboe
- Dependency: `com.google.oboe:oboe:1.9.0`
- License: Apache License 2.0
- License text: https://www.apache.org/licenses/LICENSE-2.0

## Pianova piano SoundFont

- File: `app/src/main/assets/soundfonts/pianova.sf2`
- Upstream: Chaos Bank v1.9, https://github.com/bratpeki/soundfonts
- License: CC0 1.0 Universal
- Pinned source commit and provenance: see
  `app/src/main/assets/soundfonts/LICENSE.txt`.
- Downloaded at build time from an immutable upstream commit and verified
  against its Git blob SHA-1.
