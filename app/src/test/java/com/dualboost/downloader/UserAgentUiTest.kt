package com.dualboost.downloader

import com.dualboost.downloader.data.UserAgentConfig
import com.dualboost.downloader.data.UserAgentPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentUiTest {

    @Test
    fun testUserAgentUi_PresetLabelsAndDescriptions() {
        val presets = UserAgentPreset.entries
        assertEquals(5, presets.size)

        for (preset in presets) {
            assertTrue(preset.displayName.isNotBlank())
            assertTrue(preset.description.isNotBlank())
            assertTrue(preset.id.isNotBlank())
        }

        assertEquals("Native Device", UserAgentPreset.NATIVE.displayName)
        assertEquals("Pixel 9", UserAgentPreset.PIXEL_9.displayName)
        assertEquals("Galaxy S24 Ultra", UserAgentPreset.GALAXY_S24.displayName)
        assertEquals("Desktop Chrome", UserAgentPreset.DESKTOP_CHROME.displayName)
        assertEquals("Custom", UserAgentPreset.CUSTOM.displayName)
    }

    @Test
    fun testUserAgentUi_FormatSubtitle() {
        val nativeConfig = UserAgentConfig(UserAgentPreset.NATIVE)
        assertTrue(nativeConfig.getSubtitleText().contains("Native Device"))

        val pixelConfig = UserAgentConfig(UserAgentPreset.PIXEL_9)
        assertEquals("Google Pixel 9 • Android 15", pixelConfig.getSubtitleText())

        val galaxyConfig = UserAgentConfig(UserAgentPreset.GALAXY_S24)
        assertEquals("Samsung Galaxy S24 Ultra • SM-S928B", galaxyConfig.getSubtitleText())

        val desktopConfig = UserAgentConfig(UserAgentPreset.DESKTOP_CHROME)
        assertEquals("Windows 11 • Chrome 131 (Desktop Fast-Track)", desktopConfig.getSubtitleText())

        val customEmpty = UserAgentConfig(UserAgentPreset.CUSTOM, "")
        assertEquals("Custom (Empty - fallback to Native)", customEmpty.getSubtitleText())

        val customFilled = UserAgentConfig(UserAgentPreset.CUSTOM, "MyBot/1.0")
        assertEquals("Custom: MyBot/1.0", customFilled.getSubtitleText())
    }

    @Test
    fun testUserAgentUi_FormatsCustomBadgeText() {
        val nativeConfig = UserAgentConfig(UserAgentPreset.NATIVE)
        assertEquals("", nativeConfig.getBadgeLabel())

        val pixelConfig = UserAgentConfig(UserAgentPreset.PIXEL_9)
        assertEquals("⚡ Pixel 9 UA", pixelConfig.getBadgeLabel())

        val galaxyConfig = UserAgentConfig(UserAgentPreset.GALAXY_S24)
        assertEquals("⚡ Galaxy S24 UA", galaxyConfig.getBadgeLabel())

        val desktopConfig = UserAgentConfig(UserAgentPreset.DESKTOP_CHROME)
        assertEquals("⚡ Desktop UA", desktopConfig.getBadgeLabel())

        val customConfig = UserAgentConfig(UserAgentPreset.CUSTOM, "CustomUA/1.0")
        assertEquals("⚡ Custom UA", customConfig.getBadgeLabel())
    }
}
