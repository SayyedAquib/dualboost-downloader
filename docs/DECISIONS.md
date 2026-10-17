# Decision Log

## ADR-001: Native Kotlin & Jetpack Compose over Cross-Platform Frameworks

### Date
2026-10-15

### Status
Accepted

### Context
The application requires direct, low-level access to Android's `ConnectivityManager`, explicit interface requests (`TRANSPORT_WIFI` and `TRANSPORT_CELLULAR`), and socket binding via `Network.socketFactory`.

### Options Considered
- Native Android with Kotlin and Jetpack Compose
- Cross-platform framework (Flutter)
- Cross-platform framework (React Native)

### Decision
Use Native Kotlin 2.0 with Jetpack Compose.

### Reasoning
Cross-platform network libraries (Dart `http`, JavaScript `fetch`) route through standard system sockets without exposed bindings for Android's `Network` objects. Supporting dual-interface binding in Flutter or React Native would require writing the exact same native Kotlin platform channels anyway, introducing unnecessary bridge serialization overhead.

### Trade-offs
No iOS code reuse, but the application's core feature relies on Android-specific hardware and kernel socket capabilities.

### Consequences
Direct access to Android SDK 35 APIs, zero bridge overhead, and first-class coroutine concurrency.

### Alternatives Rejected
- *Flutter*: Rejected due to lack of native dual-socket bindings in Dart network runtime.
- *React Native*: Rejected due to unnecessary JavaScript thread bridge latency.

---

## ADR-002: Dynamic Adaptive Chunk Queue over Static Splitting

### Date
2026-10-15

### Status
Accepted

### Context
Wi-Fi and 5G connections have variable and asymmetric throughput. A static 50/50 division causes the total download time to be bottlenecked by the slower connection.

### Options Considered
- Static 50/50 partition (two large streams)
- Dynamic adaptive chunk queue (e.g., 4MB segments claimed on demand)
- Wi-Fi primary with threshold-triggered cellular boost

### Decision
Implement a dynamic work queue of 4 MB byte-range chunks.

### Reasoning
Whenever an interface completes a chunk, it immediately pulls the next available chunk from the shared queue. If 5G is 3x faster than Wi-Fi, it naturally consumes 75% of the chunks while Wi-Fi consumes 25%, self-balancing automatically without stalls.

### Trade-offs
Slightly more HTTP request headers compared to a single long-lived stream.

### Consequences
Optimal aggregate throughput and resilience against fluctuations or sudden drops on either connection.

### Alternatives Rejected
- *Static Split*: If Wi-Fi slows to 1 Mbps and 5G runs at 100 Mbps, 5G finishes its half early and idles while waiting for Wi-Fi.

---

## ADR-003: Direct RandomAccessFile Writes over Temp File Concatenation

### Date
2026-10-15

### Status
Accepted

### Context
When chunks arrive out of order across Wi-Fi and Cellular, they must be assembled into a single coherent file on disk.

### Options Considered
- Write individual chunk files (`.part1`, `.part2`) and concatenate at the end
- Pre-allocate destination file and write chunks directly to byte offsets using `RandomAccessFile`
- Buffer in memory and stream sequentially

### Decision
Use direct `RandomAccessFile` offset writes.

### Reasoning
Pre-allocating the file (`raf.setLength(totalBytes)`) and seeking to `chunk.startByte` allows chunks to be written immediately to their final positions. When the last chunk completes, the download is instantly finished with zero post-processing delay.

### Trade-offs
Requires knowing the exact file size upfront via `Content-Length`. For files with chunked transfer encoding (unknown size), a single-stream sequential fallback is used.

### Consequences
Eliminates the requirement for $2\times$ disk space during download and prevents device stalls when finishing multi-gigabyte files.

### Alternatives Rejected
- *Temp file concatenation*: Copying and stitching a 4 GB file on mobile flash storage can take over a minute and wears flash memory.
- *In-memory buffering*: Out-of-memory crash risk on files larger than several hundred megabytes.

---

## ADR-004: CLI-First Toolchain (Gradle + ADB) over Android Studio

### Date
2026-10-15

### Status
Accepted

### Context
The user prefers a lightweight, terminal-based development workflow without the overhead and GUI constraints of Android Studio.

### Options Considered
- Standard Android Studio IDE workflow
- Headless command-line workflow using Gradle Wrapper (`gradlew.bat`) and ADB

### Decision
Standardize the project on Gradle CLI and ADB.

### Reasoning
The user already has OpenJDK 17, Android SDK 35, and ADB installed. Gradle CLI provides faster, reproducible builds and easier automation for AI coding agents.

### Trade-offs
No graphical layout inspector or drag-and-drop UI designer; layouts are inspected on device or via logcat.

### Consequences
Faster iteration speed, zero IDE project file pollution in git, and full headless CI/CD readiness.

### Alternatives Rejected
- *Android Studio*: Rejected per user constraint to avoid IDE resource overhead.

---

## ADR-005: Decoupled NetworkProvider Interface for MockWebServer Testing

### Date
2026-10-15

### Status
Accepted

### Context
Testing dual-network concurrent downloads on developer workstations requires running without Android's physical `ConnectivityManager` service stubs.

### Options Considered
- Direct coupling to `DualNetworkManager` requiring Robolectric or connected device
- Decoupled `NetworkProvider` interface allowing synthetic in-memory MockWebServer routing

### Decision
Extract `NetworkProvider` interface implemented by `DualNetworkManager` and mockable by `TestNetworkProvider`.

### Reasoning
Enables lightning-fast JVM execution of multi-megabyte segmented chunk downloads, testing HTTP 206 range slices and SHA-256 hash validation in seconds without requiring an emulator or physical device.

### Consequences
100% automated integration test coverage for segmented downloads via `./gradlew test`.

---

## ADR-006: Pre-Flight Storage Check & Strict Chunk Truncation Verification

### Date
2026-10-15

### Status
Accepted

### Context
Carrier connection drops can truncate TCP sockets early, and large files can exhaust device storage mid-download.

### Options Considered
- Assume connection stream always completes full chunk length
- Verify `bytesRead == expectedChunkBytes` and re-queue truncated chunks, plus pre-flight `StatFs` check

### Decision
Implement strict byte-count verification per chunk and query `StatFs` before file pre-allocation.

### Reasoning
Prevents silent data corruption from carrier TCP resets and avoids abrupt OS disk exhaustion crashes.

---

## ADR-007: Foreground WakeLock & Exponential Backoff Auto-Recovery

### Date
2026-10-15

### Status
Accepted

### Context
Android 14/15 Doze mode and mobile network blackspots (e.g., elevators, tunnels) can sleep the CPU or drop both connections momentarily.

### Options Considered
- Immediate fail-fast abort on network drop
- `PARTIAL_WAKE_LOCK` held during download with exponential backoff auto-recovery

### Decision
Hold a partial wake lock while downloads are active and retry with exponential backoff (`delay(min(backoff * 2, 30_000L))`) when all networks are temporarily disconnected.

### Reasoning
Keeps transfers active across screen lock and seamlessly resumes when connectivity returns without corrupting files.

---

## ADR-008: Official Project Branding and Repository Naming (`dualboost-downloader`)

### Date
2026-10-15

### Status
Accepted

### Context
The project was originally initialized in a generic directory path `A:\Development\New folder` with default placeholder settings.

### Options Considered
- Retain existing `New folder` directory and generic project title
- Standardize branding as "DualBoost Downloader" with kebab-case directory `dualboost-downloader` and Gradle root project name `dualboost-downloader`

### Decision
Officially brand the project as **DualBoost Downloader**, configure `rootProject.name = "dualboost-downloader"` in `settings.gradle.kts`, update all internal markdown documentation links, and rename the root directory to `A:\Development\dualboost-downloader`.

### Reasoning
Provides professional naming clarity across CLI tooling, Gradle build artifacts, IDEs, and version control repositories while eliminating space-in-path issues.

### Consequences
Working directory is shifted from `New folder` to `dualboost-downloader`. All documentation links and CLI scripts now reference the new path.

---

## ADR-009: Application-Scoped State Architecture with MainViewModel & DownloadRepository

### Date
2026-10-16

### Status
Accepted

### Context
Android 14/15/17 aggressively kills or idles background services when the user navigates away or when services lack active foreground status. The previous UI pattern relied on direct local service binding (`ServiceConnection`), causing `service` references in `MainActivity` to become `null` upon service idle disconnects, disabling action buttons and freezing UI reactivity.

### Options Considered
- Ephemeral `ServiceConnection` with reconnection retry loops.
- Application-scoped singleton repository (`DownloadRepository`) exposed to Compose UI via `MainViewModel` and observed by `DownloadForegroundService`.

### Decision
Implement `DownloadRepository` managed by `DualBoostApp` application singleton. UI binds directly to `MainViewModel`, while `DownloadForegroundService` acts strictly as the Android lifecycle tether holding `FOREGROUND_SERVICE_TYPE_DATA_SYNC` notifications and `PARTIAL_WAKE_LOCK`.

### Reasoning
Decouples UI state reactivity completely from Android service lifecycle quirks. Ensures buttons remain responsive, downloads survive activity teardown, and notification throttlers prevent system message shedding.

---

## ADR-010: Dynamic HTTP Header Injection and Range-Verified Benchmark Presets

### Date
2026-10-16

### Status
Accepted

### Context
Major CDN endpoints (e.g. Cloudflare speedtest) deploy anti-bot protections that immediately reject requests lacking browser headers with `HTTP 403 Forbidden`. Furthermore, dynamically streamed endpoints (like Cloudflare `__down`) do not support HTTP `Range: bytes` headers or `206 Partial Content`, forcing single-stream fallback rather than dual-socket aggregation.

### Options Considered
- Rely on raw user URLs and unauthenticated GET requests.
- Inject dynamic origin/referer headers based on target host and provide verified HTTP/2 Range benchmark test endpoints (OVH speedtest).

### Decision
1. In `DualDownloaderEngine.buildRequest()`, dynamically extract the target host and inject `Referer: https://<host>/`, `Origin: https://<host>`, and `Accept-Encoding: identity`.
2. Update preset quick-test chips to include verified range-supporting files (`https://proof.ovh.net/files/10Mb.dat`, `100Mb.dat`, `1Gb.dat`) with explicit visual indicators (`⚡`) and explanatory capability captions in the UI.

### Reasoning
Prevents 403 Forbidden errors across CDNs, guarantees true multi-channel aggregation across Wi-Fi and 5G during testing, and educates the user on server range capabilities.

---

## ADR-011: Active Call Tracking Cancellation, Interface Circuit-Breaker, and High-Reliability Edge Presets

### Date
2026-10-16

### Status
Accepted

### Context
1. User encountered "Pause / Cancel Download" button unresponsive because standard coroutine cancellation does not abort blocking OkHttp socket read loops (`InputStream.read()` / `Call.execute()`).
2. Downloads experienced 15-second timeouts (`proof.ovh.net`) due to Indian broadband ISP peering drops to European OVH servers.
3. When one interface suffered timeouts, workers starved or stalled instead of dynamically transferring workload to the healthy interface.
4. Material 3 `dynamicColor = true` washed out Electric Cyan (`#00E5FF`) and Neon Violet (`#B388FF`) branding with wallpaper palette extraction.

### Options Considered
- Rely solely on coroutine job cancellation and static retry counts.
- Maintain an active `ConcurrentHashMap` of in-flight `Call` references with double-checked registration, dynamic per-interface circuit breaker, and edge-cached CDN mirrors.

### Decision
1. **Thread-Safe Call Registration**: Implement `registerActiveCall(call: Call): Boolean` with `AtomicBoolean(isPaused)`. On `pauseDownload()`, invoke `call.cancel()` directly on all registered calls and clear active calls.
2. **Interface Circuit-Breaker**: Count consecutive chunk failures per interface (`wifiFailures`, `cellularFailures`). If an interface fails 2 consecutive chunks, back it off and allow the healthy interface to absorb all remaining chunks.
3. **High-Reliability Edge Presets**: Replace high-latency OVH mirrors with anycast edge CDN mirrors (Google Edge CDN 10 MB, Fastly Mumbai Edge 25 MB, Google Edge CDN 150 MB).
4. **Vibrant Cyber-Electric Theme**: Set `dynamicColor = false`, enforcing high-contrast `#00E5FF` (Wi-Fi), `#B388FF` (5G Cellular), `#FFFF5252` (Pause/Cancel), and `#0A0E17` OLED dark background.

### Reasoning
Ensures instant (<50ms) user cancellation, eliminates download stalling on erratic physical networks, and provides a polished, consistent visual identity.

---

## ADR-012: Material You Dynamic Theming with Dedicated Network Clarity Accents

### Date
2026-10-16

### Status
Accepted

### Context
User expressed preference for the earlier Pixel system integration where app surfaces, action buttons, cards, and backgrounds dynamically adapt to their Pixel wallpaper (Material You Monet extraction), while maintaining clear visual distinction between Wi-Fi and 5G Cellular streams.

### Options Considered
- Pure static dark theme (Cyber-Electric).
- Pure dynamic theme (everything extracts from wallpaper, reducing interface distinction).
- Material You dynamic theming (`dynamicColor = true`) for backgrounds, cards, buttons, and chips, coupled with dedicated functional accent indicators (`wifiColor`, `cellularColor`) for network clarity.

### Decision
1. Re-enable `dynamicColor = true` in `Theme.kt`, automatically switching between `dynamicDarkColorScheme` and `dynamicLightColorScheme` based on the user's Pixel dark theme setting.
2. Adopt native Material 3 Expressive components:
   - Elevated cards using `MaterialTheme.colorScheme.surface` and tonal elevation.
   - Primary filled `Button` using `MaterialTheme.colorScheme.primary` and `onPrimary` for "Boost Download".
   - Aggregation status pill using `MaterialTheme.colorScheme.secondaryContainer`.
   - Preset chips using `primaryContainer` (selected) and `surfaceVariant` (unselected).
   - "Pause / Cancel" action button using `MaterialTheme.colorScheme.error`.
3. Retain functional network clarity accents: Wi-Fi uses Electric Cyan (`#00E5FF` dark / `#00838F` light) and 5G Cellular uses Neon Violet (`#B388FF` dark / `#651FFF` light) strictly for status dots, icons, and speed meter bars.

### Reasoning
Provides first-class Google Pixel integration matching the user's personal wallpaper and device mode (Day/Night) while ensuring unmistakable physical network transparency.

---

## ADR-013: AnimatedContent ContentKey Flicker Prevention & Single-Stream Speed Metering

### Date
2026-10-16

### Status
Accepted

### Context
1. During active downloads, the progress card in `MainActivity.kt` flickered violently and intermittently vanished from the screen (verified via user screen recording).
2. Single-stream fallback downloads (e.g. servers not supporting HTTP Range like Cloudflare) emitted progress updates on every 64KB socket read without throttling, flooding Compose state and reporting a static `0.0 Mbps`.

### Options Considered
- Remove `AnimatedContent` entirely and use raw `if/else` rendering.
- Supply `contentKey = { it::class }` to `AnimatedContent` to isolate transitions to major lifecycle state changes, coupled with a 300ms throttled delta speed calculator for single-stream transfers.

### Decision
1. In `MainActivity.kt`, add `contentKey = { it::class }` to `AnimatedContent(targetState = downloadState)`. This ensures Compose transitions execute strictly when transitioning between distinct lifecycle states (`Idle`, `Probing`, `Downloading`, `Paused`, `Completed`, `Failed`), keeping opacity solid (1.0) and eliminating re-composition flicker during high-frequency byte updates.
2. In `DualDownloaderEngine.kt`, throttle `executeSingleStreamDownload` emissions to 300ms intervals (plus completion) and calculate real-time transfer speed (`((totalRead - lastBytes) * 8.0) / (deltaSec * 1_000_000.0)`).

### Reasoning
Prevents Compose animation restarts from starving the UI thread and alpha-fading the card during active downloads, while giving users accurate real-time Mbps telemetry even on non-chunked servers.

---

## ADR-014: Persistent Chunk Metadata (.dualboost_meta) & Resumable Downloads

### Date
2026-10-16

### Status
Accepted

### Context
1. When downloads were paused or interrupted by network drops, previously completed chunks were discarded upon restart, forcing full re-download of entire files and wasting carrier bandwidth.
2. In the `Paused` state, the UI rendered a generic flat card without progress information or a clear resume/discard action.

### Options Considered
- In-memory session tracking only (lost on process death or app exit).
- Persistent sidecar metadata file (`<outputFile>.dualboost_meta`) tracking completed chunk indices, surviving process kill and device reboots.

### Decision
1. **Sidecar Chunk Persistence**: Save completed chunk indices, target URL, and total length into `<outputFile>.dualboost_meta` atomically (using `.tmp` write and rename).
2. **Seamless Chunk Resumption**: On start/resume, read `.dualboost_meta`. If URL and length match, mark completed chunks as `COMPLETED`, calculate initial byte offset, and only enqueue pending chunks.
3. **Automatic Cleanup & Discard**: Delete `.dualboost_meta` upon 100% completion. Provide `discardDownload()` to delete partial files and metadata when user chooses to discard.
4. **Rich Paused UI State**:
   - `DownloadState.Paused` carries `fileName`, `filePath`, `totalBytes`, `downloadedBytes`, `progressPercentage`, and `url`.
   - Render ElevatedCard with preserved progress bar, exact byte counts, primary "Resume" button, and secondary "Discard" button.
   - Primary action button switches to "Resume Download" with Play icon when paused.

### Reasoning
Guarantees zero lost bandwidth across pauses, app kills, and cellular drops while providing a clean, explicit user control model.

---

## ADR-015: Storage Pre-Flight Verification, Server ETag Invalidation, Public MediaStore Export, and Startup Auto-Recovery

### Date
2026-10-16

### Status
Accepted

### Context
1. Large file downloads could exhaust available disk storage, causing abrupt unhandled `ENOSPC` crashes mid-transfer.
2. When resuming paused downloads, remote servers may have updated the file (e.g., nightly builds, updated installers). Without validating ETag or Last-Modified headers, resumed chunks could corrupt the assembled file.
3. Android 14/15/17 scoped storage restricts user access to app-private directories (`Android/data/...`), preventing downloaded files from appearing in Google Files or system media pickers.
4. Relaunching the app after closing or OS process termination started in an empty `Idle` state rather than detecting unfinished partial downloads.
5. The Completed Card only offered an "Open File" button without a direct "Share" action.

### Options Considered
- Rely on OS file write exceptions and manual user discovery.
- Implement production hardening: overflow-safe pre-flight disk storage checks, ETag/Last-Modified validation on resume, automatic export to public `MediaStore.Downloads`, startup metadata auto-recovery, and dual "Open File" + "Share" actions.

### Decision
1. **Overflow-Safe Pre-Flight Storage Check**: Implement `checkAvailableStorage(targetDir, requiredBytes, existingFile)` in `DualDownloaderEngine` with 20 MB safety buffer and 64-bit Long arithmetic overflow protection. Throw clear `IOException` with required vs. available megabytes.
2. **Server ETag & Last-Modified Invalidation**: Persist `etag` and `lastModified` in `.dualboost_meta`. On resume, compare probe headers: if changed, delete stale chunks and restart cleanly from byte 0.
3. **Automatic Public MediaStore Export**: Upon 100% completion, stream the assembled file to `MediaStore.Downloads` via `ContentResolver` (with `IS_PENDING = 1` -> `0`), preserving file visibility in Google Files, Chrome, and system file managers. Store public content URI in `DownloadState.Completed.contentUriString`.
4. **Startup Auto-Recovery**: Call `restoreIncompleteDownload()` during repository initialization. If an unfinished `.dualboost_meta` is detected, restore `DownloadState.Paused` and pre-populate the URL in `MainViewModel`.
5. **Interactive Completed Card**: Provide side-by-side "Open File" (`ACTION_VIEW`) and "Share" (`ACTION_SEND`) buttons on the Completed Card, passing content URI / FileProvider with `FLAG_GRANT_READ_URI_PERMISSION`.

### Reasoning
Elevates DualBoost Downloader from a proof-of-concept into a resilient, production-grade Android utility capable of handling real-world network anomalies, storage boundaries, and Android lifecycle restarts.

---

## ADR-016: Discard Download State Decoupling & Inactive Network Context-Aware UI

### Date
2026-10-16

### Status
Accepted

### Context
1. When discarding a paused or active download, `DownloadRepository.discardDownload()` dispatched `ACTION_CANCEL_DOWNLOAD` to `DownloadForegroundService`. The service command handler treated `ACTION_CANCEL_DOWNLOAD` identically to pause, invoking `downloaderEngine.pauseDownload()` which overwrote `DownloadState.Idle` with `DownloadState.Paused(0%)`. Furthermore, coroutine cancellation inside `startDownload` caught `CancellationException` and defaulted to `DownloadState.Paused(url)`. This caused the Discard button to reset partial downloads to 0% Paused instead of returning cleanly to `Idle` ("Ready to Download").
2. When Wi-Fi or 5G was disabled, network cards turned into sunken dark rectangles with faint borders, and the status banner only supported dual-active or single generic idle messages, leaving users without actionable guidance when only one or neither interface was active.

### Options Considered
- Continue using `ACTION_CANCEL_DOWNLOAD` with boolean flags.
- Create a dedicated `ACTION_DISCARD_DOWNLOAD` service action, introduce an atomic `isDiscarding` flag in `DualDownloaderEngine`, update repository and UI discard actions, style inactive cards with crisp borders and dynamic `WifiOff` / `SignalCellularOff` icons, and expand the status banner to 4 distinct context-aware states.

### Decision
1. **Dedicated Service Action (`ACTION_DISCARD_DOWNLOAD`)**:
   - `DownloadForegroundService` handles `ACTION_DISCARD_DOWNLOAD` independently, stopping the foreground service and removing notifications immediately without calling `pauseDownload()`.
   - Guarded `ACTION_CANCEL_DOWNLOAD` so that it never calls `pauseDownload()` if the engine is already in `Idle` or `Paused`.
2. **Atomic `isDiscarding` State Protection**:
   - Added `val isDiscarding = AtomicBoolean(false)` in `DualDownloaderEngine`.
   - In `discardDownload()`, `isDiscarding.set(true)` before job cancellation.
   - When workers catch `CancellationException`, if `isDiscarding.get()` is true, they safely emit `DownloadState.Idle` instead of resurrecting `DownloadState.Paused`.
   - Existing partial `.part` files and `.dualboost_meta` sidecars are deleted, resetting disk and memory state.
3. **URL Input Retention**:
   - In `MainViewModel`, `discardDownload()` resets state to `Idle` while preserving `urlInput`, allowing users to immediately restart or select a new preset without re-entering the URL.
4. **Inactive Network Cards & 4-State Context-Aware Banner**:
   - Inactive network cards use `surfaceContainer` fill, 1dp `outlineVariant.copy(alpha = 0.5f)` border, amber/grey indicator dots, and dynamic `WifiOff` / `SignalCellularOff` icons with 0.5f opacity.
   - Status banner implements 4 contextual states:
     - Dual Active: `primaryContainer` pill ("Dual-Channel Active: Aggregating Wi-Fi + 5G").
     - Both Inactive: `errorContainer` warning banner ("No Network Available: Reconnect Wi-Fi or Cellular to download.").
     - Wi-Fi Only: `surfaceContainerHigh` tip ("Using Wi-Fi only: Enable 'Mobile data always active' in Developer Options to aggregate 5G.").
     - 5G Only: `surfaceContainerHigh` info ("Using 5G Cellular only: Connect Wi-Fi to activate DualBoost aggregation.").

### Reasoning
Prevents race conditions between coroutine cancellation, foreground service lifecycles, and engine state emissions, ensuring discard cleanly returns to Idle while providing crystal-clear network diagnostics across all connection permutations.

---

## ADR-017: Ephemeral 3-Phase Speed Benchmark & Google Material 3 App Icon Redesign

### Date
2026-10-16

### Status
Accepted

### Context
1. Users need direct, demonstrable proof of the aggregate bandwidth advantages provided by DualBoost prior to downloading large files.
2. Speed tests must not exhaust local device storage or leave behind multi-megabyte artifact files.
3. The previous launcher icon utilized a dark slate linear gradient with glowing center effects, clashing with the Google Pixel home screen aesthetic where first-party apps (Google Files, Chrome, Photos, Drive) utilize clean white adaptive icon backgrounds and minimalist Material geometric curves.

### Options Considered
- Integrate third-party Ookla or Speedtest SDKs (rejected: heavy dependencies, ads, tracking).
- Implement an in-engine ephemeral 3-phase speed benchmark: sequentially testing Wi-Fi alone (3s), 5G Cellular alone (3s), and DualBoost combined (4s) using high-speed CDN chunk streams discarded directly in memory (0 bytes stored).
- Redesign the app icon to match Google's Material 3 adaptive icon specifications: solid `#FFFFFF` background, geometric converging streams (Cyan/Azure & Violet/Purple) into a downward boost arrow with lightning core, and high-contrast monochrome silhouette for Monet themed icons.

### Decision
1. **SpeedTestEngine & Data Models**:
   - Implemented `SpeedTestEngine` in `com.dualboost.downloader.engine`.
   - Utilizes `NetworkProvider` for interface isolation (`wifiClient`, `cellularClient`).
   - Streams incoming bytes into an in-memory discard buffer (`ByteArray(32768)`), completely bypassing disk storage (**0 bytes stored on disk**).
   - Sequential 3-phase execution: Phase 1 (Wi-Fi 3s), Phase 2 (5G Cellular 3s), Phase 3 (DualBoost 4s).
   - Computes real-time and final Mbps, along with dynamic boost multiplier gain:
     $$\text{Boost \%} = \frac{S_{\text{dual}} - \max(S_{\text{wifi}}, S_{\text{cell}})}{\max(S_{\text{wifi}}, S_{\text{cell}})} \times 100\%$$
   - Emits `SpeedTestState` (`Idle`, `Running`, `Completed`, `Error`) with full cancellation safety via `stopSpeedTest()`.
2. **Interactive UI (`SpeedTestBottomSheet`) & Dashboard Quick Card**:
   - Created `SpeedTestBottomSheet` using Material 3 `ModalBottomSheet`.
   - TopAppBar action button and dedicated dashboard card ("Speed Benchmark: Test combined Wi-Fi + 5G speed").
   - Live real-time speedometer readout, progress indicator, and comparative results card displaying individual Wi-Fi, 5G, and aggregate DualBoost speeds.
3. **Google-Style Material 3 App Icon**:
   - `ic_launcher_background.xml`: Clean white (`#FFFFFF`) adaptive background.
   - `ic_launcher_foreground.xml`: Bold, geometric Material 3 vector curves with Google-inspired color harmony (Electric Cyan `#00B0FF` / Azure `#0288D1` left stream, Neon Violet `#B388FF` / Purple `#651FFF` right stream) converging into a central downward arrow with white lightning core, sized within the 72dp safe zone.
   - `ic_launcher_monochrome.xml`: Crisp single-color silhouette for Google Pixel dynamic themed icon theming.

### Reasoning
Empowers users to quantitatively benchmark their network aggregation capability with zero storage footprint, while seamlessly harmonizing the launcher icon with Google Pixel's first-party design system.

---

## ADR-018: Standardized Megabytes-per-Second (MB/s) Telemetry and Single-Stream Fallback Amber Alert

### Date
2026-10-17

### Status
Accepted

### Context
1. In downloader applications (e.g., Chrome, Steam, Android DownloadManager), users calculate transfer times against file sizes measured in Megabytes (MB). Displaying speed in Megabits per second (`Mbps`) requires mental division by 8 and creates friction.
2. When downloading files from remote servers that do not support HTTP Range requests (`Accept-Ranges: bytes`), the engine automatically falls back to single-stream downloading. However, the UI previously showed `5G: 0.0 Mbps` without explicit explanation, leaving users confused about why dual aggregation was not active.

### Options Considered
- Keep `Mbps` everywhere to match ISP speed tests (rejected: counterintuitive for file progress).
- Switch to binary `MB/s` with adaptive `KB/s` granularity and explicit single-stream UI cues (accepted).

### Decision
1. **Binary MB/s Speed Calculation**:
   - Standardized calculations in `DualDownloaderEngine` and `SpeedTestEngine` to binary Megabytes per second using $1024 \times 1024$ bytes:
     $$\text{Speed (MB/s)} = \frac{\Delta \text{Bytes}}{\Delta \text{Seconds} \times 1,048,576.0}$$
   - Perfectly aligns speed with file sizes formatted with `formatBytes()`.
2. **Adaptive Formatting (`formatSpeed` and `formatSpeedComponents`)**:
   - Speeds $\ge 1.0\text{ MB/s}$: `%.1f MB/s` (e.g., `12.4 MB/s`).
   - Speeds $< 1.0\text{ MB/s}$ and $> 0$: `%.0f KB/s` (e.g., `512 KB/s`).
   - Speeds $\le 0$: `0.0 MB/s`.
   - Propagated to active download card, notification shade, and speed benchmark bottom sheet.
3. **Single-Stream Informative Alert Banner & Dimming**:
   - When `!state.supportsRanges`, render an amber container banner in `DownloadState.Downloading`: *"Single-Stream Mode: Server does not support dual-channel splitting • Running on Wi-Fi to preserve mobile data"*.
   - Adapt hero title from "Aggregated Bandwidth" to "Single-Channel Bandwidth".
   - Dim the inactive interface in `SpeedDetailRow` using `alpha(0.45f)` and append `"(Inactive in Single-Stream)"`.
4. **Smart Interface Attribution**:
   - Single-stream downloads dynamically check `networkManager.isWifiAvailable.value`: routes to Wi-Fi by default to save cellular data, or seamlessly uses Cellular if Wi-Fi is offline.

### Consequences
Eliminates mental math friction for users calculating download times, provides complete transparency on non-range servers, and guarantees zero data waste on unboosted links.

---

## ADR-019: Desktop Browser Header Spoofing & Adaptive Two-Stage Range Probe

### Date
2026-10-17

### Status
Accepted

### Context
Certain file hosting providers, CDNs, and media servers inspect incoming HTTP client headers (`User-Agent`, Sec-CH-UA hints). When encountering mobile Android clients, they frequently downgrade connections to single-stream downloads (returning `HTTP 200 OK` ignoring byte ranges or omitting `Accept-Ranges: bytes`), while serving full `HTTP 206 Partial Content` segmented byte streams to modern desktop web browsers. Without header adaptation, DualBoost Downloader would needlessly fall back to single-stream mode on such servers.

### Options Considered
- Global Desktop Spoofing on all requests (rejected: might degrade servers with mobile-optimized CDNs).
- User toggle for Desktop Mode in settings (rejected: introduces unnecessary manual friction).
- Smart Adaptive Two-Stage Probing with resume persistence (accepted).

### Decision
1. **Adaptive Two-Stage Range Probe Protocol**:
   - **Stage 1 (Standard Mobile Probe)**: Probes with native Android 15 Pixel 9 headers. If the server unlocks HTTP 206 or declares `Accept-Ranges: bytes`, dual-channel segmented downloading starts immediately (`isDesktopSpoofed = false`).
   - **Stage 2 (Adaptive Desktop Retrying)**: If Stage 1 fails to yield byte ranges or returns HTTP 200, the engine immediately initiates a Stage 2 probe with Windows 11 Chrome 131 Desktop headers and Client Hints:
     - `User-Agent`: `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36`
     - `Sec-CH-UA`: `"Google Chrome";v="131", "Chromium";v="131", "Not_A Brand";v="24"`
     - `Sec-CH-UA-Mobile`: `?0`
     - `Sec-CH-UA-Platform`: `"Windows"`
     - `Sec-CH-UA-Platform-Version`: `"15.0.0"`
     - `Sec-Fetch-Dest`: `empty`, `Sec-Fetch-Mode`: `cors`, `Sec-Fetch-Site`: `same-origin`
   - If Stage 2 unlocks HTTP 206 byte ranges, the engine marks `supportsRanges = true`, sets `isDesktopSpoofed = true`, and executes all chunk workers using the desktop identity.
   - If both Stage 1 and Stage 2 fail, the engine gracefully falls back to single-stream mode with the informative amber banner.
2. **Sidecar State Persistence (`.dualboost_meta`)**:
   - The `isDesktopSpoofed` boolean flag is serialized to the `.dualboost_meta` sidecar file upon every chunk completion.
   - Resuming paused or interrupted downloads retains the spoofed state, immediately bypassing the mobile probe on resumption.
3. **Speed Test Unthrottling (`SpeedTestEngine`)**:
   - Applied desktop browser headers across all speed test benchmark requests to ensure CDNs provide full, unthrottled bandwidth evaluation.
4. **Subtle UI Presentation**:
   - In `DownloadState.Downloading`, when `isDesktopSpoofed == true`, render a subtle `⚡ Desktop Mode Active` badge tinted with `primaryContainer` and rounded corners right next to the file details.
   - In `DownloadState.Paused`, also render a compact `⚡ Desktop Mode` tag.

### Consequences
Significantly expands the catalog of servers and CDNs supporting concurrent Wi-Fi + 5G bandwidth aggregation with zero user effort, maintaining complete fallback safety and resume integrity.






