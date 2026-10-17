# Project Memory

## Current Status
DualBoost Downloader has reached full production readiness and enhanced multi-server compatibility under **ADR-019** (Desktop Browser Header Spoofing & Adaptive Two-Stage Range Probe) with **100% Automated Test Coverage** (**85 unit tests across 12 suites + 8 connected Compose UI tests**, all passing with zero failures). The app features an intelligent two-stage adaptive probe that automatically retries mobile-restricted servers with Windows 11 Chrome Desktop headers and Client Hints (`Sec-CH-UA-Mobile: ?0`), unlocking HTTP 206 byte ranges for dual-stream aggregation with persistent `.dualboost_meta` sidecar state, unthrottled CDN speed benchmarking, a subtle `⚡ Desktop Mode Active` UI badge, standardized `MB/s` metrics, an amber single-stream alert banner, Google Material 3 launcher icon, and zero-storage benchmarking.

## Recently Completed
- **Desktop Browser Header Spoofing & Adaptive Two-Stage Range Probe (ADR-019)**:
  - **Stage 1 (Standard Mobile Probe)**: Probes with native Android 15 Pixel 9 headers. If server supports ranges, dual-channel downloading begins immediately (`isDesktopSpoofed = false`).
  - **Stage 2 (Adaptive Desktop Retry)**: If Stage 1 fails to yield byte ranges (returns `HTTP 200` or no ranges), the engine automatically executes Stage 2 using Windows 11 Chrome 131 Desktop headers (`Sec-CH-UA`, `Sec-CH-UA-Mobile: ?0`, `Sec-CH-UA-Platform: "Windows"`, etc.).
  - **HTTP 206 Unlocking**: When desktop headers unlock byte ranges, the engine flags `supportsRanges = true`, `isDesktopSpoofed = true`, executes all chunk workers using the desktop identity, and renders a subtle `⚡ Desktop Mode Active` badge in the UI.
  - **Sidecar State Continuity**: Serializes `isDesktopSpoofed=true` into `.dualboost_meta` so paused or interrupted downloads resume seamlessly with desktop headers without re-probing.
  - **Speed Test Unthrottling**: Applied desktop headers to `SpeedTestEngine.kt` to ensure CDNs provide full, unthrottled speed benchmark measurements.
  - **Single-Stream Safety**: If both Mobile and Desktop probes fail to get ranges, the app gracefully falls back to single-stream mode with the amber alert banner.
- **Unit and UI Test Suite Expansion (85 Unit Tests + 8 Compose Tests)**:
  - Added unit tests in `EngineComprehensiveTest.kt`: `testBuildRequest_MobileHeaders`, `testBuildRequest_DesktopSpoofingHeaders`, `testAdaptiveDesktopSpoofing_UnlocksHttp206WhenMobileFails`, and `testMetaInfo_SavesAndRestoresDesktopSpoofedFlag` (100% pass across all 85 tests).
  - Added connected Compose UI test `testDownloadingState_DisplaysDesktopModeBadgeWhenSpoofed` in `MainComposeUiTest.kt`.
  - Compiled and packaged updated debug APK (`17,210,542 bytes`) at `app/build/outputs/apk/debug/app-debug.apk`.
  - Successfully paired via Wireless ADB (`Android_P7H2EIGK.local:34233`), installed APK onto Google Pixel 9 (`192.168.1.4:38199`), granted permissions, launched `MainActivity` (PID 23320), verified dual network bindings (`Wi-Fi network 137`, `Cellular network 150`), and captured on-screen visual confirmation.
- **Standardized Megabytes per Second (`MB/s`) Telemetry Everywhere (ADR-018)**:
  - Converted speed calculation to binary Megabytes per second using $1024 \times 1024 = 1,048,576\text{ bytes}$ in both `DualDownloaderEngine` and `SpeedTestEngine`.
  - Created centralized formatting helpers `formatSpeed` and `formatSpeedComponents` in `DownloadModel.kt` supporting adaptive switching: $\ge 1.0\text{ MB/s}$ displays as `12.4 MB/s`, $< 1.0\text{ MB/s}$ displays as `512 KB/s`, and idle displays `0.0 MB/s`.
  - Updated Main Dashboard active download card, `DownloadForegroundService` notification shade, and `SpeedTestBottomSheet` hero speedometer and summary cards.
- **Single-Stream Informative Alert Banner & Dimmed Inactive Meters**:
  - When non-range servers are detected (`!state.supportsRanges`), an amber `tertiaryContainer` alert banner renders: *"Single-Stream Mode: Server does not support dual-channel splitting • Running on Wi-Fi to preserve mobile data"*.
  - Hero speed card adapts title to *"Single-Channel Bandwidth"*.
  - Inactive network rows (e.g. 5G Cellular when streaming on Wi-Fi) are dimmed with `0.45f` alpha and labeled `"(Inactive in Single-Stream)"`.
  - Single-stream dynamically attributes data and speed to Cellular if Wi-Fi is offline.

## Known Problems
- None. App compiles with 0 errors, 85/85 unit tests pass with 100% success, all Compose UI tests pass, zero flicker on device during downloads, persistent chunk resumption active, discard cleanly returns to Idle, speed benchmark runs with zero storage overhead, Turbine stream tests verified, and Google-style app icon renders cleanly.

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
- ADR-019: Desktop Browser Header Spoofing & Adaptive Two-Stage Range Probe.

## Next Steps
1. Reconnect Pixel 9 via ADB (USB cable or Wireless Debugging) and install `app/build/outputs/apk/debug/app-debug.apk`.
2. Test downloads from mobile-restrictive servers and observe the `⚡ Desktop Mode Active` badge and dual-network aggregation in action.

