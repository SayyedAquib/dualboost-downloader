package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.ui.DEFAULT_PRESETS
import com.dualboost.downloader.ui.MainViewModel
import org.junit.Assert.*
import org.junit.Test

class UIFlowAndViewModelComprehensiveTest {

    @Test
    fun testAllPresetDefinitions_HaveValidAttributesAndProtocols() {
        assertEquals("Should have exactly 7 curated presets", 7, DEFAULT_PRESETS.size)

        val ids = DEFAULT_PRESETS.map { it.id }
        assertEquals("Preset IDs must be unique", ids.size, ids.distinct().size)

        for (preset in DEFAULT_PRESETS) {
            assertTrue("Preset ID '${preset.id}' must not be blank", preset.id.isNotBlank())
            assertTrue("Preset label '${preset.label}' must not be blank", preset.label.isNotBlank())
            assertTrue("Preset URL '${preset.url}' must use HTTPS", preset.url.startsWith("https://"))
            assertTrue("Preset description must describe speed and range capability", preset.description.length >= 10)
        }
    }

    @Test
    fun testSequentialPresetSwitching_MaintainsCorrectViewModelState() {
        val viewModel = MainViewModel()

        for (preset in DEFAULT_PRESETS) {
            viewModel.onPresetSelected(preset)
            assertEquals("URL must match selected preset", preset.url, viewModel.urlInput.value)
            assertEquals("Selected preset must match", preset, viewModel.selectedPreset.value)
        }
    }

    @Test
    fun testCustomUrlTyping_DeselectsPresetAutomatically() {
        val viewModel = MainViewModel()

        // Select first preset
        val firstPreset = DEFAULT_PRESETS.first()
        viewModel.onPresetSelected(firstPreset)
        assertEquals(firstPreset, viewModel.selectedPreset.value)

        // User edits single character
        viewModel.onUrlChanged(firstPreset.url + "x")
        assertEquals(firstPreset.url + "x", viewModel.urlInput.value)
        assertNull("Selected preset must be null when URL differs", viewModel.selectedPreset.value)
    }

    @Test
    fun testClearUrl_ResetsBothUrlAndPreset() {
        val viewModel = MainViewModel()
        viewModel.onPresetSelected(DEFAULT_PRESETS.last())

        viewModel.clearUrl()

        assertEquals("", viewModel.urlInput.value)
        assertNull(viewModel.selectedPreset.value)
    }

    @Test
    fun testStartButtonStateMatrix_Exhaustive() {
        val viewModel = MainViewModel()
        val validUrl = "https://speed.cloudflare.com/__down?bytes=10485760"

        // Invalid URL inputs across states
        val invalidUrls = listOf("", "   ", "\t", "\n", " \t \n ")
        for (invalid in invalidUrls) {
            assertFalse(viewModel.isStartButtonEnabled(DownloadState.Idle, invalid))
            assertFalse(viewModel.isStartButtonEnabled(DownloadState.Completed("file", "/path", 100, 1), invalid))
            assertFalse(viewModel.isStartButtonEnabled(DownloadState.Failed("Error"), invalid))
            assertFalse(viewModel.isStartButtonEnabled(DownloadState.Paused(), invalid))
        }

        // Active transfer states must ALWAYS disable the Start button (prevents concurrent download corruption)
        assertFalse(viewModel.isStartButtonEnabled(DownloadState.Probing("Connecting..."), validUrl))
        val activeDownloading = DownloadState.Downloading(
            fileName = "movie.mp4",
            totalBytes = 500_000_000L,
            downloadedBytes = 100_000_000L,
            wifiDownloadedBytes = 60_000_000L,
            cellularDownloadedBytes = 40_000_000L,
            wifiSpeedMbps = 25.0,
            cellularSpeedMbps = 15.0,
            totalSpeedMbps = 40.0,
            progressPercentage = 0.20f,
            supportsRanges = true
        )
        assertFalse(viewModel.isStartButtonEnabled(activeDownloading, validUrl))

        // Ready / Inactive states must ENABLE the Start button
        assertTrue(viewModel.isStartButtonEnabled(DownloadState.Idle, validUrl))
        assertTrue(viewModel.isStartButtonEnabled(DownloadState.Completed("done.bin", "/path", 1000, 5), validUrl))
        assertTrue(viewModel.isStartButtonEnabled(DownloadState.Failed("Connection dropped"), validUrl))
        assertTrue(viewModel.isStartButtonEnabled(DownloadState.Paused(), validUrl))
        assertTrue(viewModel.isStartButtonEnabled(DownloadState.AutoRecovering("Waiting for network", 1), validUrl))
    }


    @Test
    fun testDualColorProgressSplit_FormulaPrecision() {
        fun computeSplits(wifiBytes: Long, cellBytes: Long, totalBytes: Long): Pair<Float, Float> {
            val total = maxOf(1L, totalBytes).toFloat()
            val wifiRatio = (wifiBytes.toFloat() / total).coerceIn(0f, 1f)
            val cellRatio = (cellBytes.toFloat() / total).coerceIn(0f, 1f - wifiRatio)
            return Pair(wifiRatio, cellRatio)
        }

        // 1. Balanced: 50 MB Wi-Fi, 50 MB Cellular out of 100 MB
        val (w1, c1) = computeSplits(50_000_000L, 50_000_000L, 100_000_000L)
        assertEquals(0.50f, w1, 0.0001f)
        assertEquals(0.50f, c1, 0.0001f)
        assertEquals(1.00f, w1 + c1, 0.0001f)

        // 2. Wi-Fi Dominant: 90 MB Wi-Fi, 10 MB Cellular
        val (w2, c2) = computeSplits(90_000_000L, 10_000_000L, 100_000_000L)
        assertEquals(0.90f, w2, 0.0001f)
        assertEquals(0.10f, c2, 0.0001f)

        // 3. Cellular Only: 0 MB Wi-Fi, 100 MB Cellular
        val (w3, c3) = computeSplits(0L, 100_000_000L, 100_000_000L)
        assertEquals(0.00f, w3, 0.0001f)
        assertEquals(1.00f, c3, 0.0001f)

        // 4. Wi-Fi Only: 100 MB Wi-Fi, 0 MB Cellular
        val (w4, c4) = computeSplits(100_000_000L, 0L, 100_000_000L)
        assertEquals(1.00f, w4, 0.0001f)
        assertEquals(0.00f, c4, 0.0001f)

        // 5. Zero downloaded bytes at start
        val (w5, c5) = computeSplits(0L, 0L, 100_000_000L)
        assertEquals(0.00f, w5, 0.0001f)
        assertEquals(0.00f, c5, 0.0001f)

        // 6. Zero total bytes edge case (protection against division by zero)
        val (w6, c6) = computeSplits(0L, 0L, 0L)
        assertEquals(0.00f, w6, 0.0001f)
        assertEquals(0.00f, c6, 0.0001f)
    }
}

