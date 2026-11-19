# Product Requirements Document

## Product Overview
DualBoost Downloader is an Android utility application engineered for the Google Pixel 9 that accelerates file downloads by concurrently aggregating bandwidth across Wi-Fi and Cellular (5G/LTE) networks. It operates entirely without requiring root privileges or external subscription proxy servers, and is built, deployed, and tested via command-line tools (Gradle CLI and ADB) without dependency on the Android Studio IDE.

## Target Users
- Power users and developers downloading large assets (firmware, ROMs, ISOs, game caches, video archives) who want to combine Wi-Fi and 5G speeds.
- Users connected to bandwidth-constrained or asymmetric Wi-Fi networks (e.g., airport, hotel, or office Wi-Fi) who want to supplement transfer rates using cellular data.
- Developers developing and testing Android apps strictly from terminal environments via ADB and Gradle.

## Problem Statement
Standard Android operating system behavior automatically deactivates or idles the mobile cellular radio when Wi-Fi connects, routing 100% of download traffic through Wi-Fi. While some OEM skins (e.g., Samsung Download Booster) provide proprietary aggregators, stock Pixel devices running Android 14/15 provide no native user-facing mechanism to bond or concurrently pull download streams over both physical network interfaces.

## Goals
- Provide concurrent multi-network file downloads using simultaneous Wi-Fi and Cellular data streams.
- Dynamically self-balance download chunk distribution so faster connections automatically process more data without blocking on slower connections.
- Ensure completed downloads are directly accessible via the public Android `Downloads/` directory across all third-party file managers and USB file transfers.
- Enable full background execution through an Android Foreground Service with live dual-speed status reporting in the notification shade.
- Support link intake via manual text entry and native Android system Share Sheet from web browsers (e.g., Chrome, Firefox).
- Provide clear user controls for toggling Cellular Boost and enforcing cellular safety caps to prevent unwanted data overages.
- Maintain a 100% command-line buildable and deployable toolchain using Gradle CLI and ADB.

## Non-Goals
- System-wide VPN channel bonding for third-party streaming apps (e.g., YouTube, Netflix, social feeds).
- Torrent / BitTorrent peer-to-peer downloading.
- Bypassing DRM, paywalls, or authentication on protected video/audio streams.
- Supporting iOS or desktop platforms.
- Requiring root or kernel modifications on the device.

## Core Features
1. **Multi-Interface Concurrency Engine**: Simultaneous HTTP Range chunk downloading over separate Wi-Fi and 5G network interfaces.
2. **Dynamic Work Queue**: Automatic distribution of discrete byte chunks (e.g., 4 MB segments) to whichever interface completes its workload first.
3. **Public Downloads Storage**: Direct storage integration saving finished files directly to the public device `Downloads/` folder.
4. **Data Safety Controls**:
   - One-tap toggle to enable or disable Cellular Boost.
   - Optional configurable maximum cellular data threshold per download.
5. **Real-Time Visual Speed Telemetry**: Live UI and persistent notification displaying discrete Wi-Fi speed (`MB/s`), Cellular speed (`MB/s`), and combined total speed calculated via true binary Megabytes per second ($1\text{ MB} = 1024 \times 1024\text{ bytes}$) with adaptive auto-switching to `KB/s` for sub-megabyte transfers.
6. **Graceful Fallback & Single-Stream Alert**:
   - Automatic single-stream fallback if remote servers do not support HTTP 206 Partial Content, accompanied by a prominent amber alert banner on the active download card.
   - Seamless chunk reassignment to the surviving network if either Wi-Fi or Cellular disconnects mid-transfer.
7. **System Share Sheet Integration**: Receiver intent allowing URLs to be sent directly to the downloader from any browser.
8. **Combined Dual-Interface Speed Benchmark**: Ephemeral 3-phase throughput test measuring Wi-Fi alone (3s), 5G Cellular alone (3s), and DualBoost Combined (4s) using high-speed CDN chunk streams with zero storage consumption (0 bytes stored on disk) and displaying real-time speedometers and aggregate boost multiplier.
9. **Google Material 3 System Harmonization**: Adaptive launcher icon designed to match Google Pixel system apps with clean white background, geometric vector stream convergence, and Monet dynamic themed icon support.
10. **Curated Quick Test Benchmark Tiers**: Seven pre-configured download presets sorted in ascending order (10 MB, 25 MB, 50 MB, 100 MB Cloudflare, 150 MB, 500 MB, 1 GB) targeting high-capacity Google Edge and Fastly CDNs to easily stress-test dual-channel aggregation across various payload sizes with zero manual typing.
11. **Desktop Browser Header Spoofing & Adaptive Two-Stage Range Probe**: Automatic Stage 2 probe escalation using Windows 11 Chrome Desktop headers and Client Hints (`Sec-CH-UA-Mobile: ?0`, `Sec-CH-UA-Platform: "Windows"`) when remote CDNs refuse byte ranges to mobile Android clients, unlocking multi-stream acceleration with persistent sidecar state and displaying an active `⚡ Desktop Mode Active` badge.
12. **Personal Cloud Slicer (Serverless Cloudflare Worker Integration)**: Optional Stage 3 serverless edge proxy that slices non-range streams into HTTP 206 Partial Content on-the-fly, protected by DoH dual-stack anti-SSRF filtering, timing-safe authentication, and port whitelisting.
13. **Persistent Download History & File Library**: App-private atomic JSON ledger preserving completed download records across sessions with disk usage counters, 1-tap FileProvider opening (`ACTION_VIEW`), system sharing (`ACTION_SEND`), SHA-256 verification copy, and smart deletion choice (history only vs physical device storage).
14. **Dynamic Network Generation Telemetry & Responsive Warning Badges**: Real-time signal strength bars (0..4) and qualitative status indicators (`Excellent • 4/4 bars`) across both Wi-Fi and Cellular interfaces, with automatic detection of 5G vs 4G LTE via `TelephonyCallback.DisplayInfoListener` and dynamic amber warning badges alerting the user when operating on 4G LTE where aggregation throughput is lower than 5G.
15. **Harmonized Top Navigation & Modal Drawer**: Balanced Material 3 `CenterAlignedTopAppBar` featuring a left navigation drawer trigger and right-side action buttons, backed by a slide-out `ModalNavigationDrawer` organizing secondary utilities (Download Library, Speed Benchmark, Cloud Slicer Settings, and About DualBoost).
16. **Large File Performance & Thermal Optimization**: Instant completion with zero post-processing delays on multi-gigabyte transfers (e.g. 30GB files) via bypass of synchronous SHA-256 flash hashing, accompanied by fixed-height (12dp) dual-color contribution progress rendering that preserves phone thermals and eliminates high Compose node tree recompositions.
17. **Consumer-Grade Humanized UI & Integrated Files Management**: Decoded human-readable media titles (free of `%20`/`_20`), consumer-friendly labels ("Download Speed", "Wi-Fi", "Mobile Data"), separate Cancel & Pause buttons with safety confirmation, and direct system file manager navigation ("Show in Files" integrating with Google Files / `ACTION_VIEW_DOWNLOADS`).
18. **Production-Grade Concurrency, Crash Resilience & Lifecycle Hardening (`ADR-032`)**: Single-dispatch Foreground Service architecture preventing download initialization race conditions, zero-dependency `CrashVault` capturing sanitized local crash dumps, interactive Foreground notification actions (Pause/Resume, Cancel), smart back-navigation handling with active transfer minimization dialogs, and actionable Retry/Dismiss recovery controls in failure states.
19. **Sequential Chunking & Instant Video Stream Preview (`ADR-036`)**: Automatic detection of video containers (`.mkv`, `.mp4`, `.webm`, etc.) with sequential chunk ordering, MP4 `moov` atom trailer prioritization, a 24 MB lead buffer readiness threshold, and 1-tap `[ ▶ Watch Preview ]` in-app hero button and ongoing notification action that launches external media players (VLC, MX Player, Google Photos) with hardware decoding while downloading continues in the background.
20. **Multi-Download Queue & Auto-Advancing Batch Downloader (`ADR-037`)**: Atomic persistent FIFO queue enabling overnight multi-file downloading with progress tracking, persistent disk state, and resilient auto-advance that continues to the next file if an item completes or encounters an error/threat.
21. **In-App Browser & Floating Media Sniffer (`ADR-037`)**: Embedded isolated WebView browser bypassing ad redirectors, popups, and click-jack traps (e.g. on streaming portals like `moviesleech.media`), with dynamic sniffer intercepting underlying direct media streams (`.mkv`, `.mp4`, etc.) with 1-tap `[ ⚡ Boost Download ]` and `[ ➕ Add to Queue ]`.
22. **Hybrid 3-Stage Antivirus & Threat Guard System (`ADR-038`)**: On-device real-time inspection blocking malware, trojans, disguised APK packages (`.mkv.apk`), executable extensions (`.exe`, `.bat`, `.apk`), fake MIME types (`application/vnd.android.package-archive`), known malicious ad-redirect domains, and magic byte header mismatches (`PK\x03\x04` or `MZ` disguised as video), with immediate socket abort, disk byte scrubbing, persistent `SecurityPreferences` toggle, and dedicated `SecurityThreatDialog`.
23. **Expanded Social Media Downloader (`ADR-039`)**: Automatic link detection and high-quality stream extraction across 9 platforms (YouTube, Instagram, TikTok, Twitter/X, Reddit, Facebook, Pinterest, SoundCloud, Vimeo) with multi-instance resolver failover, direct Instagram HTML scraping, and a sleek `SocialMediaModalSheet` offering 4K/1080p, 720p HD, and MP3 audio downloads.
24. **Dynamic Cellular Generation Labeling (`ADR-040`)**: Pure telephony mapping resolving active cellular generations (`5G`, `4G LTE`, `3G`, `Cellular`) across the UI, quick presets, and foreground notifications, eliminating hardcoded "5G" claims on 4G networks.
25. **Dynamic Wi-Fi Generation, Frequency Band & Link Speed Classification (`ADR-041`)**: Real-time extraction of Wi-Fi 7 (be), 6E (6 GHz), 6 (ax), 5 (ac), and frequency bands with negotiated multi-gigabit link speeds (`X Mbps`), providing full transport transparency.
26. **Dynamic Multi-Thread Concurrency Tuning & Auto-Scaling (`ADR-042`)**: Configurable independent per-interface worker streams (`1..8`), opportunistic Auto-Scale mode on high-bandwidth links (>25 MB/s), persistent SharedPreferences storage, and non-negotiable thermal fail-safe limits (`MODERATE` caps cellular to 1 stream, `SEVERE` offloads to Wi-Fi).
27. **Dynamic Stream Visualizer Palette & Material You Theming (`ADR-043`)**: Material You wallpaper-adaptive stream colors on Android 12+ harmonizing with system primary and tertiary accents, alongside selectable curated dual-tone palettes (Cyberpunk Neon, Emerald & Amber, Sunset Coral) with dark/light mode contrast compliance.
28. **Dynamic Download Storage Directory & SAF Integration (`ADR-044`)**: Customizable target download locations with built-in subfolder presets (`Standard`, `DualBoost`, `Media`), Storage Access Framework (SAF) folder picker (`ACTION_OPEN_DOCUMENT_TREE`) supporting external directories and removable media, automatic safe fallback to standard Downloads on unmounted/unwritable volumes, and cross-directory resumption scanning.
29. **Dynamic Cellular Network Type Detection & 5G Real-Time Handoff (`ADR-048`)**: Continuous telephony and network capabilities classification eliminating 5G NSA anchor traps (`telephonyManager.dataNetworkType` returning LTE 13 on 5G NSA), integrating `TelephonyCallback.DisplayInfoListener` (`OVERRIDE_NETWORK_TYPE_NR_NSA` / `NR_ADVANCED`), link downstream bandwidth ($\ge 120\text{ Mbps}$) carrier aggregation heuristic, automatic modem radio wake-up re-evaluation upon download start, and user-configurable modes (`Auto-Detect (Dynamic)`, `Force 5G Mode`, `Force 4G Mode`) with live UI badges (`⚡ Force 5G`, `⚡ Force 4G`).

## User Flows
1. **Direct URL Download Flow**:
   - User opens DualBoost Downloader.
   - Verifies active status chips for both Wi-Fi and Mobile Data.
   - Pastes URL into the input field and taps "Start Dual Download".
   - Observes real-time Wi-Fi and 5G speed meters and progress bar.
   - Upon completion, file is available in device `Downloads/` folder.
2. **Browser Share Sheet Flow**:
   - User browses to a file download link in Google Chrome on Pixel 9.
   - Taps "Share" > selects "DualBoost Downloader".
   - App opens with URL pre-populated and prompts to start.
3. **Background Download Flow**:
   - User starts download and locks phone or switches to another app.
   - Foreground notification maintains ongoing download without OS termination.
   - Notification updates live transfer rates and dual-network breakdown.
4. **Personal Cloud Slicer Flow**:
   - User navigates to Settings (gear icon in TopAppBar).
   - Taps "Copy Script" to copy the pre-built serverless slice worker to clipboard.
   - Deploys it to Cloudflare Workers in 60 seconds (free tier, 100k requests/day).
   - Enters their worker URL into DualBoost and taps "Test Worker".
   - Any future file download on servers that reject byte ranges automatically routes through the Cloud Slicer, transforming the single stream into parallel HTTP 206 byte chunks and rendering the `⚡ Cloud Slicer Active` badge.
5. **Dynamic Chunk Segment Slicing & Auto-Adaptive Sizing (ADR-045)**:
   - User navigates to Settings > "Chunk Slicing Strategy".
   - By default, Auto-Adaptive Sizing scales chunk segment sizes dynamically from 1 MB (small files or spotty links) up to 64 MB (multi-gigabit Wi-Fi 7 / 5G on multi-gigabyte payloads).
   - User can manually select fixed chunk sizes spanning 1 MB, 2 MB, 4 MB, 8 MB, 16 MB, 32 MB, or 64 MB.
   - Incomplete downloads resuming from `.dualboost_meta` strictly preserve their original chunk size to ensure 100% data integrity and zero corruption.
   - Active download card displays an adaptive badge (`⚡ X MB Chunks`).
6. **Dynamic User-Agent Strings & Phone Model Identity (ADR-046)**:
   - User navigates to Settings > "Browser Identity (User-Agent)".
   - By default, Native Device Auto-Detect dynamically discovers the phone model and Android OS version via `Build.MODEL` and `Build.VERSION.RELEASE`.
   - User can choose from built-in device presets (`Pixel 9`, `Galaxy S24 Ultra`, `Desktop Chrome`) or define arbitrary custom User-Agent strings.
   - Desktop Chrome fast-tracks directly to desktop headers and Client Hints, bypassing mobile probing delays on desktop-only CDNs.
   - Live Header Preview box allows 1-tap copy of the active User-Agent string to the system clipboard.
   - Session Resumption Invariance ensures resumed downloads retain their originating User-Agent in `.dualboost_meta`, preventing mid-stream CDN 403 Forbidden errors.
   - Active download card displays an active `⚡ Custom UA` badge when non-default identities are engaged.
7. **Configurable Network Timeouts & Fast Failover Policy (ADR-047)**:
   - User navigates to Settings > "Network Timeouts & Failover".
   - Built-in failover profiles tailored to connection characteristics:
     - `Fast Failover` (AGGRESSIVE): 5s connect, 10s read, 3 retries, 150ms backoff. Rapidly aborts dead sockets and shifts traffic to the alternative interface.
     - `Balanced (Default)`: 15s connect, 30s read, 5 retries, 300ms backoff. Optimized for stable broadband and 5G.
     - `Resilient`: 30s connect, 60s read, 8 retries, 1000ms backoff. Tolerates high jitter, packet loss, and satellite/hotel Wi-Fi.
     - `Custom`: Granular steppers for user-defined limits (connect 3s..60s, read 5s..120s, retries 1..15, backoff 50ms..3000ms).
   - Dynamic Socket Re-binding: Adjusting timeouts rebuilds underlying OkHttp clients immediately on the fly without network disconnection.
   - Dynamic Transport Resumption: Unlike chunk size or User-Agent, timeout configurations apply dynamically to active and resumed downloads without re-downloading existing chunks.
   - Active download card displays a dynamic badge (`⚡ Fast Failover`, `⚡ Resilient Mode`, or `⚡ Custom Timeouts`).
8. **Dynamic Cellular Network Type Detection & 5G Real-Time Handoff (ADR-048)**:
   - User navigates to Side Drawer > Settings > "Cellular Network Detection".
   - By default, `Auto-Detect (Dynamic)` leverages telephony display overrides and hardware link bandwidth to detect 5G NSA, 5G SA, and 5G Advanced.
   - When the modem sleeps in LTE idle mode, starting a download automatically triggers `refreshCellularGeneration()` to catch the carrier SCG radio wake-up and instantly transition the UI hero label to 5G.
   - Users on non-standard carriers can manually select `Force 5G Mode` or `Force 4G Mode` to override carrier telemetry.
   - Active and paused download cards display a status badge (`⚡ Force 5G` or `⚡ Force 4G`) when manual mode is active.

## Future Features
- Multi-file batch download queue.
- Scheduled downloading (e.g., trigger mobile boost only during off-peak carrier hours).
- In-app browser with automatic media sniffer.
- Per-host download speed benchmarking history.

## Technical Constraints
- Target Device: Google Pixel 9 running Android 14 (API 34) or Android 15 (API 35).
- OS Prerequisite: Device must have "Mobile data always active" enabled in Developer Options.
- Build Environment: 100% headless / terminal buildable using Gradle CLI and Java 17.
- Deployment Tool: Android Debug Bridge (`adb`) via USB or Wi-Fi pairing. No Android Studio IDE requirement.
- Server Requirement: Multi-network acceleration requires remote servers supporting HTTP `Range` requests (`Accept-Ranges: bytes`).

## Success Metrics
- **Throughput Boost**: Combined download speed achieves $\ge 80\%$ of (Wi-Fi speed + Cellular speed) for multi-megabyte files on range-compliant servers.
- **Reliability**: 0% data corruption on completed segmented downloads (verified via hash matching against single-stream downloads).
- **Zero Stall**: Seamless transfer completion when either network is intentionally toggled off during an active download.

## Acceptance Criteria
- [ ] User can initiate a dual-network download from both manual input and browser Share Sheet.
- [ ] UI displays distinct, real-time speed metrics for both Wi-Fi and Cellular.
- [ ] Completed files appear in the device `Downloads/` folder and can be opened by native Android file viewers.
- [ ] Toggling Cellular off mid-download does not abort the download; remaining chunks transfer over Wi-Fi.
- [ ] App builds via command line `./gradlew.bat assembleRelease` and installs via `adb install -r app-release.apk` with authentic release signing.
- [ ] 0% silent download corruption guaranteed by write boundary capping, single-stream length checks, and fail-closed resume verification.

