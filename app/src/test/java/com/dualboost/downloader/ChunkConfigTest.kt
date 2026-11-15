package com.dualboost.downloader

import com.dualboost.downloader.data.ChunkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkConfigTest {

    private val MB = 1024 * 1024L

    @Test
    fun testChunkConfig_DefaultsToAutoAdaptiveAnd4MB() {
        val config = ChunkConfig()
        assertTrue(config.isAutoAdaptive)
        assertEquals(4 * MB, config.manualChunkSizeBytes)
    }

    @Test
    fun testChunkConfig_ClampsManualChunkSizeBetween1MBAnd64MB() {
        val tooSmall = ChunkConfig(isAutoAdaptive = false, manualChunkSizeBytes = 256 * 1024L)
        assertEquals(1 * MB, tooSmall.manualChunkSizeBytes)

        val tooLarge = ChunkConfig(isAutoAdaptive = false, manualChunkSizeBytes = 128 * MB)
        assertEquals(64 * MB, tooLarge.manualChunkSizeBytes)

        val valid = ChunkConfig(isAutoAdaptive = false, manualChunkSizeBytes = 16 * MB)
        assertEquals(16 * MB, valid.manualChunkSizeBytes)
    }

    @Test
    fun testChunkConfig_AutoAdaptive_ScalesByPayloadSize() {
        val config = ChunkConfig(isAutoAdaptive = true)

        // Small file (< 8 MB) -> 1 MB chunks
        assertEquals(1 * MB, config.resolveChunkSize(contentLength = 4 * MB))

        // Moderate file (< 16 MB) -> 2 MB chunks
        assertEquals(2 * MB, config.resolveChunkSize(contentLength = 12 * MB))

        // Standard file (16 MB - 200 MB) -> 4 MB chunks
        assertEquals(4 * MB, config.resolveChunkSize(contentLength = 50 * MB))

        // Large file (200 MB - 1000 MB) -> 8 MB chunks
        assertEquals(8 * MB, config.resolveChunkSize(contentLength = 500 * MB))

        // Very large file (> 1000 MB) -> 16 MB chunks
        assertEquals(16 * MB, config.resolveChunkSize(contentLength = 2000 * MB))
    }

    @Test
    fun testChunkConfig_AutoAdaptive_UpgradesOnGigabitWifi7() {
        val config = ChunkConfig(isAutoAdaptive = true)

        // On Gigabit/Wi-Fi 7, large file (1 GB) upgrades from 8 MB to 16 MB
        assertEquals(16 * MB, config.resolveChunkSize(contentLength = 1000 * MB, isWifi7OrGigabit = true))

        // On Gigabit/Wi-Fi 7, massive file (8 GB) upgrades to 64 MB
        assertEquals(64 * MB, config.resolveChunkSize(contentLength = 8000 * MB, isWifi7OrGigabit = true))
    }

    @Test
    fun testChunkConfig_AutoAdaptive_ClampsOnSpottyNetwork() {
        val config = ChunkConfig(isAutoAdaptive = true)

        // A large file (1000 MB) that normally gets 8 MB gets clamped to 2 MB on weak/spotty link
        assertEquals(2 * MB, config.resolveChunkSize(contentLength = 1000 * MB, isWeakOrSpotty = true))
    }

    @Test
    fun testChunkConfig_ManualOverride_ReturnsConfiguredSize() {
        val config = ChunkConfig(isAutoAdaptive = false, manualChunkSizeBytes = 32 * MB)

        // Irrespective of file size or network, returns manual size
        assertEquals(32 * MB, config.resolveChunkSize(contentLength = 10 * MB))
        assertEquals(32 * MB, config.resolveChunkSize(contentLength = 5000 * MB, isWeakOrSpotty = true))
    }

    @Test
    fun testChunkConfig_SupportedTiersList() {
        val tiers = ChunkConfig.SUPPORTED_TIERS
        assertEquals(7, tiers.size)
        assertEquals(1 * MB, tiers[0])
        assertEquals(2 * MB, tiers[1])
        assertEquals(4 * MB, tiers[2])
        assertEquals(8 * MB, tiers[3])
        assertEquals(16 * MB, tiers[4])
        assertEquals(32 * MB, tiers[5])
        assertEquals(64 * MB, tiers[6])
    }
}
