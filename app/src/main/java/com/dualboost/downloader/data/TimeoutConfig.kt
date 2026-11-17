package com.dualboost.downloader.data

/**
 * Supported Network Timeout and Failover profiles (ADR-047).
 */
enum class TimeoutProfile(
    val id: String,
    val displayName: String,
    val description: String,
    val connectTimeoutSeconds: Int,
    val readTimeoutSeconds: Int,
    val maxChunkRetries: Int,
    val retryBackoffMs: Long
) {
    AGGRESSIVE(
        id = "aggressive",
        displayName = "Fast Failover",
        description = "Quickly aborts dead sockets and shifts traffic (5s connect • 10s read • 3 retries)",
        connectTimeoutSeconds = 5,
        readTimeoutSeconds = 10,
        maxChunkRetries = 3,
        retryBackoffMs = 150L
    ),
    BALANCED(
        id = "balanced",
        displayName = "Balanced (Default)",
        description = "Optimized for home broadband and stable 5G (15s connect • 30s read • 5 retries)",
        connectTimeoutSeconds = 15,
        readTimeoutSeconds = 30,
        maxChunkRetries = 5,
        retryBackoffMs = 300L
    ),
    RESILIENT(
        id = "resilient",
        displayName = "Resilient",
        description = "Tolerates high latency, hotel Wi-Fi & satellite links (30s connect • 60s read • 8 retries)",
        connectTimeoutSeconds = 30,
        readTimeoutSeconds = 60,
        maxChunkRetries = 8,
        retryBackoffMs = 1000L
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom",
        description = "User-configured connect, read, retry, and backoff limits",
        connectTimeoutSeconds = 15,
        readTimeoutSeconds = 30,
        maxChunkRetries = 5,
        retryBackoffMs = 300L
    );

    companion object {
        fun fromId(id: String): TimeoutProfile {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BALANCED
        }
    }
}

/**
 * Domain model representing network timeout, retry ceiling, and backoff configuration (ADR-047).
 */
data class TimeoutConfig(
    val profile: TimeoutProfile = TimeoutProfile.BALANCED,
    val customConnectTimeoutSeconds: Int = 15,
    val customReadTimeoutSeconds: Int = 30,
    val customMaxChunkRetries: Int = 5,
    val customRetryBackoffMs: Long = 300L
) {
    val effectiveConnectTimeoutSeconds: Int
        get() = if (profile == TimeoutProfile.CUSTOM) customConnectTimeoutSeconds else profile.connectTimeoutSeconds

    val effectiveReadTimeoutSeconds: Int
        get() = if (profile == TimeoutProfile.CUSTOM) customReadTimeoutSeconds else profile.readTimeoutSeconds

    val effectiveMaxChunkRetries: Int
        get() = if (profile == TimeoutProfile.CUSTOM) customMaxChunkRetries else profile.maxChunkRetries

    val effectiveRetryBackoffMs: Long
        get() = if (profile == TimeoutProfile.CUSTOM) customRetryBackoffMs else profile.retryBackoffMs

    fun getBadgeLabel(): String {
        return when (profile) {
            TimeoutProfile.AGGRESSIVE -> "⚡ Fast Failover"
            TimeoutProfile.RESILIENT -> "⚡ Resilient Mode"
            TimeoutProfile.CUSTOM -> "⚡ Custom Timeouts"
            TimeoutProfile.BALANCED -> ""
        }
    }

    companion object {
        val DEFAULT = TimeoutConfig()
    }
}

