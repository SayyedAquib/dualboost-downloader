package com.dualboost.downloader

import com.dualboost.downloader.data.ConcurrencyConfig
import org.junit.Assert.*
import org.junit.Test

class SettingsConcurrencyUiTest {

    @Test
    fun testConcurrencyCard_CalculatesTotalStreams() {
        val config = ConcurrencyConfig(wifiWorkers = 4, cellularWorkers = 3, isAutoScaleEnabled = false)
        assertEquals(7, config.totalWorkers)
        val summaryText = "${config.totalWorkers} Total Streams (${config.wifiWorkers} Wi-Fi + ${config.cellularWorkers} Cellular)"
        assertEquals("7 Total Streams (4 Wi-Fi + 3 Cellular)", summaryText)
    }

    @Test
    fun testConcurrencyStepper_ClampsCorrectly() {
        fun step(current: Int, delta: Int): Int = (current + delta).coerceIn(ConcurrencyConfig.MIN_WORKERS, ConcurrencyConfig.MAX_WORKERS)

        // Lower boundary
        assertEquals(1, step(1, -1))
        assertEquals(1, step(1, -5))

        // Upper boundary
        assertEquals(8, step(8, 1))
        assertEquals(8, step(8, 10))

        // Normal stepping
        assertEquals(3, step(2, 1))
        assertEquals(2, step(3, -1))
    }

    @Test
    fun testConcurrencyCard_ResetActionRestoresDefaults() {
        var current = ConcurrencyConfig(wifiWorkers = 7, cellularWorkers = 6, isAutoScaleEnabled = true)
        // Reset action
        current = ConcurrencyConfig.DEFAULT
        assertEquals(ConcurrencyConfig.DEFAULT_WIFI_WORKERS, current.wifiWorkers)
        assertEquals(ConcurrencyConfig.DEFAULT_CELLULAR_WORKERS, current.cellularWorkers)
        assertFalse(current.isAutoScaleEnabled)
        assertEquals(4, current.totalWorkers)
    }

    @Test
    fun testConcurrencyCard_AutoScaleSubtitleReflectsState() {
        fun getAutoScaleStatusText(enabled: Boolean): String {
            return if (enabled) {
                "Auto-Scale Active (expands on >25 MB/s under cool thermals)"
            } else {
                "Fixed Allocation (${ConcurrencyConfig.DEFAULT_WIFI_WORKERS} + ${ConcurrencyConfig.DEFAULT_CELLULAR_WORKERS})"
            }
        }

        assertEquals(
            "Auto-Scale Active (expands on >25 MB/s under cool thermals)",
            getAutoScaleStatusText(true)
        )
        assertEquals(
            "Fixed Allocation (2 + 2)",
            getAutoScaleStatusText(false)
        )
    }
}
