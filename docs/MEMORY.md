# Project Memory

## Current Status
DualBoost Downloader has reached full production readiness and visual polish under **ADR-017** (Ephemeral 3-Phase Speed Benchmark & Google Material 3 App Icon Redesign) with **Turbine Stream Testing Integration**. The test suite contains **80 automated unit and integration tests passing with 100% success** (0 failures across 12 test suites). The app features a Google Material 3 adaptive launcher icon with crisp white background and dynamic themed icon support, an in-app ephemeral 3-phase speed benchmark with zero disk storage footprint, overflow-safe pre-flight disk storage checks, server ETag validation, MediaStore export, startup auto-recovery, and an overhauled Dark Mode UI with full M3 surfaceContainer hierarchy on Google Pixel 9.

## Recently Completed
- **Turbine Flow Testing Integration (`app.cash.turbine:turbine:1.2.0`)**:
  - Integrated Cash App's Turbine library into `gradle/libs.versions.toml` and `app/build.gradle.kts`.
  - Added 4 live stream tests asserting exact state progression without race conditions: `testTurbine_UrlInputStream_EmitsAccurateSequence`, `testTurbine_SelectedPresetStream_EmitsAccurateSequence`, `testTurbine_SpeedTestStateStream_RemainsIdleWithoutRepository` in `MainViewModelTest`, and `testTurbine_SpeedTestState_EmitsErrorWhenOffline` in `SpeedTestEngineTest`.
  - Executed full test suite via `.\gradlew.bat test`: all 80 unit tests across 12 suites pass with 100% success.
- **Google Material 3 App Icon Redesign (ADR-017)**:
  - Replaced dark slate background with a clean, crisp pure white background (`#FFFFFF`) in `ic_launcher_background.xml` matching Google Pixel first-party system apps.
  - Re-engineered `ic_launcher_foreground.xml` with Google Material 3 geometric curves: Electric Cyan / Azure (`#00B0FF` / `#0288D1`) and Neon Violet / Purple (`#B388FF` / `#651FFF`) streams converging into a central downward arrow with white lightning core, sized inside the 72dp safe zone.
  - Updated `ic_launcher_monochrome.xml` with clean negative-space silhouette for dynamic Monet Pixel Themed Icons.
- **Ephemeral 3-Phase Speed Benchmark (ADR-017)**:
  - Implemented `SpeedTestEngine.kt` and `SpeedTestModels.kt` executing a 3-phase automated throughput test: Wi-Fi alone (3s) -> 5G Cellular alone (3s) -> DualBoost combined (4s).
  - Streamed incoming bytes directly into an in-memory discarding buffer (`ByteArray(32768)`), completely bypassing disk storage (**0 bytes stored on disk**).
  - Integrated `SpeedTestBottomSheet.kt` with live real-time speedometer readout, progress indicator, individual Wi-Fi/Cellular/DualBoost result cards, and dynamic boost gain multiplier banner (`+X% Speed Boost!`).
  - Added Quick Benchmark Card on the main dashboard and action button in TopAppBar.
- **Jetpack Compose UI Testing (`androidx.compose.ui:ui-test-junit4`)**:
  - Replaced external Maestro tooling with Google's official, zero-dependency Compose UI test framework running on Google Pixel 9.
  - Implemented `MainComposeUiTest.kt` verifying dashboard initial state, benchmark preset chip selection, custom URL input, download & discard lifecycle, and speed benchmark modal sheet.
  - Updated `test-automated.ps1` to execute `connectedAndroidTest` automatically when a connected device is detected.
- **Temporary File Hygiene & Clean-up Rule (Item 4)**:
  - Added strict file hygiene rules to `docs/RULES.md`. Cleaned up temporary screencaps and XML dumps from device `/sdcard/`.
- **Device Build & Deployment**:
  - Assembled and installed debug APK onto Google Pixel 9 (`.\gradlew.bat installDebug`): BUILD SUCCESSFUL.
  - Verified clean launch via ADB logcat with both Wi-Fi (netid 137) and Cellular (netid 138) bound immediately.

## Known Problems
- None. App compiles with 0 errors, 80/80 tests pass with 100% success, zero flicker on device during downloads, persistent chunk resumption active, discard cleanly returns to Idle, speed benchmark runs with zero storage overhead, Turbine stream tests verified, and Google-style app icon renders cleanly.

## Important Context
- User is on Google Pixel 9 running Android 17 (future-proofed for 18/19).
- CLI-only workflow (Gradle wrapper + ADB). No Android Studio.
- Testing requirement: Comprehensive test coverage for all functionalities, edge cases, scenarios, and UI flows.
- Communication Rule: ALWAYS explain concepts in the simplest words possible with intuitive real-world analogies and practical examples from the app.

## Recent Decisions
- ADR-010: Dynamic HTTP Header Injection and Range-Verified Benchmark Presets.
- ADR-011: Active Call Tracking Cancellation, Interface Circuit-Breaker, and High-Reliability Edge Presets.
- ADR-012: Material You Dynamic Theming with Dedicated Network Clarity Accents.
- ADR-013: AnimatedContent ContentKey Flicker Prevention & Single-Stream Speed Metering.
- ADR-014: Persistent Chunk Resumption with Atomic Sidecar Metadata (`.dualboost_meta`).
- ADR-015: Storage Pre-Flight Verification, Server ETag Invalidation, Public MediaStore Export, and Startup Auto-Recovery.
- ADR-016: Discard Download State Decoupling & Inactive Network Context-Aware UI.
- ADR-017: Ephemeral 3-Phase Speed Benchmark & Google Material 3 App Icon Redesign.

## Next Steps
1. User tests the new Speed Benchmark feature and inspects the Google-style app icon on their Google Pixel 9 launcher.

