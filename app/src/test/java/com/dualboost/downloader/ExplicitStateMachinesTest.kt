package com.dualboost.downloader

import com.dualboost.downloader.data.ChunkStatus
import com.dualboost.downloader.data.DownloadChunk
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.DownloadStateValidator
import com.dualboost.downloader.data.NetworkInterfaceType
import com.dualboost.downloader.data.QueuePhase
import com.dualboost.downloader.engine.CircuitBreakerState
import com.dualboost.downloader.engine.DualInterfaceCircuitBreaker
import com.dualboost.downloader.engine.EnginePhase
import com.dualboost.downloader.engine.ProbeResult
import com.dualboost.downloader.engine.ProbeStage
import com.dualboost.downloader.network.NetworkTopology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive verification suite for all 6 explicit state machines in DualBoost Downloader:
 * 1. EnginePhase (Engine Lifecycle)
 * 2. DualInterfaceCircuitBreaker (Fault tolerance & failover)
 * 3. ProbeStateMachine (3-stage capability discovery)
 * 4. DownloadStateValidator & ChunkStatus (Core transfer lifecycle)
 * 5. QueuePhase (Sequential multi-download orchestration)
 * 6. NetworkTopology (Aggregated interface topology)
 */
class ExplicitStateMachinesTest {

    // =========================================================================
    // 1. EnginePhase Lifecycle Tests
    // =========================================================================

    @Test
    fun testEnginePhase_PropertiesAndInvariants() {
        // IDLE
        assertEquals(false, EnginePhase.IDLE.isActive)
        assertEquals(false, EnginePhase.IDLE.isInterrupted)

        // EXECUTING
        assertEquals(true, EnginePhase.EXECUTING.isActive)
        assertEquals(false, EnginePhase.EXECUTING.isInterrupted)

        // PAUSED
        assertEquals(false, EnginePhase.PAUSED.isActive)
        assertEquals(true, EnginePhase.PAUSED.isInterrupted)

        // DISCARDING
        assertEquals(false, EnginePhase.DISCARDING.isActive)
        assertEquals(true, EnginePhase.DISCARDING.isInterrupted)
    }

    // =========================================================================
    // 2. DualInterfaceCircuitBreaker Tests
    // =========================================================================

    @Test
    fun testCircuitBreaker_StartsHealthy() {
        val cb = DualInterfaceCircuitBreaker(failureThreshold = 2, maxRecoveryCycles = 3)
        assertEquals(false, cb.isExhausted())
        assertEquals(CircuitBreakerState.HEALTHY, cb.evaluateState(NetworkInterfaceType.WIFI, isOtherHealthyAndAvailable = true))
        assertEquals(CircuitBreakerState.HEALTHY, cb.evaluateState(NetworkInterfaceType.CELLULAR, isOtherHealthyAndAvailable = true))
    }

    @Test
    fun testCircuitBreaker_TripsSingleInterfaceWhenPeerHealthy() {
        val cb = DualInterfaceCircuitBreaker(failureThreshold = 2, maxRecoveryCycles = 3)

        // 1 failure: still healthy
        assertEquals(CircuitBreakerState.HEALTHY, cb.recordFailure(NetworkInterfaceType.WIFI))
        assertEquals(CircuitBreakerState.HEALTHY, cb.evaluateState(NetworkInterfaceType.WIFI, isOtherHealthyAndAvailable = true))

        // 2 failures: trips into TRIPPED when other interface is healthy
        assertEquals(CircuitBreakerState.TRIPPED, cb.recordFailure(NetworkInterfaceType.WIFI))
        assertEquals(CircuitBreakerState.TRIPPED, cb.evaluateState(NetworkInterfaceType.WIFI, isOtherHealthyAndAvailable = true))

        // Cellular remains healthy
        assertEquals(CircuitBreakerState.HEALTHY, cb.evaluateState(NetworkInterfaceType.CELLULAR, isOtherHealthyAndAvailable = true))

        // Success on Wi-Fi resets it back to HEALTHY
        cb.recordSuccess(NetworkInterfaceType.WIFI)
        assertEquals(CircuitBreakerState.HEALTHY, cb.evaluateState(NetworkInterfaceType.WIFI, isOtherHealthyAndAvailable = true))
    }

    @Test
    fun testCircuitBreaker_EntersRecoveringWhenBothTripped() {
        val cb = DualInterfaceCircuitBreaker(failureThreshold = 2, maxRecoveryCycles = 3)

        cb.recordFailure(NetworkInterfaceType.WIFI)
        cb.recordFailure(NetworkInterfaceType.WIFI)

        cb.recordFailure(NetworkInterfaceType.CELLULAR)
        cb.recordFailure(NetworkInterfaceType.CELLULAR)

        // Both are tripped, so other is not healthy
        assertEquals(CircuitBreakerState.RECOVERING, cb.evaluateState(NetworkInterfaceType.WIFI, isOtherHealthyAndAvailable = false))
        assertEquals(CircuitBreakerState.RECOVERING, cb.evaluateState(NetworkInterfaceType.CELLULAR, isOtherHealthyAndAvailable = false))

        // Cycle 1 recovery
        val cycle1 = cb.attemptCoordinatedRecovery()
        assertEquals(1, cycle1)
        assertEquals(1000L, cb.calculateRecoveryBackoffMs())

        // Cycle 2 recovery
        val cycle2 = cb.attemptCoordinatedRecovery()
        assertEquals(2, cycle2)
        assertEquals(2000L, cb.calculateRecoveryBackoffMs())

        // Cycle 3 recovery
        val cycle3 = cb.attemptCoordinatedRecovery()
        assertEquals(3, cycle3)
        assertEquals(4000L, cb.calculateRecoveryBackoffMs())

        // Cycle 4 exceeds maxRecoveryCycles (3) -> enters EXHAUSTED
        val cycle4 = cb.attemptCoordinatedRecovery()
        assertNull(cycle4)
        assertTrue(cb.isExhausted())
        assertEquals(CircuitBreakerState.EXHAUSTED, cb.evaluateState(NetworkInterfaceType.WIFI, isOtherHealthyAndAvailable = false))
    }

    @Test
    fun testCircuitBreaker_ChunkFailureEscalation() {
        val cb = DualInterfaceCircuitBreaker()
        assertFalse(cb.recordChunkFailure(chunkIndex = 0, maxRetries = 3))
        assertFalse(cb.recordChunkFailure(chunkIndex = 0, maxRetries = 3))
        assertTrue(cb.recordChunkFailure(chunkIndex = 0, maxRetries = 3)) // 3rd attempt reaches max
        assertTrue(cb.isExhausted())
    }

    // =========================================================================
    // 3. ProbeStateMachine Tests
    // =========================================================================

    @Test
    fun testProbeStateMachine_StagesAndResults() {
        val dualResult = ProbeResult(
            stage = ProbeStage.RESOLVED_DUAL_STREAM,
            supportsRanges = true,
            contentLength = 100_000_000L,
            etag = "\"etag-123\"",
            lastModified = "Wed, 21 Oct 2026 07:28:00 GMT",
            fileName = "archive.zip",
            isDesktopSpoofed = false,
            isCloudSlicerActive = false,
            effectiveUrl = "https://example.com/archive.zip",
            effectiveUserAgent = "Pixel9-UA"
        )

        assertEquals(ProbeStage.RESOLVED_DUAL_STREAM, dualResult.stage)
        assertTrue(dualResult.supportsRanges)
        assertEquals(100_000_000L, dualResult.contentLength)
        assertEquals("archive.zip", dualResult.fileName)

        val fallbackResult = ProbeResult(
            stage = ProbeStage.FALLBACK_SINGLE_STREAM,
            supportsRanges = false,
            contentLength = 50_000_000L,
            etag = null,
            lastModified = null,
            fileName = "stream.bin",
            isDesktopSpoofed = false,
            isCloudSlicerActive = false,
            effectiveUrl = "https://example.com/stream.bin",
            effectiveUserAgent = "Pixel9-UA"
        )
        assertEquals(ProbeStage.FALLBACK_SINGLE_STREAM, fallbackResult.stage)
        assertFalse(fallbackResult.supportsRanges)
    }

    // =========================================================================
    // 4. DownloadStateValidator & ChunkStatus Transition Tests
    // =========================================================================

    @Test
    fun testDownloadStateValidator_ValidTransitions() {
        // Idle transitions
        assertTrue(DownloadStateValidator.isValidTransition(DownloadState.Idle, DownloadState.Probing("Connecting...")))
        assertTrue(DownloadStateValidator.isValidTransition(DownloadState.Idle, DownloadState.Paused()))

        // Probing transitions
        val probing = DownloadState.Probing("Testing...")
        assertTrue(DownloadStateValidator.isValidTransition(probing, DownloadState.Downloading("f", 100L, 0L, 0L, 0L, 0.0, 0.0, 0.0, 0f, true)))
        assertTrue(DownloadStateValidator.isValidTransition(probing, DownloadState.Paused()))
        assertTrue(DownloadStateValidator.isValidTransition(probing, DownloadState.Failed("Error")))
        assertTrue(DownloadStateValidator.isValidTransition(probing, DownloadState.Idle))

        // Downloading transitions
        val downloading = DownloadState.Downloading("f", 100L, 50L, 50L, 0L, 5.0, 0.0, 5.0, 0.5f, true)
        assertTrue(DownloadStateValidator.isValidTransition(downloading, DownloadState.Completed("f", "/path", 100L, 10L)))
        assertTrue(DownloadStateValidator.isValidTransition(downloading, DownloadState.Paused()))
        assertTrue(DownloadStateValidator.isValidTransition(downloading, DownloadState.AutoRecovering("Reconnecting", 1)))
        assertTrue(DownloadStateValidator.isValidTransition(downloading, DownloadState.Failed("Dropped")))
        assertTrue(DownloadStateValidator.isValidTransition(downloading, DownloadState.Idle))

        // Same state instance transitions (e.g. progress updates)
        val downloading2 = downloading.copy(downloadedBytes = 80L)
        assertTrue(DownloadStateValidator.isValidTransition(downloading, downloading2))
    }

    @Test
    fun testChunkStatus_TransitionsAndGuards() {
        val chunk = DownloadChunk(index = 0, startByte = 0, endByte = 1000)
        assertEquals(ChunkStatus.PENDING, chunk.status)

        // PENDING -> DOWNLOADING (valid)
        assertTrue(chunk.transitionTo(ChunkStatus.DOWNLOADING, NetworkInterfaceType.WIFI))
        assertEquals(ChunkStatus.DOWNLOADING, chunk.status)
        assertEquals(NetworkInterfaceType.WIFI, chunk.assignedNetwork)

        // DOWNLOADING -> COMPLETED (valid)
        assertTrue(chunk.transitionTo(ChunkStatus.COMPLETED))
        assertEquals(ChunkStatus.COMPLETED, chunk.status)

        // COMPLETED -> DOWNLOADING (invalid without returning to PENDING first)
        assertFalse(chunk.transitionTo(ChunkStatus.DOWNLOADING))
        assertEquals(ChunkStatus.COMPLETED, chunk.status)

        // COMPLETED -> PENDING (valid rollback on connection failure)
        assertTrue(chunk.transitionTo(ChunkStatus.PENDING, NetworkInterfaceType.DEFAULT))
        assertEquals(ChunkStatus.PENDING, chunk.status)
        assertEquals(NetworkInterfaceType.DEFAULT, chunk.assignedNetwork)
    }

    // =========================================================================
    // 5. QueuePhase Tests
    // =========================================================================

    @Test
    fun testQueuePhase_States() {
        val idle = QueuePhase.Idle
        assertTrue(idle is QueuePhase.Idle)

        val running = QueuePhase.Running("item-123")
        assertEquals("item-123", running.activeItemId)

        val runningEmpty = QueuePhase.Running()
        assertNull(runningEmpty.activeItemId)

        val paused = QueuePhase.Paused
        assertTrue(paused is QueuePhase.Paused)
    }

    // =========================================================================
    // 6. NetworkTopology Tests
    // =========================================================================

    @Test
    fun testNetworkTopology_ResolutionPermutations() {
        // VPN overrides everything
        assertEquals(
            NetworkTopology.VPN_TUNNEL,
            NetworkTopology.resolve(
                isWifiAvailable = true,
                isCellularAvailable = true,
                isRoamingActive = false,
                isVpnActive = true,
                isModemStandbyDetected = false
            )
        )

        // DualBoost Dual-Channel (Wi-Fi + Cellular)
        assertEquals(
            NetworkTopology.DUAL_CHANNEL,
            NetworkTopology.resolve(
                isWifiAvailable = true,
                isCellularAvailable = true,
                isRoamingActive = false,
                isVpnActive = false,
                isModemStandbyDetected = false
            )
        )
        assertTrue(NetworkTopology.DUAL_CHANNEL.isDualChannelAvailable)

        // Dual connected but cellular is roaming -> Roaming safeguard
        assertEquals(
            NetworkTopology.ROAMING_SAFEGUARD,
            NetworkTopology.resolve(
                isWifiAvailable = true,
                isCellularAvailable = true,
                isRoamingActive = true,
                isVpnActive = false,
                isModemStandbyDetected = false
            )
        )
        assertFalse(NetworkTopology.ROAMING_SAFEGUARD.isDualChannelAvailable)

        // Wi-Fi only
        assertEquals(
            NetworkTopology.WIFI_ONLY,
            NetworkTopology.resolve(
                isWifiAvailable = true,
                isCellularAvailable = false,
                isRoamingActive = false,
                isVpnActive = false,
                isModemStandbyDetected = false
            )
        )

        // Wi-Fi connected but cellular modem in standby sleep
        assertEquals(
            NetworkTopology.MODEM_STANDBY,
            NetworkTopology.resolve(
                isWifiAvailable = true,
                isCellularAvailable = false,
                isRoamingActive = false,
                isVpnActive = false,
                isModemStandbyDetected = true
            )
        )

        // Cellular only
        assertEquals(
            NetworkTopology.CELLULAR_ONLY,
            NetworkTopology.resolve(
                isWifiAvailable = false,
                isCellularAvailable = true,
                isRoamingActive = false,
                isVpnActive = false,
                isModemStandbyDetected = false
            )
        )

        // Disconnected
        assertEquals(
            NetworkTopology.DISCONNECTED,
            NetworkTopology.resolve(
                isWifiAvailable = false,
                isCellularAvailable = false,
                isRoamingActive = false,
                isVpnActive = false,
                isModemStandbyDetected = false
            )
        )
    }
}
