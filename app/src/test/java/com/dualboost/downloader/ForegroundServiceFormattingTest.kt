package com.dualboost.downloader

import com.dualboost.downloader.network.CellularGeneration
import com.dualboost.downloader.service.DownloadForegroundService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit verification for Foreground Service notification content formatting (ADR-040, ADR-041).
 */
class ForegroundServiceFormattingTest {

    @Test
    fun testFormatNotificationSpeedContent_AdaptsToCellularGeneration() {
        // FIVE_G
        val content5G = DownloadForegroundService.formatNotificationSpeedContent(
            wifiSpeedMBps = 12.0,
            cellularSpeedMBps = 8.5,
            totalSpeedMBps = 20.5,
            cellularGeneration = CellularGeneration.FIVE_G
        )
        assertEquals("Wi-Fi: 12.0 MB/s | 5G: 8.5 MB/s (Total: 20.5 MB/s)", content5G)

        // FOUR_G
        val content4G = DownloadForegroundService.formatNotificationSpeedContent(
            wifiSpeedMBps = 12.0,
            cellularSpeedMBps = 4.2,
            totalSpeedMBps = 16.2,
            cellularGeneration = CellularGeneration.FOUR_G
        )
        assertEquals("Wi-Fi: 12.0 MB/s | 4G: 4.2 MB/s (Total: 16.2 MB/s)", content4G)

        // THREE_G_OR_LOWER
        val content3G = DownloadForegroundService.formatNotificationSpeedContent(
            wifiSpeedMBps = 12.0,
            cellularSpeedMBps = 1.1,
            totalSpeedMBps = 13.1,
            cellularGeneration = CellularGeneration.THREE_G_OR_LOWER
        )
        assertEquals("Wi-Fi: 12.0 MB/s | 3G: 1.1 MB/s (Total: 13.1 MB/s)", content3G)

        // UNKNOWN
        val contentUnknown = DownloadForegroundService.formatNotificationSpeedContent(
            wifiSpeedMBps = 12.0,
            cellularSpeedMBps = 0.0,
            totalSpeedMBps = 12.0,
            cellularGeneration = CellularGeneration.UNKNOWN
        )
        assertEquals("Wi-Fi: 12.0 MB/s | Cellular: 0.0 MB/s (Total: 12.0 MB/s)", contentUnknown)
    }

    @Test
    fun testFormatNotificationSpeedContent_AdaptsToWifiGeneration() {
        // Wi-Fi 7 + 5G
        val contentWifi7 = DownloadForegroundService.formatNotificationSpeedContent(
            wifiSpeedMBps = 55.0,
            cellularSpeedMBps = 40.0,
            totalSpeedMBps = 95.0,
            cellularGeneration = CellularGeneration.FIVE_G,
            wifiLabel = "Wi-Fi 7"
        )
        assertEquals("Wi-Fi 7: 55.0 MB/s | 5G: 40.0 MB/s (Total: 95.0 MB/s)", contentWifi7)

        // Wi-Fi 6 + 4G
        val contentWifi6 = DownloadForegroundService.formatNotificationSpeedContent(
            wifiSpeedMBps = 25.0,
            cellularSpeedMBps = 10.0,
            totalSpeedMBps = 35.0,
            cellularGeneration = CellularGeneration.FOUR_G,
            wifiLabel = "Wi-Fi 6"
        )
        assertEquals("Wi-Fi 6: 25.0 MB/s | 4G: 10.0 MB/s (Total: 35.0 MB/s)", contentWifi6)
    }
}
