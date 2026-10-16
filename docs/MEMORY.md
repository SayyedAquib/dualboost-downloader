# Project Memory

## Current Status
DualBoost Downloader has reached full production readiness and visual polish under **ADR-015** and the **Material 3 Dark Mode Overhaul**. The test suite contains **68 automated unit and integration tests passing with 100% success** (0 failures across 11 test suites). The app features overflow-safe pre-flight disk storage checks, server ETag validation, MediaStore export, startup auto-recovery, and an overhauled Dark Mode UI with full M3 surfaceContainer hierarchy and glowing interface accents on Google Pixel 9.

## Recently Completed
- **Material 3 Expressive Dark Mode Overhaul**:
  - Implemented true M3 surface hierarchy (`surfaceContainerLowest`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`) across `Theme.kt` and `MainActivity.kt`, eliminating pitch-black card flattening.
  - Added subtle 1dp border strokes: interface-tinted glows (`#00E5FF` Cyan for Wi-Fi, `#B388FF` Violet for 5G) on `NetworkCard`, and crisp `outlineVariant` borders on input, state, and speed telemetry containers.
  - Redesigned Empty State ("Ready to Download"): sleek 24dp rounded hero card with 56dp primary-tinted circular icon badge (`Icons.Rounded.CloudDownload`), bold M3 typography, and clean centered spacing.
  - Upgraded Preset Benchmark Chips: distinct `primaryContainer` active state with 1.5dp border vs `surfaceContainerHighest` idle state.
  - Upgraded Aggregation Status Pill: high-contrast `primaryContainer` with clear iconography and text.
  - Validated live on Google Pixel 9: captured screenshots confirming high-contrast depth, sharp card borders, selected preset chip styling, and complete download telemetry.
- **Production Hardening & Storage Resilience (ADR-015)**:
  - Added overflow-guarded `checkAvailableStorage(targetDir, requiredBytes, existingFile)` in `DualDownloaderEngine` with 20 MB safety margin.
  - Implemented server ETag and Last-Modified validation in `.dualboost_meta`; stale remote files trigger clean restarts from 0%.
  - Added public `MediaStore.Downloads` pipeline with `ContentResolver` and automatic MIME type resolution; attaches public content URI to `DownloadState.Completed`.
  - Added startup auto-recovery: `restoreIncompleteDownload()` scans for pending `.dualboost_meta` files on cold launch, restoring `DownloadState.Paused` and pre-filling `MainViewModel.urlInput`.
  - Upgraded Completed Card UI in `MainActivity.kt` with side-by-side "Open File" (`ACTION_VIEW`) and "Share" (`ACTION_SEND`) buttons.
- **Comprehensive Test Suite Expansion (68 tests, 100% passing across 11 suites)**:
  - Verified `ProductionHardeningResilienceTest` (9 tests), `EngineComprehensiveTest` (13), `ResumableDownloadTest` (5), `DownloadRepositoryTest` (4), `DualNetworkManagerTest` (5), `ServiceLifecycleTest` (3), `UIFlowAndViewModelComprehensiveTest` (6), `ChunkCalculationTest` (4), `EngineEdgeCasesTest` (9), `MainViewModelTest` (6), `MockWebServerDownloadTest` (2), `NotificationThrottlerTest` (2).
- **Verification & Builds**:
  - Ran full 68-test automated suite via `.\gradlew.bat test`: 100% passing.
  - Compiled and installed debug APK onto Google Pixel 9 via ADB (`.\gradlew.bat installDebug`): BUILD SUCCESSFUL.
  - Live device screencaps verified: `screen_dark_ui.png`, `screen_dark_ui_selected.png`, `screen_dark_downloading.png`.

## Known Problems
- None. App compiles with 0 errors, 68/68 tests pass with 100% success, zero flicker on device during downloads, persistent chunk resumption active, storage protected, and dual physical sockets remain active.

## Important Context
- User is on Google Pixel 9 running Android 17 (future-proofed for 18/19).
- CLI-only workflow (Gradle wrapper + ADB). No Android Studio.
- Testing requirement: Comprehensive test coverage for all functionalities, edge cases, scenarios, and UI flows.

## Recent Decisions
- ADR-010: Dynamic HTTP Header Injection and Range-Verified Benchmark Presets.
- ADR-011: Active Call Tracking Cancellation, Interface Circuit-Breaker, and High-Reliability Edge Presets.
- ADR-012: Material You Dynamic Theming with Dedicated Network Clarity Accents.
- ADR-013: AnimatedContent ContentKey Flicker Prevention & Single-Stream Speed Metering.
- ADR-014: Persistent Chunk Resumption with Atomic Sidecar Metadata (`.dualboost_meta`).
- ADR-015: Storage Pre-Flight Verification, Server ETag Invalidation, Public MediaStore Export, and Startup Auto-Recovery.

## Next Steps
1. User tests downloads on their Google Pixel 9 to confirm rock-solid zero-flicker UI, auto-recovery on restart, and Open/Share actions on completed downloads.

