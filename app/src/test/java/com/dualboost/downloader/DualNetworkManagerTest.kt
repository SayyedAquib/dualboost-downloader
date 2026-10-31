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
        val roamingFlow = MutableStateFlow(false)
        val vpnFlow = MutableStateFlow(false)
        val carrierFlow = MutableStateFlow<String?>(null)
        val modemStandbyFlow = MutableStateFlow(false)
        val generationFlow = MutableStateFlow(com.dualboost.downloader.network.CellularGeneration.FOUR_G)
        val keepAliveFlow = MutableStateFlow(false)

        var wifiClientInstance: OkHttpClient? = null
        var cellularClientInstance: OkHttpClient? = null

        val defaultClientInstance: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        override val isWifiAvailable: StateFlow<Boolean> = wifiFlow
        override val isCellularAvailable: StateFlow<Boolean> = cellFlow
        override val isRoamingActive: StateFlow<Boolean> = roamingFlow
        override val isVpnActive: StateFlow<Boolean> = vpnFlow
        override val cellularCarrierName: StateFlow<String?> = carrierFlow
        override val isModemStandbyDetected: StateFlow<Boolean> = modemStandbyFlow
        override val cellularGeneration: StateFlow<com.dualboost.downloader.network.CellularGeneration> = generationFlow
        override val isCellularKeepAliveActive: StateFlow<Boolean> = keepAliveFlow

        override fun acquireCellularKeepAlive() {
            keepAliveFlow.value = true
        }

        override fun releaseCellularKeepAlive() {
            keepAliveFlow.value = false
        }

        override fun getWifiClient(): OkHttpClient? =
            if (vpnFlow.value) defaultClientInstance else wifiClientInstance

        override fun getCellularClient(): OkHttpClient? {
            if (roamingFlow.value) return null
            if (vpnFlow.value) return defaultClientInstance
            return cellularClientInstance
        }

        override fun getDefaultClient(): OkHttpClient = defaultClientInstance

        fun setCellularGeneration(gen: com.dualboost.downloader.network.CellularGeneration) {
            generationFlow.value = gen
        }

        fun setWifiConnected(connected: Boolean) {
            wifiFlow.value = connected
            wifiClientInstance = if (connected) OkHttpClient.Builder().build() else null
        }

        fun setCellularConnected(connected: Boolean) {
            cellFlow.value = connected
            cellularClientInstance = if (connected) OkHttpClient.Builder().build() else null
        }

        fun setRoaming(roaming: Boolean) {
            roamingFlow.value = roaming
        }

        fun setVpn(vpn: Boolean) {
            vpnFlow.value = vpn
        }

        fun setCarrierName(name: String?) {
            carrierFlow.value = name
        }

        fun setModemStandby(standby: Boolean) {
            modemStandbyFlow.value = standby
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

    @Test
    fun testNetworkProvider_RoamingGuard_BlocksCellularClientAndEmitsRoamingState() {
        val provider = DynamicNetworkProvider()
        provider.setWifiConnected(true)
        provider.setCellularConnected(true)

        // Precondition: Both available, roaming is false
        assertFalse("Roaming should be inactive by default", provider.isRoamingActive.value)
        assertNotNull("Cellular client must be available when not roaming", provider.getCellularClient())

        // Action: Roaming detected
        provider.setRoaming(true)

        // Verification: Roaming is active, cellular client is cut off to protect user from charges
        assertTrue("Roaming must be reported as active", provider.isRoamingActive.value)
        assertNull("Cellular client MUST be null during roaming to prevent bill shock", provider.getCellularClient())
        assertNotNull("Wi-Fi client must continue operating smoothly during roaming", provider.getWifiClient())

        // Action: Return to home network (not roaming)
        provider.setRoaming(false)
        assertFalse("Roaming must be reported as inactive", provider.isRoamingActive.value)
        assertNotNull("Cellular client must be restored when returning to home network", provider.getCellularClient())
    }

    @Test
    fun testNetworkProvider_VpnActive_RoutesToDefaultClientAndEmitsVpnState() {
        val provider = DynamicNetworkProvider()
        provider.setWifiConnected(true)
        provider.setCellularConnected(true)

        // Precondition: Normal non-VPN dual connection
        assertFalse("VPN should be inactive initially", provider.isVpnActive.value)
        val normalWifi = provider.getWifiClient()
        val normalCellular = provider.getCellularClient()
        val defaultClient = provider.getDefaultClient()
        assertNotNull(normalWifi)
        assertNotNull(normalCellular)
        assertNotSame("Physical Wi-Fi client should be distinct from default client when VPN off", defaultClient, normalWifi)
        assertNotSame("Physical Cellular client should be distinct from default client when VPN off", defaultClient, normalCellular)

        // Action: User enables VPN (WireGuard, Cloudflare WARP, AdGuard)
        provider.setVpn(true)

        // Verification: VPN is detected as active
        assertTrue("VPN must be reported as active", provider.isVpnActive.value)
        // Both interfaces must route through the default client (which encapsulates the VPN tunnel)
        assertSame("Wi-Fi worker must route through default VPN client", defaultClient, provider.getWifiClient())
        assertSame("Cellular worker must route through default VPN client", defaultClient, provider.getCellularClient())

        // Action: VPN turned off
        provider.setVpn(false)
        assertFalse("VPN should be reported as inactive", provider.isVpnActive.value)
        assertNotSame("Wi-Fi returns to interface-specific socket factory", defaultClient, provider.getWifiClient())
        assertNotSame("Cellular returns to interface-specific socket factory", defaultClient, provider.getCellularClient())
    }

    @Test
    fun testNetworkProvider_MultiSimCarrierAndModemStandby_Flows() {
        val provider = DynamicNetworkProvider()

        // Initially no carrier and no modem standby detected
        assertNull("Carrier name should be null initially", provider.cellularCarrierName.value)
        assertFalse("Modem standby should be false initially", provider.isModemStandbyDetected.value)

        // Action: Active data SIM carrier updated
        provider.setCarrierName("SIM 1: Jio 5G")
        assertEquals("SIM 1: Jio 5G", provider.cellularCarrierName.value)

        // Action: Modem standby triggered (Wi-Fi on, but carrier modem asleep)
        provider.setModemStandby(true)
        assertTrue("Modem standby should reflect true when modem sleeps mobile data", provider.isModemStandbyDetected.value)

        // Action: Modem wakes up or mobile data becomes active
        provider.setModemStandby(false)
        assertFalse("Modem standby resets when cellular radio engages", provider.isModemStandbyDetected.value)
    }

    @Test
    fun testDynamicCellularGeneration_HandoverPreserves5GState() {
        val provider = DynamicNetworkProvider()
        assertEquals("Initial generation should be 4G LTE", com.dualboost.downloader.network.CellularGeneration.FOUR_G, provider.cellularGeneration.value)

        // Handover to 5G NSA (e.g. Airtel 5G Plus)
        provider.setCellularGeneration(com.dualboost.downloader.network.CellularGeneration.FIVE_G)
        assertEquals("Generation must update to 5G", com.dualboost.downloader.network.CellularGeneration.FIVE_G, provider.cellularGeneration.value)

        // Capabilities or signal changes occur mid-download
        provider.setCarrierName("airtel")
        assertEquals("Carrier name updated", "airtel", provider.cellularCarrierName.value)
        assertEquals("5G generation must NOT be clobbered back to 4G", com.dualboost.downloader.network.CellularGeneration.FIVE_G, provider.cellularGeneration.value)
    }

    @Test
    fun testCellularKeepAlive_OnDemandLifecycle() {
        val provider = DynamicNetworkProvider()
        assertFalse("Cellular keep-alive should be inactive initially during idle", provider.isCellularKeepAliveActive.value)

        // Transfer starts (Download or Speed Test):
        provider.acquireCellularKeepAlive()
        assertTrue("Cellular keep-alive must be active during transfer", provider.isCellularKeepAliveActive.value)

        // Transfer finishes, cancels, or pauses:
        provider.releaseCellularKeepAlive()
        assertFalse("Cellular keep-alive must be released to let radio sleep", provider.isCellularKeepAliveActive.value)
    }
}

