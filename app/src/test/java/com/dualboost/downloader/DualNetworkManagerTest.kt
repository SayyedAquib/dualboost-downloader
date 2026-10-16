package com.dualboost.downloader

import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class DualNetworkManagerTest {

    class DynamicNetworkProvider : NetworkProvider {
        val wifiFlow = MutableStateFlow(false)
        val cellFlow = MutableStateFlow(false)

        var wifiClientInstance: OkHttpClient? = null
        var cellularClientInstance: OkHttpClient? = null

        val defaultClientInstance: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        override val isWifiAvailable: StateFlow<Boolean> = wifiFlow
        override val isCellularAvailable: StateFlow<Boolean> = cellFlow

        override fun getWifiClient(): OkHttpClient? = wifiClientInstance
        override fun getCellularClient(): OkHttpClient? = cellularClientInstance
        override fun getDefaultClient(): OkHttpClient = defaultClientInstance

        fun setWifiConnected(connected: Boolean) {
            wifiFlow.value = connected
            wifiClientInstance = if (connected) defaultClientInstance else null
        }

        fun setCellularConnected(connected: Boolean) {
            cellFlow.value = connected
            cellularClientInstance = if (connected) defaultClientInstance else null
        }
    }

    @Test
    fun testNetworkProvider_BothDisconnectedInitially() {
        val provider = DynamicNetworkProvider()
        assertFalse("Wi-Fi should be unavailable initially", provider.isWifiAvailable.value)
        assertFalse("Cellular should be unavailable initially", provider.isCellularAvailable.value)
        assertNull("Wi-Fi client should be null when disconnected", provider.getWifiClient())
        assertNull("Cellular client should be null when disconnected", provider.getCellularClient())
        assertNotNull("Default client should always be available as fallback", provider.getDefaultClient())
    }

    @Test
    fun testNetworkProvider_WifiOnlyMode() {
        val provider = DynamicNetworkProvider()
        provider.setWifiConnected(true)

        assertTrue("Wi-Fi should be available", provider.isWifiAvailable.value)
        assertFalse("Cellular should still be unavailable", provider.isCellularAvailable.value)
        assertNotNull("Wi-Fi client should be non-null", provider.getWifiClient())
        assertNull("Cellular client should remain null", provider.getCellularClient())
    }

    @Test
    fun testNetworkProvider_CellularOnlyMode() {
        val provider = DynamicNetworkProvider()
        provider.setCellularConnected(true)

        assertFalse("Wi-Fi should be unavailable", provider.isWifiAvailable.value)
        assertTrue("Cellular should be available", provider.isCellularAvailable.value)
        assertNull("Wi-Fi client should be null", provider.getWifiClient())
        assertNotNull("Cellular client should be non-null", provider.getCellularClient())
    }

    @Test
    fun testNetworkProvider_DualChannelAggregatedMode() {
        val provider = DynamicNetworkProvider()
        provider.setWifiConnected(true)
        provider.setCellularConnected(true)

        assertTrue("Both Wi-Fi and Cellular must be active in dual mode", provider.isWifiAvailable.value && provider.isCellularAvailable.value)
        assertNotNull("Both socket clients must be accessible", provider.getWifiClient())
        assertNotNull("Both socket clients must be accessible", provider.getCellularClient())
    }

    @Test
    fun testNetworkProvider_DynamicDisconnectionMidStream() {
        val provider = DynamicNetworkProvider()
        provider.setWifiConnected(true)
        provider.setCellularConnected(true)

        // Wi-Fi disconnects (e.g. walked out of range)
        provider.setWifiConnected(false)

        assertFalse("Wi-Fi must reflect disconnect", provider.isWifiAvailable.value)
        assertTrue("Cellular must remain active", provider.isCellularAvailable.value)
        assertNull(provider.getWifiClient())
        assertNotNull(provider.getCellularClient())
    }
}

