package com.dualboost.downloader

import com.dualboost.downloader.data.TimeoutConfig
import com.dualboost.downloader.data.TimeoutProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimeoutConfigTest {

    @Test
    fun testConfig_DefaultsToBalancedProfile() {
        val config = TimeoutConfig()
        assertEquals(TimeoutProfile.BALANCED, config.profile)
        assertEquals(15, config.effectiveConnectTimeoutSeconds)
        assertEquals(30, config.effectiveReadTimeoutSeconds)
        assertEquals(5, config.effectiveMaxChunkRetries)
        assertEquals(300L, config.effectiveRetryBackoffMs)
        assertEquals("", config.getBadgeLabel())
    }

    @Test
    fun testConfig_AggressiveProfileValues() {
        val config = TimeoutConfig(profile = TimeoutProfile.AGGRESSIVE)
        assertEquals(TimeoutProfile.AGGRESSIVE, config.profile)
        assertEquals(5, config.effectiveConnectTimeoutSeconds)
        assertEquals(10, config.effectiveReadTimeoutSeconds)
        assertEquals(3, config.effectiveMaxChunkRetries)
        assertEquals(150L, config.effectiveRetryBackoffMs)
        assertEquals("⚡ Fast Failover", config.getBadgeLabel())
    }

    @Test
    fun testConfig_ResilientProfileValues() {
        val config = TimeoutConfig(profile = TimeoutProfile.RESILIENT)
        assertEquals(TimeoutProfile.RESILIENT, config.profile)
        assertEquals(30, config.effectiveConnectTimeoutSeconds)
        assertEquals(60, config.effectiveReadTimeoutSeconds)
        assertEquals(8, config.effectiveMaxChunkRetries)
        assertEquals(1000L, config.effectiveRetryBackoffMs)
        assertEquals("⚡ Resilient Mode", config.getBadgeLabel())
    }

    @Test
    fun testConfig_CustomProfileValues_RespectsOverrides() {
        val config = TimeoutConfig(
            profile = TimeoutProfile.CUSTOM,
            customConnectTimeoutSeconds = 8,
            customReadTimeoutSeconds = 20,
            customMaxChunkRetries = 4,
            customRetryBackoffMs = 250L
        )
        assertEquals(TimeoutProfile.CUSTOM, config.profile)
        assertEquals(8, config.effectiveConnectTimeoutSeconds)
        assertEquals(20, config.effectiveReadTimeoutSeconds)
        assertEquals(4, config.effectiveMaxChunkRetries)
        assertEquals(250L, config.effectiveRetryBackoffMs)
        assertEquals("⚡ Custom Timeouts", config.getBadgeLabel())
    }

    @Test
    fun testConfig_FindProfileById_CaseInsensitive() {
        assertEquals(TimeoutProfile.BALANCED, TimeoutProfile.fromId("balanced"))
        assertEquals(TimeoutProfile.AGGRESSIVE, TimeoutProfile.fromId("aggressive"))
        assertEquals(TimeoutProfile.RESILIENT, TimeoutProfile.fromId("resilient"))
        assertEquals(TimeoutProfile.CUSTOM, TimeoutProfile.fromId("custom"))
        // Case-insensitivity
        assertEquals(TimeoutProfile.AGGRESSIVE, TimeoutProfile.fromId("Aggressive"))
        // Unknown defaults to BALANCED
        assertEquals(TimeoutProfile.BALANCED, TimeoutProfile.fromId("non_existent_profile"))
    }
}
