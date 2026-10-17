# Project Memory

## Current Status
DualBoost Downloader is fully updated with **ADR-022 (Defense-in-Depth Security Hardening)** on top of **ADR-021 (Android Scoped Storage Conflict-Free File Resolution & Resilient Directory Fallback)** and **ADR-020 (Personal Cloud Slicer)**. All unit tests pass with 100% success rate. The application and companion Cloudflare Worker are protected against Server-Side Request Forgery (SSRF), open proxy relay abuse, timing side-channels, edge DoS memory exhaustion, directory path traversal (CWE-22), unauthenticated intent hijacking, physical ADB backup data extraction, and Logcat credential exposure.

## Recently Completed
- **Defense-in-Depth Security Hardening (ADR-022)**:
  - **Cloud Slicer Worker Hardening (`worker.js` & `SlicerPreferences.kt`)**:
    - **Fail-Closed Auth (`SEC-002`)**: Cloud Slicer strictly checks `SECRET_KEY`; returns HTTP 500 if unset in worker environment, HTTP 401 if invalid.
    - **Timing-Safe Token Comparison**: Implemented `timingSafeEqual()` constant-time check to prevent timing side-channel attacks.
    - **Anti-SSRF Protection (`SEC-001`)**: Implemented `validateTargetUrl()` blocking non-HTTP/HTTPS protocols, loopback (`127.0.0.1`, `localhost`), link-local/cloud metadata (`169.254.169.254`), private IP subnets (RFC 1918 `10.x`, `172.16-31.x`, `192.168.x`), and CGNAT (`100.64.x`).
    - **Anti-DoS Byte Bounds (`SEC-005`)**: Enforced 16 MB maximum slice limit (`MAX_SLICE_SIZE = 16 * 1024 * 1024`) preventing edge memory exhaustion.
    - **Live Health Security Check**: Updated `/health` endpoint to expose `secured: Boolean`.
  - **Android Path Traversal Containment (`SEC-003`)**:
    - Sanitized `extractFileNameFromDisposition` and `extractFileName` by stripping all directory path separators (`/`, `\`) and restricting to safe alphanumeric characters (`[a-zA-Z0-9._\-\s()]`).
    - Added strict canonical directory boundary validation in `resolveWritableOutputFile` (`candidate.canonicalPath.startsWith(safeBaseCanonical)`).
  - **Intent Auto-Start Hijacking Neutralization (`SEC-004`)**:
    - Removed `auto_start` intent extra execution in `MainActivity.kt`. Shared URLs strictly pre-populate the input field and display details, requiring deliberate user initiation.
  - **Credential & Backup Protection (`SEC-006`)**:
    - Set `android:allowBackup="false"` in `AndroidManifest.xml` preventing physical and ADB backup extraction of `SharedPreferences`.
  - **Logcat Query Parameter & Token Sanitization (`SEC-007`)**:
    - Added `sanitizeUrlForLog(url: String)` in `DualDownloaderEngine.kt` to strip query parameters (`?`) prior to logging, shielding HMAC tokens and signed URLs.
  - **Comprehensive Security Unit Tests**:
    - Added unit tests in `EngineComprehensiveTest.kt` verifying Unix/Windows path traversal neutralization, character sanitization, URL log query stripping, and directory containment.
- **Scoped Storage Conflict Resolution & Directory Fallback (ADR-021)**:
  - **Conflict-Safe Resolver (`resolveWritableOutputFile`) in `DualDownloaderEngine.kt`**: Checks if target file exists and belongs to another app or cannot be opened in `rw` mode; automatically generates indexed unique names (`name (1).ext`) and verifies write permissions before downloading.
  - **Graceful Directory Fallback**: Validates directory writability (`isDirWritable`); transparently routes to `context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)` if public Downloads rejects direct POSIX creation.
  - **Zero-Copy MediaStore Registration**: For files directly downloaded to public Downloads, indexes via `MediaScannerConnection.scanFile` with zero duplication.
  - **Dual-Directory Resume**: Updated `restoreIncompleteDownload` to check both public and app-specific directories.
  - **Manifest & Runtime Support**: Declared `MANAGE_EXTERNAL_STORAGE` and configured permission request handling in `MainActivity.kt`.
  - **Verified on Physical Pixel 9**: Replaced `open failed: EACCES (Permission denied)` failure with clean, resilient path resolution and safe error handling.
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
- None. App compiles with 0 errors, all unit tests pass with 100% success, zero flicker on device during downloads, persistent chunk resumption active, discard cleanly returns to Idle, speed benchmark runs with zero storage overhead, Turbine stream tests verified, and Google-style app icon renders cleanly.

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
- ADR-021: Android Scoped Storage Conflict-Free File Resolution & Resilient Directory Fallback.
- ADR-022: Defense-in-Depth Security Hardening (SSRF, Path Traversal, Intent Isolation, Anti-DoS).

## Next Steps
1. User can deploy `cloud-slicer/worker.js` to Cloudflare (or tap "Copy Script" in the app's Settings).
2. Enter the Worker URL in DualBoost Settings and tap "Test Worker".
3. Download any dynamic stream (like the Cloudflare 100MB test link) and enjoy high-speed dual Wi-Fi + 5G download aggregation!

