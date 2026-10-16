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
5. **Real-Time Visual Speed Telemetry**: Live UI and persistent notification displaying discrete Wi-Fi speed (Mbps), Cellular speed (Mbps), and combined total speed.
6. **Graceful Fallback**:
   - Automatic single-stream fallback if remote servers do not support HTTP 206 Partial Content.
   - Seamless chunk reassignment to the surviving network if either Wi-Fi or Cellular disconnects mid-transfer.
7. **System Share Sheet Integration**: Receiver intent allowing URLs to be sent directly to the downloader from any browser.
8. **Combined Dual-Interface Speed Benchmark**: Ephemeral 3-phase throughput test measuring Wi-Fi alone (3s), 5G Cellular alone (3s), and DualBoost Combined (4s) using high-speed CDN chunk streams with zero storage consumption (0 bytes stored on disk) and displaying real-time speedometers and aggregate boost multiplier.
9. **Google Material 3 System Harmonization**: Adaptive launcher icon designed to match Google Pixel system apps with clean white background, geometric vector stream convergence, and Monet dynamic themed icon support.

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
- [ ] App builds via command line `./gradlew assembleDebug` and installs via `adb install -r`.

