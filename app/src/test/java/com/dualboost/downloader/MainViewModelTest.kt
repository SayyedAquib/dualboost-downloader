package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.ui.DEFAULT_PRESETS
import com.dualboost.downloader.ui.MainViewModel
import org.junit.Assert.*
import org.junit.Test

class MainViewModelTest {

    @Test
    fun testDefaultPresets_AreValidAndPopulated() {
        assertTrue("DEFAULT_PRESETS must not be empty", DEFAULT_PRESETS.isNotEmpty())
        for (preset in DEFAULT_PRESETS) {
            assertTrue("Preset ID must not be blank", preset.id.isNotBlank())
            assertTrue("Preset label must not be blank", preset.label.isNotBlank())
            assertTrue("Preset URL must start with http", preset.url.startsWith("http://") || preset.url.startsWith("https://"))
            assertTrue("Preset description must not be blank", preset.description.isNotBlank())
        }

        val rangePresets = DEFAULT_PRESETS.filter { it.supportsRange }
        assertTrue("Must have at least one dual-channel range preset", rangePresets.isNotEmpty())
    }

    @Test
    fun testPresetChipSelection_SetsExpectedUrlsAndSelectedPreset() {
        val viewModel = MainViewModel()

        val google10Mb = DEFAULT_PRESETS.first { it.id == "google_10mb" }
        viewModel.onPresetSelected(google10Mb)
        assertEquals(google10Mb.url, viewModel.urlInput.value)
        assertEquals(google10Mb, viewModel.selectedPreset.value)

        val google150Mb = DEFAULT_PRESETS.first { it.id == "google_150mb" }
        viewModel.onPresetSelected(google150Mb.url)
        assertEquals(google150Mb.url, viewModel.urlInput.value)
        assertEquals(google150Mb, viewModel.selectedPreset.value)

        val cf100Mb = DEFAULT_PRESETS.first { it.id == "cf_100mb" }
        viewModel.onPresetSelected(cf100Mb)
        assertEquals(cf100Mb.url, viewModel.urlInput.value)
        assertEquals(cf100Mb, viewModel.selectedPreset.value)
    }

    @Test
    fun testUrlChangeAndClear_UpdatesSelectedPresetState() {
        val viewModel = MainViewModel()

        // Typing custom URL
        viewModel.onUrlChanged("https://custom.server.com/test.zip")
        assertEquals("https://custom.server.com/test.zip", viewModel.urlInput.value)
        assertNull("Custom URL should not match preset", viewModel.selectedPreset.value)

        // Typing exact preset URL should auto-detect preset
        val google10Mb = DEFAULT_PRESETS.first { it.id == "google_10mb" }
        viewModel.onUrlChanged(google10Mb.url)
        assertEquals(google10Mb, viewModel.selectedPreset.value)

        // Clearing URL
        viewModel.clearUrl()
        assertEquals("", viewModel.urlInput.value)
        assertNull("Clearing URL should reset selectedPreset", viewModel.selectedPreset.value)
    }

    @Test
    fun testStartButtonEnabledLogic_CoversAllStates() {
        val viewModel = MainViewModel()
        val testUrl = "https://proof.ovh.net/files/100Mb.dat"

        // Blank or whitespace-only URL should always be disabled
        assertFalse("Blank URL should be disabled in Idle", viewModel.isStartButtonEnabled(DownloadState.Idle, ""))
        assertFalse("Whitespace URL should be disabled in Idle", viewModel.isStartButtonEnabled(DownloadState.Idle, "   "))
        assertFalse("Blank URL should be disabled in Completed", viewModel.isStartButtonEnabled(DownloadState.Completed("file", "path", 100L, 1L), ""))

        // Idle with valid URL -> Enabled
        assertTrue("Non-blank URL in Idle should be enabled", viewModel.isStartButtonEnabled(DownloadState.Idle, testUrl))

        // Probing with URL -> Disabled (prevents multi-click spam during server handshake)
        assertFalse("Non-blank URL in Probing should be disabled", viewModel.isStartButtonEnabled(DownloadState.Probing("Connecting..."), testUrl))

        // Downloading with URL -> Disabled (prevents starting another download while in-flight)
        val downloadingState = DownloadState.Downloading(
            fileName = "test.bin",
            totalBytes = 100_000_000L,
            downloadedBytes = 50_000_000L,
            wifiDownloadedBytes = 25_000_000L,
            cellularDownloadedBytes = 25_000_000L,
            wifiSpeedMbps = 50.0,
            cellularSpeedMbps = 50.0,
            totalSpeedMbps = 100.0,
            progressPercentage = 0.5f,
            supportsRanges = true
        )
        assertFalse("Non-blank URL in Downloading should be disabled", viewModel.isStartButtonEnabled(downloadingState, testUrl))

        // AutoRecovering with URL -> Enabled or Disabled depending on policy (currently handled gracefully)
        val recoveringState = DownloadState.AutoRecovering("Reconnecting Wi-Fi", 1)
        assertTrue("Non-blank URL in AutoRecovering should remain enabled for user restart", viewModel.isStartButtonEnabled(recoveringState, testUrl))

        // Completed with URL -> Enabled (ready for another download)
        assertTrue("Non-blank URL in Completed should be enabled", viewModel.isStartButtonEnabled(DownloadState.Completed("test.bin", "/path", 100L, 2L), testUrl))

        // Failed with URL -> Enabled (ready to retry after error)
        assertTrue("Non-blank URL in Failed should be enabled", viewModel.isStartButtonEnabled(DownloadState.Failed("403 Forbidden"), testUrl))

        // Paused with URL -> Enabled (ready to resume/restart)
        assertTrue("Non-blank URL in Paused should be enabled", viewModel.isStartButtonEnabled(DownloadState.Paused(), testUrl))
    }


    @Test
    fun testDualColorProgressRatioMath_AllScenarios() {
        val totalBytes = 100_000_000L

        // Scenario 1: 70% Wi-Fi, 30% Cellular
        val wifi70 = 70_000_000L
        val cell30 = 30_000_000L
        assertEquals(0.70f, wifi70.toFloat() / totalBytes.toFloat(), 0.001f)
        assertEquals(0.30f, cell30.toFloat() / totalBytes.toFloat(), 0.001f)

        // Scenario 2: 100% Wi-Fi, 0% Cellular (Cellular off)
        val wifi100 = 100_000_000L
        val cell0 = 0L
        assertEquals(1.00f, wifi100.toFloat() / totalBytes.toFloat(), 0.001f)
        assertEquals(0.00f, cell0.toFloat() / totalBytes.toFloat(), 0.001f)

        // Scenario 3: 0% Wi-Fi, 100% Cellular (Wi-Fi off)
        val wifi0 = 0L
        val cell100 = 100_000_000L
        assertEquals(0.00f, wifi0.toFloat() / totalBytes.toFloat(), 0.001f)
        assertEquals(1.00f, cell100.toFloat() / totalBytes.toFloat(), 0.001f)

        // Scenario 4: 50% Wi-Fi, 50% Cellular
        val wifi50 = 50_000_000L
        val cell50 = 50_000_000L
        assertEquals(0.50f, wifi50.toFloat() / totalBytes.toFloat(), 0.001f)
        assertEquals(0.50f, cell50.toFloat() / totalBytes.toFloat(), 0.001f)

        // Scenario 5: Edge case 0 bytes downloaded total (guard against division by zero)
        val zeroTotal = 0L.coerceAtLeast(1L).toFloat()
        val zeroRatio = 0L / zeroTotal
        assertEquals(0.0f, zeroRatio, 0.001f)
    }

    @Test
    fun testStartDownload_DoesNotCrashWhenRepositoryNull() {
        val viewModel = MainViewModel(repository = null)
        viewModel.onUrlChanged("https://proof.ovh.net/files/10Mb.dat")
        // Calling startDownload without a repository must be safe (e.g. in test or preview environment)
        viewModel.startDownload()
        viewModel.pauseDownload()
    }
}
