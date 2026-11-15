package com.dualboost.downloader

import androidx.compose.ui.graphics.Color
import com.dualboost.downloader.data.StreamPalette
import org.junit.Assert.*
import org.junit.Test

class StreamPaletteModelTest {

    @Test
    fun testStreamPalette_DefaultIsMaterialYou() {
        assertEquals("Default palette must be DYNAMIC_MATERIAL_YOU", StreamPalette.DYNAMIC_MATERIAL_YOU, StreamPalette.DEFAULT)
        assertEquals("dynamic_material_you", StreamPalette.DYNAMIC_MATERIAL_YOU.id)
    }

    @Test
    fun testStreamPalette_FromId_FallbackSafe() {
        assertEquals(StreamPalette.DYNAMIC_MATERIAL_YOU, StreamPalette.fromId("dynamic_material_you"))
        assertEquals(StreamPalette.CYBERPUNK_NEON, StreamPalette.fromId("cyberpunk_neon"))
        assertEquals(StreamPalette.EMERALD_AMBER, StreamPalette.fromId("emerald_amber"))
        assertEquals(StreamPalette.SUNSET_CORAL, StreamPalette.fromId("sunset_coral"))

        // Unknown or null falls back to DEFAULT
        assertEquals(StreamPalette.DEFAULT, StreamPalette.fromId("unknown_id"))
        assertEquals(StreamPalette.DEFAULT, StreamPalette.fromId(null))
    }

    @Test
    fun testStreamPalette_CyberpunkNeon_ResolvesCorrectDarkAndLightPairs() {
        val palette = StreamPalette.CYBERPUNK_NEON
        val mockPrimary = Color(0xFF123456)
        val mockTertiary = Color(0xFF654321)

        // Dark mode
        val darkWifi = palette.resolveWifiColor(isDark = true, materialPrimary = mockPrimary)
        val darkCell = palette.resolveCellularColor(isDark = true, materialTertiary = mockTertiary)
        assertEquals(Color(0xFF00E5FF), darkWifi)
        assertEquals(Color(0xFFB388FF), darkCell)

        // Light mode
        val lightWifi = palette.resolveWifiColor(isDark = false, materialPrimary = mockPrimary)
        val lightCell = palette.resolveCellularColor(isDark = false, materialTertiary = mockTertiary)
        assertEquals(Color(0xFF00838F), lightWifi)
        assertEquals(Color(0xFF651FFF), lightCell)
    }

    @Test
    fun testStreamPalette_EmeraldAmber_ResolvesCorrectDarkAndLightPairs() {
        val palette = StreamPalette.EMERALD_AMBER
        val mockPrimary = Color(0xFF123456)
        val mockTertiary = Color(0xFF654321)

        // Dark mode
        val darkWifi = palette.resolveWifiColor(isDark = true, materialPrimary = mockPrimary)
        val darkCell = palette.resolveCellularColor(isDark = true, materialTertiary = mockTertiary)
        assertEquals(Color(0xFF00E676), darkWifi)
        assertEquals(Color(0xFFFFD54F), darkCell)

        // Light mode
        val lightWifi = palette.resolveWifiColor(isDark = false, materialPrimary = mockPrimary)
        val lightCell = palette.resolveCellularColor(isDark = false, materialTertiary = mockTertiary)
        assertEquals(Color(0xFF1B5E20), lightWifi)
        assertEquals(Color(0xFFE65100), lightCell)
    }

    @Test
    fun testStreamPalette_SunsetCoral_ResolvesCorrectDarkAndLightPairs() {
        val palette = StreamPalette.SUNSET_CORAL
        val mockPrimary = Color(0xFF123456)
        val mockTertiary = Color(0xFF654321)

        // Dark mode
        val darkWifi = palette.resolveWifiColor(isDark = true, materialPrimary = mockPrimary)
        val darkCell = palette.resolveCellularColor(isDark = true, materialTertiary = mockTertiary)
        assertEquals(Color(0xFF40C4FF), darkWifi)
        assertEquals(Color(0xFFFF5252), darkCell)

        // Light mode
        val lightWifi = palette.resolveWifiColor(isDark = false, materialPrimary = mockPrimary)
        val lightCell = palette.resolveCellularColor(isDark = false, materialTertiary = mockTertiary)
        assertEquals(Color(0xFF0277BD), lightWifi)
        assertEquals(Color(0xFFC62828), lightCell)
    }

    @Test
    fun testStreamPalette_MaterialYou_DelegatesToMaterialThemeTokens() {
        val palette = StreamPalette.DYNAMIC_MATERIAL_YOU
        val mockPrimary = Color(0xFF00AABB)
        val mockTertiary = Color(0xFFCC8800)

        // Must dynamically delegate directly to MaterialTheme primary and tertiary tokens
        assertEquals(mockPrimary, palette.resolveWifiColor(isDark = true, materialPrimary = mockPrimary))
        assertEquals(mockTertiary, palette.resolveCellularColor(isDark = true, materialTertiary = mockTertiary))

        assertEquals(mockPrimary, palette.resolveWifiColor(isDark = false, materialPrimary = mockPrimary))
        assertEquals(mockTertiary, palette.resolveCellularColor(isDark = false, materialTertiary = mockTertiary))
    }
}
