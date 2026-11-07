package com.dualboost.downloader

import com.dualboost.downloader.security.SecurityPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecurityPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: DummyTestContext

    @Before
    fun setUp() {
        val baseDir = tempFolder.newFolder("security_prefs_test")
        context = DummyTestContext(baseDir)
    }

    @Test
    fun threatGuard_isEnabledByDefault() {
        assertTrue(SecurityPreferences.isThreatGuardEnabled(context))
    }

    @Test
    fun setThreatGuardEnabled_updatesValueCorrectly() {
        SecurityPreferences.setThreatGuardEnabled(context, false)
        assertFalse(SecurityPreferences.isThreatGuardEnabled(context))

        SecurityPreferences.setThreatGuardEnabled(context, true)
        assertTrue(SecurityPreferences.isThreatGuardEnabled(context))
    }

    @Test
    fun safeBrowsingApiKey_storesAndRetrievesCorrectly() {
        assertNull(SecurityPreferences.getSafeBrowsingApiKey(context))

        SecurityPreferences.setSafeBrowsingApiKey(context, "AIzaSyFakeKey123")
        assertEquals("AIzaSyFakeKey123", SecurityPreferences.getSafeBrowsingApiKey(context))

        SecurityPreferences.setSafeBrowsingApiKey(context, "")
        assertNull(SecurityPreferences.getSafeBrowsingApiKey(context))
    }
}
