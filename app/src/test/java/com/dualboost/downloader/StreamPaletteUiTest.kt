package com.dualboost.downloader

import androidx.compose.ui.graphics.Color
import com.dualboost.downloader.data.StreamPalette
import org.junit.Assert.*
import org.junit.Test

class StreamPaletteUiTest {

    @Test
    fun testPaletteUi_FormatsDisplayNameAndSummary() {
        assertEquals("Material You", StreamPalette.DYNAMIC_MATERIAL_YOU.displayName)
        assertEquals("Wallpaper Adaptive", StreamPalette.DYNAMIC_MATERIAL_YOU.description)

        assertEquals("Cyberpunk Neon", StreamPalette.CYBERPUNK_NEON.displayName)
        assertEquals("Classic Cyan & Violet", StreamPalette.CYBERPUNK_NEON.description)

        assertEquals("Emerald & Amber", StreamPalette.EMERALD_AMBER.displayName)
        assertEquals("High Visibility Mint & Amber", StreamPalette.EMERALD_AMBER.description)

        assertEquals("Sunset Coral", StreamPalette.SUNSET_CORAL.displayName)
        assertEquals("Sky Blue & Coral Red", StreamPalette.SUNSET_CORAL.description)
    }

    @Test
    fun testPaletteUi_AllPalettesHaveDistinctWifiAndCellularColors() {
        val mockPrimary = Color(0xFF1E88E5)
        val mockTertiary = Color(0xFFAB47BC)

        for (palette in StreamPalette.entries) {
            val darkWifi = palette.resolveWifiColor(isDark = true, materialPrimary = mockPrimary)
            val darkCell = palette.resolveCellularColor(isDark = true, materialTertiary = mockTertiary)
            assertNotEquals("Dark Wi-Fi and Cellular colors must differ for ${palette.displayName}", darkWifi, darkCell)

            val lightWifi = palette.resolveWifiColor(isDark = false, materialPrimary = mockPrimary)
            val lightCell = palette.resolveCellularColor(isDark = false, materialTertiary = mockTertiary)
            assertNotEquals("Light Wi-Fi and Cellular colors must differ for ${palette.displayName}", lightWifi, lightCell)
        }
    }
}
