# Testing Strategy

## Testing Philosophy
Focus on pragmatic verification of critical path operations: chunk calculation, state transitions, socket binding isolation, and file integrity on disk. We prioritize deterministic unit tests for mathematical and algorithmic operations, coupled with live device verification via ADB.

## Test Stack
- **Unit Testing Framework**: JUnit 4 / JUnit 5 (Android standard)
- **Assertion Library**: Kotlin `test` assertions
- **Mocking**: MockWebServer (OkHttp) for simulating HTTP Range responses
- **Device Instrumentation**: Android Debug Bridge (`adb`) CLI and connected Android tests (`connectedAndroidTest`)

## Unit Testing
Unit tests reside in `app/src/test/java/com/dualboost/downloader/` (175 active tests across 22 test suites, 100% passing):

- **CrashVaultTest** (2 tests):
  - `testRecordCrash_WritesSanitizedCrashDump`: Verifies `CrashVault` writes structured, timestamped crash dumps to `noBackupFilesDir/crash_vault.log`, ensuring URL query parameters and auth tokens are completely redacted from stack traces.
  - `testSanitizeCrashLog_RedactsUrlsWithParameters`: Tests regex-based URL redaction, ensuring sensitive domains, paths, and API keys are masked to `https://host/[REDACTED]`.

- **ProductionHardeningResilienceTest** (11 tests):
  - `testDownloadChunk_ConcurrentReadWriteSafety_PreservesDataIntegrity`: Launches 4 concurrent worker coroutines mutating `DownloadChunk` byte counters, statuses, and assigned networks alongside a reader coroutine to verify atomic memory visibility and absence of race conditions.
  - `testResumption_BaselineInterfaceAccounting_DoesNotDistortSessionCounters`: Tests that resuming an interrupted download initializes session Wi-Fi and Cellular counters strictly to 0L, while tracking pre-existing progress in `resumedBytes`, eliminating speed spikes and carrier metric distortions.

- **DownloadRepositoryTest** (8 tests):
  - `testRepository_StartDownload_FallsBackToEngineWhenServiceUnavailable`: Verifies single-dispatch FGS boundary fallback to direct engine execution in headless JVM test runners.
  - `testRepository_TestWorkerConnectivity_SuccessAndFailure`: Validates worker health checks via OkHttpClient in `Dispatchers.IO`, returning `WorkerTestStatus.Success` on 200 and `WorkerTestStatus.Error` on failure or 401.
  - `testRepository_ResetToIdle_ResetsFailedState`: Asserts `resetToIdle()` cleanly resets `DownloadState.Failed` to `DownloadState.Idle`.

- **MainViewModelTest** (20 tests):
  - `testWorkerTestStatus_InitialStateIsIdle`: Asserts reactive worker test status stream initializes to `WorkerTestStatus.Idle`.
  - `testFailedState_PreservesUrlAndSupportsRetry`: Validates that failed state retains the attempted URL and triggers retry on `retryDownload()`.
  - `testShowBatteryGuidance_TrueForAggressiveOemWhenNotIgnored`: Validates `PowerExemptionChecker` evaluation, asserting `showBatteryGuidance` is true on non-exempt aggressive OEMs and resets to false upon `dismissBatteryGuidance()`.

- **ServiceLifecycleTest** (3 tests):
  - `testServiceConstants_CorrectAndConsistent`: Verifies FGS actions (`ACTION_START_DOWNLOAD`, `ACTION_CANCEL_DOWNLOAD`, `ACTION_PAUSE_DOWNLOAD`, `ACTION_RESUME_DOWNLOAD`, `ACTION_DISCARD_DOWNLOAD`).
  - `testServiceActionNaming_FollowsReverseDomainConvention`: Asserts all action strings adhere to reverse-domain naming.

- **SecurityHardeningPhase1Test** (6 tests):
  - `testRangedChunk_WhenServerReturnsHttp200_RejectsChunkAndDoesNotClobberOtherBytes`: Tests H-2a defense; simulates rogue server returning HTTP 200 to ranged chunk request; asserts engine immediately rejects with `ProtocolException` and prevents clobbering neighboring chunk byte ranges on disk.
  - `testRangedChunk_BuffersAreCappedToExpectedChunkBytes`: Tests H-2a write capping; asserts buffer reads are strictly capped to `min(buffer.size, remaining)` so oversized bodies cannot spill past chunk boundaries.
  - `testSingleStreamFallback_WhenTruncated_ReportsFailureInsteadOfCompleted`: Tests H-2b defense; asserts premature EOF or dropped connection during single-stream transfer triggers failure rather than falsely marking `Completed`.
  - `testResume_WhenPartialFileTruncatedOrDeleted_RestartsCleanlyInsteadOfZeroFilling`: Tests H-2c defense; asserts deleted or truncated partial files invalidate sidecar metadata and force clean restarts from byte 0, eliminating sparse zero-byte holes.
  - `testCircuitBreaker_EmitsAutoRecoveringAndBacksOff_BeforePausing`: Tests M-4; asserts dual interface circuit break triggers `DownloadState.AutoRecovering`, exponential backoff (1s -> 2s -> 4s), 5-attempt retry caps, and escalates to `DownloadState.Paused`.
  - `testDiscardDownload_RejectsPathTraversalOutsideDownloadDir`: Tests M-5; asserts canonical directory containment validation (`+ File.separator`) blocks path traversal deletion attempts.

- **SecurityHardeningPhase2Test** (3 tests):
  - `testSettingsBottomSheet_HealthCheckOmitsSecret`: Tests M-1; verifies that `/health` ping requests omit `X-Slicer-Key` to avoid needless credential exposure.
  - `testWorkerSSRF_RejectsIPv4MappedIPv6AndPrivateRanges`: Tests M-2; validates Cloud Slicer address parsing against IPv4-mapped IPv6 literals (`[::ffff:192.168.1.1]`), NAT64, 6to4, and unique local addresses.
  - `testWorkerRangeHandling_NonRangeUpstreamReturns501InsteadOfAmplifying`: Tests M-6; verifies Cloud Slicer immediately aborts with HTTP 501 when upstream origins refuse Range headers, preventing quadratic bandwidth amplification.

- **SecurityHardeningPhase3Test** (2 tests):
  - `testDownloadForegroundService_OnDestroyDoesNotStopNetworkManagerListening`: Asserts `service.onDestroy()` does not call `networkManager.stopListening()`, preserving app-wide network telemetry.
  - `testDownloadHistory_SupportsContentUriStringWhenAppDirDeleted`: Asserts `isFilePresent()`, `openFile()`, and `shareFile()` verify and launch successfully using `contentUriString` after app-dir files are deleted post-MediaStore export.

- **EngineBoundaryAndStorageSafetyTest** (16 tests):
  - `testResolveWritableOutputFile_FilenameCollisionSequence`: Asserts non-conflicting filename increments (`file.bin` -> `file (1).bin` -> `file (2).bin` -> `file (3).bin`).
  - `testResolveWritableOutputFile_ReusesExistingFileWhenMetaUrlMatches`: Verifies reuse of existing files when `.dualboost_meta` sidecar matches the URL.
  - `testResolveWritableOutputFile_StrictPathTraversalBoundaryCheck`: Confirms directory traversal sequences (`../../`, `..\\..\\`, `/var/tmp/`) are strictly confined within the designated target directory.
  - `testCheckAvailableStorage_ZeroOrNegativeRequiredBytes_NeverThrows`: Ensures zero or negative byte requirements return immediately without throwing.
  - `testCheckAvailableStorage_ExistingFileSizeCoversRequired_NeverThrows`: Validates existing partial file size is subtracted, preventing false out-of-storage rejections.
  - `testCheckAvailableStorage_64BitOverflow_ThrowsInsufficientStorage`: Verifies 64-bit Long arithmetic overflow guard (`neededAdditionalBytes > Long.MAX_VALUE - safetyBuffer`) throwing informative `IOException`.
  - `testExtractFileName_PercentDecodingAndSpaceReplacement`: Validates percent-decoding and `_20` replacement into human-readable space characters.
  - `testExtractFileName_UrlWithQueryContainingDotsAndSlashes`: Ensures query strings containing dots and slashes do not corrupt the target file name.
  - `testExtractFileNameFromDisposition_SemicolonsInsideQuotesSanitizedToUnderscores`: Asserts semicolons in quoted disposition headers are safely sanitized into underscores by regex filtering.
  - `testExtractFileNameFromDisposition_EmptyOrDotsOnly_ReturnsNull`: Ensures empty or traversal-only disposition filenames return null.
  - `testComputeSha256_NonExistentFileReturnsNull`: Verifies null return when checking non-existent files.
  - `testComputeSha256_ZeroByteFileReturnsNull`: Verifies null return for zero-byte files.
  - `testComputeSha256_AccurateHexDigest`: Validates bit-for-bit SHA-256 computation against known reference digests.
  - `testGetMimeType_CaseInsensitiveAndFallback`: Asserts case-insensitive extension matching (`.APK`), unknown extension fallback, and extensionless fallback to `application/octet-stream`.
  - `testSanitizeUrlForLog_VariousFormats`: Confirms sensitive query parameters and tokens are stripped from log outputs.
  - `testStartDownload_WhenAlreadyDownloadingSameUrl_IsIdempotent`: Verifies calling `startDownload` on an already-running download with identical URL is safe and idempotent.

- **FormatAndTelemetryEdgeCasesTest** (12 tests):
  - `testFormatEta_SubSecondAndZero`: Validates countdown formatting for zero and negative seconds (`"< 1s"`).
  - `testFormatEta_SecondsOnly`: Validates sub-minute countdowns (`"1s"`, `"45s"`, `"59s"`).
  - `testFormatEta_MinutesAndSeconds`: Asserts exact minutes (`"1m"`) and mixed minute/second countdowns (`"1m 20s"`, `"59m 59s"`).
  - `testFormatEta_HoursAndMinutes`: Asserts exact hours (`"1h 0m"`, `"24h 0m"`) and multi-hour countdowns (`"1h 1m"`, `"25h 1m"`).
  - `testFormatSpeed_ZeroAndNegative`: Asserts zero and negative speeds default to `"0.0 MB/s"`.
  - `testFormatSpeed_SubMegabyteUsesKbps`: Validates automatic adaptation to `KB/s` for sub-megabyte transfers (`0.5 MB/s` -> `"512 KB/s"`).
  - `testFormatSpeed_MegabyteAndAbove`: Asserts decimal precision formatting for transfers >= 1.0 MB/s.
  - `testFormatSpeedComponents_ZeroAndNegative`: Asserts hero component formatting for zero and negative speeds.
  - `testFormatSpeedComponents_SubMegabyte`: Asserts hero component splitting for sub-megabyte values (`"512"`, `"KB/s"`).
  - `testFormatSpeedComponents_MegabyteAndAbove`: Asserts hero component splitting for megabyte values (`"42.8"`, `"MB/s"`).
  - `testDownloadChunk_TotalBytesCalculation`: Validates chunk total byte mathematical invariants across 1-byte, 100-byte, and 4MB ranges.
  - `testDownloadChunk_DefaultValues`: Validates initial chunk status (`PENDING`) and assigned network (`DEFAULT`).
  - `testChunkSnapshot_PropertiesPreserved`: Asserts chunk snapshot immutability and progress values.
  - `testSpeedTestStateCompleted_BoostCalculationAndCompatibilityGetters`: Validates speed test completion state, boost percentage, and Mbps compatibility getters.
  - `testSpeedTestStateRunning_ProgressAndPhase`: Validates speed test active phase, running progress bounds, and live telemetry fields.

- **SecurityAndResilienceEdgeCasesTest** (8 tests):
  - `testRootDetector_ExecutesSafelyWithoutCrashing`: Verifies `RootDetector.isDeviceRooted()` executes without throwing exceptions or security errors across test JVMs.
  - `testSlicerPreferences_BuildSlicedUrl_EndpointsAndSlashes`: Asserts `/slice` endpoint construction idempotency across URLs with/without trailing slashes and pre-existing `/slice` paths.
  - `testSlicerPreferences_BuildSlicedUrl_SpecialCharactersInTarget`: Validates URL encoding of target URLs containing spaces, hashes, and complex query parameters.
  - `testSlicerPreferences_StorageAndTrimming`: Confirms in-memory preference storage, secret masking, and whitespace trimming.
  - `testTruncatedChunk_TriggersRollbackAndSuccessfulRetry`: End-to-end MockWebServer test where server truncates a chunk stream mid-transfer; asserts engine rolls back byte counters, restores chunk status to `PENDING`, retries, and delivers 100% bit-for-bit SHA-256 payload integrity.


- **DownloadHistoryRepositoryTest** (9 tests):
  - `testSerializationRoundTrip`: Validates pure Kotlin JSON serialization and parsing of `DownloadHistoryItem` preserving all fields.
  - `testRecordAndRetrieveSingleDownload`: Tests adding an item, persistent JSON writing to disk, and retrieving it via fresh repository instances.
  - `testOrderIsDescendingByTimestamp`: Confirms items are automatically sorted with newest downloads first.
  - `testDuplicateFilePathUpdatesEntry`: Ensures duplicate file paths update existing records rather than creating redundant entries.
  - `testDeleteItemWithoutDiskDeletion`: Verifies deleting a record leaves the physical file on disk.
  - `testDeleteItemWithDiskDeletion`: Verifies deleting a record with disk removal permanently cleans the physical file.
  - `testClearAllWithDiskDeletion`: Tests clearing the entire history and deleting all disk files.
  - `testCorruptionRecovery`: Confirms corrupt or invalid JSON in storage is handled gracefully without crashing.
  - `testStorageCalculationAndFileExistence`: Validates `calculateTotalStorageBytes()` and `isFilePresent()` accurately account only for files physically present on disk.

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

- **DualNetworkManagerTest** (8 tests):
  - `testNetworkProvider_BothDisconnectedInitially`: Validates default state when neither interface is active.
  - `testNetworkProvider_WifiOnlyMode`: Tests socket client availability and state flags in Wi-Fi-only mode.
  - `testNetworkProvider_CellularOnlyMode`: Tests socket client availability and state flags in Cellular-only mode.
  - `testNetworkProvider_DualChannelAggregatedMode`: Tests concurrent dual-socket mode when both interfaces are connected.
  - `testNetworkProvider_DynamicDisconnectionMidStream`: Tests dynamic interface detachment when Wi-Fi drops mid-session.
  - `testRoamingDetection_RevokesCellularClient`: Asserts that when roaming is detected, `isRoamingActive` is true and `getCellularClient()` immediately returns `null` to prevent carrier overage fees.
  - `testVpnDetection_RoutesThroughDefaultOkHttpClient`: Asserts that when an active VPN tunnel is detected (`isVpnActive = true`), both Wi-Fi and Cellular requests fall back to `baseOkHttpClient` rather than raw socket factories, preventing `EPERM` kernel crashes and tunnel bypasses.
  - `testCarrierNameAndModemStandby_ExposedCorrectly`: Asserts that carrier names are correctly propagated and modem standby is detected when Wi-Fi is active but cellular requests are unfulfilled.

- **ServiceLifecycleTest** (3 tests):
  - `testServiceConstants_CorrectAndConsistent`: Verifies actions, channel ID (`dualboost_download_channel`), notification ID (`1001`), and extra keys.
  - `testServiceActionNaming_FollowsReverseDomainConvention`: Ensures action naming adheres to Android package reverse-domain guidelines.
  - `testThrottledNotificationLogic_LimitsToOncePerSecond`: Verifies that notification updates are capped to 1 per second while terminal states (Complete/Fail) dispatch immediately.

- **UIFlowAndViewModelComprehensiveTest** (7 tests):
  - `testAllPresetDefinitions_HaveValidAttributesAndProtocols`: Verifies all 7 curated presets have valid HTTPS URLs, non-blank labels, and capability descriptions.
  - `testSequentialPresetSwitching_MaintainsCorrectViewModelState`: Verifies sequential chip clicks correctly update URL and active preset selection.
  - `testCustomUrlTyping_DeselectsPresetAutomatically`: Ensures typing custom URLs clears preset chip highlights.
  - `testClearUrl_ResetsBothUrlAndPreset`: Verifies the clear button resets both input text and preset state.
  - `testStartButtonStateMatrix_Exhaustive`: Tests the complete state machine matrix for button enablement across all 7 lifecycle states.
  - `testDualColorProgressSplit_FormulaPrecision`: Validates dual-channel progress split calculations across asymmetric loads and edge cases.
  - `testFormatSpeed_AdaptiveFormatting`: Asserts binary MB/s speed formatting, adaptive KB/s switching for sub-megabyte transfers, zero/idle boundaries, and hero speedometer components.

- **MainViewModelTest** (11 tests):
  - Validates presets, chip clicks, URL updates, start button states, dual-color ratio math, null repository tolerance, URL preservation across `discardDownload()`, safe speed test state delegation, and Turbine live stream assertions (`testTurbine_UrlInputStream_EmitsAccurateSequence`, `testTurbine_SelectedPresetStream_EmitsAccurateSequence`, `testTurbine_SpeedTestStateStream_RemainsIdleWithoutRepository`).

- **EngineEdgeCasesTest** (9 tests):
  - Tests request header injection, HTTP 403, 404, 500 handling, User-Agent propagation, single-stream fallback, pause cancellation, instantaneous socket abort, and interface circuit-breaker recovery.

- **EngineComprehensiveTest** (18 tests):
  - Validates filename parsing, query stripping, Cloudflare endpoint regex, Content-Disposition header extraction (quoted, unquoted, case-insensitive), `formatBytes()` string formatting, single-stream fallback data integrity, Content-Disposition overrides, mobile request headers (`testBuildRequest_MobileHeaders`), desktop spoofing request headers (`testBuildRequest_DesktopSpoofingHeaders`), two-stage adaptive desktop spoofing unlocking HTTP 206 Partial Content on mobile-restricted servers (`testAdaptiveDesktopSpoofing_UnlocksHttp206WhenMobileFails`), and sidecar `.dualboost_meta` persistence/resumption of `isDesktopSpoofed` state (`testMetaInfo_SavesAndRestoresDesktopSpoofedFlag`).

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

### Jetpack Compose UI Testing (`androidx.compose.ui:ui-test-junit4`)
End-to-end and component UI testing on Android devices is performed using Google's official Jetpack Compose Testing framework:
- **Test Suite**: `app/src/androidTest/java/com/dualboost/downloader/ui/MainComposeUiTest.kt`
- **Execution Command**:
  ```powershell
  .\gradlew.bat connectedAndroidTest
  ```
- **Tested User Flows**:
  1. **Dashboard Initial State**: Confirms "Ready to Download" state, preset chips, and input field rendering.
  2. **Benchmark Preset Selection**: Tapping preset chips (e.g. "150 MB") updates the URL input reactive state.
  3. **Custom URL Typing**: Ensures custom URL typing updates reactive StateFlow.
  4. **Download & Discard Lifecycle**: Tests starting download, pausing, and discarding, asserting complete reset back to Idle without losing the URL.
  5. **Speed Benchmark Bottom Sheet**: Tests clicking "Test Speed", opening the modal sheet, inspecting comparative metric cards, and dismissing cleanly back to dashboard.
  6. **Single-Stream Alert & Dimming**: Confirms amber banner rendering, "Single-Channel Bandwidth" header, and dimmed inactive cellular meter.
  7. **Desktop Mode Active Badge**: Asserts that `⚡ Desktop Mode Active` badge renders with primary container tint when `isDesktopSpoofed == true`.
  8. **Cloud Slicer Active Badge**: Asserts that `⚡ Cloud Slicer Active` badge renders with tertiary container tint when `isCloudSlicerActive == true`.
  9. **Settings Modal Sheet**: Asserts that tapping Settings gear icon opens `SettingsBottomSheet` and renders Worker URL, Secret Key, Test Worker, Copy Script, and Save buttons.

### Personal Cloud Slicer & Stage 3 Unit Tests (`EngineComprehensiveTest.kt`)
- `testSlicerPreferences_SaveAndRetrieveUrlAndSecret`: Asserts SharedPreferences storage and retrieval of worker URL and optional secret token.
- `testSlicerPreferences_BuildSlicedUrl_WithAndWithoutSecret`: Asserts correct URL encoding, `/slice` endpoint construction, and secret key query parameter handling.
- `testMetaInfo_SavesAndRestoresCloudSlicerActiveFlag`: Asserts `.dualboost_meta` sidecar serialization and restoration of `isCloudSlicerActive = true` into `DownloadState.Paused`.
- `testAdaptiveProbe_Stage3_CloudSlicerFallback`: End-to-end MockWebServer test where standard mobile and desktop spoofing probes fail (returning HTTP 200 without ranges), triggering Stage 3 Personal Cloud Slicer probe, downloading chunks via slicer, setting `isCloudSlicerActive = true`, and validating 100% SHA-256 byte payload integrity.

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

