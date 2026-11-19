package com.dualboost.downloader

import com.dualboost.downloader.data.CellularNetworkMode
import com.dualboost.downloader.data.CellularPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for CellularPreferences (ADR-048 Slice 1).
 */
class CellularPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: DummyTestContext
    private lateinit var preferences: CellularPreferences

    @Before
    fun setUp() {
        dummyContext = DummyTestContext(tempFolder.newFolder("prefs_test"))
        preferences = CellularPreferences(dummyContext)
    }

    @Test
    fun testPreferences_DefaultModeIsAuto() {
        val mode = preferences.getCellularMode()
        assertEquals(CellularNetworkMode.AUTO, mode)
    }

    @Test
    fun testPreferences_PersistAndReadMode() {
        preferences.setCellularMode(CellularNetworkMode.FORCE_5G)
        assertEquals(CellularNetworkMode.FORCE_5G, preferences.getCellularMode())

        preferences.setCellularMode(CellularNetworkMode.FORCE_4G)
        assertEquals(CellularNetworkMode.FORCE_4G, preferences.getCellularMode())

        preferences.setCellularMode(CellularNetworkMode.AUTO)
        assertEquals(CellularNetworkMode.AUTO, preferences.getCellularMode())
    }

    @Test
    fun testPreferences_ResetToDefault() {
        preferences.setCellularMode(CellularNetworkMode.FORCE_5G)
        assertEquals(CellularNetworkMode.FORCE_5G, preferences.getCellularMode())

        preferences.resetToDefault()
        assertEquals(CellularNetworkMode.AUTO, preferences.getCellularMode())
    }
}

