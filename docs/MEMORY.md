# Project Memory

## Current Status
DualBoost Downloader has reached full production readiness and visual polish under **ADR-018** (Standardized Binary MB/s Telemetry & Single-Stream Fallback Amber Alert) with **100% Automated Test Coverage** (**81 unit tests across 12 suites + 7 connected Compose UI tests** on physical Google Pixel 9, all passing with zero failures). The app features standardized Megabytes per second (`MB/s`) metrics with adaptive `KB/s` auto-switching for sub-megabyte transfers, an informative amber alert banner for non-range servers with dimmed inactive meters, Google Material 3 adaptive launcher icon with crisp white background and dynamic themed icon support, an in-app ephemeral 3-phase speed benchmark with zero disk storage footprint, overflow-safe pre-flight disk storage checks, server ETag validation, MediaStore export, and startup auto-recovery on Google Pixel 9.

## Recently Completed
- **Standardized Megabytes per Second (`MB/s`) Telemetry Everywhere (ADR-018)**:
  - Converted speed calculation to binary Megabytes per second using $1024 \times 1024 = 1,048,576\text{ bytes}$ in both `DualDownloaderEngine` and `SpeedTestEngine`.
  - Created centralized formatting helpers `formatSpeed` and `formatSpeedComponents` in `DownloadModel.kt` supporting adaptive switching: $\ge 1.0\text{ MB/s}$ displays as `12.4 MB/s`, $< 1.0\text{ MB/s}$ displays as `512 KB/s`, and idle displays `0.0 MB/s`.
  - Updated Main Dashboard active download card, `DownloadForegroundService` notification shade, and `SpeedTestBottomSheet` hero speedometer and summary cards.
- **Single-Stream Informative Alert Banner & Dimmed Inactive Meters**:
  - When non-range servers are detected (`!state.supportsRanges`), an amber `tertiaryContainer` alert banner renders: *"Single-Stream Mode: Server does not support dual-channel splitting • Running on Wi-Fi to preserve mobile data"*.
  - Hero speed card adapts title to *"Single-Channel Bandwidth"*.
  - Inactive network rows (e.g. 5G Cellular when streaming on Wi-Fi) are dimmed with `0.45f` alpha and labeled `"(Inactive in Single-Stream)"`.
  - Single-stream dynamically attributes data and speed to Cellular if Wi-Fi is offline.
- **Automated Test Suite Expansion**:
  - Added unit test `testFormatSpeed_AdaptiveFormatting` in `UIFlowAndViewModelComprehensiveTest.kt` asserting all boundary conditions (81/81 unit tests pass).
  - Added connected Compose UI test `testSingleStreamFallback_DisplaysAmberBannerAndSingleChannelBandwidth` in `MainComposeUiTest.kt`.
  - Executed `test-automated.ps1`: all 81 unit tests + all 7 connected on-device tests on Pixel 9 passed with 100% success (0 failures).
  - Built, installed, and launched latest APK on Pixel 9 with zero crashes.
- **Expanded Quick Test Benchmark Suite (7 Curated Tiers)**:
  - Added 50 MB (`platform-35_r02.zip`), 500 MB (`emulator-windows_x64-16555280.zip`), and 1 GB (`android-studio-2024.2.1.11-windows.exe`) from Google Edge CDN, verified via HTTP 206 probe testing for full dual-channel byte aggregation.
  - Organized all 7 presets in strictly ascending size order: 10 MB, 25 MB, 50 MB, 100 MB (Cloudflare single-stream fallback), 150 MB, 500 MB, and 1 GB.
- **Turbine Flow Testing Integration (`app.cash.turbine:turbine:1.2.0`)**:
  - Integrated Cash App's Turbine library into `gradle/libs.versions.toml` and `app/build.gradle.kts`.
  - Added 4 live stream tests asserting exact state progression without race conditions in `MainViewModelTest` and `SpeedTestEngineTest`.
- **Google Material 3 App Icon Redesign (ADR-017)**:
  - Clean, crisp pure white background (`#FFFFFF`) in `ic_launcher_background.xml` matching Google Pixel first-party system apps.
  - Geometric Material 3 vector curves with Electric Cyan / Azure and Neon Violet / Purple streams converging into a central downward arrow with white lightning core, sized inside the 72dp safe zone.
  - Monochrome silhouette for dynamic Monet Pixel Themed Icons.
- **Ephemeral 3-Phase Speed Benchmark (ADR-017)**:
  - Implemented `SpeedTestEngine.kt` and `SpeedTestModels.kt` executing a 3-phase automated throughput test (Wi-Fi 3s -> 5G 3s -> DualBoost 4s) with 0 bytes stored on disk.
  - Integrated `SpeedTestBottomSheet.kt` with live speedometer readout and dynamic boost gain multiplier banner.

## Known Problems
- None. App compiles with 0 errors, 81/81 unit tests pass with 100% success, 7/7 on-device Compose UI tests pass, zero flicker on device during downloads, persistent chunk resumption active, discard cleanly returns to Idle, speed benchmark runs with zero storage overhead, Turbine stream tests verified, and Google-style app icon renders cleanly.

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
- ADR-018: Standardized Megabytes-per-Second (MB/s) Telemetry and Single-Stream Fallback Amber Alert.

## Next Steps
1. User tests live downloads and the in-app speed benchmark with the new `MB/s` metrics on their Google Pixel 9.

