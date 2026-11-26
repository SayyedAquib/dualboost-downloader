package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadChannelMode
import com.dualboost.downloader.data.DownloadHistoryItem
import com.dualboost.downloader.data.DownloadHistoryRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.DownloadStateValidator
import com.dualboost.downloader.data.NetworkInterfaceType
import com.dualboost.downloader.data.QueueItemStatus
import com.dualboost.downloader.data.QueuePhase
import com.dualboost.downloader.data.StreamReadiness
import com.dualboost.downloader.engine.CircuitBreakerAggregateState
import com.dualboost.downloader.engine.CircuitBreakerState
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.engine.DualInterfaceCircuitBreaker
import com.dualboost.downloader.engine.EnginePhase
import com.dualboost.downloader.engine.ProbeStage
import com.dualboost.downloader.engine.ProbeStateMachine
import com.dualboost.downloader.engine.ResumeDecision
import com.dualboost.downloader.engine.ResumeInvalidationReason
import com.dualboost.downloader.network.CellularKeepAliveState
import com.dualboost.downloader.network.NetworkListenerState
import com.dualboost.downloader.network.NetworkTopology
import com.dualboost.downloader.security.CredentialStorageMode
import com.dualboost.downloader.security.ThreatAssessment
import com.dualboost.downloader.security.ThreatSeverity
import com.dualboost.downloader.security.ThreatType
import com.dualboost.downloader.service.ServiceCommand
import com.dualboost.downloader.service.ServiceExecutionPhase
import com.dualboost.downloader.service.ServiceLockState
import com.dualboost.downloader.sniffer.MediaSnifferEngine
import com.dualboost.downloader.sniffer.SniffIgnoreReason
import com.dualboost.downloader.sniffer.SniffOutcome
import com.dualboost.downloader.social.MediaQuality
import com.dualboost.downloader.social.SocialIntakeState
import com.dualboost.downloader.social.SocialPlatform
import com.dualboost.downloader.thermal.ThermalLevel
import com.dualboost.downloader.thermal.ThermalStatus
import com.dualboost.downloader.thermal.ThermalThrottlePolicy
import com.dualboost.downloader.ui.BatteryGuidanceState
import com.dualboost.downloader.ui.ClipboardSuggestionState
import com.dualboost.downloader.ui.MainDialogState
import com.dualboost.downloader.ui.PrimaryActionUiState
import com.dualboost.downloader.ui.SequentialStreamMode
import com.dualboost.downloader.ui.SequentialStreamUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Exhaustive unit verification for Phase 2 explicit state machines across Engine,
 * Network, Thermal, Service, Queue, Sniffer, History, Security, Social, and Compose UI layers.
 */
class ExplicitStateMachinesPhase2Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // 1. EnginePhase & ProbeStateMachine
    // =========================================================================

    @Test
    fun enginePhase_enforcesGuardMatrix() {
        assertTrue(EnginePhase.IDLE.canTransitionTo(EnginePhase.EXECUTING))
        assertTrue(EnginePhase.EXECUTING.canTransitionTo(EnginePhase.PAUSED))
        assertTrue(EnginePhase.EXECUTING.canTransitionTo(EnginePhase.DISCARDING))
        assertTrue(EnginePhase.PAUSED.canTransitionTo(EnginePhase.EXECUTING))
        assertTrue(EnginePhase.DISCARDING.canTransitionTo(EnginePhase.IDLE))

        // DISCARDING must finish in IDLE before another download can start
        assertFalse(EnginePhase.DISCARDING.canTransitionTo(EnginePhase.EXECUTING))
        assertFalse(EnginePhase.DISCARDING.canTransitionTo(EnginePhase.PAUSED))
    }

    @Test
    fun probeStateMachine_transitionsCleanlyAcrossAllStages() {
        assertTrue(ProbeStage.RESOLVED_DUAL_STREAM.isTerminal)
        assertTrue(ProbeStage.FALLBACK_SINGLE_STREAM.isTerminal)
        assertTrue(ProbeStage.ABORTED.isTerminal)
        assertFalse(ProbeStage.INITIAL_HEAD.isTerminal)

        // Happy path from Stage 1a HEAD
        assertEquals(
            ProbeStage.RESOLVED_DUAL_STREAM,
            ProbeStateMachine.nextAfterHead(
                supportsRanges = true,
                contentLength = 50_000_000L,
                forceDesktop = false,
                isCloudSlicerActive = false,
                hasSlicerConfigured = true,
                isExecuting = true
            )
        )

        // Stage 1a -> Stage 1b when ranges unsupported or length missing
        assertEquals(
            ProbeStage.INITIAL_GET_RANGE,
            ProbeStateMachine.nextAfterHead(
                supportsRanges = false,
                contentLength = -1L,
                forceDesktop = false,
                isCloudSlicerActive = false,
                hasSlicerConfigured = true,
                isExecuting = true
            )
        )

        // Stage 1b -> Stage 2 DESKTOP_SPOOF -> Stage 3 CLOUD_SLICER -> Single Stream
        assertEquals(
            ProbeStage.DESKTOP_SPOOF,
            ProbeStateMachine.nextAfterGetRange(
                supportsRanges = false,
                contentLength = 10_000L,
                forceDesktop = false,
                isCloudSlicerActive = false,
                hasSlicerConfigured = true,
                isExecuting = true
            )
        )
        assertEquals(
            ProbeStage.CLOUD_SLICER,
            ProbeStateMachine.nextAfterDesktopSpoof(
                supportsRanges = false,
                contentLength = 10_000L,
                isCloudSlicerActive = false,
                hasSlicerConfigured = true,
                isExecuting = true
            )
        )
        assertEquals(
            ProbeStage.FALLBACK_SINGLE_STREAM,
            ProbeStateMachine.nextAfterCloudSlicer(
                supportsRanges = false,
                contentLength = 10_000L,
                isExecuting = true
            )
        )

        // Aborted mid-probe returns ABORTED terminal stage
        assertEquals(
            ProbeStage.ABORTED,
            ProbeStateMachine.nextAfterHead(
                supportsRanges = true,
                contentLength = 10_000L,
                forceDesktop = false,
                isCloudSlicerActive = false,
                hasSlicerConfigured = true,
                isExecuting = false
            )
        )

        // Terminal stages cannot move forward
        assertFalse(ProbeStage.RESOLVED_DUAL_STREAM.canTransitionTo(ProbeStage.DESKTOP_SPOOF))
        assertTrue(ProbeStage.INITIAL_HEAD.canTransitionTo(ProbeStage.DESKTOP_SPOOF))
    }

    // =========================================================================
    // 2. DualInterfaceCircuitBreaker Snapshot & HALF_OPEN Peer Capability
    // =========================================================================

    @Test
    fun circuitBreaker_treatsHalfOpenPeerAsCapableAndEmitsCompositeSnapshot() {
        val cb = DualInterfaceCircuitBreaker(failureThreshold = 1, maxRecoveryCycles = 3, cooldownBaseMs = 5000L)
        assertTrue(cb.snapshot.value.aggregateState is CircuitBreakerAggregateState.BothHealthy)

        // Trip Wi-Fi at t = 1_000
        cb.recordFailure(NetworkInterfaceType.WIFI, currentTimeMs = 1_000L)
        val snapFailover = cb.refreshSnapshot(isWifiAvailable = true, isCellularAvailable = true, currentTimeMs = 1_500L)
        assertEquals(CircuitBreakerState.TRIPPED, snapFailover.wifiState)
        assertEquals(CircuitBreakerState.HEALTHY, snapFailover.cellularState)
        assertTrue(snapFailover.aggregateState is CircuitBreakerAggregateState.FailoverActive)

        // At t = 6_500, Wi-Fi cooldown elapsed -> HALF_OPEN (capable of trial!)
        assertEquals(CircuitBreakerState.HALF_OPEN, cb.evaluateInterfaceOnly(NetworkInterfaceType.WIFI, currentTimeMs = 6_500L))
        assertTrue(cb.isInterfaceCapable(NetworkInterfaceType.WIFI, currentTimeMs = 6_500L))

        // Trip Cellular at t = 7_000 -> Cellular should see TRIPPED (not RECOVERING) because peer Wi-Fi is HALF_OPEN!
        cb.recordFailure(NetworkInterfaceType.CELLULAR, currentTimeMs = 7_000L)
        val snapHalfOpenPeer = cb.refreshSnapshot(isWifiAvailable = true, isCellularAvailable = true, currentTimeMs = 7_100L)
        assertEquals(CircuitBreakerState.HALF_OPEN, snapHalfOpenPeer.wifiState)
        assertEquals(CircuitBreakerState.TRIPPED, snapHalfOpenPeer.cellularState)
        assertTrue(snapHalfOpenPeer.aggregateState is CircuitBreakerAggregateState.FailoverActive)
    }

    // =========================================================================
    // 3. ThermalLevel & ThermalThrottlePolicy
    // =========================================================================

    @Test
    fun thermalPolicy_mapsSeverityAndWifiAvailabilityDeterministically() {
        assertEquals(ThermalLevel.NONE, ThermalLevel.fromCode(ThermalStatus.NONE))
        assertEquals(ThermalLevel.SHUTDOWN, ThermalLevel.fromCode(99))
        assertTrue(ThermalLevel.LIGHT.isCoolForAutoScale)
        assertFalse(ThermalLevel.MODERATE.isCoolForAutoScale)
        assertTrue(ThermalLevel.SEVERE.isSevereOrHigher)

        // Light -> Unrestricted
        val normal = ThermalThrottlePolicy.resolve(ThermalLevel.LIGHT, isWifiAvailable = true)
        assertEquals(ThermalThrottlePolicy.UNRESTRICTED, normal)
        assertTrue(normal.allowsAutoScale)
        assertTrue(normal.allowsCellularWorker(2))

        // Moderate -> Cap cellular to single stream
        val moderate = ThermalThrottlePolicy.resolve(ThermalLevel.MODERATE, isWifiAvailable = true)
        assertEquals(ThermalThrottlePolicy.CAP_CELLULAR_SINGLE, moderate)
        assertFalse(moderate.allowsAutoScale)
        assertTrue(moderate.allowsCellularWorker(0))
        assertFalse(moderate.allowsCellularWorker(1))

        // Severe with Wi-Fi -> Offload 100% cellular to Wi-Fi
        val severeWithWifi = ThermalThrottlePolicy.resolve(ThermalLevel.SEVERE, isWifiAvailable = true)
        assertEquals(ThermalThrottlePolicy.OFFLOAD_CELLULAR_TO_WIFI, severeWithWifi)
        assertFalse(severeWithWifi.allowsCellularWorker(0))

        // Severe without Wi-Fi -> Keep 1 cellular emergency worker alive
        val severeNoWifi = ThermalThrottlePolicy.resolve(ThermalLevel.CRITICAL, isWifiAvailable = false)
        assertEquals(ThermalThrottlePolicy.EMERGENCY_SINGLE_CELLULAR, severeNoWifi)
        assertTrue(severeNoWifi.allowsCellularWorker(0))
        assertFalse(severeNoWifi.allowsCellularWorker(1))
    }

    // =========================================================================
    // 4. ResumeDecision State Machine
    // =========================================================================

    @Test
    fun resumeDecision_evaluatesValidatorsAndInvalidationReasons() {
        val url = "https://cdn.example.com/archive.iso"
        val meta = DualDownloaderEngine.MetaInfo(
            url = url,
            totalLength = 1_000_000L,
            completedChunks = setOf(0, 1),
            etag = "\"abc-123\"",
            lastModified = "Mon, 01 Jan 2026 00:00:00 GMT"
        )

        assertEquals(
            ResumeDecision.FreshStart,
            ResumeDecision.evaluate(url, 1_000_000L, "\"abc-123\"", null, true, 1_000_000L, null)
        )

        val valid = ResumeDecision.evaluate(
            url = url,
            contentLength = 1_000_000L,
            etag = "\"abc-123\"",
            lastModified = "Mon, 01 Jan 2026 00:00:00 GMT",
            physicalFileExisted = true,
            physicalFileLength = 1_000_000L,
            existingMeta = meta
        )
        assertTrue(valid.canResume)
        assertTrue(valid is ResumeDecision.Resumable)

        val missingFile = ResumeDecision.evaluate(url, 1_000_000L, "\"abc-123\"", meta.lastModified, false, 0L, meta)
        assertEquals(ResumeInvalidationReason.PHYSICAL_FILE_MISSING, (missingFile as ResumeDecision.Invalidated).reason)

        val sizeMismatch = ResumeDecision.evaluate(url, 2_000_000L, "\"abc-123\"", meta.lastModified, true, 1_000_000L, meta)
        assertEquals(ResumeInvalidationReason.SIZE_MISMATCH, (sizeMismatch as ResumeDecision.Invalidated).reason)

        val urlMismatch = ResumeDecision.evaluate("https://cdn.example.com/other.iso", 1_000_000L, "\"abc-123\"", meta.lastModified, true, 1_000_000L, meta)
        assertEquals(ResumeInvalidationReason.URL_MISMATCH, (urlMismatch as ResumeDecision.Invalidated).reason)

        val etagChanged = ResumeDecision.evaluate(url, 1_000_000L, "\"xyz-999\"", meta.lastModified, true, 1_000_000L, meta)
        assertEquals(ResumeInvalidationReason.ETAG_CHANGED, (etagChanged as ResumeDecision.Invalidated).reason)

        val lastModDropped = ResumeDecision.evaluate(url, 1_000_000L, "\"abc-123\"", null, true, 1_000_000L, meta)
        assertEquals(ResumeInvalidationReason.LAST_MODIFIED_CHANGED, (lastModDropped as ResumeDecision.Invalidated).reason)
    }

    // =========================================================================
    // 5. DownloadChannelMode, StreamReadiness & DownloadStateValidator
    // =========================================================================

    @Test
    fun downloadModel_channelModeReadinessAndValidatorGuards() {
        assertEquals(DownloadChannelMode.DUAL_CHANNEL, DownloadChannelMode.resolve(true, 0L, 0L))
        assertEquals(DownloadChannelMode.SINGLE_STREAM_WIFI, DownloadChannelMode.resolve(false, 1024L, 0L))
        assertEquals(DownloadChannelMode.SINGLE_STREAM_CELLULAR, DownloadChannelMode.resolve(false, 0L, 2048L))

        assertEquals(StreamReadiness.DISABLED, StreamReadiness.resolve(isSequentialMode = false, isPlayable = false))
        assertEquals(StreamReadiness.BUFFERING, StreamReadiness.resolve(isSequentialMode = true, isPlayable = false))
        assertEquals(StreamReadiness.READY, StreamReadiness.resolve(isSequentialMode = true, isPlayable = true))

        val recovering = DownloadState.AutoRecovering("Waiting", retryAttempt = 1)
        val paused = DownloadState.Paused(url = "https://example.com/a.bin")
        val threat = DownloadState.ThreatDetected(
            threat = ThreatAssessment(true, ThreatType.CLOUD_FLAGGED_MALWARE, ThreatSeverity.CRITICAL, "Malware"),
            url = "https://example.com/a.bin",
            suggestedFilename = "a.bin"
        )
        assertTrue(recovering.isTransferInProgress)
        assertFalse(recovering.blocksConcurrentStart)
        assertTrue(threat.isTerminal)
        assertTrue(DownloadStateValidator.isValidTransition(recovering, threat))
        assertTrue(DownloadStateValidator.isValidTransition(paused, threat))
    }

    // =========================================================================
    // 6. Network & Service State Machines
    // =========================================================================

    @Test
    fun networkAndServiceStateMachines_validateTransitions() {
        assertTrue(NetworkListenerState.STOPPED.canTransitionTo(NetworkListenerState.LISTENING))
        assertFalse(NetworkListenerState.LISTENING.canTransitionTo(NetworkListenerState.LISTENING))

        assertTrue(CellularKeepAliveState.REQUESTING.isActiveOrPending)
        assertTrue(CellularKeepAliveState.ACTIVE.isActiveOrPending)
        assertFalse(CellularKeepAliveState.FAILED.isActiveOrPending)
        assertTrue(CellularKeepAliveState.IDLE.canTransitionTo(CellularKeepAliveState.REQUESTING))
        assertFalse(CellularKeepAliveState.IDLE.canTransitionTo(CellularKeepAliveState.ACTIVE))

        assertTrue(NetworkTopology.VPN_TUNNEL.showsVpnBanner)
        assertFalse(NetworkTopology.VPN_TUNNEL.showsRoamingBanner)
        assertTrue(NetworkTopology.ROAMING_SAFEGUARD.showsRoamingBanner)

        assertTrue(ServiceExecutionPhase.FOREGROUND_ACTIVE.holdsLocks)
        assertFalse(ServiceExecutionPhase.PAUSED.holdsLocks)
        assertTrue(ServiceExecutionPhase.FOREGROUND_ACTIVE.canTransitionTo(ServiceExecutionPhase.PAUSED))
        assertTrue(ServiceLockState.RELEASED.canTransitionTo(ServiceLockState.ACQUIRED))
        assertEquals(ServiceCommand.Unknown(null), ServiceCommand.fromIntent(null))
    }

    // =========================================================================
    // 7. QueueItemStatus, QueuePhase, MediaSniffer, Security, History
    // =========================================================================

    @Test
    fun queueStateMachines_validateStatusAndPhaseTransitions() {
        assertTrue(QueueItemStatus.QUEUED.isDispatchable)
        assertTrue(QueueItemStatus.PAUSED.isDispatchable)
        assertTrue(QueueItemStatus.COMPLETED.isTerminal)
        assertFalse(QueueItemStatus.COMPLETED.canTransitionTo(QueueItemStatus.DOWNLOADING))
        assertTrue(QueueItemStatus.COMPLETED.canTransitionTo(QueueItemStatus.QUEUED))

        val running = QueuePhase.Running("item-42")
        assertTrue(running.isRunning)
        assertEquals("item-42", running.currentActiveItemId)
        assertTrue(QueuePhase.Idle.canTransitionTo(running))
        assertFalse(QueuePhase.Idle.canTransitionTo(QueuePhase.Paused))
        assertTrue(running.canTransitionTo(QueuePhase.Paused))
    }

    @Test
    fun mediaSniffer_reportsExplicitOutcomesAndHandlesConcurrentTraffic() {
        val sniffer = MediaSnifferEngine()

        assertEquals(
            SniffOutcome.Ignored(SniffIgnoreReason.AD_OR_TRACKER),
            sniffer.inspectRequestWithOutcome("https://doubleclick.net/pixel.mp4")
        )
        assertEquals(
            SniffOutcome.Ignored(SniffIgnoreReason.STATIC_ASSET),
            sniffer.inspectRequestWithOutcome("https://cdn.site.com/styles/app.css")
        )
        assertEquals(
            SniffOutcome.Ignored(SniffIgnoreReason.UNSUPPORTED_FORMAT),
            sniffer.inspectRequestWithOutcome("https://api.site.com/v1/feed.dat")
        )

        val detected = sniffer.inspectRequestWithOutcome("https://cdn.site.com/video/movie.mp4")
        assertTrue(detected is SniffOutcome.Detected)
        assertEquals(
            SniffOutcome.Ignored(SniffIgnoreReason.DUPLICATE),
            sniffer.inspectRequestWithOutcome("https://cdn.site.com/video/movie.mp4")
        )

        sniffer.clear()
        val threads = 8
        val perThread = 25
        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        for (t in 0 until threads) {
            executor.submit {
                try {
                    for (i in 0 until perThread) {
                        sniffer.inspectRequestWithOutcome("https://cdn.site.com/stream_${t}_$i.mp4")
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals(threads * perThread, sniffer.detectedMedia.value.size)
    }

    @Test
    fun historyAndCredentialEnums_resolveAccurately() {
        assertEquals(3, CredentialStorageMode.entries.size)

        val dir = tempFolder.newFolder("hist_test")
        val existing = File(dir, "done.zip").apply { writeText("payload") }
        val repo = DownloadHistoryRepository(
            context = DummyTestContext(dir),
            historyFileOverride = File(dir, "history.json")
        )

        val presentItem = DownloadHistoryItem(fileName = "done.zip", filePath = existing.absolutePath, fileSize = 7L)
        val statusPresent = repo.resolveFileStatus(presentItem)
        assertTrue(statusPresent is DownloadHistoryRepository.HistoryFileStatus.LocalFile)

        val missingItem = DownloadHistoryItem(fileName = "gone.zip", filePath = File(dir, "gone.zip").absolutePath, fileSize = 100L)
        assertEquals(DownloadHistoryRepository.HistoryFileStatus.Missing, repo.resolveFileStatus(missingItem))
    }

    // =========================================================================
    // 8. UI & Social Explicit State Machines
    // =========================================================================

    @Test
    fun uiStateMachines_resolveClipboardSequentialBatteryDialogAndPrimaryCta() {
        // ClipboardSuggestionState
        assertFalse(ClipboardSuggestionState.Hidden.isVisible)
        assertTrue(ClipboardSuggestionState.TextAvailable.isVisible)
        assertTrue(ClipboardSuggestionState.UrlDetected("https://a.com/x.zip").isVisible)

        // SequentialStreamUiState
        val autoVideo = SequentialStreamUiState.resolve(SequentialStreamMode.AUTO, "https://cdn.com/clip.mp4")
        assertTrue(autoVideo.isAutoVideoUrl)
        assertTrue(autoVideo.isEffectiveSequential)
        assertFalse(autoVideo.isManualOverride)
        assertEquals("Video detected • Ready to watch while downloading", autoVideo.statusText)

        val forcedOffVideo = SequentialStreamUiState.resolve(SequentialStreamMode.FORCE_OFF, "https://cdn.com/clip.mp4")
        assertFalse(forcedOffVideo.isEffectiveSequential)
        assertTrue(forcedOffVideo.isManualOverride)

        // BatteryGuidanceState
        assertTrue(BatteryGuidanceState.ACTION_REQUIRED.shouldShowBanner)
        assertTrue(BatteryGuidanceState.ACTION_REQUIRED.canTransitionTo(BatteryGuidanceState.EXEMPTED))
        assertTrue(BatteryGuidanceState.ACTION_REQUIRED.canTransitionTo(BatteryGuidanceState.DISMISSED))
        assertFalse(BatteryGuidanceState.NOT_REQUIRED.canTransitionTo(BatteryGuidanceState.ACTION_REQUIRED))

        // MainDialogState
        val cancelDialog = MainDialogState.ConfirmCancelOrDiscard("movie.mp4", isPaused = false)
        assertFalse(cancelDialog.isPaused)
        assertNull((MainDialogState.None as? MainDialogState.ConfirmCancelOrDiscard))

        // PrimaryActionUiState (fixes URL edited while Paused showing wrong "Resume Download" label)
        val pausedUrl = "https://example.com/first.zip"
        val pausedState = DownloadState.Paused(url = pausedUrl)
        val resumeAction = PrimaryActionUiState.resolve(pausedState, pausedUrl)
        assertTrue(resumeAction is PrimaryActionUiState.ReadyToResume)
        assertEquals("Resume Download", resumeAction.label)
        assertTrue(resumeAction.isEnabled)

        val switchedUrlAction = PrimaryActionUiState.resolve(pausedState, "https://example.com/second.zip")
        assertTrue(switchedUrlAction is PrimaryActionUiState.ReadyToBoost)
        assertEquals("Boost Download", switchedUrlAction.label)
        assertTrue(switchedUrlAction.isEnabled)

        val probingAction = PrimaryActionUiState.resolve(DownloadState.Probing("Probing"), pausedUrl)
        assertFalse(probingAction.isEnabled)
        assertEquals("Probing Server...", probingAction.label)

        // SocialIntakeState transitions
        val ready = SocialIntakeState.Ready("https://x.com/i/status/1", SocialPlatform.TWITTER)
        val error = SocialIntakeState.Error("https://x.com/i/status/1", "Fail", SocialPlatform.TWITTER, MediaQuality.HD_720)
        assertTrue(ready.canTransitionTo(SocialIntakeState.Resolving("https://x.com/i/status/1", SocialPlatform.TWITTER)))
        assertTrue(error.canTransitionTo(SocialIntakeState.Resolving("https://x.com/i/status/1", SocialPlatform.TWITTER)))
        assertFalse(SocialIntakeState.Idle.canTransitionTo(error))
    }
}
