package com.dualboost.downloader

import com.dualboost.downloader.data.CellularNetworkConfig
import com.dualboost.downloader.data.CellularNetworkMode
import com.dualboost.downloader.network.CellularGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CellularNetworkConfig and CellularNetworkMode (ADR-048 Slice 1).
 */
class CellularNetworkConfigTest {

    @Test
    fun testCellularNetworkMode_OptionsAndDisplayNames() {
        assertEquals("auto", CellularNetworkMode.AUTO.id)
        assertEquals("Auto-Detect (Dynamic)", CellularNetworkMode.AUTO.displayName)
        assertTrue(CellularNetworkMode.AUTO.description.contains("Dynamic"))

        assertEquals("force_5g", CellularNetworkMode.FORCE_5G.id)
        assertEquals("Force 5G Mode", CellularNetworkMode.FORCE_5G.displayName)
        assertTrue(CellularNetworkMode.FORCE_5G.description.contains("5G"))

        assertEquals("force_4g", CellularNetworkMode.FORCE_4G.id)
        assertEquals("Force 4G Mode", CellularNetworkMode.FORCE_4G.displayName)
        assertTrue(CellularNetworkMode.FORCE_4G.description.contains("4G"))
    }

    @Test
    fun testCellularNetworkConfig_EffectiveGeneration_AutoMode() {
        val config5G = CellularNetworkConfig(
            mode = CellularNetworkMode.AUTO,
            detectedGeneration = CellularGeneration.FIVE_G
        )
        assertEquals(CellularGeneration.FIVE_G, config5G.effectiveGeneration)

        val config4G = CellularNetworkConfig(
            mode = CellularNetworkMode.AUTO,
            detectedGeneration = CellularGeneration.FOUR_G
        )
        assertEquals(CellularGeneration.FOUR_G, config4G.effectiveGeneration)

        val configUnknown = CellularNetworkConfig(
            mode = CellularNetworkMode.AUTO,
            detectedGeneration = CellularGeneration.UNKNOWN
        )
        assertEquals(CellularGeneration.UNKNOWN, configUnknown.effectiveGeneration)
    }

    @Test
    fun testCellularNetworkConfig_EffectiveGeneration_Force5GMode() {
        // Even when detected generation is 4G, 3G or UNKNOWN, FORCE_5G always returns FIVE_G
        val configWith4G = CellularNetworkConfig(
            mode = CellularNetworkMode.FORCE_5G,
            detectedGeneration = CellularGeneration.FOUR_G
        )
        assertEquals(CellularGeneration.FIVE_G, configWith4G.effectiveGeneration)

        val configWithUnknown = CellularNetworkConfig(
            mode = CellularNetworkMode.FORCE_5G,
            detectedGeneration = CellularGeneration.UNKNOWN
        )
        assertEquals(CellularGeneration.FIVE_G, configWithUnknown.effectiveGeneration)
    }

    @Test
    fun testCellularNetworkConfig_EffectiveGeneration_Force4GMode() {
        // Even when detected generation is 5G, FORCE_4G always returns FOUR_G
        val configWith5G = CellularNetworkConfig(
            mode = CellularNetworkMode.FORCE_4G,
            detectedGeneration = CellularGeneration.FIVE_G
        )
        assertEquals(CellularGeneration.FOUR_G, configWith5G.effectiveGeneration)
    }

    @Test
    fun testCellularNetworkConfig_BadgeLabels() {
        val autoConfig = CellularNetworkConfig(mode = CellularNetworkMode.AUTO)
        assertEquals("", autoConfig.getBadgeLabel())

        val force5GConfig = CellularNetworkConfig(mode = CellularNetworkMode.FORCE_5G)
        assertEquals("⚡ Force 5G", force5GConfig.getBadgeLabel())

        val force4GConfig = CellularNetworkConfig(mode = CellularNetworkMode.FORCE_4G)
        assertEquals("⚡ Force 4G", force4GConfig.getBadgeLabel())
    }

    @Test
    fun testCellularNetworkMode_FromIdCaseInsensitive() {
        assertEquals(CellularNetworkMode.AUTO, CellularNetworkMode.fromId("auto"))
        assertEquals(CellularNetworkMode.AUTO, CellularNetworkMode.fromId("AUTO"))
        assertEquals(CellularNetworkMode.FORCE_5G, CellularNetworkMode.fromId("force_5g"))
        assertEquals(CellularNetworkMode.FORCE_5G, CellularNetworkMode.fromId("FORCE_5G"))
        assertEquals(CellularNetworkMode.FORCE_4G, CellularNetworkMode.fromId("force_4g"))
        assertEquals(CellularNetworkMode.FORCE_4G, CellularNetworkMode.fromId("FORCE_4G"))
        // Fallback to AUTO for unknown IDs
        assertEquals(CellularNetworkMode.AUTO, CellularNetworkMode.fromId("non_existent"))
        assertEquals(CellularNetworkMode.AUTO, CellularNetworkMode.fromId(null))
    }
}

