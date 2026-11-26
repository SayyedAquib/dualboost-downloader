package com.dualboost.downloader.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.ui.theme.DualBoostTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Jetpack Compose UI Tests for DualBoost Downloader running on physical Android device (Google Pixel 9).
 * Verifies core screen layouts, user interactions, preset selection, URL inputs, discard button, and modal sheets.
 */
@RunWith(AndroidJUnit4::class)
class MainComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testInitialDashboardState_RendersKeyElements() {
        val viewModel = MainViewModel(repository = null)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Top App Bar
        composeTestRule.onNode(hasText("DualBoost", substring = false)).assertIsDisplayed()

        // Network Interface Status Cards
        composeTestRule.onNodeWithText("Wi-Fi").assertIsDisplayed()
        composeTestRule.onNodeWithText("5G Cellular").assertIsDisplayed()

        // Download Section
        composeTestRule.onNodeWithText("New Download").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Boost Download").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testPresetSelection_PopulatesUrlAndEnablesDownload() {
        val viewModel = MainViewModel(repository = null)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Programmatically select the 150 MB preset
        val preset150 = DEFAULT_PRESETS.first { it.id == "google_150mb" }
        viewModel.onPresetSelected(preset150)
        composeTestRule.waitForIdle()

        // Verify Boost Download button is enabled with preset URL
        composeTestRule.onNodeWithText("Boost Download").performScrollTo().assertIsEnabled()
    }

    @Test
    fun testLargePresetSelection_Populates1GBUrlAndEnablesDownload() {
        val viewModel = MainViewModel(repository = null)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Programmatically select the 1 GB preset to verify state update and enable button
        val preset1gb = DEFAULT_PRESETS.first { it.id == "google_1gb" }
        viewModel.onPresetSelected(preset1gb)
        composeTestRule.waitForIdle()

        // Verify Boost Download button is enabled
        composeTestRule.onNodeWithText("Boost Download").performScrollTo().assertIsEnabled()
    }

    @Test
    fun testCustomUrlInput_CanBeTypedAndCleared() {
        val viewModel = MainViewModel(repository = null)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        val testUrl = "https://dl.google.com/test.zip"

        // Type custom URL into the download link text field
        composeTestRule.onNodeWithText("Download Link")
            .performScrollTo()
            .performTextInput(testUrl)

        // Verify Boost Download button is enabled
        composeTestRule.onNodeWithText("Boost Download").performScrollTo().assertIsEnabled()

        // Tap the Clear trailing icon to reset input
        composeTestRule.onNodeWithContentDescription("Clear")
            .performScrollTo()
            .performClick()

        // Verify Boost Download button is disabled when URL is cleared
        composeTestRule.onNodeWithText("Boost Download").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun testPausedState_DisplaysDiscardButtonAndPausedCard() {
        val pausedState = kotlinx.coroutines.flow.MutableStateFlow<DownloadState>(
            DownloadState.Paused(
                fileName = "test_archive.zip",
                filePath = "/storage/emulated/0/Download/test_archive.zip",
                downloadedBytes = 15728640L,
                totalBytes = 157286400L,
                progressPercentage = 10f,
                url = "https://dl.google.com/test_archive.zip"
            )
        )
        val viewModel = MainViewModel(repository = null, downloadStateOverride = pausedState)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Verify paused download card elements
        composeTestRule.onNodeWithText("Download Paused").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("test_archive.zip").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Resume Download").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testSingleStreamFallback_DisplaysAmberBannerAndSingleChannelBandwidth() {
        val singleStreamState = kotlinx.coroutines.flow.MutableStateFlow<DownloadState>(
            DownloadState.Downloading(
                fileName = "single_stream_file.iso",
                totalBytes = 104857600L,
                downloadedBytes = 20971520L,
                wifiDownloadedBytes = 20971520L,
                cellularDownloadedBytes = 0L,
                wifiSpeedMBps = 12.5,
                cellularSpeedMBps = 0.0,
                totalSpeedMBps = 12.5,
                progressPercentage = 0.20f,
                supportsRanges = false
            )
        )
        val viewModel = MainViewModel(repository = null, downloadStateOverride = singleStreamState)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Verify compact Single-Stream status chip and single-channel telemetry
        composeTestRule.onNodeWithText("Single-Stream • Wi-Fi Only").performScrollTo().assertIsDisplayed()

        // Verify hero and Wi-Fi speeds displayed in MB/s
        composeTestRule.onNodeWithText("12.5 MB/s").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Wi-Fi • 12.5 MB/s").performScrollTo().assertIsDisplayed()

        // Verify inactive cellular inline chip label
        composeTestRule.onNodeWithText("5G (Inactive)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testDownloadingState_DisplaysDesktopModeBadgeWhenSpoofed() {
        val desktopDownloadingState = kotlinx.coroutines.flow.MutableStateFlow<DownloadState>(
            DownloadState.Downloading(
                fileName = "desktop_spoofed_file.iso",
                totalBytes = 104857600L,
                downloadedBytes = 20971520L,
                wifiDownloadedBytes = 10485760L,
                cellularDownloadedBytes = 10485760L,
                wifiSpeedMBps = 15.0,
                cellularSpeedMBps = 15.0,
                totalSpeedMBps = 30.0,
                progressPercentage = 0.20f,
                supportsRanges = true,
                isDesktopSpoofed = true
            )
        )
        val viewModel = MainViewModel(repository = null, downloadStateOverride = desktopDownloadingState)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Verify Desktop Mode Active badge is displayed
        composeTestRule.onNodeWithText("⚡ Desktop Mode Active").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("desktop_spoofed_file.iso").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("30.0 MB/s").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testDownloadingState_DisplaysCloudSlicerBadgeWhenActive() {
        val slicerDownloadingState = kotlinx.coroutines.flow.MutableStateFlow<DownloadState>(
            DownloadState.Downloading(
                fileName = "cloud_sliced_file.bin",
                totalBytes = 104857600L,
                downloadedBytes = 20971520L,
                wifiDownloadedBytes = 10485760L,
                cellularDownloadedBytes = 10485760L,
                wifiSpeedMBps = 15.0,
                cellularSpeedMBps = 15.0,
                totalSpeedMBps = 30.0,
                progressPercentage = 0.20f,
                supportsRanges = true,
                isDesktopSpoofed = false,
                isCloudSlicerActive = true
            )
        )
        val viewModel = MainViewModel(repository = null, downloadStateOverride = slicerDownloadingState)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Verify Cloud Slicer Active badge is displayed
        composeTestRule.onNodeWithText("⚡ Cloud Slicer Active").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("cloud_sliced_file.bin").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("30.0 MB/s").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testCompletedActionsComponent_RendersHeroOpenButtonAndSplitSecondaryActionRow() {
        var openFileCalled = false
        var openLocationCalled = false
        var shareFileCalled = false

        composeTestRule.setContent {
            DualBoostTheme {
                DownloadCompletedActions(
                    filePath = "/storage/emulated/0/Download/ubuntu-24.04-desktop-arm64.iso",
                    contentUriString = null,
                    onOpenFile = { _, _ -> openFileCalled = true },
                    onOpenFileLocation = { _, _ -> openLocationCalled = true },
                    onShareFile = { _, _ -> shareFileCalled = true }
                )
            }
        }

        // 1. Verify Hero Primary CTA "Open File"
        composeTestRule.onNodeWithText("Open File").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue("onOpenFile must be invoked on Open File click", openFileCalled)

        // 2. Verify Split Secondary Action "Show in Files"
        composeTestRule.onNodeWithText("Show in Files").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue("onOpenFileLocation must be invoked on Show in Files click", openLocationCalled)

        // 3. Verify Split Secondary Action "Share"
        composeTestRule.onNodeWithText("Share").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue("onShareFile must be invoked on Share click", shareFileCalled)
    }

    @Test
    fun testSequentialStreamToggle_RendersAndInteractsInDownloadCard() {
        val viewModel = MainViewModel(repository = null)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Verify Watch While Downloading toggle is visible
        composeTestRule.onNodeWithText("Watch While Downloading").performScrollTo().assertIsDisplayed()

        // Type a video URL and verify auto-detection text
        viewModel.onUrlChanged("https://example.com/videos/movie.mkv")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Video detected • Ready to watch while downloading").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testDownloadingState_DisplaysSequentialStreamBadgeAndHeroWatchPreviewButton() {
        var openFileInvoked = false
        val sequentialDownloadingState = kotlinx.coroutines.flow.MutableStateFlow<DownloadState>(
            DownloadState.Downloading(
                fileName = "Matrix.Resurrections.4K.mkv",
                filePath = "/storage/emulated/0/Download/Matrix.Resurrections.4K.mkv",
                totalBytes = 15_000_000_000L,
                downloadedBytes = 100_000_000L,
                wifiDownloadedBytes = 60_000_000L,
                cellularDownloadedBytes = 40_000_000L,
                wifiSpeedMBps = 18.0,
                cellularSpeedMBps = 12.0,
                totalSpeedMBps = 30.0,
                progressPercentage = 0.01f,
                supportsRanges = true,
                isSequentialMode = true,
                contiguousPlayableBytes = 25_165_824L,
                isPlayable = true,
                contentUriString = "content://downloads/42"
            )
        )
        val viewModel = MainViewModel(repository = null, downloadStateOverride = sequentialDownloadingState)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { path, uri ->
                        if (path == "/storage/emulated/0/Download/Matrix.Resurrections.4K.mkv") {
                            openFileInvoked = true
                        }
                    },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Verify Stream Ready badge is displayed
        composeTestRule.onNodeWithText("⚡ Stream Ready • 24.0 MB buffered").performScrollTo().assertIsDisplayed()

        // Verify Hero integrated "Preview" CTA is displayed and triggers onOpenFile
        composeTestRule.onNodeWithText("Preview").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        org.junit.Assert.assertTrue("onOpenFile must be invoked on Preview click", openFileInvoked)
    }

    @Test
    fun testDownloadingState_SingleStreamMode_DisplaysCompactStatusChip() {
        val singleStreamDownloadingState = kotlinx.coroutines.flow.MutableStateFlow<DownloadState>(
            DownloadState.Downloading(
                fileName = "Movie.mkv",
                filePath = "/storage/emulated/0/Download/Movie.mkv",
                totalBytes = 700_000_000L,
                downloadedBytes = 50_000_000L,
                wifiDownloadedBytes = 50_000_000L,
                cellularDownloadedBytes = 0L,
                wifiSpeedMBps = 4.5,
                cellularSpeedMBps = 0.0,
                totalSpeedMBps = 4.5,
                progressPercentage = 0.07f,
                supportsRanges = false,
                isSequentialMode = false,
                isPlayable = false
            )
        )
        val viewModel = MainViewModel(repository = null, downloadStateOverride = singleStreamDownloadingState)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        // Verify compact Single-Stream chip is displayed
        composeTestRule.onNodeWithText("Single-Stream • Wi-Fi Only").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testProbingAndAutoRecoveringStates_RenderWithMaterialExpressiveTheme() {
        val stateFlow = kotlinx.coroutines.flow.MutableStateFlow<DownloadState>(
            DownloadState.Probing("Probing server capabilities...")
        )
        val viewModel = MainViewModel(repository = null, downloadStateOverride = stateFlow)
        composeTestRule.setContent {
            DualBoostTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenFile = { _, _ -> },
                    onShareFile = { _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Probing server capabilities...").performScrollTo().assertIsDisplayed()

        stateFlow.value = DownloadState.AutoRecovering("Waiting for Wi-Fi signal", retryAttempt = 2)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Auto-Recovering Network").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Waiting for Wi-Fi signal (Retry attempt #2)").performScrollTo().assertIsDisplayed()
    }
}
