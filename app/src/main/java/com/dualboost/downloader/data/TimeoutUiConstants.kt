package com.dualboost.downloader.data

/**
 * UI bounds and formatting constants for Network Timeouts & Failover (ADR-047).
 */
object TimeoutUiConstants {
    const val MIN_CONNECT_TIMEOUT_SEC = 3
    const val MAX_CONNECT_TIMEOUT_SEC = 60
    const val MIN_READ_TIMEOUT_SEC = 5
    const val MAX_READ_TIMEOUT_SEC = 120
    const val MIN_MAX_RETRIES = 1
    const val MAX_MAX_RETRIES = 15
    const val MIN_RETRY_BACKOFF_MS = 50L
    const val MAX_RETRY_BACKOFF_MS = 3000L

    fun formatSummary(config: TimeoutConfig): String {
        return "Connect: ${config.effectiveConnectTimeoutSeconds}s • Read: ${config.effectiveReadTimeoutSeconds}s • ${config.effectiveMaxChunkRetries} retries • ${config.effectiveRetryBackoffMs}ms backoff"
    }
}
