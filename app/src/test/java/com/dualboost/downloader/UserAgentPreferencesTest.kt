package com.dualboost.downloader

import com.dualboost.downloader.data.UserAgentConfig
import com.dualboost.downloader.data.UserAgentPreferences
import com.dualboost.downloader.data.UserAgentPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserAgentPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: DummyTestContext

    @Before
    fun setUp() {
        val baseDir = tempFolder.newFolder("user_agent_prefs_test")
        context = DummyTestContext(baseDir)
    }

    @Test
    fun testPreferences_DefaultsToNativePreset() {
        val prefs = UserAgentPreferences(context)
        val config = prefs.getUserAgentConfig()

        assertEquals(UserAgentPreset.NATIVE, config.preset)
        assertEquals("", config.customUserAgentString)
    }

    @Test
    fun testPreferences_SavesAndRestoresPresetId() {
        val prefs = UserAgentPreferences(context)
        prefs.setPreset(UserAgentPreset.GALAXY_S24)

        val config = prefs.getUserAgentConfig()
        assertEquals(UserAgentPreset.GALAXY_S24, config.preset)

        prefs.setPreset(UserAgentPreset.DESKTOP_CHROME)
        assertEquals(UserAgentPreset.DESKTOP_CHROME, prefs.getUserAgentConfig().preset)
    }

    @Test
    fun testPreferences_SavesAndRestoresCustomUserAgent() {
        val prefs = UserAgentPreferences(context)
        val custom = "Mozilla/5.0 (PlayStation 5 7.00) AppleWebKit/605.1.15"
        prefs.setCustomUserAgent(custom)

        val config = prefs.getUserAgentConfig()
        assertEquals(UserAgentPreset.CUSTOM, config.preset)
        assertEquals(custom, config.customUserAgentString)
    }

    @Test
    fun testPreferences_ResetToDefault_RestoresNative() {
        val prefs = UserAgentPreferences(context)
        prefs.setPreset(UserAgentPreset.DESKTOP_CHROME)
        prefs.setCustomUserAgent("Custom String")

        val modified = prefs.getUserAgentConfig()
        assertEquals(UserAgentPreset.CUSTOM, modified.preset)
        assertEquals("Custom String", modified.customUserAgentString)

        prefs.resetToDefault()

        val restored = prefs.getUserAgentConfig()
        assertEquals(UserAgentPreset.NATIVE, restored.preset)
        assertEquals("", restored.customUserAgentString)
    }
}
