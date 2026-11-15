package com.dualboost.downloader

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.dualboost.downloader.network.CellularGeneration
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit verification for CellularGeneration adaptive two-tier labels
 * and TelephonyManager/TelephonyDisplayInfo classification (ADR-040).
 */
class CellularGenerationTest {

    @Test
    fun testCellularGenerationProperties_LabelsMatchSpecification() {
        // FIVE_G
        assertEquals("5G", CellularGeneration.FIVE_G.shortLabel)
        assertEquals("5G Cellular", CellularGeneration.FIVE_G.detailLabel)
        assertEquals("5G", CellularGeneration.FIVE_G.fullDisplayName)

        // FOUR_G
        assertEquals("4G", CellularGeneration.FOUR_G.shortLabel)
        assertEquals("4G LTE Cellular", CellularGeneration.FOUR_G.detailLabel)
        assertEquals("4G LTE", CellularGeneration.FOUR_G.fullDisplayName)

        // THREE_G_OR_LOWER
        assertEquals("3G", CellularGeneration.THREE_G_OR_LOWER.shortLabel)
        assertEquals("3G Cellular", CellularGeneration.THREE_G_OR_LOWER.detailLabel)
        assertEquals("3G", CellularGeneration.THREE_G_OR_LOWER.fullDisplayName)

        // UNKNOWN
        assertEquals("Cellular", CellularGeneration.UNKNOWN.shortLabel)
        assertEquals("Cellular", CellularGeneration.UNKNOWN.detailLabel)
        assertEquals("Cellular", CellularGeneration.UNKNOWN.fullDisplayName)
    }

    @Test
    fun testFromTelephony_Detects5GCorrectly() {
        // 5G NSA via override
        val nsa = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA
        )
        assertEquals(CellularGeneration.FIVE_G, nsa)

        // 5G NR Advanced (mmWave / 5G+) via override
        val mmWave = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
        )
        assertEquals(CellularGeneration.FIVE_G, mmWave)

        // 5G SA (Standalone) via base network type
        val nrSa = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_NR,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        )
        assertEquals(CellularGeneration.FIVE_G, nrSa)
    }

    @Test
    fun testFromTelephony_Detects4GLteCorrectly() {
        val lte = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        )
        assertEquals(CellularGeneration.FOUR_G, lte)

        val lteCa = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_LTE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA
        )
        assertEquals(CellularGeneration.FOUR_G, lteCa)
    }

    @Test
    fun testFromTelephony_Detects3GAndLegacyCorrectly() {
        val hsdpa = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_HSDPA,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        )
        assertEquals(CellularGeneration.THREE_G_OR_LOWER, hsdpa)

        val edge = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_EDGE,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        )
        assertEquals(CellularGeneration.THREE_G_OR_LOWER, edge)
    }

    @Test
    fun testFromTelephony_UnknownAndFallback() {
        val unknown = CellularGeneration.fromTelephony(
            networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN,
            overrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
        )
        assertEquals(CellularGeneration.UNKNOWN, unknown)
    }
}
