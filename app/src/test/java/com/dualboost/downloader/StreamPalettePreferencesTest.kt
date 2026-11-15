package com.dualboost.downloader

import com.dualboost.downloader.data.StreamPalette
import com.dualboost.downloader.data.StreamPalettePreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class StreamPalettePreferencesTest {

    private lateinit var tempDir: File
    private lateinit var dummyContext: DummyTestContext
    private lateinit var preferences: StreamPalettePreferences

    @Before
    fun setUp() {
        tempDir = File("build/test-palette-prefs-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        dummyContext = DummyTestContext(tempDir)
        preferences = StreamPalettePreferences(dummyContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testPreferences_DefaultsToMaterialYou() {
        val palette = preferences.getStreamPalette()
        assertEquals("Default palette must be DYNAMIC_MATERIAL_YOU", StreamPalette.DYNAMIC_MATERIAL_YOU, palette)
    }

    @Test
    fun testPreferences_SavesAndRestoresSelectedPalette() {
        preferences.setStreamPalette(StreamPalette.EMERALD_AMBER)
        assertEquals(StreamPalette.EMERALD_AMBER, preferences.getStreamPalette())

        preferences.setStreamPalette(StreamPalette.SUNSET_CORAL)
        assertEquals(StreamPalette.SUNSET_CORAL, preferences.getStreamPalette())

        preferences.setStreamPalette(StreamPalette.CYBERPUNK_NEON)
        assertEquals(StreamPalette.CYBERPUNK_NEON, preferences.getStreamPalette())
    }

    @Test
    fun testPreferences_ResetToDefault_RestoresMaterialYou() {
        preferences.setStreamPalette(StreamPalette.CYBERPUNK_NEON)
        assertEquals(StreamPalette.CYBERPUNK_NEON, preferences.getStreamPalette())

        preferences.resetToDefault()
        assertEquals(StreamPalette.DYNAMIC_MATERIAL_YOU, preferences.getStreamPalette())
    }
}
