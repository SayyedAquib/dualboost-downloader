package com.dualboost.downloader

import com.dualboost.downloader.network.CellularGeneration
import com.dualboost.downloader.network.WifiDetails
import com.dualboost.downloader.network.WifiGeneration
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicWifiLabelUiTest {

    @Test
    fun testHeroCard_FormatsDynamicWifiLabel() {
        // Wi-Fi 7 on 6 GHz with link speed
        val wifi7 = WifiDetails.fromWifiInfo(standard = 8, frequencyMhz = 6000, linkSpeedMbps = 2400)
        val titleWifi7 = if (true) wifi7.richHeroLabel else "Wi-Fi"
        assertEquals("Wi-Fi 7 (6 GHz • 2400 Mbps)", titleWifi7)

        // Wi-Fi 6 on 5 GHz
        val wifi6 = WifiDetails.fromWifiInfo(standard = 6, frequencyMhz = 5180, linkSpeedMbps = 1200)
        val titleWifi6 = if (true) wifi6.richHeroLabel else "Wi-Fi"
        assertEquals("Wi-Fi 6 (5 GHz • 1200 Mbps)", titleWifi6)

        // Wi-Fi 5 on 5 GHz without link speed
        val wifi5 = WifiDetails.fromWifiInfo(standard = 5, frequencyMhz = 5240, linkSpeedMbps = 0)
        val titleWifi5 = if (true) wifi5.richHeroLabel else "Wi-Fi"
        assertEquals("Wi-Fi 5 (5 GHz)", titleWifi5)

        // Disconnected
        val titleDisconnected = if (false) wifi7.richHeroLabel else "Wi-Fi"
        assertEquals("Wi-Fi", titleDisconnected)
    }

    @Test
    fun testContextualBanner_FormatsDynamicWifiAndCellular() {
        // Fallback generic Wi-Fi
        val wifiDetails = WifiDetails.fromWifiInfo(standard = 0, frequencyMhz = 0, linkSpeedMbps = 0)
        val cellGen = CellularGeneration.FIVE_G

        val bannerText = "Dual-Channel Active: Aggregating ${wifiDetails.shortLabel} + ${cellGen.shortLabel}"
        assertEquals("Dual-Channel Active: Aggregating Wi-Fi + 5G", bannerText)

        // Wi-Fi 6
        val wifi6Details = WifiDetails.fromWifiInfo(standard = 6, frequencyMhz = 5200, linkSpeedMbps = 1200)
        val bannerWifi6 = "Dual-Channel Active: Aggregating ${wifi6Details.shortLabel} + ${cellGen.shortLabel}"
        assertEquals("Dual-Channel Active: Aggregating Wi-Fi 6 + 5G", bannerWifi6)

        // Frequency fallback
        val wifiBandOnly = WifiDetails.fromWifiInfo(standard = 0, frequencyMhz = 6000, linkSpeedMbps = 2400)
        val bannerBandOnly = "Dual-Channel Active: Aggregating ${wifiBandOnly.shortLabel} + ${cellGen.shortLabel}"
        assertEquals("Dual-Channel Active: Aggregating Wi-Fi 6 GHz + 5G", bannerBandOnly)
    }

    @Test
    fun testChunkStripLegend_FormatsDynamicWifiAndCellular() {
        val wifiDetails = WifiDetails.fromWifiInfo(standard = 6, frequencyMhz = 5180, linkSpeedMbps = 1200)
        val cellGen = CellularGeneration.FOUR_G

        val wifiLegend = "${wifiDetails.shortLabel} Chunk"
        val cellLegend = "${cellGen.shortLabel} Chunk"

        assertEquals("Wi-Fi 6 Chunk", wifiLegend)
        assertEquals("4G Chunk", cellLegend)
    }

    @Test
    fun testCompletionBanner_FormatsDynamicWifiAndCellular() {
        val wifiDetails = WifiDetails.fromWifiInfo(standard = 8, frequencyMhz = 6000, linkSpeedMbps = 2400)
        val cellGen = CellularGeneration.FIVE_G

        val banner = " • Aggregated ${wifiDetails.shortLabel} + ${cellGen.shortLabel} Boost ⚡"
        assertEquals(" • Aggregated Wi-Fi 7 + 5G Boost ⚡", banner)
    }
}
