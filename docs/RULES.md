# Project Rules

## Adversarial & Test-First Engineering Workflow (Mandatory Standard)
For all new features, bug fixes, and architectural modifications, strictly follow this 3-step battle-testing approach:
1. **Step 1: Edge-Case Checklist & Tests First (TDD)**:
   - Before writing or modifying code, map out all potential edge cases and failure modes (e.g., zero-byte files, server timeouts, malformed headers, socket drops, out-of-disk space, rapid cancellation).
   - Write or update unit/integration tests covering these boundaries before finalizing implementation.
2. **Step 2: Adversarial Self-Audit ("Break Your Own Code" / Pen-Tester Mode)**:
   - Once a feature or fix is implemented, never accept the initial "happy path".
   - Switch into adversarial mode: critically inspect the code as a hostile attacker/QA pen-tester trying to break the app, find logic flaws, race conditions, memory leaks, or unhandled exceptions.
   - Enumerate all identified vulnerabilities and patch them methodically.
3. **Step 3: Concurrency, Chaos & Load Simulation**:
   - Explicitly simulate multi-threaded stress and real-world chaos (e.g., simultaneous worker writes, rapid button taps like Pause/Resume/Cancel, radio handovers between Wi-Fi and 5G, and background service interruptions).
   - Ensure thread safety with atomic primitives or Mutex locking to prevent race conditions.
4. **Verification**:
   - Run the complete unit test suite (`.\gradlew.bat test`), compile the hardened release APK (`.\gradlew.bat assembleRelease`), and ensure 100% verification before concluding.

## General Rules
- Always build, verify, and install the Production / Release build (`.\gradlew.bat assembleRelease` producing `app-release.apk`) by permanent user rule.
- Never add dependencies or files that require the Android Studio GUI to function.
- Keep the project 100% buildable and deployable via CLI and ADB.

## Code Organization
- All source files belong under `app/src/main/java/com/dualboost/downloader/`.
- Maintain strict package modularity:
  - `data/` for models, enums, and state representations.
  - `network/` exclusively for network connectivity, callbacks, and socket factories.
  - `engine/` for download scheduling, chunk calculation, and file I/O.
  - `service/` for Android services and notification management.
  - `ui/` for Jetpack Compose screens, components, and themes.

## Naming Conventions
- Kotlin files and classes: PascalCase (e.g., `DualDownloaderEngine.kt`).
- Functions and properties: camelCase (e.g., `wifiDownloadedBytes`).
- Constants: UPPER_SNAKE_CASE (e.g., `CHUNK_SIZE`, `NOTIFICATION_ID`).
- StateFlow variables: Private mutable backing flow prefixed with underscore (`_downloadState`), public immutable flow without (`downloadState`).

## Type Safety
- Never use untyped `Any` or raw JSON maps for internal state.
- Use explicit sealed classes (`DownloadState`) and enums (`ChunkStatus`, `NetworkInterfaceType`).
- Explicitly annotate Long for byte counts and offsets to avoid 32-bit integer overflow on files >2 GB.

## Error Handling
- Never swallow network exceptions silently; log via `Log.w(TAG, ...)` and handle state transitions.
- If a chunk fails over Cellular, catch the exception, set `chunk.status = ChunkStatus.PENDING`, and allow Wi-Fi or another worker to re-attempt the chunk.
- Server probe failures must transition `DownloadState` to `DownloadState.Failed` with human-readable error reasons.

## API Rules
- Use RFC 7233 range syntax: `Range: bytes=start-end`.
- Always verify HTTP status code 206 for partial content; status code 200 on any chunk where `startByte > 0` must be immediately rejected with an exception and never written to disk.

## Database Rules
- Keep download chunks and state in-memory during transfer. Do not introduce an SQLite/Room layer until scheduled in future iterations.

## Architecture & Concurrency Rules
- **Zero UI Network Calls (`ADR-032`)**: Compose UI composable functions must NEVER execute direct network operations or instantiate `OkHttpClient` directly. All network health checks, probes, and transfers must route through ViewModel, Repository, and Engine on `Dispatchers.IO`.
- **Single Dispatch Boundary (`ADR-032`)**: Production runtime initiates downloads exclusively through `ContextCompat.startForegroundService()`. `DownloadForegroundService` is the single authoritative dispatcher triggering the engine.
- **Two-Stage Atomic Persistence (`ADR-032`)**: All disk metadata and ledger persistence must implement two-stage atomic fallbacks: if `renameTo()` fails, delete destination and retry rename; if still failing, use `copyTo(overwrite = true)` with temp file deletion and warning logging.
- **Concurrent Memory Visibility (`ADR-032`)**: Any state or counter mutated across worker coroutines and read by telemetry monitors or UI StateFlows must use atomic primitives (`AtomicLong`, `AtomicInteger`, `AtomicBoolean`) or `@Volatile` annotations.

## Security Rules
- Do not log sensitive user URLs, authorization tokens, or carrier IMSI/IMEI identifiers; sanitize exception strings with `sanitizeLogMessage()` before logging.
- Strip `Log.v`, `Log.d`, and `Log.i` calls in release builds via ProGuard/R8 `-assumenosideeffects`.
- Always store authentication keys, secrets, and credentials in Android Keystore-backed `EncryptedSharedPreferences`.
- Never commit keystores, signing keys, or `keystore.properties` to version control.
- Never sign release APKs with the Android debug keystore. Release signing must load from environment variables, `keystore.properties`, or auto-generate a dedicated `release-local.jks`.
- Enforce TLS 1.2+ (`ConnectionSpec.MODERN_TLS`) and domain-specific certificate pinning for sensitive edge services.
- Explicitly declare backup exclusion rules in `data_extraction_rules.xml` and `backup_rules.xml`.
- Always require runtime `POST_NOTIFICATIONS` permission before posting notifications on Android 13+, and enforce `NotificationCompat.VISIBILITY_PRIVATE` for lock-screen confidentiality.
- All file deletion operations (`discardDownload`, `deletePhysicalFile`) MUST strictly validate canonical directory containment (`canonicalPath.startsWith(safeRoot.canonicalPath + File.separator)`) before invoking `delete()`.
- Chunk write loops MUST strictly cap read buffer sizes to `min(buffer.size, remaining)` to prevent clobbering neighboring chunks.
- Never mark downloads `Completed` without asserting `outputFile.length() == contentLength` (and `totalRead == totalLength` for single-stream transfers).
- Always verify physical file presence and byte size before allocating files or trusting sidecar chunk bitmaps on resume.

## Network & Service Lifecycle Rules
- Strict Physical Socket Isolation: Cellular workers must route exclusively through `cellularClient`; NEVER fall back to the default unbound client.
- **Network Request Permissions**: Android's `ConnectivityManager.requestNetwork()` strictly requires `android.permission.CHANGE_NETWORK_STATE` to hold dual network handles active. Never remove this permission from `AndroidManifest.xml`.
- **Roaming Guard Policy (`ADR-033`)**: Cellular requests must filter `NET_CAPABILITY_NOT_ROAMING`. When roaming is detected (`isRoamingActive == true`), cellular data routing must immediately be revoked (`getCellularClient() -> null`) to safeguard users against unexpected roaming charges.
- **VPN Tunnel Inviolability (`ADR-033`)**: When a VPN is active (`isVpnActive == true`), the app must never attempt raw physical socket binding (`Network.socketFactory`) as it causes kernel `EPERM` denials or leaks traffic outside the encrypted tunnel. All multi-stream workers must route through `baseOkHttpClient`.
- **OEM Battery Exemption Transparency (`ADR-033`)**: On aggressive OEM manufacturers (Samsung, Xiaomi, Huawei, Oppo, Vivo, OnePlus), background service survival must be monitored via `PowerExemptionChecker` and users offered direct one-tap battery exemption navigation.
- App-Wide Network Singleton Protection: `DownloadForegroundService.onDestroy()` must NEVER call `networkManager.stopListening()`. Network listener lifecycle belongs strictly to `DualBoostApp`.
- Always call `stopSelf()` immediately after calling `stopForeground(STOP_FOREGROUND_DETACH)` to prevent orphaned service processes.
- Implement Android 15 (API 35) `onTimeout(startId, fgsType)` to pause downloads and release wake locks before system termination.

## Dependency Rules
- All third-party libraries must be registered in `gradle/libs.versions.toml`.
- Do not import heavy frameworks (e.g., RxJava, Guava, Ktor-client) when OkHttp and Coroutines already provide the required capabilities.

## UI Rules
- Compose UI must remain declarative and reactive. No business logic or I/O in `@Composable` functions.
- Every interactive element must satisfy accessibility touch target dimensions ($\ge 48 \times 48\text{dp}$).

## State Management
- All state flow must originate from `DualDownloaderEngine.downloadState`.
- Avoid mutable state sharing across coroutines without Mutex or atomic primitives (`AtomicLong`).

## Testing Rules
- Unit tests must be placed under `app/src/test/java/`.
- Any modification to chunk offset arithmetic must be covered by unit tests verifying chunk boundary calculation.

## Git Rules
- Ignore build artifacts: `.gradle/`, `build/`, `app/build/`, `local.properties`.
- Keep commit messages concise and imperative: e.g., `Add dynamic chunk work queue self-balancing`.

## Performance Rules
- Never buffer entire file chunks in RAM; stream directly into `RandomAccessFile` using a 64 KB byte buffer (`65,536` bytes) for I/O coalescing.
- Limit concurrent network worker coroutines to at most 4 (2 Wi-Fi, 2 5G) under normal conditions; dynamically throttle cellular concurrency to 1 worker under `MODERATE` heat, and pause cellular transfers under `SEVERE` heat when Wi-Fi is available.
- Asymmetric Work-Stealing & Dynamic Batching: Fast interfaces exceeding 15 MB/s must batch contiguous chunks (8 MB or 16 MB requests) to reduce HTTP connection overhead. When the queue is empty, idle workers must speculatively hedge straggler chunks having > 512 KB unread.
- Cellular Radio Management: Maintain passive `registerNetworkCallback` while idle to prevent radio wake locks; acquire explicit `requestNetwork` keep-alive strictly during active transfers and release immediately upon pause/completion/cancellation.
- Limit concurrent network worker coroutines to at most 4 (2 Wi-Fi, 2 5G) to prevent socket thrashing.
- Throttle UI telemetry updates to at most once every 500ms.

## Documentation Maintenance Rules
- Never close a task or submit code changes without updating the relevant documents in `docs/`:
  - **Feature / Scope Changes**: Update `docs/PRD.md` and `docs/MEMORY.md`.
  - **Architecture / Networking / Storage**: Update `docs/ARCHITECTURE.md`, record an ADR in `docs/DECISIONS.md`, and update `docs/MEMORY.md`.
  - **UI / Styling / Design**: Update `docs/DESIGN.md` and `docs/MEMORY.md`.
  - **Engineering Conventions / Rules**: Update `docs/RULES.md` and `docs/MEMORY.md`.
  - **QA & Testing**: Update `docs/TESTING.md` and `docs/MEMORY.md`.
  - **Build / Tooling / Dependencies**: Update `AGENTS.md` and `docs/MEMORY.md`.
  - **All Sessions**: Always update `docs/MEMORY.md` ("Recently Completed", "Current Status", "Next Steps").

## Forbidden Practices
- NEVER use in-memory ByteArrays for multi-gigabyte files.
- NEVER perform network operations on `Dispatchers.Main`.
- NEVER bypass Android's `Network.socketFactory` when intending to route through a specific network interface.
- NEVER commit personal API keys, passwords, or local SDK directory configurations to git.
- NEVER deliver code changes without updating corresponding docs in `docs/`.
- NEVER leave behind temporary dumps, screen captures, or scratch files on either the host machine or connected device after testing and verification steps are concluded.

## Temporary File Hygiene & Clean-up Rules
- Always delete temporary test files, diagnostic screenshots (`/sdcard/screen_*.png`), and UI dumps (`window_dump.xml`) from both device `/sdcard/` and local scratch folders as soon as verification completes.
- Keep the git working tree clean; never commit or leave untracked scratch scripts in the root directory.

## Communication & Explanation Rules
- ALWAYS explain concepts, technical decisions, and answers in the simplest words possible.
- ALWAYS accompany explanations with intuitive real-world analogies and practical examples directly from the DualBoost Downloader app.
- ALWAYS proactively notify the user immediately whenever any external component (such as Cloudflare Worker scripts, cloud functions, or server deployment code) is modified. Explicitly clarify: (1) whether redeployment is optional or required right away, (2) what changed, and (3) provide simple copy-paste steps so the user doesn't have to guess.

## Material 3 Contrast & Color Token Pairing Rules
To prevent inverted contrast regressions (e.g. black-on-dark in light mode, white-on-white in dark mode, or washed-out dynamic colors):
- **Strict Token Pairing Requirement**: Every container token MUST strictly pair with its designated `on-` content token:
  - `primaryContainer` MUST ONLY host `onPrimaryContainer` content (NEVER `primary` or `onSurfaceVariant`).
  - `secondaryContainer` MUST ONLY host `onSecondaryContainer` content.
  - `tertiaryContainer` MUST ONLY host `onTertiaryContainer` content.
  - `errorContainer` MUST ONLY host `onErrorContainer` content (NEVER `error`).
  - `surface` / `surfaceContainer` MUST ONLY host `onSurface` or `onSurfaceVariant` content.
- **Universal Translucent Tint Pattern**: When designing colored accent badges or circular hero icons over surfaces, NEVER mix a container token with an accent tint. ALWAYS use `brandColor.copy(alpha = 0.14f)` for the background and `brandColor` for the foreground icon/text. This mathematically guarantees > 4.5:1 WCAG contrast across both Light and Dark themes.
- **Theme-Aware Color Literals**: NEVER use static hex color literals without branching on `isSystemInDarkTheme()`.



