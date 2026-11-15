package com.dualboost.downloader

import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.network.WifiDetails
import com.dualboost.downloader.network.WifiGeneration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualNetworkManagerWifiDetailsTest {

    class MockNetworkProvider : NetworkProvider {
        val wifiFlow = MutableStateFlow(false)
        val cellFlow = MutableStateFlow(false)
        val wifiDetailsFlow = MutableStateFlow(WifiDetails.DISCONNECTED)

        override val isWifiAvailable: StateFlow<Boolean> = wifiFlow
        override val isCellularAvailable: StateFlow<Boolean> = cellFlow
        override val wifiDetails: StateFlow<WifiDetails> = wifiDetailsFlow

        override fun getWifiClient() = null
        override fun getCellularClient() = null
        override fun getDefaultClient() = okhttp3.OkHttpClient()

        fun onWifiConnected(details: WifiDetails) {
            wifiFlow.value = true
            wifiDetailsFlow.value = details
        }

        fun onWifiDisconnected() {
            wifiFlow.value = false
            wifiDetailsFlow.value = WifiDetails.DISCONNECTED
        }
    }

    @Test
    fun testWifiDetails_DefaultsToDisconnected() {
        val provider = MockNetworkProvider()
        assertFalse(provider.isWifiAvailable.value)
        assertEquals(WifiDetails.DISCONNECTED, provider.wifiDetails.value)
        assertEquals(WifiGeneration.UNKNOWN, provider.wifiDetails.value.generation)
        assertEquals("Wi-Fi", provider.wifiDetails.value.shortLabel)
    }

    @Test
    fun testWifiDetails_UpdatesOnConnect() {
        val provider = MockNetworkProvider()
        val wifi7Details = WifiDetails.fromWifiInfo(standard = 8, frequencyMhz = 6000, linkSpeedMbps = 2400)
        provider.onWifiConnected(wifi7Details)

        assertTrue(provider.isWifiAvailable.value)
        assertEquals(WifiGeneration.WIFI_7, provider.wifiDetails.value.generation)
        assertEquals("Wi-Fi 7", provider.wifiDetails.value.shortLabel)
        assertEquals("Wi-Fi 7 (6 GHz • 2400 Mbps)", provider.wifiDetails.value.richHeroLabel)
    }

    @Test
    fun testWifiDetails_ResetsOnDisconnect() {
        val provider = MockNetworkProvider()
        val wifi6Details = WifiDetails.fromWifiInfo(standard = 6, frequencyMhz = 5200, linkSpeedMbps = 1200)
        provider.onWifiConnected(wifi6Details)
        assertEquals(WifiGeneration.WIFI_6, provider.wifiDetails.value.generation)

        provider.onWifiDisconnected()
        assertFalse(provider.isWifiAvailable.value)
        assertEquals(WifiDetails.DISCONNECTED, provider.wifiDetails.value)
    }
}
