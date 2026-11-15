package com.dualboost.downloader

import com.dualboost.downloader.network.WifiDetails
import com.dualboost.downloader.network.WifiGeneration
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiGenerationTest {

    @Test
    fun testFromStandard_Wifi7_ReturnsWifi7() {
        val details = WifiDetails.fromWifiInfo(standard = 8, frequencyMhz = 6000, linkSpeedMbps = 2400)
        assertEquals(WifiGeneration.WIFI_7, details.generation)
        assertEquals("Wi-Fi 7", details.shortLabel)
        assertEquals("Wi-Fi 7 (6 GHz)", details.detailLabel)
        assertEquals("Wi-Fi 7 (6 GHz • 2400 Mbps)", details.richHeroLabel)
    }

    @Test
    fun testFromStandard_Wifi6E_When6GHz_ReturnsWifi6E() {
        val details = WifiDetails.fromWifiInfo(standard = 6, frequencyMhz = 6125, linkSpeedMbps = 1200)
        assertEquals(WifiGeneration.WIFI_6E, details.generation)
        assertEquals("Wi-Fi 6E", details.shortLabel)
        assertEquals("Wi-Fi 6E (6 GHz)", details.detailLabel)
        assertEquals("Wi-Fi 6E (6 GHz • 1200 Mbps)", details.richHeroLabel)
    }

    @Test
    fun testFromStandard_Wifi6_When5GHzOr24GHz_ReturnsWifi6() {
        val details5G = WifiDetails.fromWifiInfo(standard = 6, frequencyMhz = 5240, linkSpeedMbps = 1200)
        assertEquals(WifiGeneration.WIFI_6, details5G.generation)
        assertEquals("Wi-Fi 6", details5G.shortLabel)
        assertEquals("Wi-Fi 6 (5 GHz)", details5G.detailLabel)
        assertEquals("Wi-Fi 6 (5 GHz • 1200 Mbps)", details5G.richHeroLabel)

        val details24G = WifiDetails.fromWifiInfo(standard = 6, frequencyMhz = 2437, linkSpeedMbps = 286)
        assertEquals(WifiGeneration.WIFI_6, details24G.generation)
        assertEquals("Wi-Fi 6", details24G.shortLabel)
        assertEquals("Wi-Fi 6 (2.4 GHz)", details24G.detailLabel)
        assertEquals("Wi-Fi 6 (2.4 GHz • 286 Mbps)", details24G.richHeroLabel)
    }

    @Test
    fun testFromStandard_Wifi5_ReturnsWifi5() {
        val details = WifiDetails.fromWifiInfo(standard = 5, frequencyMhz = 5180, linkSpeedMbps = 866)
        assertEquals(WifiGeneration.WIFI_5, details.generation)
        assertEquals("Wi-Fi 5", details.shortLabel)
        assertEquals("Wi-Fi 5 (5 GHz)", details.detailLabel)
        assertEquals("Wi-Fi 5 (5 GHz • 866 Mbps)", details.richHeroLabel)
    }

    @Test
    fun testFromStandard_Wifi4_ReturnsWifi4() {
        val details = WifiDetails.fromWifiInfo(standard = 4, frequencyMhz = 2412, linkSpeedMbps = 144)
        assertEquals(WifiGeneration.WIFI_4, details.generation)
        assertEquals("Wi-Fi 4", details.shortLabel)
        assertEquals("Wi-Fi 4 (2.4 GHz)", details.detailLabel)
        assertEquals("Wi-Fi 4 (2.4 GHz • 144 Mbps)", details.richHeroLabel)
    }

    @Test
    fun testProgressiveFallback_WhenStandardUnknown_UsesBandOrGeneric() {
        // Unknown standard, 5 GHz frequency
        val detailsBandOnly = WifiDetails.fromWifiInfo(standard = 0, frequencyMhz = 5200, linkSpeedMbps = 300)
        assertEquals(WifiGeneration.UNKNOWN, detailsBandOnly.generation)
        assertEquals("Wi-Fi 5 GHz", detailsBandOnly.shortLabel)
        assertEquals("Wi-Fi (5 GHz)", detailsBandOnly.detailLabel)
        assertEquals("Wi-Fi (5 GHz • 300 Mbps)", detailsBandOnly.richHeroLabel)

        // Completely unknown or disconnected
        val detailsUnknown = WifiDetails.fromWifiInfo(standard = 0, frequencyMhz = 0, linkSpeedMbps = 0)
        assertEquals(WifiGeneration.UNKNOWN, detailsUnknown.generation)
        assertEquals("Wi-Fi", detailsUnknown.shortLabel)
        assertEquals("Wi-Fi", detailsUnknown.detailLabel)
        assertEquals("Wi-Fi", detailsUnknown.richHeroLabel)
    }

    @Test
    fun testRichHeroLabel_OmitsLinkSpeedWhenZero() {
        val detailsNoSpeed = WifiDetails.fromWifiInfo(standard = 6, frequencyMhz = 5180, linkSpeedMbps = 0)
        assertEquals("Wi-Fi 6 (5 GHz)", detailsNoSpeed.richHeroLabel)
    }
}
