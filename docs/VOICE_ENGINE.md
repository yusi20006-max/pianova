# Pianova Persian voice coach engine

Pianova offers two voice-engine choices in the lesson controls:
- Android system TTS, only if that engine reports Persian support.
- The separately installed Sherpa-ONNX Persian Android TTS engine.

## Recommended offline engine

- Engine: Sherpa-ONNX Android TTS Engine
- Android package: `com.k2fsa.sherpa.onnx.tts.engine`
- Voice: Piper `fa_IR-ganji-medium`
- Official model and Android APK page:
  https://k2-fsa.github.io/sherpa/onnx/tts/all/Persian/vits-piper-fa_IR-ganji-medium.html
- Select the APK matching the device ABI. Most modern Android phones use
  `arm64-v8a`.
- After installing the companion engine, return to Pianova and select
  **فارسی آفلاین** in the lesson voice controls.

The engine synthesizes speech on-device. Lesson text is not sent to a server,
and Pianova does not request microphone or network permissions for narration.

## Licensing and provenance

- Sherpa-ONNX: Apache-2.0.
- Piper voice collection: MIT repository license.
- The Ganji voice model card identifies its source dataset as CC0.
- The companion engine is distributed separately and is not bundled or copied
  into Pianova. Its installation and updates are managed by Android/the user.
- Pianova does not incorporate AvaCore source code; AvaCore is GPL-3.0-or-later.

## Fallback behavior

The Android system engine remains optional. Pianova checks actual Persian
voice/language availability after initialization and never marks an unsupported
engine as ready. A system voice may require a network connection; its offline
status is read from the selected Android voice metadata.

## Manual validation still required

CI can verify compilation and pure Kotlin language/engine selection logic, but
cannot prove audio output on a physical phone. Validate installation, Persian
pronunciation, Play/Stop, media volume, Bluetooth routing, and lesson lifecycle
on a real device.
