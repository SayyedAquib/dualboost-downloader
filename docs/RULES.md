# Project Rules

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
- Always verify HTTP status code 206 for partial content; status code 200 indicates the server sent the full file instead of the requested chunk.

## Database Rules
- Keep download chunks and state in-memory during transfer. Do not introduce an SQLite/Room layer until scheduled in future iterations.

## Security Rules
- Do not log sensitive user URLs, authorization tokens, or carrier IMSI/IMEI identifiers.
- Always require runtime `POST_NOTIFICATIONS` permission before posting notifications on Android 13+.

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
- Never buffer entire file chunks in RAM; stream directly into `RandomAccessFile` using a 32 KB byte buffer.
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



