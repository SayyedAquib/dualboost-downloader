package com.dualboost.downloader

import com.dualboost.downloader.data.UserAgentConfig
import com.dualboost.downloader.data.UserAgentPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentConfigTest {

    @Test
    fun testConfig_DefaultsToNativeDevice() {
        val config = UserAgentConfig()
        assertEquals(UserAgentPreset.NATIVE, config.preset)
        assertEquals("", config.customUserAgentString)
        assertFalse(config.isDesktopFastTrack())
    }

    @Test
    fun testConfig_ResolvesNativeUserAgentWithDeviceModel() {
        val config = UserAgentConfig(preset = UserAgentPreset.NATIVE)
        val resolved = config.resolveUserAgentString(
            deviceModel = "Pixel 9",
            androidVersion = "15"
        )
        assertTrue(resolved.contains("Linux; Android 15; Pixel 9"))
        assertTrue(resolved.contains("AppleWebKit/537.36"))
        assertTrue(resolved.contains("Chrome/"))
        assertTrue(resolved.contains("Mobile Safari/537.36"))
    }

    @Test
    fun testConfig_ResolvesBuiltInPresets_Pixel9_GalaxyS24_Desktop() {
        val pixelConfig = UserAgentConfig(preset = UserAgentPreset.PIXEL_9)
        val pixelUa = pixelConfig.resolveUserAgentString()
        assertTrue(pixelUa.contains("Pixel 9"))
        assertFalse(pixelConfig.isDesktopFastTrack())

        val galaxyConfig = UserAgentConfig(preset = UserAgentPreset.GALAXY_S24)
        val galaxyUa = galaxyConfig.resolveUserAgentString()
        assertTrue(galaxyUa.contains("SM-S928B")) // Samsung Galaxy S24 Ultra model
        assertFalse(galaxyConfig.isDesktopFastTrack())

        val desktopConfig = UserAgentConfig(preset = UserAgentPreset.DESKTOP_CHROME)
        val desktopUa = desktopConfig.resolveUserAgentString()
        assertTrue(desktopUa.contains("Windows NT 10.0; Win64; x64"))
        assertTrue(desktopConfig.isDesktopFastTrack())
    }

    @Test
    fun testConfig_CustomPreset_UsesUserDefinedString() {
        val customRaw = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
        val config = UserAgentConfig(
            preset = UserAgentPreset.CUSTOM,
            customUserAgentString = customRaw
        )
        assertEquals(customRaw, config.resolveUserAgentString())
        assertFalse(config.isDesktopFastTrack())
    }

    @Test
    fun testConfig_CustomPreset_BlankStringFallsBackToNative() {
        val config = UserAgentConfig(
            preset = UserAgentPreset.CUSTOM,
            customUserAgentString = "   "
        )
        val resolved = config.resolveUserAgentString(deviceModel = "OnePlus 12", androidVersion = "14")
        assertTrue(resolved.contains("Linux; Android 14; OnePlus 12"))
    }

    @Test
    fun testConfig_DetectsDesktopModeFastTrack() {
        val desktopPreset = UserAgentConfig(preset = UserAgentPreset.DESKTOP_CHROME)
        assertTrue(desktopPreset.isDesktopFastTrack())

        val mobilePreset = UserAgentConfig(preset = UserAgentPreset.PIXEL_9)
        assertFalse(mobilePreset.isDesktopFastTrack())

        val customDesktop = UserAgentConfig(preset = UserAgentPreset.CUSTOM, customUserAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...")
        // Custom with Windows NT should also be detected as desktop
        assertTrue(customDesktop.isDesktopFastTrack())
    }

    @Test
    fun testConfig_FindPresetById() {
        assertEquals(UserAgentPreset.NATIVE, UserAgentPreset.fromId("native"))
        assertEquals(UserAgentPreset.PIXEL_9, UserAgentPreset.fromId("pixel_9"))
        assertEquals(UserAgentPreset.GALAXY_S24, UserAgentPreset.fromId("galaxy_s24"))
        assertEquals(UserAgentPreset.DESKTOP_CHROME, UserAgentPreset.fromId("desktop_chrome"))
        assertEquals(UserAgentPreset.CUSTOM, UserAgentPreset.fromId("custom"))
        // Unknown id defaults to NATIVE
        assertEquals(UserAgentPreset.NATIVE, UserAgentPreset.fromId("unknown_xyz"))
    }
}
