package com.dualboost.downloader

import com.dualboost.downloader.data.TimeoutConfig
import com.dualboost.downloader.data.TimeoutPreferences
import com.dualboost.downloader.data.TimeoutProfile
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TimeoutPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: DummyTestContext
    private lateinit var baseDir: File

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("timeout_prefs_test")
        dummyContext = DummyTestContext(baseDir)
    }

    @Test
    fun testPreferences_DefaultsToBalanced() {
        val prefs = TimeoutPreferences(dummyContext)
        val config = prefs.getTimeoutConfig()

        assertEquals(TimeoutProfile.BALANCED, config.profile)
        assertEquals(15, config.effectiveConnectTimeoutSeconds)
        assertEquals(30, config.effectiveReadTimeoutSeconds)
        assertEquals(5, config.effectiveMaxChunkRetries)
        assertEquals(300L, config.effectiveRetryBackoffMs)
    }

    @Test
    fun testPreferences_PersistAndReadProfile() {
        val prefs1 = TimeoutPreferences(dummyContext)
        prefs1.setProfile(TimeoutProfile.AGGRESSIVE)

        // Read back from a fresh instance
        val prefs2 = TimeoutPreferences(dummyContext)
        val config = prefs2.getTimeoutConfig()

        assertEquals(TimeoutProfile.AGGRESSIVE, config.profile)
        assertEquals(5, config.effectiveConnectTimeoutSeconds)
        assertEquals(10, config.effectiveReadTimeoutSeconds)
        assertEquals(3, config.effectiveMaxChunkRetries)
        assertEquals(150L, config.effectiveRetryBackoffMs)
    }

    @Test
    fun testPreferences_PersistAndReadCustomValues() {
        val prefs1 = TimeoutPreferences(dummyContext)
        prefs1.setCustomConfig(
            connectSec = 8,
            readSec = 20,
            maxRetries = 4,
            retryBackoffMs = 250L
        )

        val prefs2 = TimeoutPreferences(dummyContext)
        val config = prefs2.getTimeoutConfig()

        assertEquals(TimeoutProfile.CUSTOM, config.profile)
        assertEquals(8, config.effectiveConnectTimeoutSeconds)
        assertEquals(20, config.effectiveReadTimeoutSeconds)
        assertEquals(4, config.effectiveMaxChunkRetries)
        assertEquals(250L, config.effectiveRetryBackoffMs)
    }

    @Test
    fun testPreferences_ResetToDefault() {
        val prefs = TimeoutPreferences(dummyContext)
        prefs.setProfile(TimeoutProfile.RESILIENT)
        assertEquals(TimeoutProfile.RESILIENT, prefs.getTimeoutConfig().profile)

        prefs.resetToDefault()
        val resetConfig = prefs.getTimeoutConfig()

        assertEquals(TimeoutProfile.BALANCED, resetConfig.profile)
        assertEquals(15, resetConfig.effectiveConnectTimeoutSeconds)
        assertEquals(30, resetConfig.effectiveReadTimeoutSeconds)
        assertEquals(5, resetConfig.effectiveMaxChunkRetries)
        assertEquals(300L, resetConfig.effectiveRetryBackoffMs)
    }
}
