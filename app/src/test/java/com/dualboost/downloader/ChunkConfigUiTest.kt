package com.dualboost.downloader

import com.dualboost.downloader.data.ChunkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkConfigUiTest {

    private fun formatChunkBadge(chunkSizeBytes: Long, isAuto: Boolean): String {
        val mb = chunkSizeBytes / (1024 * 1024L)
        return if (isAuto) {
            "⚡ $mb MB Chunks (Auto)"
        } else {
            "⚡ $mb MB Chunks"
        }
    }

    private fun formatChunkTierLabel(bytes: Long): String {
        val mb = bytes / (1024 * 1024L)
        return "$mb MB"
    }

    private fun formatChunkSummary(config: ChunkConfig): String {
        return if (config.isAutoAdaptive) {
            "Auto-Adaptive (1 MB - 64 MB based on link & payload)"
        } else {
            "Manual (${formatChunkTierLabel(config.manualChunkSizeBytes)})"
        }
    }

    @Test
    fun testChunkUi_SupportedTiersList() {
        assertEquals(7, ChunkConfig.SUPPORTED_TIERS.size)
        assertEquals(1 * 1024 * 1024L, ChunkConfig.SUPPORTED_TIERS[0])
        assertEquals(2 * 1024 * 1024L, ChunkConfig.SUPPORTED_TIERS[1])
        assertEquals(4 * 1024 * 1024L, ChunkConfig.SUPPORTED_TIERS[2])
        assertEquals(8 * 1024 * 1024L, ChunkConfig.SUPPORTED_TIERS[3])
        assertEquals(16 * 1024 * 1024L, ChunkConfig.SUPPORTED_TIERS[4])
        assertEquals(32 * 1024 * 1024L, ChunkConfig.SUPPORTED_TIERS[5])
        assertEquals(64 * 1024 * 1024L, ChunkConfig.SUPPORTED_TIERS[6])
    }

    @Test
    fun testChunkUi_TierLabelsAndFormatting() {
        assertEquals("1 MB", formatChunkTierLabel(1 * 1024 * 1024L))
        assertEquals("4 MB", formatChunkTierLabel(4 * 1024 * 1024L))
        assertEquals("16 MB", formatChunkTierLabel(16 * 1024 * 1024L))
        assertEquals("64 MB", formatChunkTierLabel(64 * 1024 * 1024L))
    }

    @Test
    fun testChunkUi_FormatsChunkBadgeText() {
        assertEquals("⚡ 4 MB Chunks (Auto)", formatChunkBadge(4 * 1024 * 1024L, isAuto = true))
        assertEquals("⚡ 16 MB Chunks (Auto)", formatChunkBadge(16 * 1024 * 1024L, isAuto = true))
        assertEquals("⚡ 8 MB Chunks", formatChunkBadge(8 * 1024 * 1024L, isAuto = false))
        assertEquals("⚡ 64 MB Chunks", formatChunkBadge(64 * 1024 * 1024L, isAuto = false))
    }

    @Test
    fun testChunkUi_SummaryTextFormatting() {
        val autoConfig = ChunkConfig(isAutoAdaptive = true, manualChunkSizeBytes = 4 * 1024 * 1024L)
        assertEquals("Auto-Adaptive (1 MB - 64 MB based on link & payload)", formatChunkSummary(autoConfig))

        val manualConfig = ChunkConfig(isAutoAdaptive = false, manualChunkSizeBytes = 16 * 1024 * 1024L)
        assertEquals("Manual (16 MB)", formatChunkSummary(manualConfig))
    }
}
