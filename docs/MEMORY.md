# Project Memory

## Current Status
DualBoost Downloader has reached full production readiness and visual polish under **ADR-016** (Discard Download State Decoupling & Inactive Network UI). The test suite contains **70 automated unit and integration tests passing with 100% success** (0 failures across 11 test suites). The app features overflow-safe pre-flight disk storage checks, server ETag validation, MediaStore export, startup auto-recovery, and an overhauled Dark Mode UI with full M3 surfaceContainer hierarchy, glowing interface accents, crisp inactive network state borders with `WifiOff` / `SignalCellularOff` icons, a 4-state context-aware status banner, and clean discard handling returning directly to `Idle` with URL preservation.

## Recently Completed
- **Discard Button Bug Root Cause Identified & Resolved (ADR-016)**:
  - Discovered that `ACTION_CANCEL_DOWNLOAD` sent to `DownloadForegroundService` was calling `downloaderEngine.pauseDownload()`, inadvertently overwriting `Idle` with `Paused(0%)`.
  - Discovered that coroutine cancellation inside `startDownload` caught `CancellationException` and defaulted to `DownloadState.Paused(url)`.
  - Introduced `ACTION_DISCARD_DOWNLOAD` in `DownloadForegroundService` to safely stop service notifications without modifying engine state.
  - Added atomic `isDiscarding` flag in `DualDownloaderEngine` to ensure cancellation transitions directly to `DownloadState.Idle`.
  - Ensured partial `.part` files and `.dualboost_meta` sidecars are deleted on discard while preserving `urlInput` in `MainViewModel`.
- **Inactive Network Cards & Context-Aware 4-State Banner (ADR-016)**:
  - Updated inactive network cards with `surfaceContainer` fill, 1dp `outlineVariant.copy(alpha = 0.5f)` border, amber/grey indicator dots, and dynamic `WifiOff` / `SignalCellularOff` icons with 0.5f opacity.
  - Implemented 4-state context-aware network status banner:
    - *Dual-Channel Active*: `primaryContainer` pill ("Dual-Channel Active: Aggregating Wi-Fi + 5G").
    - *Both Offline*: `errorContainer` warning banner ("No Network Available: Reconnect Wi-Fi or Cellular to download.").
    - *Wi-Fi Only*: `surfaceContainerHigh` tip ("Using Wi-Fi only: Enable 'Mobile data always active' in Developer Options to aggregate 5G.").
    - *5G Only*: `surfaceContainerHigh` tip ("Using 5G Cellular only: Connect Wi-Fi to activate DualBoost aggregation.").
- **Test Suite Expanded (70 tests, 100% passing across 11 suites)**:
  - Added `testRepository_DiscardDownload_TransitionsToIdleAndCleansFiles` in `DownloadRepositoryTest.kt`.
  - Added `testDiscardDownload_PreservesUrlInputWhenDiscarded` in `MainViewModelTest.kt`.
  - Executed `.\gradlew.bat test` — all 70 tests passed cleanly.
- **Device Build & Verification**:
  - Compiled and installed debug APK onto Google Pixel 9 (`.\gradlew.bat installDebug`): BUILD SUCCESSFUL.
  - Captured live device screenshot `screen_current.png` confirming active dual-channel status, Dark Mode styling, and typography.

## Known Problems
- None. App compiles with 0 errors, 70/70 tests pass with 100% success, zero flicker on device during downloads, persistent chunk resumption active, discard cleanly returns to Idle with URL preserved, and inactive network cards render clearly.

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
- ADR-016: Discard Download State Decoupling & Inactive Network Context-Aware UI.

## Next Steps
1. User tests downloads on their Google Pixel 9 to confirm Discard button immediately resets to "Ready to Download" with URL preserved, and inactive network indicators when Wi-Fi/Cellular are toggled.

