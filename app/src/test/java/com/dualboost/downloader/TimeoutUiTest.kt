package com.dualboost.downloader

import com.dualboost.downloader.data.TimeoutConfig
import com.dualboost.downloader.data.TimeoutProfile
import com.dualboost.downloader.data.TimeoutUiConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI verification tests for Network Timeouts and Fast Failover (ADR-047 Slice 4).
 */
class TimeoutUiTest {

    @Test
    fun testTimeoutUi_ProfileLabelsAndDescriptions() {
        assertEquals("Fast Failover", TimeoutProfile.AGGRESSIVE.displayName)
        assertEquals("Balanced (Default)", TimeoutProfile.BALANCED.displayName)
        assertEquals("Resilient", TimeoutProfile.RESILIENT.displayName)
        assertEquals("Custom", TimeoutProfile.CUSTOM.displayName)

        assertTrue(TimeoutProfile.AGGRESSIVE.description.contains("5s connect"))
        assertTrue(TimeoutProfile.BALANCED.description.contains("15s connect"))
        assertTrue(TimeoutProfile.RESILIENT.description.contains("30s connect"))
    }

    @Test
    fun testTimeoutUi_BadgeFormatting() {
        val aggressive = TimeoutConfig(profile = TimeoutProfile.AGGRESSIVE)
        val resilient = TimeoutConfig(profile = TimeoutProfile.RESILIENT)
        val custom = TimeoutConfig(profile = TimeoutProfile.CUSTOM)
        val balanced = TimeoutConfig(profile = TimeoutProfile.BALANCED)

        assertEquals("⚡ Fast Failover", aggressive.getBadgeLabel())
        assertEquals("⚡ Resilient Mode", resilient.getBadgeLabel())
        assertEquals("⚡ Custom Timeouts", custom.getBadgeLabel())
        assertEquals("", balanced.getBadgeLabel())
    }

    @Test
    fun testTimeoutUi_SummaryTextFormatting() {
        val balanced = TimeoutConfig(profile = TimeoutProfile.BALANCED)
        assertEquals("Connect: 15s • Read: 30s • 5 retries • 300ms backoff", TimeoutUiConstants.formatSummary(balanced))

        val aggressive = TimeoutConfig(profile = TimeoutProfile.AGGRESSIVE)
        assertEquals("Connect: 5s • Read: 10s • 3 retries • 150ms backoff", TimeoutUiConstants.formatSummary(aggressive))

        val resilient = TimeoutConfig(profile = TimeoutProfile.RESILIENT)
        assertEquals("Connect: 30s • Read: 60s • 8 retries • 1000ms backoff", TimeoutUiConstants.formatSummary(resilient))

        val custom = TimeoutConfig(
            profile = TimeoutProfile.CUSTOM,
            customConnectTimeoutSeconds = 20,
            customReadTimeoutSeconds = 45,
            customMaxChunkRetries = 6,
            customRetryBackoffMs = 500L
        )
        assertEquals("Connect: 20s • Read: 45s • 6 retries • 500ms backoff", TimeoutUiConstants.formatSummary(custom))
    }

    @Test
    fun testTimeoutUi_CustomStepperBounds() {
        assertEquals(3, TimeoutUiConstants.MIN_CONNECT_TIMEOUT_SEC)
        assertEquals(60, TimeoutUiConstants.MAX_CONNECT_TIMEOUT_SEC)
        assertEquals(5, TimeoutUiConstants.MIN_READ_TIMEOUT_SEC)
        assertEquals(120, TimeoutUiConstants.MAX_READ_TIMEOUT_SEC)
        assertEquals(1, TimeoutUiConstants.MIN_MAX_RETRIES)
        assertEquals(15, TimeoutUiConstants.MAX_MAX_RETRIES)
        assertEquals(50L, TimeoutUiConstants.MIN_RETRY_BACKOFF_MS)
        assertEquals(3000L, TimeoutUiConstants.MAX_RETRY_BACKOFF_MS)

        // Stepper clamping logic verification
        val clampedConnectLow = 1.coerceIn(TimeoutUiConstants.MIN_CONNECT_TIMEOUT_SEC, TimeoutUiConstants.MAX_CONNECT_TIMEOUT_SEC)
        val clampedConnectHigh = 100.coerceIn(TimeoutUiConstants.MIN_CONNECT_TIMEOUT_SEC, TimeoutUiConstants.MAX_CONNECT_TIMEOUT_SEC)
        assertEquals(3, clampedConnectLow)
        assertEquals(60, clampedConnectHigh)

        val clampedReadLow = 2.coerceIn(TimeoutUiConstants.MIN_READ_TIMEOUT_SEC, TimeoutUiConstants.MAX_READ_TIMEOUT_SEC)
        val clampedReadHigh = 200.coerceIn(TimeoutUiConstants.MIN_READ_TIMEOUT_SEC, TimeoutUiConstants.MAX_READ_TIMEOUT_SEC)
        assertEquals(5, clampedReadLow)
        assertEquals(120, clampedReadHigh)

        val clampedRetriesLow = 0.coerceIn(TimeoutUiConstants.MIN_MAX_RETRIES, TimeoutUiConstants.MAX_MAX_RETRIES)
        val clampedRetriesHigh = 20.coerceIn(TimeoutUiConstants.MIN_MAX_RETRIES, TimeoutUiConstants.MAX_MAX_RETRIES)
        assertEquals(1, clampedRetriesLow)
        assertEquals(15, clampedRetriesHigh)
    }
}
