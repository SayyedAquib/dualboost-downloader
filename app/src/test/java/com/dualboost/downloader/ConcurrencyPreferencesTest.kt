package com.dualboost.downloader

import com.dualboost.downloader.data.ConcurrencyConfig
import com.dualboost.downloader.data.ConcurrencyPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConcurrencyPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: DummyTestContext

    @Before
    fun setUp() {
        val baseDir = tempFolder.newFolder("concurrency_prefs_test")
        context = DummyTestContext(baseDir)
    }

    @Test
    fun testConfig_ClampsWorkersBetweenOneAndEight() {
        // Lower bound clamp
        val configLow = ConcurrencyConfig(wifiWorkers = 0, cellularWorkers = -2)
        assertEquals(1, configLow.wifiWorkers)
        assertEquals(1, configLow.cellularWorkers)
        assertEquals(2, configLow.totalWorkers)

        // Upper bound clamp
        val configHigh = ConcurrencyConfig(wifiWorkers = 16, cellularWorkers = 10)
        assertEquals(8, configHigh.wifiWorkers)
        assertEquals(8, configHigh.cellularWorkers)
        assertEquals(16, configHigh.totalWorkers)

        // Valid configuration
        val configValid = ConcurrencyConfig(wifiWorkers = 4, cellularWorkers = 3, isAutoScaleEnabled = true)
        assertEquals(4, configValid.wifiWorkers)
        assertEquals(3, configValid.cellularWorkers)
        assertTrue(configValid.isAutoScaleEnabled)
        assertEquals(7, configValid.totalWorkers)
    }

    @Test
    fun testPreferences_DefaultsToTwoWifiAndTwoCellular() {
        val prefs = ConcurrencyPreferences(context)
        val config = prefs.getConcurrencyConfig()

        assertEquals(2, config.wifiWorkers)
        assertEquals(2, config.cellularWorkers)
        assertTrue(config.isAutoScaleEnabled)
        assertEquals(4, config.totalWorkers)
    }

    @Test
    fun testPreferences_SavesAndRestoresWorkerCounts() {
        val prefs = ConcurrencyPreferences(context)

        prefs.setWifiWorkers(5)
        prefs.setCellularWorkers(3)
        prefs.setAutoScaleEnabled(true)

        val config = prefs.getConcurrencyConfig()
        assertEquals(5, config.wifiWorkers)
        assertEquals(3, config.cellularWorkers)
        assertTrue(config.isAutoScaleEnabled)
        assertEquals(8, config.totalWorkers)
    }

    @Test
    fun testPreferences_ResetToDefault_RestoresTwoAndTwo() {
        val prefs = ConcurrencyPreferences(context)

        prefs.setWifiWorkers(6)
        prefs.setCellularWorkers(4)
        prefs.setAutoScaleEnabled(true)

        prefs.resetToDefault()

        val config = prefs.getConcurrencyConfig()
        assertEquals(2, config.wifiWorkers)
        assertEquals(2, config.cellularWorkers)
        assertTrue(config.isAutoScaleEnabled)
    }
}
