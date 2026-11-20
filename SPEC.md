# PIANOVA — Product & Technical Specification (v1.0)

## 1. Product Overview
**Pianova** is an offline-first, native Android, game-based piano learning application in **Persian (RTL)** designed for complete beginners.
- **Core Goal:** Enable users with zero musical background to play real piano immediately, learn interactively, and practice using either a multi-touch virtual keyboard or a physical USB MIDI keyboard.
- **v1.0 Scope Rule:** Prioritize rock-solid audio, low-latency touch response, and interactive learning over decorative features.

### Deferred to v1.1+ (Explicitly Out of Scope for v1.0)
- Full MusicXML sheet music rendering engine (v1.0 uses a lightweight Compose Canvas staff & Falling Notes).
- Bluetooth Low Energy (BLE) MIDI (v1.0 focuses on USB MIDI / OTG).
- Microphone-based pitch/chord detection.
- Cloud sync, user accounts, and online leaderboards.

---

## 2. Tech Stack & Architecture
- **Platform:** Native Android (Min SDK 26 / Android 8.0, Target SDK 35).
- **Language:** Kotlin + C++ (JNI/NDK for Audio).
- **UI:** Jetpack Compose, Material 3, Navigation Compose, Dastkhat font.
- **Architecture:** **Single-Module (app)** Clean Architecture with strict package boundaries, MVVM, Hilt (DI), Coroutines & Flow.
- **Persistence:** Room Database (progress, XP, streaks) + DataStore Preferences (settings).
- **Content:** Versioned JSON files bundled in assets/lessons/ and assets/songs/ parsed via Kotlinx Serialization.
- **CI/CD:** GitHub Actions workflow to build, test, and output debug/release APKs on every push.

### Package Structure (app/src/main/java/ir/pianova/app/)
    ├── core/
    │   ├── audio/          # PianoAudioEngine interface, JNI bridge, SoundFont loader
    │   ├── midi/           # PianoInput interface, UsbMidiInput, VirtualKeyboardInput
    │   ├── database/       # Room DB, Entities, DAOs
    │   ├── datastore/      # User settings (volume, labels, octave, haptic)
    │   └── designsystem/   # Theme (Dark Navy/Purple/Electric Blue/Gold), RTL utils, Dastkhat
    ├── domain/
    │   ├── model/          # Note, Chord, Lesson, Step, EvaluationResult, Song
    │   └── evaluation/     # Pitch, sequence, and timing evaluator
    ├── data/
    │   ├── repository/     # LessonRepository, ProgressRepository, SongRepository
    │   └── parser/         # JSON lesson & song loaders
    └── feature/
        ├── home/           # Dashboard, streak, XP, continue learning
        ├── piano/          # Multi-touch virtual keyboard & Free-play mode
        ├── learning/       # Curriculum list & Interactive lesson player
        ├── songs/          # Beginner song library & Falling Notes practice
        ├── progress/       # Stats, levels, achievements
        └── settings/       # Audio, MIDI, keyboard, accessibility settings

### Native C++ Structure (app/src/main/cpp/)
    ├── CMakeLists.txt
    ├── tsf.h               # TinySoundFont single-header library
    ├── AudioEngine.h/.cpp  # Google Oboe stream + TSF rendering loop
    └── native-lib.cpp      # JNI bindings

---

## 3. Audio Engine (Critical Path)
- **Engine:** **Google Oboe** (for lowest possible Android audio latency) + **TinySoundFont (tsf.h)** (single-header C SoundFont2 synthesizer).
- **Assets:** Bundled commercially-licensed lightweight .sf2 piano SoundFont (under 15MB) in assets/soundfonts/.
- **Requirements:**
  - Full polyphony (at least 32 simultaneous voices).
  - Real-time noteOn(note, velocity), noteOff(note), setSustain(enabled), setVolume(vol), stopAllNotes().
  - Runs strictly on Oboe's high-priority audio thread; zero allocations in the audio callback.
  - Proper lifecycle handling: pause/stop audio cleanly when app goes to background (onPause/onStop) to prevent stuck notes or battery drain.

---

## 4. Virtual Piano & Unified Input
- **Unified Interface:** PianoInput emits a SharedFlow of PianoEvent (NoteOn, NoteOff, Sustain). Both VirtualKeyboardInput and UsbMidiInput implement this interface so the lesson/evaluation engines are completely input-agnostic.
- **Virtual Keyboard (Compose):**
  - Internal range: 88 keys (A0-C8). Default visible view: 2 octaves (adjustable + horizontally scrollable).
  - **Strict LTR Rule:** Piano keys, octave ordering, and musical timelines MUST ALWAYS be Left-to-Right, even though the app UI is Persian RTL.
  - **Multi-touch Tracking:** Custom pointerInput tracking each pointer ID independently so sliding a finger or pressing chords never drops or sticks a note.
  - Visual feedback: Pressed state, highlighted target keys for lessons, optional Persian (دو، ر، می) or Latin (C, D, E) labels.

---

## 5. Interactive Lesson Engine (JSON-Driven)
Lessons are stored as versioned JSON files in assets/lessons/.
- **Supported Step Types (v1.0):**
  1. explanation: Persian text + optional highlighted key(s) on piano.
  2. listen: Plays reference note(s) via audio engine while highlighting keys.
  3. play_note: User must play target note(s) N times.
  4. play_sequence: User plays a melody in order (no strict speed penalty for beginners).
  5. play_chord: User plays simultaneous notes.
- **Initial Curriculum (v1.0):**
  - Chapter 1: آشنایی با پیانو (کلیدهای سفید و سیاه، نت دو میانی)
  - Chapter 2: نت‌های موسیقی (دو تا سی)
  - Chapter 3: دست راست و ملودی‌های ساده
  - Chapter 4: ریتم پایه و تمرین‌های تعاملی
  - Chapter 5: اولین آهنگ‌های کامل (ایرانی و بین‌المللی ساده)

---

## 6. UI, Gamification & Offline Persistence
- **Navigation (5 Tabs, Persian RTL):** خانه | آموزش | پیانو | آهنگ‌ها | پیشرفت من
- **Visual Direction:** Dark Navy background (#0F172A), Deep Purple accents (#6D28D9), Electric Blue highlights (#38BDF8), Gold badges (#F59E0B).
- **Gamification (Room DB):**
  - XP rewards for completing lessons, exercises, and daily practice (with anti-farming cap on repeated lessons).
  - 6 Persian Levels (از نوازنده تازه‌کار تا پیانیست حرفه‌ای).
  - Daily Streak & Achievements.
- **Monetization Hook:** Abstract PurchaseEntitlement interface (local unlocked implementation for dev/testing; ready for Play Billing / local stores without touching core modules).

---

## 7. Phased Execution Roadmap

### Phase 0: Foundation & CI
- Initialize single-module Android project (Kotlin, Compose, Hilt, Room, CMake/NDK).
- Set up dark theme, Dastkhat font, and RTL layout wrapper.
- Configure **GitHub Actions** to build a debug APK automatically.
- **Exit Criteria:** Clean green build & downloadable APK from GitHub Actions.

### Phase 1: The Living Skeleton (Audio + Virtual Piano)
- Integrate Google Oboe (via Prefab) + tsf.h + free-to-use .sf2 piano file.
- Build the multi-touch Compose Virtual Piano with octave scrolling, sustain button, and note labels.
- **Exit Criteria:** APK installed on a real Android phone plays polyphonic piano audio with low latency and zero stuck notes.

### Phase 2: Lesson Engine & Evaluation
- Implement JSON lesson parser, LessonPlayerViewModel, and EvaluationEngine.
- Author the first 5 interactive Persian lessons (Chapter 1 & 2).
- **Exit Criteria:** User can complete interactive lessons with immediate Persian feedback ("آفرین، درست نواختی!").

### Phase 3: USB MIDI Integration
- Implement Android MidiManager (UsbMidiInput) behind PianoInput.
- Add connection status indicator and device selector in Settings.
- **Exit Criteria:** Physical MIDI keyboard via USB OTG triggers audio engine and passes lessons.

### Phase 4: Persistence, Gamification & Dashboard
- Implement Room DB for progress, XP, streaks, and achievements.
- Build خانه (Dashboard) and پیشرفت من (Stats) screens.
- **Exit Criteria:** Progress survives app restarts; XP and streaks update accurately offline.

### Phase 5: Song Library & Release Polish
- Add Beginner Song Library with Guided / Falling Notes mode and adjustable tempo.
- Performance profiling (prevent Compose recomposition spikes during fast playing).
- License audit for .sf2 and songs, generate signed Release APK/AAB.
