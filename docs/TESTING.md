# Testing Strategy

## Testing Philosophy
Focus on pragmatic verification of critical path operations: chunk calculation, state transitions, socket binding isolation, and file integrity on disk. We prioritize deterministic unit tests for mathematical and algorithmic operations, coupled with live device verification via ADB.

## Test Stack
- **Unit Testing Framework**: JUnit 4 / JUnit 5 (Android standard)
- **Assertion Library**: Kotlin `test` assertions
- **Mocking**: MockWebServer (OkHttp) for simulating HTTP Range responses
- **Device Instrumentation**: Android Debug Bridge (`adb`) CLI and connected Compose UI tests (`connectedAndroidTest`, 13 active tests in `MainComposeUiTest`, 100% passing on Pixel 9)

## Connected UI Testing (`androidTest`)
Compose instrumentation tests reside in `app/src/androidTest/java/com/dualboost/downloader/ui/MainComposeUiTest.kt` (`13` tests executed directly on Pixel 9 via `.\gradlew.bat connectedAndroidTest`, 100% passing):
- Covers idle dashboard state, URL validation/preset selection, "Watch While Downloading" sequential switch interaction, compact single-stream telemetry (`Single-Stream • Wi-Fi Only`, `Wi-Fi • 12.5 MB/s`, `5G (Inactive)`), desktop spoof & cloud slicer badges, sequential `⚡ Stream Ready • 24.0 MB buffered` + `Preview` CTA, paused/discard state, completed action layout, and Material 3 `1.5.0` Expressive `Probing` / `AutoRecovering` states (`testProbingAndAutoRecoveringStates_RenderWithMaterialExpressiveTheme`).

## Unit Testing
Unit tests reside in `app/src/test/java/com/dualboost/downloader/` (425 active tests across 73 test suites, 100% passing):

- **ThemeContrastTest** (4 tests):
  - `testLightColorScheme_AllStandardPairingsExceedWcagAA`: Verifies WCAG AA contrast ratios (>= 4.5:1) across LightColorScheme container and surface pairs.
  - `testDarkColorScheme_AllStandardPairingsExceedWcagAA`: Verifies WCAG AA contrast ratios (>= 4.5:1) across DarkColorScheme container, surface, and fixed color (`*Fixed`) pairs.
  - `testTranslucentBrandTintPattern_ExceedsHighContrastInBothModes`: Verifies 14% primary translucent tint contrast (>= 3.0:1) in Light and Dark modes.
  - `testMaterial3ExpressiveMotionScheme_ProvidesDistinctSpringSpecs`: Verifies Material 3 `1.5.0` `MotionScheme.expressive()` provides non-null spatial/effects specs distinct from `MotionScheme.standard()`.

- **ChunkConfigTest** (6 tests):
  - `testChunkConfig_DefaultsToAutoAdaptiveAnd4MB`: Verifies domain model defaults to Auto-Adaptive sizing with 4 MB manual baseline.
  - `testChunkConfig_ClampsManualChunkSizeBetween1MBAnd64MB`: Tests boundary clamping ensuring manual chunk sizes stay strictly within 1 MB to 64 MB.
  - `testChunkConfig_AutoAdaptive_ScalesByPayloadSize`: Validates Balanced Payload & Speed Matrix scaling across small (< 8 MB), moderate (< 16 MB), standard (< 200 MB), large (< 1000 MB), and massive (> 1000 MB) payloads.
  - `testChunkConfig_AutoAdaptive_UpgradesOnGigabitWifi7`: Verifies chunk size upgrades to 16 MB and 64 MB on multi-gigabit / Wi-Fi 7 connections.
  - `testChunkConfig_AutoAdaptive_ClampsOnSpottyNetwork`: Verifies spotty or weak connections clamp chunk sizes to at most 2 MB.
  - `testChunkConfig_SupportedTiersList`: Verifies all 7 supported tiers (1 MB, 2 MB, 4 MB, 8 MB, 16 MB, 32 MB, 64 MB).

- **ChunkPreferencesTest** (5 tests):
  - `testPreferences_DefaultsToAutoAdaptiveAnd4MB`: Verifies SharedPreferences defaults to Auto-Adaptive with 4 MB baseline.
  - `testPreferences_SavesAndRestoresManualSize`: Tests saving and reloading custom chunk sizes.
  - `testPreferences_TogglesAutoAdaptive`: Verifies toggling Auto-Adaptive mode on and off.
  - `testPreferences_ClampsManualSizeOnSave`: Verifies boundary clamping on save.
  - `testPreferences_ResetToDefault_RestoresAutoAdaptive`: Tests one-tap reset restoring default Auto-Adaptive mode.

- **EngineDynamicChunkTest** (6 tests):
  - `testEngine_DefaultsToAutoAdaptiveChunkConfig`: Verifies engine initializes with Auto-Adaptive config.
  - `testEngine_SetsAndRetrievesChunkConfig`: Verifies engine updates and retains chunk config.
  - `testEngine_SaveAndReadMetadata_PreservesCustomChunkSize`: Verifies metadata serialization saves and loads custom chunk sizes.
  - `testEngine_ResolveEffectiveChunkSize_PreservesMetadataOnResume`: Verifies non-negotiable resume invariance ensuring incomplete downloads strictly resume with `existingMeta.chunkSize` to prevent file corruption.
  - `testRepository_ExposesChunkConfigStateFlowAndUpdates`: Tests repository StateFlow and engine synchronization.
  - `testDownloadState_Downloading_IncludesChunkSize`: Verifies telemetry state exposes active chunk size.

- **ChunkConfigUiTest** (4 tests):
  - `testChunkUi_SupportedTiersList`: Verifies 7 supported tiers in UI model.
  - `testChunkUi_TierLabelsAndFormatting`: Tests formatting tier chip labels ("1 MB", "4 MB", etc.).
  - `testChunkUi_FormatsChunkBadgeText`: Tests active download badge formatting ("⚡ 4 MB Chunks (Auto)" vs "⚡ 16 MB Chunks").
  - `testChunkUi_SummaryTextFormatting`: Tests strategy summary text in Settings card.

- **StorageDestinationTest** (6 tests):
  - `testDestination_PresetsArePopulated`: Verifies that `StorageDestination.PRESETS` contains all 3 built-in presets (Standard, DualBoost, Media) with accurate IDs and display names.
  - `testDestination_DefaultResolvesBaseDirectory`: Validates public Downloads root resolution.
  - `testDestination_SubfolderResolvesCorrectChildPath`: Tests subfolder resolution (`Downloads/DualBoost`) and automatic directory creation.
  - `testDestination_CustomPathResolvesCorrectly`: Tests custom directory path resolution and `isCustom()` evaluation.
  - `testDestination_UnwritableFolder_FallsBackSafely`: Tests fail-safe fallback to public Downloads or app-internal files when given an unwritable or inaccessible path.
  - `testDestination_FindPresetById`: Tests ID resolution and safe fallback for unknown IDs.

- **StoragePreferencesTest** (4 tests):
  - `testPreferences_DefaultsToDefaultDownloads`: Tests preference initialization defaults to standard Downloads.
  - `testPreferences_SavesAndRestoresPresetDestination`: Verifies persistence of preset destinations across reloads.
  - `testPreferences_SavesAndRestoresCustomDestination`: Verifies persistence of custom folder paths and SAF tree URI strings.
  - `testPreferences_ResetToDefault_RestoresStandardDownloads`: Tests one-tap reset restoring default standard Downloads.

- **EngineDynamicStorageTest** (5 tests):
  - `testEngine_DefaultsToDefaultStorageDestination`: Verifies `DualDownloaderEngine` initializes with `StorageDestination.PRESET_DEFAULT`.
  - `testEngine_SetsAndUsesConfiguredSubfolderDestination`: Verifies that setting a subfolder preset updates `getDefaultDownloadDir()` to the child folder.
  - `testEngine_SetsAndUsesCustomStorageDestination`: Tests engine target resolution when pointed to a custom directory.
  - `testEngine_UnwritableCustomStorage_FallsBackToSafeDirectory`: Validates engine resilience when custom storage becomes unwritable, verifying automatic fallback without exceptions.
  - `testRepository_ExposesStorageDestinationStateFlowAndUpdates`: Tests repository reactive `storageDestination` StateFlow and synchronization with the engine.

- **MainViewModelStorageTest** (3 tests):
  - `testViewModel_ExposesStorageDestinationStateFlow`: Verifies ViewModel exposes reactive destination stream.
  - `testViewModel_UpdatesStorageDestination`: Verifies ViewModel dispatches and persists storage destination changes.
  - `testViewModel_ResetsStorageDestinationToDefault`: Verifies ViewModel one-tap reset back to default.

- **StorageDestinationUiTest** (3 tests):
  - `testStorageUi_FormatsPathPreviewAndPresets`: Validates that presets format accurately for UI previews.
  - `testStorageUi_CustomDestinationFormatting`: Tests custom path formatting and display name preservation.
  - `testStorageUi_PresetMatching`: Verifies preset matching by ID.

- **StreamPaletteModelTest** (6 tests):
  - `testStreamPalette_DefaultIsMaterialYou`: Verifies default palette is `DYNAMIC_MATERIAL_YOU`.
  - `testStreamPalette_FromId_FallbackSafe`: Tests string ID mapping and safe fallback on unknown/null IDs.
  - `testStreamPalette_CyberpunkNeon_ResolvesCorrectDarkAndLightPairs`: Validates dark and light mode color pairs for Cyberpunk Neon.
  - `testStreamPalette_EmeraldAmber_ResolvesCorrectDarkAndLightPairs`: Validates dark and light mode color pairs for Emerald & Amber.
  - `testStreamPalette_SunsetCoral_ResolvesCorrectDarkAndLightPairs`: Validates dark and light mode color pairs for Sunset Coral.
  - `testStreamPalette_MaterialYou_DelegatesToMaterialThemeTokens`: Verifies dynamic delegation to MaterialTheme `primary` and `tertiary` tokens.

- **StreamPalettePreferencesTest** (3 tests):
  - `testPreferences_DefaultsToMaterialYou`: Tests preference initialization defaults to Material You.
  - `testPreferences_SavesAndRestoresSelectedPalette`: Verifies persistence across reloads.
  - `testPreferences_ResetToDefault_RestoresMaterialYou`: Tests one-tap reset restoring default Material You.

- **MainViewModelPaletteTest** (3 tests):
  - `testViewModel_ExposesStreamPaletteStateFlow`: Verifies ViewModel exposes reactive palette stream.
  - `testViewModel_UpdateStreamPalette_PersistsAndEmits`: Verifies ViewModel dispatches and persists palette selections.
  - `testViewModel_ResetStreamPalette_RestoresDefault`: Tests ViewModel reset restoring default.

- **StreamPaletteUiTest** (2 tests):
  - `testPaletteUi_FormatsDisplayNameAndSummary`: Tests display names and descriptions.
  - `testPaletteUi_AllPalettesHaveDistinctWifiAndCellularColors`: Validates that Wi-Fi and Cellular colors are visually distinct in both dark and light modes across all presets.

- **ConcurrencyPreferencesTest** (4 tests):
  - `testConfig_ClampsWorkersBetweenOneAndEight`: Verifies constructor bounding logic clamps worker counts outside `1..8` down to 1 or up to 8.
  - `testPreferences_DefaultsToTwoWifiAndTwoCellular`: Tests default stream allocation (2 Wi-Fi + 2 Cellular) and Auto-Scale disabled.
  - `testPreferences_SavesAndRestoresWorkerCounts`: Verifies persistence of customized stream allocations across reloads.
  - `testPreferences_ResetToDefault_RestoresTwoAndTwo`: Validates one-tap reset restoring 2 + 2 allocation.

- **EngineDynamicConcurrencyTest** (3 tests):
  - `testEngine_LaunchesConfiguredNumberOfWorkers`: Tests that `DualDownloaderEngine` launches exact configured worker counts (e.g. 3 Wi-Fi + 1 Cellular) and verifies unused worker slots do not execute.
  - `testEngine_ThermalModerate_StrictlyCapsCellularToOneWorker`: Tests fail-safe thermal throttling under `ThermalStatus.MODERATE`, verifying cellular workers with `workerIndex > 0` are suppressed down to at most 1 active worker.
  - `testEngine_ThermalSevere_CompletelySuppressesCellularWorkers`: Tests fail-safe thermal offload under `ThermalStatus.SEVERE`, verifying all cellular workers are completely suppressed (0 chunk transfers) and 100% offloaded to Wi-Fi.

- **MainViewModelConcurrencyTest** (5 tests):
  - `testViewModel_ExposesDefaultConcurrencyConfig`: Tests reactive `concurrencyConfig` StateFlow initialization.
  - `testViewModel_UpdateWifiWorkers_PersistsAndEmits`: Verifies ViewModel dispatches and persists Wi-Fi stream updates with boundary clamping.
  - `testViewModel_UpdateCellularWorkers_PersistsAndEmits`: Verifies ViewModel dispatches and persists Cellular stream updates with boundary clamping.
  - `testViewModel_ToggleAutoScale_PersistsAndEmits`: Verifies ViewModel toggles and persists Auto-Scale mode.
  - `testViewModel_ResetConcurrencyToDefault_RestoresDefaults`: Verifies ViewModel one-tap reset restores default 2 + 2 configuration.

- **SettingsConcurrencyUiTest** (4 tests):
  - `testConcurrencyCard_CalculatesTotalStreams`: Verifies UI summary label computes total parallel stream allocation.
  - `testConcurrencyStepper_ClampsCorrectly`: Tests stepper delta increment/decrement logic bounded to `1..8`.
  - `testConcurrencyCard_ResetActionRestoresDefaults`: Tests reset action restores default state.
  - `testConcurrencyCard_AutoScaleSubtitleReflectsState`: Verifies assistive subtitle dynamically reflects active vs fixed stream states.

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

- **AsymmetricWorkStealingTest** (5 tests):
  - `testCalculateBatchSize_TieredScaling`: Asserts that batch sizing scales correctly from 1 chunk (4 MB) below 15 MB/s, to 2 chunks (8 MB) for 15-30 MB/s, and 4 chunks (16 MB) above 30 MB/s.
  - `testClaimContiguousPendingChunks_ClaimsSequentialBlocks`: Verifies contiguous batch claiming across sequential chunks without gaps or overlaps.
  - `testClaimContiguousPendingChunks_StopsAtNonContiguousGap`: Confirms that batch claiming immediately stops if a non-pending chunk intervenes, preserving contiguous byte spans.
  - `testFindStragglerCandidate_IdentifiesSlowestOtherInterface`: Validates that idle workers identify lagging chunks on other interfaces having > 512 KB unread bytes.
  - `testBatchedDownload_RangeHeaderCoversMultiChunkSpan_AndAllIndicesCommitted`: Verifies multi-chunk HTTP range requests across MockWebServer and confirms byte-for-byte SHA-256 payload integrity.

- **ThermalAndIoOptimizationTest** (3 tests):
  - `testThermalBackoff_SevereHeat_PausesCellularWhenWifiAvailable`: Asserts that under SEVERE thermal status (3+), cellular chunk requests are completely paused (0 requests) and 100% of chunks are transferred over Wi-Fi, maintaining exact SHA-256 payload integrity.
  - `testThermalBackoff_SevereHeat_FallbackToSingleCellularWorkerWhenWifiUnavailable`: Verifies that when Wi-Fi is down and thermal status is SEVERE, cellular transfers safely complete with single-worker pacing rather than stalling the download.
  - `testThermalBackoff_ModerateHeat_DualChannelWith64KBBufferIntegrity`: Asserts that under MODERATE thermal status (2), both interfaces operate concurrently and 64 KB streaming buffers preserve byte-for-byte integrity without over-reading chunk boundaries.

- **DualNetworkManagerTest** (10 tests):
  - `testDynamicCellularGeneration_HandoverPreserves5GState`: Verifies that active 5G NSA states delivered by `TelephonyDisplayInfo` are never regressed back to 4G LTE by subsequent `onCapabilitiesChanged` packet telemetry.
  - `testCellularKeepAlive_OnDemandLifecycle`: Asserts that `acquireCellularKeepAlive()` and `releaseCellularKeepAlive()` cleanly transition keep-alive state flags on demand.
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
  1. **Dashboard Initial State**: Confirms clean, minimalist layout with Top App Bar, Wi-Fi and 5G Cellular status cards, and the "New Download" card with "Boost Download" CTA (ADR-050).
  2. **Preset Selection Programmatic Logic**: Verifies selecting presets updates reactive state and activates the Boost Download button.
  3. **Custom URL Typing & Clearing**: Ensures custom URL typing and trailing clear icon work seamlessly.
  4. **Download & Discard Lifecycle**: Tests starting download, pausing, and discarding, asserting complete reset back to Idle without losing the URL.
  5. **Speed Benchmark Bottom Sheet (via Drawer)**: Tests opening the side navigation menu, tapping "Speed Benchmark", inspecting comparative metric cards, and dismissing cleanly back to dashboard.
  6. **Single-Stream Alert & Dimming**: Confirms amber banner rendering, "Single-Channel Bandwidth" header, and dimmed inactive cellular meter.
  7. **Desktop Mode Active Badge**: Asserts that `⚡ Desktop Mode Active` badge renders with primary container tint when `isDesktopSpoofed == true`.
  8. **Cloud Slicer Active Badge**: Asserts that `⚡ Cloud Slicer Active` badge renders with tertiary container tint when `isCloudSlicerActive == true`.
  9. **Settings Modal Sheet**: Asserts that tapping Settings in navigation drawer opens the streamlined `SettingsBottomSheet` status dashboard.
  10. **Expressive Probing & Auto-Recovery States**: Verifies `DownloadState.Probing` and `DownloadState.AutoRecovering` render cleanly under `DualBoostTheme` (`MaterialExpressiveTheme` + `LinearWavyProgressIndicator`).

### Personal Cloud Slicer & Stage 3 Unit Tests (`EngineComprehensiveTest.kt`)
- `testSlicerPreferences_SaveAndRetrieveUrlAndSecret`: Asserts SharedPreferences storage and retrieval of worker URL and optional secret token.
- `testSlicerPreferences_BuildSlicedUrl_WithAndWithoutSecret`: Asserts correct URL encoding, `/slice` endpoint construction, and secret key query parameter handling.
- `testMetaInfo_SavesAndRestoresCloudSlicerActiveFlag`: Asserts `.dualboost_meta` sidecar serialization and restoration of `isCloudSlicerActive = true` into `DownloadState.Paused`.
- `testAdaptiveProbe_Stage3_CloudSlicerFallback`: End-to-end MockWebServer test where standard mobile and desktop spoofing probes fail (returning HTTP 200 without ranges), triggering Stage 3 Personal Cloud Slicer probe, downloading chunks via slicer, setting `isCloudSlicerActive = true`, and validating 100% SHA-256 byte payload integrity.

### Antivirus & Threat Guard Test Suite (`ADR-038`)
- **ThreatDetectionEngineTest** (7 tests):
  - `detectsDoubleExtensionTrap`: Identifies deceptive extensions such as `Mirzapur.4K.mkv.apk`.
  - `detectsDisguisedExecutableExtensions`: Flags `.exe`, `.bat`, `.sh`, `.dex`, etc. masquerading as media.
  - `detectsMaliciousMimeType`: Flags `application/vnd.android.package-archive` and binary executable MIME types.
  - `detectsKnownMaliciousDomains`: Blocks domains on malware/ad-redirector threat intelligence lists.
  - `detectsZipApkMagicBytesInMedia`: Identifies `PK\x03\x04` binary headers disguised as `.mkv`/`.mp4`.
  - `detectsWindowsExeMagicBytesInMedia`: Identifies `MZ` binary headers disguised as media files.
  - `cleanMediaFile_returnsCleanAssessment`: Validates that authentic media files pass without false positives.
- **CloudThreatScannerTest** (4 tests):
  - `missingApiKey_returnsCleanFallback`: Gracefully handles missing cloud API key without blocking user downloads.
  - `flaggedMaliciousUrl_returnsThreatAssessment`: Queries MockWebServer and parses Google Safe Browsing threat match.
  - `cleanUrl_returnsCleanAssessment`: Validates clean URLs without matches.
  - `serverError_returnsCleanFallback`: Gracefully falls back to clean assessment on network timeouts or HTTP 500 errors.
- **EngineThreatInterceptionTest** (4 tests):
  - `preFlightThreat_blocksDownloadBeforeWorkersStart`: Confirms pre-flight checks block malicious URLs prior to network worker dispatch.
  - `probeHeadersThreat_blocksDownloadOnMaliciousMime`: Verifies server probe response headers with malicious MIME types immediately abort downloads.
  - `magicBytesThreat_blocksDownloadWhenPayloadIsDisguisedApk`: Verifies that if chunk 0 payload begins with `PK\x03\x04`, download throws `ThreatDetectedException`, cleans disk, and emits `ThreatDetected`.
  - `allowDangerousBypass_allowsDownloadToProceed`: Confirms that user override explicitly bypasses security guard.
- **QueueThreatSkippingTest** (2 tests):
  - `maliciousItemInQueue_isMarkedThreatDetectedAndQueueAdvancesToNextItem`: Verifies that when a queue item triggers a threat, its status becomes `THREAT_DETECTED` and the queue automatically advances to the next clean file.
  - `queueItemSerialization_supportsThreatDetectedStatus`: Tests serialization and deserialization of `QueueItemStatus.THREAT_DETECTED`.
- **SecurityPreferencesTest** (3 tests):
  - `threatGuard_isEnabledByDefault`: Asserts Threat Guard defaults to enabled.
  - `setThreatGuardEnabled_updatesValueCorrectly`: Tests toggling the preference.
  - `safeBrowsingApiKey_storesAndRetrievesCorrectly`: Tests saving and clearing custom Safe Browsing API keys.

### Social Media Downloader Test Suite (`ADR-039`)
- **SocialMediaDetectorTest** (9 tests):
  - `detectsYouTubeUrls_standardShortsAndShortlinks`: Asserts accurate classification across YouTube watch, youtu.be, shorts, and live URLs.
  - `detectsInstagramUrls_reelsPostsAndShares`: Verifies detection of Instagram reels, posts, IGTV, and instagr.am shortlinks.
  - `detectsTikTokUrls_standardAndShortlinks`: Validates standard video paths and `vm.tiktok.com` shortlinks.
  - `detectsTwitterAndXUrls`: Tests both `twitter.com` and `x.com` status links.
  - `detectsRedditUrls`: Validates comments links and `v.redd.it` direct links.
  - `detectsFacebookUrls`: Tests Facebook reels, `fb.watch`, and watch query paths.
  - `detectsPinterestSoundCloudAndVimeo`: Validates pin.it, SoundCloud tracks, and Vimeo videos.
  - `nonSocialUrls_returnNull`: Confirms standard files (`.zip`, `.bin`, `.iso`) are not misclassified as social media.
  - `cleanTrackingParams_stripsTelemetryAndPreservesCoreMediaId`: Asserts removal of telemetry (`si`, `igsh`, `utm_*`) while keeping essential video parameters.
- **SocialMediaResolverTest** (4 tests):
  - `resolveYouTube_returnsHighQualityStreamAndSuggestedFilename`: Tests resolution of 4K/1080p stream URLs with merged audio and suggested filenames.
  - `resolveAudioOnly_requestsMp3FormatAndSetsAudioFlag`: Confirms `downloadMode: audio` requests MP3 tracks and sets audio flags.
  - `multiInstanceFailover_whenPrimaryFails_retriesOnSecondaryInstance`: Simulates primary instance HTTP 429 rate limit and validates automatic failover to secondary instance.
  - `directInstagramHtmlScraper_extractsOgVideoWhenApiFails`: Validates on-device HTML fallback parser extracting `og:video` and `og:title` directly from public Instagram markup.
- **SocialIntakeViewModelTest** (3 tests):
  - `checkAndPrepareSocialLink_detectsInstagramAndSetsReadyState`: Verifies ViewModel transitions to `SocialIntakeState.Ready`.
  - `startSocialDownload_resolvesHighestQualityAndStartsDownload`: Verifies resolution dispatch and handoff to `startDownload()`.
  - `dismissSocialIntake_resetsStateToIdle`: Tests dismissing modal and resetting state to `Idle`.

### Dynamic Cellular Generation Labeling Test Suite (`ADR-040`)
- **CellularGenerationTest** (5 tests):
  - `testFromTelephony_FiveGTypes_ReturnFiveG`: Asserts `NETWORK_TYPE_NR`, `OVERRIDE_NETWORK_TYPE_NR_NSA`, and `OVERRIDE_NETWORK_TYPE_NR_ADVANCED` classify as `FIVE_G`.
  - `testFromTelephony_FourGTypes_ReturnFourG`: Asserts `NETWORK_TYPE_LTE` and `OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO` classify as `FOUR_G`.
  - `testFromTelephony_ThreeGTypes_ReturnThreeGOrLower`: Asserts legacy UMTS, HSDPA, EVDO, and EDGE classify as `THREE_G_OR_LOWER`.
  - `testFromTelephony_UnknownAndZero_ReturnUnknown`: Asserts indeterminate types default to `UNKNOWN`.
  - `testCellularGeneration_LabelFormatting_IsAccurate`: Verifies `shortLabel`, `detailLabel`, and `fullDisplayName` outputs across all generations.
- **ForegroundServiceFormattingTest** (2 tests):
  - `testFormatNotificationSpeedContent_DynamicCellularLabel`: Asserts notification title/body formatting accurately interpolates cellular generation short labels ("5G", "4G", "3G", "Cellular").
  - `testFormatNotificationSpeedContent_ZeroOrIdle`: Verifies zero/idle speed formatting with dynamic labels.
- **DynamicCellularLabelUiTest** (4 tests):
  - `testHeroCard_FormatsDynamicCellularLabel`: Tests hero card connection titles and technical descriptors across 5G, 4G, 3G, and Unknown.
  - `testQuickPresetCard_FormatsDynamicCellularLabel`: Tests quick preset description strings.
  - `testChunkStripLegend_FormatsDynamicCellularLabel`: Tests segmented chunk visualizer legend text ("5G Chunk", "4G Chunk", etc.).
  - `testSingleStreamBanner_FormatsDynamicCellularLabel`: Tests single-stream fallback banner strings.

### Dynamic Wi-Fi Generation & Telemetry Test Suite (`ADR-041`)
- **WifiGenerationTest** (7 tests):
  - `testFromStandard_Wifi7_ReturnsWifi7`: Verifies standard 8 maps to `WIFI_7` with 6 GHz and 2400 Mbps formatting.
  - `testFromStandard_Wifi6E_When6GHz_ReturnsWifi6E`: Verifies standard 6 with 6 GHz frequency maps to `WIFI_6E`.
  - `testFromStandard_Wifi6_When5GHzOr24GHz_ReturnsWifi6`: Verifies standard 6 with 5 GHz / 2.4 GHz maps to `WIFI_6`.
  - `testFromStandard_Wifi5_ReturnsWifi5`: Verifies standard 5 maps to `WIFI_5`.
  - `testFromStandard_Wifi4_ReturnsWifi4`: Verifies standard 4 maps to `WIFI_4`.
  - `testProgressiveFallback_WhenStandardUnknown_UsesBandOrGeneric`: Verifies progressive fallback to frequency band or generic "Wi-Fi".
  - `testRichHeroLabel_OmitsLinkSpeedWhenZero`: Verifies omitting link speed when non-positive.
- **DualNetworkManagerWifiDetailsTest** (3 tests):
  - `testWifiDetails_DefaultsToDisconnected`: Asserts disconnected defaults.
  - `testWifiDetails_UpdatesOnConnect`: Asserts state updates on Wi-Fi connection.
  - `testWifiDetails_ResetsOnDisconnect`: Asserts reset to `DISCONNECTED` on Wi-Fi loss.
- **DynamicWifiLabelUiTest** (4 tests):
  - `testHeroCard_FormatsDynamicWifiLabel`: Tests Hero card rich labels with link speed.
  - `testContextualBanner_FormatsDynamicWifiAndCellular`: Tests dual-channel banner strings.
  - `testChunkStripLegend_FormatsDynamicWifiAndCellular`: Tests chunk visualizer legends.
  - `testCompletionBanner_FormatsDynamicWifiAndCellular`: Tests celebratory completion banners.

### Dynamic User-Agent Strings & Phone Model Identity Test Suite (`ADR-046`)
- **UserAgentConfigTest** (7 tests):
  - `testConfig_DefaultsToNativeDevice`: Asserts default preset is `NATIVE` with empty custom string and no desktop fast-track.
  - `testConfig_ResolvesNativeUserAgentWithDeviceModel`: Verifies auto-detection of device model and Android OS version from hardware properties (`Build.MODEL` and `Build.VERSION.RELEASE`).
  - `testConfig_ResolvesBuiltInPresets_Pixel9_GalaxyS24_Desktop`: Tests curated preset strings for Pixel 9, Samsung Galaxy S24 Ultra (`SM-S928B`), and Desktop Chrome.
  - `testConfig_CustomPreset_UsesUserDefinedString`: Validates raw custom User-Agent strings.
  - `testConfig_CustomPreset_BlankStringFallsBackToNative`: Verifies graceful fallback to native device string when custom input is blank or whitespace.
  - `testConfig_DetectsDesktopModeFastTrack`: Asserts `isDesktopFastTrack()` returns true for `DESKTOP_CHROME` or custom strings containing "Windows NT".
  - `testConfig_FindPresetById`: Tests ID lookup with case-insensitive resilience and fallback to `NATIVE`.
- **UserAgentPreferencesTest** (4 tests):
  - `testPreferences_DefaultsToNativePreset`: Asserts SharedPreferences initializes with `NATIVE` and empty custom string.
  - `testPreferences_PersistAndReadPreset`: Verifies updating and reading preset ID across instances.
  - `testPreferences_PersistAndReadCustomUserAgent`: Verifies updating and reading custom UA string.
  - `testPreferences_ResetToDefault`: Tests one-tap reset restoring `NATIVE` preset and clearing custom strings.
- **EngineDynamicUserAgentTest** (6 tests):
  - `testEngine_DefaultsToNativeUserAgentConfig`: Verifies `DualDownloaderEngine` defaults to `NATIVE` configuration.
  - `testEngine_AppliesConfiguredUserAgentHeaderToRequest`: Tests applying custom and preset User-Agent headers to OkHttp requests.
  - `testEngine_DesktopPreset_FastTracksToDesktopModeDirectly`: Asserts Desktop Chrome fast-tracks directly to desktop headers with `Sec-CH-UA` client hints.
  - `testEngine_SaveAndReadMetadata_PreservesUserAgentOnResume`: Verifies session resumption invariance: stored User-Agent in `.dualboost_meta` is strictly preserved upon resuming downloads.
  - `testRepository_ExposesUserAgentConfigStateFlowAndUpdates`: Tests `DownloadRepository` state flow exposure, updates, and engine synchronization.
  - `testDownloadState_Downloading_ExposesCustomUserAgentFlag`: Verifies `isCustomUserAgent` flag exposure on download states.
- **UserAgentUiTest** (3 tests):
  - `testUserAgentUi_PresetLabelsAndDescriptions`: Verifies all 5 presets feature intuitive human-readable display names and technical descriptions.
  - `testUserAgentUi_FormatSubtitle`: Tests subtitle formatting across all presets including empty and filled custom modes.
  - `testUserAgentUi_FormatsCustomBadgeText`: Verifies badge label formatting (`⚡ Pixel 9 UA`, `⚡ Galaxy S24 UA`, `⚡ Desktop UA`, `⚡ Custom UA`).

### Network Timeouts & Fast Failover Policy Test Suite (`ADR-047`)
- **TimeoutConfigTest** (7 tests):
  - `testTimeoutConfig_DefaultValues`: Validates default is `BALANCED` profile with 15s connect, 30s read, 5 retries, 300ms backoff.
  - `testTimeoutConfig_AggressiveProfileValues`: Validates `AGGRESSIVE` profile with 5s connect, 10s read, 3 retries, 150ms backoff.
  - `testTimeoutConfig_ResilientProfileValues`: Validates `RESILIENT` profile with 30s connect, 60s read, 8 retries, 1000ms backoff.
  - `testTimeoutConfig_CustomProfileOverrides`: Validates `CUSTOM` profile custom parameter overrides and effective getters.
  - `testTimeoutConfig_BadgeLabels`: Tests badge text (`⚡ Fast Failover`, `⚡ Resilient Mode`, `⚡ Custom Timeouts`, empty for `BALANCED`).
  - `testTimeoutConfig_ProfileFromIdCaseInsensitive`: Tests case-insensitive profile ID resolution and default fallback.
  - `testTimeoutConfig_EqualityAndCopy`: Tests data class contract and immutability.
- **TimeoutPreferencesTest** (4 tests):
  - `testTimeoutPreferences_DefaultConfig`: Tests clean preference initialization to `BALANCED`.
  - `testTimeoutPreferences_PersistAndReadProfile`: Tests saving and loading profile selections across instances.
  - `testTimeoutPreferences_PersistAndReadCustomConfig`: Tests saving and retrieving custom timeout limits.
  - `testTimeoutPreferences_ResetToDefault`: Tests 1-tap reset restoring `BALANCED` defaults.
- **EngineTimeoutFailoverTest** (4 tests):
  - `testNetworkManager_ApplyTimeoutConfig_ReconfiguresClients`: Validates dynamic OkHttp client rebuilding with updated connect/read timeouts.
  - `testEngine_AppliesTimeoutConfigToRetriesAndBackoff`: Validates engine chunk retry ceilings and backoff delay integration.
  - `testRepository_ExposesTimeoutConfigStateFlowAndUpdates`: Tests repository state flow exposure, preference updates, and engine synchronization.
  - `testEngine_DynamicTransportResumption_AppliesCurrentTimeoutToResumedDownload`: Validates dynamic transport resumption where resumed transfers adopt active timeout configurations.
- **TimeoutUiTest** (4 tests):
  - `testTimeoutUi_ProfileLabelsAndDescriptions`: Validates human-readable labels and technical descriptions for all profiles.
  - `testTimeoutUi_BadgeFormatting`: Validates badge label formatting for all 4 profiles.
  - `testTimeoutUi_SummaryTextFormatting`: Validates unified `TimeoutUiConstants.formatSummary()` output across profiles.
  - `testTimeoutUi_CustomStepperBounds`: Validates boundary constants and clamping logic for custom parameter steppers.

### Dynamic Cellular Network Type Detection & 5G Real-Time Handoff Test Suite (`ADR-048`)
- **CellularNetworkConfigTest** (6 tests):
  - `testCellularNetworkConfig_DefaultValues`: Validates default mode is `AUTO` with unknown detected generation.
  - `testCellularNetworkConfig_EffectiveGeneration_AutoMode`: Asserts `effectiveGeneration` passes through raw detected telephony generation under `AUTO` mode.
  - `testCellularNetworkConfig_EffectiveGeneration_Force5GMode`: Asserts `effectiveGeneration` returns `FIVE_G` regardless of detected generation when `FORCE_5G` is engaged.
  - `testCellularNetworkConfig_EffectiveGeneration_Force4GMode`: Asserts `effectiveGeneration` returns `FOUR_G` regardless of detected generation when `FORCE_4G` is engaged.
  - `testCellularNetworkConfig_BadgeLabels`: Verifies badge strings (`⚡ Force 5G`, `⚡ Force 4G`, empty for `AUTO`).
  - `testCellularNetworkConfig_EqualityAndCopy`: Validates data class immutability and copy contract.
- **CellularPreferencesTest** (3 tests):
  - `testCellularPreferences_DefaultIsAuto`: Tests preference initialization defaulting to `AUTO`.
  - `testCellularPreferences_PersistAndReadMode`: Tests saving and reloading cellular network modes across instances.
  - `testCellularPreferences_ResetToDefault`: Tests 1-tap reset restoring `AUTO` mode.
- **DynamicCellularDetectionTest** (6 tests):
  - `test5GNSA_WithOverrideNetworkType_ClassifiesAs5G`: Asserts `NETWORK_TYPE_LTE` with `OVERRIDE_NETWORK_TYPE_NR_NSA` / `NR_ADVANCED` resolves to `FIVE_G`.
  - `test5GSA_WithNetworkTypeNR_ClassifiesAs5G`: Asserts standalone `NETWORK_TYPE_NR` resolves to `FIVE_G`.
  - `test4GLTE_StandardAndCarrierAggregation_ClassifiesAs4G`: Asserts standard LTE and LTE-CA resolve to `FOUR_G`.
  - `testBandwidthHeuristic_HighBandwidthOnLTE_ClassifiesAs5G`: Asserts downstream link bandwidth $\ge 120_000\text{ Kbps}$ triggers 5G classification as a hardware heuristic.
  - `testDynamicHandoff_IdleSleepToActive5GWakeup`: Simulates modem idle sleep (reporting 4G) transitioning to active 5G on download start via `refreshCellularGeneration()`.
  - `testTelephonyManager_ReadPhoneStatePermission_DeclaredInManifest`: Asserts `android.permission.READ_PHONE_STATE` is explicitly declared in `AndroidManifest.xml`.
- **EngineCellularDynamicIntegrationTest** (3 tests):
  - `testRepository_ExposesCellularNetworkConfigAndUpdatesMode`: Verifies repository `cellularNetworkConfig` state flow, mode updates, and preference synchronization.
  - `testRepository_EffectiveCellularGeneration_RespectsModeOverrides`: Asserts `repository.cellularGeneration` dynamically honors user overrides (`FORCE_5G` / `FORCE_4G`).
  - `testRepository_StartDownload_TriggersRefreshCellularGeneration`: Asserts starting a download invokes `refreshCellularGeneration()` on the network provider to catch modem radio wake-up.
- **CellularUiTest** (3 tests):
  - `testCellularUi_ModeLabelsAndDescriptions`: Validates human-readable labels and descriptions for `AUTO`, `FORCE_5G`, and `FORCE_4G`.
  - `testCellularUi_HeroCardFormatting`: Validates title formatting with and without carrier names (`5G Cellular (Jio)` vs `4G LTE Cellular (Airtel)`).
  - `testCellularUi_BadgeFormatting`: Validates status badge formatting for active transfers.

### End-to-End Explicit State Machines Phase 2 Suite (`ADR-059`)
- **ExplicitStateMachinesPhase2Test** (11 tests):
  - `enginePhase_enforcesGuardMatrix`: Verifies `EnginePhase.canTransitionTo` prevents illegal direct transitions such as `DISCARDING -> EXECUTING`.
  - `probeStateMachine_transitionsCleanlyAcrossAllStages`: Verifies pure `ProbeStateMachine` stage progression and `ABORTED` cancellation handling.
  - `circuitBreaker_treatsHalfOpenPeerAsCapableAndEmitsCompositeSnapshot`: Verifies `HALF_OPEN` peer capability preservation (`isInterfaceCapable`) and composite `CircuitBreakerSnapshot` emissions.
  - `thermalPolicy_mapsSeverityAndWifiAvailabilityDeterministically`: Verifies `ThermalLevel` and `ThermalThrottlePolicy` scheduling rules across light, moderate, and severe heat states.
  - `resumeDecision_evaluatesValidatorsAndInvalidationReasons`: Verifies `ResumeDecision.evaluate` across `FreshStart`, `Resumable`, and all `ResumeInvalidationReason` branches.
  - `downloadModel_channelModeReadinessAndValidatorGuards`: Verifies `DownloadChannelMode`, `StreamReadiness`, and `DownloadStateValidator` transitions (`AutoRecovering -> ThreatDetected`, `Paused -> ThreatDetected`).
  - `networkAndServiceStateMachines_validateTransitions`: Verifies `NetworkListenerState`, `CellularKeepAliveState`, `NetworkTopology` banner flags, `ServiceExecutionPhase`, `ServiceLockState`, and `ServiceCommand`.
  - `queueStateMachines_validateStatusAndPhaseTransitions`: Verifies `QueueItemStatus.canTransitionTo` and `QueuePhase` invariants.
  - `mediaSniffer_reportsExplicitOutcomesAndHandlesConcurrentTraffic`: Verifies `SniffOutcome` classification and multi-threaded synchronization in `MediaSnifferEngine`.
  - `historyAndCredentialEnums_resolveAccurately`: Verifies `CredentialStorageMode` entries and `DownloadHistoryRepository.resolveFileStatus`.
  - `uiStateMachines_resolveClipboardSequentialBatteryDialogAndPrimaryCta`: Verifies `ClipboardSuggestionState`, `SequentialStreamMode`/`SequentialStreamUiState`, `BatteryGuidanceState`, `MainDialogState`, `PrimaryActionUiState`, and `SocialIntakeState.canTransitionTo`.
- **Upgraded Existing Suites** (+6 new tests across Phase 2 fixes):
  - `NotificationThrottlerTest` (2 tests): Switched from a local mock inner class to testing production `NotificationThrottler` directly.
  - `QueueCoordinatorTest` (+3 tests -> 6 tests total): Added empty-queue `Idle` verification, external `Paused` notification handling + `PAUSED` item resumption, and cold-start recovery of crashed `DOWNLOADING` queue entries.
  - `SocialIntakeViewModelTest` (+2 tests -> 5 tests total): Added `dismissSocialIntake()` in-flight coroutine cancellation test and `retrySocialResolution` recovery test.
  - `EngineThreatInterceptionTest` (+1 test -> 5 tests total): Added `desktopSpoofProbeHeadersThreat_blocksDownloadOnMaliciousMime` covering Stage 2 threat inspection.

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
1. `.\gradlew.bat test` passes with 0 failures (425 tests across 73 suites).
2. `.\gradlew.bat connectedAndroidTest` passes with 0 failures on connected Pixel hardware (13/13 UI tests).
3. `.\gradlew.bat assembleRelease` produces a valid R8-minified APK.
4. Lint checks pass without blocking errors: `.\gradlew.bat lint`.

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

