package com.dualboost.downloader

import com.dualboost.downloader.data.ChunkConfig
import com.dualboost.downloader.data.ChunkPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChunkPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: DummyTestContext

    @Before
    fun setUp() {
        val baseDir = tempFolder.newFolder("chunk_prefs_test")
        context = DummyTestContext(baseDir)
    }

    @Test
    fun testPreferences_DefaultsToAutoAdaptiveAnd4MB() {
        val prefs = ChunkPreferences(context)
        val config = prefs.getChunkConfig()

        assertTrue(config.isAutoAdaptive)
        assertEquals(ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES, config.manualChunkSizeBytes)
        assertEquals(4 * 1024 * 1024L, config.manualChunkSizeBytes)
    }

    @Test
    fun testPreferences_SavesAndRestoresManualSize() {
        val prefs = ChunkPreferences(context)
        prefs.setManualChunkSizeBytes(16 * 1024 * 1024L)

        val config = prefs.getChunkConfig()
        assertEquals(16 * 1024 * 1024L, config.manualChunkSizeBytes)
    }

    @Test
    fun testPreferences_TogglesAutoAdaptive() {
        val prefs = ChunkPreferences(context)
        prefs.setAutoAdaptive(false)

        val config1 = prefs.getChunkConfig()
        assertFalse(config1.isAutoAdaptive)

        prefs.setAutoAdaptive(true)
        val config2 = prefs.getChunkConfig()
        assertTrue(config2.isAutoAdaptive)
    }

    @Test
    fun testPreferences_ClampsManualSizeOnSave() {
        val prefs = ChunkPreferences(context)

        // Below minimum (1MB) -> clamped to 1MB
        prefs.setManualChunkSizeBytes(512 * 1024L)
        assertEquals(ChunkConfig.MIN_CHUNK_SIZE_BYTES, prefs.getChunkConfig().manualChunkSizeBytes)

        // Above maximum (64MB) -> clamped to 64MB
        prefs.setManualChunkSizeBytes(128 * 1024 * 1024L)
        assertEquals(ChunkConfig.MAX_CHUNK_SIZE_BYTES, prefs.getChunkConfig().manualChunkSizeBytes)
    }

    @Test
    fun testPreferences_ResetToDefault_RestoresAutoAdaptive() {
        val prefs = ChunkPreferences(context)
        prefs.setAutoAdaptive(false)
        prefs.setManualChunkSizeBytes(32 * 1024 * 1024L)

        val modified = prefs.getChunkConfig()
        assertFalse(modified.isAutoAdaptive)
        assertEquals(32 * 1024 * 1024L, modified.manualChunkSizeBytes)

        prefs.resetToDefault()

        val restored = prefs.getChunkConfig()
        assertTrue(restored.isAutoAdaptive)
        assertEquals(ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES, restored.manualChunkSizeBytes)
    }
}
