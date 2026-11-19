package com.dualboost.downloader

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.dualboost.downloader.data.CellularNetworkConfig
import com.dualboost.downloader.data.CellularNetworkMode
import com.dualboost.downloader.network.CellularGeneration
import com.dualboost.downloader.network.DualNetworkManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for dynamic 5G/4G cellular detection and real-time handoff (ADR-048 Slice 2).
 */
class DynamicCellularDetectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: DummyTestContext
    private lateinit var networkManager: DualNetworkManager

    @Before
    fun setUp() {
        dummyContext = DummyTestContext(tempFolder.newFolder("network_test"))
        networkManager = DualNetworkManager(dummyContext)
    }

    @Test
    fun testCellularGeneration_FromTelephony_Detects5GNSA() {
        // 5G Non-Standalone: underlying data network type is LTE (13), but override is NR_NSA (1)
        val generation = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
        )
        assertEquals(CellularGeneration.FIVE_G, generation)
    }

    @Test
    fun testCellularGeneration_FromTelephony_Detects5GAdvanced() {
        // 5G Ultra Wideband / Advanced
        val generation = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
        )
        assertEquals(CellularGeneration.FIVE_G, generation)
    }

    @Test
    fun testCellularGeneration_FromTelephony_Detects5GStandalone() {
        // 5G Standalone (SA)
        val generation = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_NR,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        )
        assertEquals(CellularGeneration.FIVE_G, generation)
    }

    @Test
    fun testCellularGeneration_FromTelephony_LTEWithoutOverrideIs4G() {
        val generation = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        )
        assertEquals(CellularGeneration.FOUR_G, generation)
    }

    @Test
    fun testCellularGeneration_HeuristicFallback_HighBandwidthResolvesTo5G() {
        // Zero-permission / carrier fallback: When telephony reports LTE but downstream link bandwidth >= 120 Mbps (120,000 Kbps)
        val generationHighBandwidth = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
            downstreamBandwidthKbps = 150_000
        )
        assertEquals(CellularGeneration.FIVE_G, generationHighBandwidth)

        // Normal LTE bandwidth (< 120,000 Kbps) remains 4G
        val generationNormalLTE = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
            downstreamBandwidthKbps = 50_000
        )
        assertEquals(CellularGeneration.FOUR_G, generationNormalLTE)
    }

    @Test
    fun testNetworkManager_ApplyCellularConfig_OverridesGenerationDynamically() {
        // Default starts as UNKNOWN or detected
        val force5GConfig = CellularNetworkConfig(mode = CellularNetworkMode.FORCE_5G)
        networkManager.applyCellularConfig(force5GConfig)
        assertEquals(CellularGeneration.FIVE_G, networkManager.cellularGeneration.value)

        val force4GConfig = CellularNetworkConfig(mode = CellularNetworkMode.FORCE_4G)
        networkManager.applyCellularConfig(force4GConfig)
        assertEquals(CellularGeneration.FOUR_G, networkManager.cellularGeneration.value)

        val autoConfig = CellularNetworkConfig(mode = CellularNetworkMode.AUTO)
        networkManager.applyCellularConfig(autoConfig)
        // With dummy context and no telephony signal, AUTO emits UNKNOWN or detected value
        assertEquals(CellularGeneration.UNKNOWN, networkManager.cellularGeneration.value)
    }
}

