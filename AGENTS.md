# AGENTS.md

## Project Overview
DualBoost Downloader is an Android application for Google Pixel 9 (Android 14/15) that accelerates file downloads by concurrently aggregating bandwidth across Wi-Fi and Cellular (5G/LTE). The project is engineered for CLI-first development using Gradle CLI and ADB without requiring Android Studio.

## Architecture Overview
- **UI Layer**: Jetpack Compose single-activity architecture (`MainActivity.kt`, `Theme.kt`).
- **Service Layer**: Foreground Service (`DownloadForegroundService.kt`) with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` keeping downloads active across screen locks.
- **Engine Layer**: `DualDownloaderEngine.kt` coordinating dynamic byte-range work queues and writing to disk via `RandomAccessFile`.
- **Networking Layer**: `DualNetworkManager.kt` requesting independent `Network` handles from `ConnectivityManager` and binding sockets to physical interfaces via `Network.socketFactory`.

## Repository Structure
```
├── build.gradle.kts           # Root Gradle configuration
├── settings.gradle.kts        # Project module setup
├── gradle.properties         # JVM and AndroidX flags
├── local.properties          # SDK path configuration (sdk.dir)
├── gradlew, gradlew.bat       # Gradle wrapper executables
├── gradle/
│   └── libs.versions.toml    # Dependency version catalog
├── docs/
│   ├── PRD.md                # Product Requirements Document
│   ├── ARCHITECTURE.md       # System and networking architecture
│   ├── DESIGN.md             # Design system specifications
│   ├── RULES.md              # Project development rules
│   ├── DECISIONS.md          # Architecture decision record (ADR)
│   ├── TESTING.md            # QA and testing strategy
│   └── MEMORY.md             # Working session memory and status
└── app/
    ├── build.gradle.kts      # Application build configuration
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/dualboost/downloader/
            ├── data/         # Models (DownloadChunk, DownloadState)
            ├── network/      # DualNetworkManager (socket binding)
            ├── engine/       # DualDownloaderEngine (chunking & queue)
            ├── service/      # Foreground service & notifications
            └── ui/           # MainActivity and Compose components
```

## Development Commands
- Check connected devices: `adb devices`
- View device logcat (filtered): `adb logcat -s DualNetworkManager DualDownloaderEngine AndroidRuntime`
- Clear logcat: `adb logcat -c`
- Grant notification permission: `adb shell pm grant com.dualboost.downloader android.permission.POST_NOTIFICATIONS`

## Build Commands
- Clean project: `.\gradlew.bat clean`
- Assemble production release APK: `.\gradlew.bat assembleRelease`
- Assemble and install directly onto device: `.\gradlew.bat installRelease`
- Alternative manual ADB install: `adb install -r app/build/outputs/apk/release/app-release.apk`
- Launch app via ADB: `adb shell am start -n com.dualboost.downloader/.ui.MainActivity`

## Testing Commands
- Run automated end-to-end suite (Unit + Connected UI + ADB smoke tests): `powershell -ExecutionPolicy Bypass -File .\test-automated.ps1`
- Run unit & integration tests: `.\gradlew.bat test`
- Run Jetpack Compose UI tests on device: `.\gradlew.bat connectedAndroidTest`
- Run lint verification: `.\gradlew.bat lint`

## Code Conventions
- Language: 100% Kotlin 2.0 with Kotlin Coroutines and Flows.
- Asynchronous patterns: Use Kotlin `StateFlow` and `SharedFlow` for reactive state. Avoid raw threads or RxJava.
- Immutability: Use immutable data classes for state representations (`DownloadState`, `DownloadChunk`).
- Scopes: Confine background jobs to `SupervisorJob() + Dispatchers.IO`.

## Architecture Conventions
- Zero UI Network Calls: The Compose UI must never execute network calls directly; all operations dispatch to `DownloadForegroundService` and `DualDownloaderEngine`.
- Physical Socket Isolation: Never reuse a generic global OkHttpClient for interface-specific transfers. All Wi-Fi transfers must route through `wifiClient` (bound to Wi-Fi `socketFactory`), and all cellular transfers through `cellularClient`.

## Database Conventions
- No persistent SQL database in the MVP. Session and chunk states are held in memory; download files are written directly to disk via `RandomAccessFile`.

## API Conventions
- Standard HTTP 1.1 / HTTP/2 range requests (`Range: bytes=X-Y`).
- Probe protocol: Use HTTP `HEAD` or single-byte `Range: bytes=0-0` to inspect `Content-Length` and `Accept-Ranges: bytes`.

## Security Requirements
- Respect user cellular limits and clear sensitive download URLs from logs.
- Never hardcode sensitive carrier or device identifiers.
- Target SDK 35 with explicit runtime permissions (`POST_NOTIFICATIONS`).

## Dependency Guidelines
- All dependencies must be declared in `gradle/libs.versions.toml`.
- Do not introduce heavy third-party libraries (e.g., RxJava, Guava, or external VPN SDKs). Stick to Jetpack libraries and `okhttp3`.

## Testing Requirements
- Code changes touching the chunking queue or network manager must pass compilation and unit tests before committing.
- Live device testing must verify graceful fallback when Wi-Fi or Cellular is toggled off in Android Quick Settings.

## Documentation Maintenance Protocol
**MANDATORY**: Before marking any task complete, the AI coding agent MUST update the corresponding documentation files according to this trigger matrix:

| Change Category | Trigger Criteria | Required Document Updates |
| :--- | :--- | :--- |
| **Product & Scope** | Adding, modifying, or removing user-facing features or acceptance criteria | Update [`docs/PRD.md`](file:///A:/Development/dualboost-downloader/docs/PRD.md) + [`docs/MEMORY.md`](file:///A:/Development/dualboost-downloader/docs/MEMORY.md) |
| **Architecture & Core** | Modifying networking layers, chunk queue, socket binding, or storage engines | Update [`docs/ARCHITECTURE.md`](file:///A:/Development/dualboost-downloader/docs/ARCHITECTURE.md) + Add ADR in [`docs/DECISIONS.md`](file:///A:/Development/dualboost-downloader/docs/DECISIONS.md) + [`docs/MEMORY.md`](file:///A:/Development/dualboost-downloader/docs/MEMORY.md) |
| **UI & Styling** | Changing Jetpack Compose layouts, themes, colors, typography, or interaction states | Update [`docs/DESIGN.md`](file:///A:/Development/dualboost-downloader/docs/DESIGN.md) + [`docs/MEMORY.md`](file:///A:/Development/dualboost-downloader/docs/MEMORY.md) |
| **Engineering Rules** | Altering conventions, thread scopes, error handling, or forbidden practices | Update [`docs/RULES.md`](file:///A:/Development/dualboost-downloader/docs/RULES.md) + [`docs/MEMORY.md`](file:///A:/Development/dualboost-downloader/docs/MEMORY.md) |
| **Testing & Verification** | Adding unit tests, changing QA commands, or updating coverage criteria | Update [`docs/TESTING.md`](file:///A:/Development/dualboost-downloader/docs/TESTING.md) + [`docs/MEMORY.md`](file:///A:/Development/dualboost-downloader/docs/MEMORY.md) |
| **Tooling & CLI** | Modifying dependencies (`libs.versions.toml`), Gradle tasks, or ADB workflows | Update [`AGENTS.md`](file:///A:/Development/dualboost-downloader/AGENTS.md) + [`docs/MEMORY.md`](file:///A:/Development/dualboost-downloader/docs/MEMORY.md) |
| **Any Task / Session** | Concluding any working session or delivering a code change | Update [`docs/MEMORY.md`](file:///A:/Development/dualboost-downloader/docs/MEMORY.md) ("Recently Completed", "Current Status", "Next Steps") |

## Verification Checklist
1. Run `.\gradlew.bat assembleRelease` (must exit with code 0).
2. Install APK with `adb install -r app/build/outputs/apk/release/app-release.apk`.
3. Launch app with `adb shell am start -n com.dualboost.downloader/.ui.MainActivity`.
4. Inspect logs via `adb logcat -d -s DualNetworkManager DualDownloaderEngine`.
5. **Verify Documentation**: Update all relevant documents according to the Documentation Maintenance Protocol before concluding.

## Forbidden Changes
- Do NOT introduce Android Studio IDE-specific files (`.idea/`, `.iml`) into version control.
- Do NOT downgrade `targetSdk` below 34 or remove `FOREGROUND_SERVICE_DATA_SYNC`.
- Do NOT bypass `RandomAccessFile` direct writes in favor of in-memory chunk buffering for large files.
- Do NOT use Android root-only commands (`su`, raw `iptables`).

## Additional Documentation
- Functional Specifications: See [docs/PRD.md](file:///A:/Development/dualboost-downloader/docs/PRD.md)
- Architectural Deep-Dive: See [docs/ARCHITECTURE.md](file:///A:/Development/dualboost-downloader/docs/ARCHITECTURE.md)
- UI Guidelines: See [docs/DESIGN.md](file:///A:/Development/dualboost-downloader/docs/DESIGN.md)
- Engineering Rules: See [docs/RULES.md](file:///A:/Development/dualboost-downloader/docs/RULES.md)
- ADR Decision Log: See [docs/DECISIONS.md](file:///A:/Development/dualboost-downloader/docs/DECISIONS.md)
- Test Strategy: See [docs/TESTING.md](file:///A:/Development/dualboost-downloader/docs/TESTING.md)
- Active Memory: See [docs/MEMORY.md](file:///A:/Development/dualboost-downloader/docs/MEMORY.md)

