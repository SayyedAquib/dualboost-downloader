package com.dualboost.downloader

import com.dualboost.downloader.network.CellularGeneration
import com.dualboost.downloader.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit verification for dynamic cellular generation label bindings (ADR-040).
 */
class DynamicCellularLabelUiTest {

    @Test
    fun testChunkLegendLabel_MatchesCellularGeneration() {
        assertEquals("5G Chunk", "${CellularGeneration.FIVE_G.shortLabel} Chunk")
        assertEquals("4G Chunk", "${CellularGeneration.FOUR_G.shortLabel} Chunk")
        assertEquals("3G Chunk", "${CellularGeneration.THREE_G_OR_LOWER.shortLabel} Chunk")
        assertEquals("Cellular Chunk", "${CellularGeneration.UNKNOWN.shortLabel} Chunk")
    }

    @Test
    fun testPresetDescriptionText_AdaptsConcurrentlyLabel() {
        fun formatPresetDesc(desc: String, supportsRange: Boolean, gen: CellularGeneration): String {
            return "$desc • ${if (supportsRange) "Aggregates Wi-Fi + ${gen.shortLabel} concurrently" else "Single-stream fallback mode"}"
        }

        val text5G = formatPresetDesc("Google Cloud 100MB", true, CellularGeneration.FIVE_G)
        assertEquals("Google Cloud 100MB • Aggregates Wi-Fi + 5G concurrently", text5G)

        val text4G = formatPresetDesc("Google Cloud 100MB", true, CellularGeneration.FOUR_G)
        assertEquals("Google Cloud 100MB • Aggregates Wi-Fi + 4G concurrently", text4G)

        val textUnknown = formatPresetDesc("Google Cloud 100MB", true, CellularGeneration.UNKNOWN)
        assertEquals("Google Cloud 100MB • Aggregates Wi-Fi + Cellular concurrently", textUnknown)
    }

    @Test
    fun testSingleStreamBannerText_AdaptsDetailLabel() {
        fun formatSingleStream(cellularDownloaded: Long, wifiDownloaded: Long, gen: CellularGeneration): String {
            val destination = if (cellularDownloaded > 0L && wifiDownloaded == 0L) gen.detailLabel else "Wi-Fi to preserve mobile data"
            return "Single-Stream Mode: Server does not support dual-channel splitting • Running on $destination"
        }

        val text5G = formatSingleStream(100L, 0L, CellularGeneration.FIVE_G)
        assertEquals("Single-Stream Mode: Server does not support dual-channel splitting • Running on 5G Cellular", text5G)

        val text4G = formatSingleStream(100L, 0L, CellularGeneration.FOUR_G)
        assertEquals("Single-Stream Mode: Server does not support dual-channel splitting • Running on 4G LTE Cellular", text4G)

        val textUnknown = formatSingleStream(100L, 0L, CellularGeneration.UNKNOWN)
        assertEquals("Single-Stream Mode: Server does not support dual-channel splitting • Running on Cellular", textUnknown)
    }

    @Test
    fun testCompletedBannerText_AdaptsDualChannelBoostSuffix() {
        fun formatCompletedBanner(isDualChannel: Boolean, gen: CellularGeneration): String {
            return if (isDualChannel) " • Aggregated Wi-Fi + ${gen.shortLabel} Boost ⚡" else ""
        }

        val text5G = formatCompletedBanner(true, CellularGeneration.FIVE_G)
        assertEquals(" • Aggregated Wi-Fi + 5G Boost ⚡", text5G)

        val text4G = formatCompletedBanner(true, CellularGeneration.FOUR_G)
        assertEquals(" • Aggregated Wi-Fi + 4G Boost ⚡", text4G)

        val textUnknown = formatCompletedBanner(true, CellularGeneration.UNKNOWN)
        assertEquals(" • Aggregated Wi-Fi + Cellular Boost ⚡", textUnknown)
    }

    @Test
    fun testSocialMediaModalSubtitle_AdaptsShortLabel() {
        fun formatSocialSubtitle(gen: CellularGeneration): String {
            return "Choose quality to accelerate download over Wi-Fi + ${gen.shortLabel}"
        }

        assertEquals("Choose quality to accelerate download over Wi-Fi + 5G", formatSocialSubtitle(CellularGeneration.FIVE_G))
        assertEquals("Choose quality to accelerate download over Wi-Fi + 4G", formatSocialSubtitle(CellularGeneration.FOUR_G))
        assertEquals("Choose quality to accelerate download over Wi-Fi + Cellular", formatSocialSubtitle(CellularGeneration.UNKNOWN))
    }
}
