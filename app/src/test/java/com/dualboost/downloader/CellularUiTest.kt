package com.dualboost.downloader

import com.dualboost.downloader.data.CellularNetworkConfig
import com.dualboost.downloader.data.CellularNetworkMode
import com.dualboost.downloader.network.CellularGeneration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI verification tests for Dynamic Cellular Network Mode & 5G Handoff (ADR-048 Slice 4).
 */
class CellularUiTest {

    @Test
    fun testCellularUi_ModeLabelsAndDescriptions() {
        assertEquals("Auto-Detect (Dynamic)", CellularNetworkMode.AUTO.displayName)
        assertEquals("Force 5G Mode", CellularNetworkMode.FORCE_5G.displayName)
        assertEquals("Force 4G Mode", CellularNetworkMode.FORCE_4G.displayName)

        assertTrue(CellularNetworkMode.AUTO.description.contains("Dynamic"))
        assertTrue(CellularNetworkMode.FORCE_5G.description.contains("5G"))
        assertTrue(CellularNetworkMode.FORCE_4G.description.contains("4G"))
    }

    @Test
    fun testCellularUi_HeroCardFormatting() {
        // When 5G is detected with carrier
        val cell5G = CellularGeneration.FIVE_G
        val carrierJio = "Jio"
        val title5G = "${cell5G.detailLabel} ($carrierJio)"
        assertEquals("5G Cellular (Jio)", title5G)

        // When 4G LTE is active with carrier
        val cell4G = CellularGeneration.FOUR_G
        val carrierAirtel = "Airtel"
        val title4G = "${cell4G.detailLabel} ($carrierAirtel)"
        assertEquals("4G LTE Cellular (Airtel)", title4G)

        // Without carrier
        assertEquals("5G Cellular", cell5G.detailLabel)
        assertEquals("4G LTE Cellular", cell4G.detailLabel)
    }

    @Test
    fun testCellularUi_BadgeFormatting() {
        val autoConfig = CellularNetworkConfig(mode = CellularNetworkMode.AUTO)
        assertEquals("", autoConfig.getBadgeLabel())

        val force5GConfig = CellularNetworkConfig(mode = CellularNetworkMode.FORCE_5G)
        assertEquals("⚡ Force 5G", force5GConfig.getBadgeLabel())

        val force4GConfig = CellularNetworkConfig(mode = CellularNetworkMode.FORCE_4G)
        assertEquals("⚡ Force 4G", force4GConfig.getBadgeLabel())
    }
}
