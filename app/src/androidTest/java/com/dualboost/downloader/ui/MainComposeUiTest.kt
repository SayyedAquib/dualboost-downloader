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
        composeTestRule.onNodeWithText("DualBoost").assertIsDisplayed()

        // Network Interface Status Cards
        composeTestRule.onNodeWithText("Wi-Fi").assertIsDisplayed()
        composeTestRule.onNodeWithText("5G Cellular").assertIsDisplayed()

        // Quick Benchmark Card
        composeTestRule.onNodeWithText("Speed Benchmark").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Speed").performScrollTo().assertIsDisplayed()

        // Download Section
        composeTestRule.onNodeWithText("New Download").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("150 MB").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready to Download").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testPresetChipSelection_PopulatesUrlAndEnablesDownload() {
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

        // Click on the 150 MB Range Benchmark preset chip
        composeTestRule.onNodeWithText("150 MB").performScrollTo().performClick()

        // Verify the descriptive label appears indicating dual aggregation capability
        composeTestRule.onNodeWithText(
            "Google Edge CDN (Full Aggregation Stress Test) • Aggregates Wi-Fi + 5G concurrently"
        ).performScrollTo().assertIsDisplayed()

        // Verify Boost Download button is enabled
        composeTestRule.onNodeWithText("Boost Download").performScrollTo().assertIsEnabled()
    }

    @Test
    fun testLargePresetChipSelection_Populates1GBUrlAndEnablesDownload() {
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

        // Click on the 1 GB Range Benchmark preset chip in the horizontal carousel
        composeTestRule.onNodeWithText("1 GB").performScrollTo().performClick()

        // Verify the descriptive label appears indicating dual aggregation capability for 1 GB tier
        composeTestRule.onNodeWithText(
            "Google Edge CDN (Massive 1.2 GB Endurance Stress Test) • Aggregates Wi-Fi + 5G concurrently"
        ).performScrollTo().assertIsDisplayed()

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
    fun testSpeedBenchmarkBottomSheet_OpensAndDismissesCleanly() {
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

        // Tap on Test Speed button on dashboard
        composeTestRule.onNodeWithText("Test Speed").performScrollTo().performClick()

        // Verify Speed Benchmark Bottom Sheet is displayed
        composeTestRule.onNodeWithText("Start Speed Benchmark").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ephemeral stream · 0 bytes stored").assertIsDisplayed()

        // Dismiss the sheet by tapping the Close button
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed().performClick()

        // Verify bottom sheet dismissed and returned to main dashboard
        composeTestRule.onNodeWithText("Ready to Download").performScrollTo().assertIsDisplayed()
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
}
