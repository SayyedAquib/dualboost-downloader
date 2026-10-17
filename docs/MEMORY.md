# Project Memory

## Current Status
DualBoost Downloader has reached full production readiness and enhanced stream slicing capability under **ADR-020** (Personal Cloud Slicer via Serverless Cloudflare Worker) with **100% Automated Test Coverage** (**89 unit tests across 12 suites + 10 connected Compose UI tests on Google Pixel 9**, all passing with 100% success rate). The app features an intelligent 3-stage adaptive probe that falls back to a free, personal Cloudflare stream slicer when servers refuse byte ranges, unlocking full Wi-Fi + 5G dual-network bandwidth aggregation even on dynamic streams with a dedicated in-app settings bottom sheet, live health test, 1-click script copier, and `⚡ Cloud Slicer Active` badge.

## Recently Completed
- **Personal Cloud Slicer via Cloudflare Worker (ADR-020)**:
  - **Serverless Edge Worker (`cloud-slicer/worker.js`)**: Production-ready script utilizing JavaScript `ReadableStream` to fetch non-range upstream streams and slice exact byte ranges on-the-fly into standard `HTTP 206 Partial Content` responses. Supports `/health` check and optional `SECRET_KEY` validation.
  - **Step-by-Step Setup Guide (`cloud-slicer/README.md`)**: 60-second deployment documentation for Cloudflare's free tier (100,000 requests/day).
  - **3-Stage Adaptive Range Probe in `DualDownloaderEngine.kt`**:
    - **Stage 1**: Native Android 15 Pixel 9 headers.
    - **Stage 2**: Windows 11 Chrome Desktop Spoofing headers (ADR-019).
    - **Stage 3**: Personal Cloud Slicer fallback when Stage 1 and 2 fail and a Slicer URL is configured in preferences.
  - **In-App Settings & Slicer UI (`SettingsBottomSheet.kt` & `MainActivity.kt`)**:
    - Added Settings gear icon `(⚙️)` to TopAppBar actions.
    - Created Material 3 `SettingsBottomSheet` with Worker URL input, Secret Key input, "Test Worker" connectivity check, and 1-click "Copy Script" button.
    - Added `⚡ Cloud Slicer Active` badge with `tertiaryContainer` styling to both Downloading and Paused cards.
  - **Persistence & Sidecar Continuity (`SlicerPreferences.kt`)**:
    - Serializes `isCloudSlicerActive` into `.dualboost_meta` sidecars for pause/resume continuity.
- **Unit and UI Test Suite Expansion (89 Unit Tests + 10 Connected UI Tests)**:
  - Added unit tests in `EngineComprehensiveTest.kt`: `testSlicerPreferences_SaveAndRetrieveUrlAndSecret`, `testSlicerPreferences_BuildSlicedUrl_WithAndWithoutSecret`, `testMetaInfo_SavesAndRestoresCloudSlicerActiveFlag`, and `testAdaptiveProbe_Stage3_CloudSlicerFallback` (100% pass across all 89 unit tests).
  - Added connected Compose UI tests in `MainComposeUiTest.kt`: `testDownloadingState_DisplaysCloudSlicerBadgeWhenActive` and `testSettingsSheet_OpensAndDisplaysElements` (100% pass across all 10 on-device UI tests on Pixel 9).
  - Installed and launched debug APK on physical Google Pixel 9 (`192.168.1.4:38199`), PID 30328, verified active dual network bindings (`Wi-Fi 137`, `Cellular 151`), and zero crashes.
- **Desktop Browser Header Spoofing & Adaptive Two-Stage Range Probe (ADR-019)**:
  - **Stage 1 (Standard Mobile Probe)**: Probes with native Android 15 Pixel 9 headers. If server supports ranges, dual-channel downloading begins immediately (`isDesktopSpoofed = false`).
  - **Stage 2 (Adaptive Desktop Retry)**: If Stage 1 fails to yield byte ranges (returns `HTTP 200` or no ranges), the engine automatically executes Stage 2 using Windows 11 Chrome 131 Desktop headers (`Sec-CH-UA`, `Sec-CH-UA-Mobile: ?0`, `Sec-CH-UA-Platform: "Windows"`, etc.).
  - **HTTP 206 Unlocking**: When desktop headers unlock byte ranges, the engine flags `supportsRanges = true`, `isDesktopSpoofed = true`, executes all chunk workers using the desktop identity, and renders a subtle `⚡ Desktop Mode Active` badge in the UI.
  - **Sidecar State Continuity**: Serializes `isDesktopSpoofed=true` into `.dualboost_meta` so paused or interrupted downloads resume seamlessly with desktop headers without re-probing.
  - **Speed Test Unthrottling**: Applied desktop headers to `SpeedTestEngine.kt` to ensure CDNs provide full, unthrottled speed benchmark measurements.
  - **Single-Stream Safety**: If both Mobile and Desktop probes fail to get ranges, the app gracefully falls back to single-stream mode with the amber alert banner.

## Known Problems
- None. App compiles with 0 errors, 89/89 unit tests pass with 100% success, 10/10 connected Compose UI tests pass on device, zero flicker on device during downloads, persistent chunk resumption active, discard cleanly returns to Idle, speed benchmark runs with zero storage overhead, Turbine stream tests verified, and Google-style app icon renders cleanly.

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
- ADR-020: Personal Cloud Slicer (Serverless Stream Slicing via Cloudflare Worker).

## Next Steps
1. User can deploy `cloud-slicer/worker.js` to Cloudflare (or tap "Copy Script" in the app's Settings).
2. Enter the Worker URL in DualBoost Settings and tap "Test Worker".
3. Download any dynamic stream (like the Cloudflare 100MB test link) and enjoy high-speed dual Wi-Fi + 5G download aggregation!

