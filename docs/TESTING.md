# Testing Strategy

## Testing Philosophy
Focus on pragmatic verification of critical path operations: chunk calculation, state transitions, socket binding isolation, and file integrity on disk. We prioritize deterministic unit tests for mathematical and algorithmic operations, coupled with live device verification via ADB.

## Test Stack
- **Unit Testing Framework**: JUnit 4 / JUnit 5 (Android standard)
- **Assertion Library**: Kotlin `test` assertions
- **Mocking**: MockWebServer (OkHttp) for simulating HTTP Range responses
- **Device Instrumentation**: Android Debug Bridge (`adb`) CLI and connected Android tests (`connectedAndroidTest`)

## Unit Testing
Unit tests reside in `app/src/test/java/com/dualboost/downloader/` (80 active tests across 12 test suites, 100% passing):

- **EngineComprehensiveTest** (13 tests):
  - `testExtractFileName_StandardExtension`: Verifies filename extraction across standard `.iso`, `.dat`, and `.tar.gz` extensions.
  - `testExtractFileName_WithQueryParams`: Ensures URL authentication and query parameters do not corrupt target file extension.
  - `testExtractFileName_CloudflareDownEndpoint`: Validates special handling for dynamically generated Cloudflare speedtest streams.
  - `testExtractFileName_NoExtensionAddsBin`: Tests fallback addition of `.bin` extension when URL path lacks a file extension.
  - `testExtractFileName_EmptyPathOrRoot`: Tests fallback generation when URL is an apex domain with no path.
  - `testExtractFileName_MalformedUrlFallback`: Tests graceful fallback without crashing when encountering malformed URIs.
  - `testExtractFileNameFromDisposition_Quoted`: Tests standard quoted RFC `Content-Disposition: attachment; filename="file.ext"`.
  - `testExtractFileNameFromDisposition_Unquoted`: Tests unquoted disposition headers.
  - `testExtractFileNameFromDisposition_CaseInsensitive`: Tests case-insensitive header parsing (`FILENAME=`, `filename=`).
  - `testExtractFileNameFromDisposition_MissingFilenameReturnsNull`: Ensures null returns when header lacks filename attribute.
  - `testFormatBytes_MegabytesAndGigabytes`: Tests human-readable byte formatting across MB and GB boundaries.
  - `testSingleStreamFallback_MetersSpeedAndPreservesDataIntegrity`: Exercises end-to-end single-stream fallback with real-time speed calculation, 300ms throttling, and bit-for-bit SHA-256 verification.
  - `testContentDispositionHeader_OverridesUrlFilename`: Confirms server-supplied `Content-Disposition` header supersedes URL path naming.

- **DownloadRepositoryTest** (5 tests):
  - `testRepository_InitialStateIsIdle`: Verifies initial state of repository starts in `DownloadState.Idle`.
  - `testRepository_StartDownload_TransitionsToProbing`: Verifies `startDownload()` delegates to engine and transitions lifecycle states.
  - `testRepository_PauseDownload_TransitionsToPaused`: Confirms `pauseDownload()` cancels active downloads and transitions state to `Paused`.
  - `testRepository_ExposesNetworkStateFlows`: Validates that `isWifiAvailable` and `isCellularAvailable` flows are exposed directly to consumers.
  - `testRepository_DiscardDownload_TransitionsToIdleAndCleansFiles`: Verifies `discardDownload()` stops foreground service without triggering pause, cancels engine with `isDiscarding`, cleans partial files and metadata, and transitions directly to `Idle`.

- **DualNetworkManagerTest** (5 tests):
  - `testNetworkProvider_BothDisconnectedInitially`: Validates default state when neither interface is active.
  - `testNetworkProvider_WifiOnlyMode`: Tests socket client availability and state flags in Wi-Fi-only mode.
  - `testNetworkProvider_CellularOnlyMode`: Tests socket client availability and state flags in Cellular-only mode.
  - `testNetworkProvider_DualChannelAggregatedMode`: Tests concurrent dual-socket mode when both interfaces are connected.
  - `testNetworkProvider_DynamicDisconnectionMidStream`: Tests dynamic interface detachment when Wi-Fi drops mid-session.

- **ServiceLifecycleTest** (3 tests):
  - `testServiceConstants_CorrectAndConsistent`: Verifies actions, channel ID (`dualboost_download_channel`), notification ID (`1001`), and extra keys.
  - `testServiceActionNaming_FollowsReverseDomainConvention`: Ensures action naming adheres to Android package reverse-domain guidelines.
  - `testThrottledNotificationLogic_LimitsToOncePerSecond`: Verifies that notification updates are capped to 1 per second while terminal states (Complete/Fail) dispatch immediately.

- **UIFlowAndViewModelComprehensiveTest** (6 tests):
  - `testAllPresetDefinitions_HaveValidAttributesAndProtocols`: Verifies all 4 presets have valid HTTPS URLs, non-blank labels, and capability descriptions.
  - `testSequentialPresetSwitching_MaintainsCorrectViewModelState`: Verifies sequential chip clicks correctly update URL and active preset selection.
  - `testCustomUrlTyping_DeselectsPresetAutomatically`: Ensures typing custom URLs clears preset chip highlights.
  - `testClearUrl_ResetsBothUrlAndPreset`: Verifies the clear button resets both input text and preset state.
  - `testStartButtonStateMatrix_Exhaustive`: Tests the complete state machine matrix for button enablement across all 7 lifecycle states.
  - `testDualColorProgressSplit_FormulaPrecision`: Validates dual-channel progress split calculations across asymmetric loads and edge cases.

- **MainViewModelTest** (11 tests):
  - Validates presets, chip clicks, URL updates, start button states, dual-color ratio math, null repository tolerance, URL preservation across `discardDownload()`, safe speed test state delegation, and Turbine live stream assertions (`testTurbine_UrlInputStream_EmitsAccurateSequence`, `testTurbine_SelectedPresetStream_EmitsAccurateSequence`, `testTurbine_SpeedTestStateStream_RemainsIdleWithoutRepository`).

- **EngineEdgeCasesTest** (9 tests):
  - Tests request header injection, HTTP 403, 404, 500 handling, User-Agent propagation, single-stream fallback, pause cancellation, instantaneous socket abort, and interface circuit-breaker recovery.

- **ChunkCalculationTest** (4 tests):
  - Tests contiguous chunk partitioning, boundary alignment, exact multiples, and single-byte edge cases.

- **MockWebServerDownloadTest** (2 tests):
  - Validates multi-chunk dual-socket aggregation with SHA-256 validation and single-stream fallback integrity.

- **ResumableDownloadTest** (5 tests):
  - `testSaveAndReadMetadata_RoundTrip`: Validates writing completed chunk indices to `.dualboost_meta` and reading them back accurately.
  - `testResumeSkipsAlreadyCompletedChunks`: Ensures resuming skips previously downloaded chunks and only requests remaining missing chunks.
  - `testCorruptedMetadata_FallsBackToCleanRestart`: Failsafe testing confirming that corrupted or partial metadata cleanly resets without crashes.
  - `testDiscardDownload_DeletesPartialFileAndMetadata`: Confirms `discardDownload()` purges partial `.part` files and `.dualboost_meta` sidecars.
  - `testMetadataDeletedUponSuccessfulCompletion`: Validates automatic removal of `.dualboost_meta` upon 100% completion.

- **ProductionHardeningResilienceTest** (9 tests):
  - `testStoragePreCheck_SucceedsWhenUsableSpaceIsSufficient`: Verifies that disk storage checks pass safely under normal volume conditions.
  - `testStoragePreCheck_FailsWhenStorageExceeded`: Verifies overflow-guarded rejection when file size exceeds available disk storage.
  - `testStoragePreCheck_SubtractsExistingPartialFileSize`: Verifies that partial files on disk reduce the net required free space.
  - `testMetaInfo_WithETagAndLastModified_RoundTrip`: Validates writing and reading ETag and Last-Modified headers into sidecar metadata.
  - `testRestoreIncompleteDownload_RestoresPausedStateOnStartup`: Validates auto-restoration of paused download state and URL upon cold launch.
  - `testRestoreIncompleteDownload_WhenNoMetaFilesExist_ReturnsFalse`: Ensures clean Idle state when no pending downloads exist.
  - `testMimeTypeResolution_HandlesCommonFileExtensions`: Validates accurate MIME type resolution across binary, compressed, and raw formats.
  - `testDownloadStateCompleted_PreservesContentUriString`: Validates public MediaStore content URI propagation.
  - `testETagMismatch_DiscardsStaleChunksAndRestartsCleanly`: Validates that remote server file updates trigger safe stale chunk purging and clean restart.

- **NotificationThrottlerTest** (2 tests):
  - Verifies rapid update suppression and instant terminal event dispatch.

- **SpeedTestEngineTest** (6 tests):
  - `testSpeedTest_InitialStateIsIdle`: Verifies initial state of SpeedTestEngine starts in `Idle`.
  - `testSpeedTest_FailsWhenNoNetworkAvailable`: Confirms error transition when both Wi-Fi and Cellular are unavailable.
  - `testSpeedTest_StopResetsStateToIdle`: Tests user cancellation safety and immediate return to `Idle`.
  - `testSpeedTestModels_DataClassIntegrity`: Tests state immutability, progress calculation, and telemetry fields across Running, Completed, and Error states.
  - `testSpeedTestPhaseEnum_ValuesExist`: Verifies all 7 lifecycle phases (`IDLE`, `TESTING_WIFI`, `TESTING_CELLULAR`, `TESTING_DUALBOOST`, `COMPLETED`, `CANCELLED`, `FAILED`).
  - `testTurbine_SpeedTestState_EmitsErrorWhenOffline`: Asserts live `speedTestState` stream transitions sequentially from `Idle` to `Error` using Turbine.


## End-to-End & Automated Smoke Testing
Automated testing is performed without manual clicks using the provided automated script:
```powershell
.\test-automated.ps1
```
This script executes:
1. All local JVM unit and MockWebServer integration tests via `.\gradlew.bat test`.
2. Inspects ADB for attached devices (e.g. Pixel 9).
3. If connected, automatically compiles the APK (`.\gradlew.bat assembleDebug`), installs it via ADB, grants permissions, launches the app, and monitors logcat to confirm zero crashes.

### Playwright-Style Mobile UI Automation (Maestro)
Playwright-style declarative mobile UI testing is implemented via **Maestro** (`.maestro/`):
- **Runner Script**: `.\run-maestro.ps1`
- **Master Suite**: `.maestro/master_suite.yaml` (runs all flows sequentially).
- **Core Download & Discard Flow** (`.maestro/download_flow.yaml`): Automatically selects benchmark presets, initiates downloads, verifies live telemetry, triggers pause, verifies discard resets cleanly to `Idle`, and verifies URL preservation.
- **Speed Benchmark Flow** (`.maestro/speed_benchmark_flow.yaml`): Taps "Test Speed", executes the 3-phase automated benchmark (Wi-Fi -> 5G -> DualBoost), verifies the celebratory summary, and closes the bottom sheet.
- **Windows Native Installation**:
  Download and extract the official release zip:
  ```powershell
  curl.exe -L "https://github.com/mobile-dev-inc/Maestro/releases/latest/download/maestro.zip" -o "$HOME\maestro.zip"
  Expand-Archive "$HOME\maestro.zip" -DestinationPath "$HOME\.maestro" -Force
  Remove-Item "$HOME\maestro.zip"
  ```
  Or download `maestro.zip` directly via browser and extract to `C:\maestro\bin`.

## API Testing
- Verify HTTP 206 response validation.
- Verify fallback behavior when a mock server returns HTTP 200 (server ignored Range header).

## Database Testing
- Currently not applicable (no persistent SQL database in MVP).

## Mocking Strategy
- Use OkHttp's `MockWebServer` to simulate network latency, range responses, and connection drops without incurring cellular data charges during development.

## Test Data
- Test URLs with known Range support:
  - `https://speed.hetzner.de/100MB.bin`
  - `https://speed.hetzner.de/1GB.bin`
  - Internal synthetic MockWebServer byte buffers.

## Test Naming Conventions
- Pattern: `fun subject_condition_expectedResult()`
- Example: `fun chunkQueue_whenPartitioned_hasNoGapsOrOverlaps()`

## Coverage Requirements
- 100% test coverage for chunk boundary calculation logic in `DualDownloaderEngine`.
- Critical error recovery paths (re-queuing failed chunks) must be exercised.

## Required Checks
Before marking any development task complete:
1. `.\gradlew.bat test` passes with 0 failures.
2. `.\gradlew.bat assembleDebug` produces a valid APK.
3. Lint checks pass without blocking errors: `.\gradlew.bat lint`.

## CI Testing
CLI commands designed for local terminal or headless CI (GitHub Actions):
```bash
.\gradlew.bat check
.\gradlew.bat assembleDebug
```

## Definition of Done
1. Code conforms to [RULES.md](file:///A:/Development/dualboost-downloader/docs/RULES.md).
2. The APK compiles cleanly via command line with no warnings treated as errors.
3. Dual-interface throughput and failover have been validated via `adb logcat`.
4. File hash of downloaded asset matches the remote origin.
5. All documentation files (`docs/PRD.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, `docs/RULES.md`, `docs/MEMORY.md`, `docs/DECISIONS.md`, `docs/TESTING.md`) are updated to reflect the change.

