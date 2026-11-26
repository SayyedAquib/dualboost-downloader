package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadQueueRepository
import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.QueueItemStatus
import com.dualboost.downloader.social.MediaQuality
import com.dualboost.downloader.social.SocialIntakeState
import com.dualboost.downloader.social.SocialMediaAsset
import com.dualboost.downloader.social.SocialMediaResolver
import com.dualboost.downloader.social.SocialPlatform
import com.dualboost.downloader.ui.MainViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SocialIntakeViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var queueFile: File
    private lateinit var queueRepo: DownloadQueueRepository

    private class MockSocialResolver(
        private val mockAsset: SocialMediaAsset
    ) : SocialMediaResolver() {
        var resolveCount = 0
        var lastQuality: MediaQuality? = null

        override suspend fun resolve(url: String, quality: MediaQuality): Result<SocialMediaAsset> {
            resolveCount++
            lastQuality = quality
            return Result.success(mockAsset)
        }
    }

    @Before
    fun setUp() {
        val baseDir = tempFolder.newFolder("social_vm_test")
        queueFile = File(baseDir, "test_queue.json")
        queueRepo = DownloadQueueRepository(queueFileOverride = queueFile, ioDispatcher = testDispatcher)
    }

    @Test
    fun checkAndPrepareSocialLink_detectsInstagramAndSetsReadyState() {
        val viewModel = MainViewModel(repository = null)

        val instagramUrl = "https://www.instagram.com/reel/C8xyz123/?igsh=abcdef"
        val handled = viewModel.checkAndPrepareSocialLink(instagramUrl)

        assertTrue(handled)
        val state = viewModel.socialIntakeState.value
        assertTrue("State should be Ready", state is SocialIntakeState.Ready)
        val ready = state as SocialIntakeState.Ready
        assertEquals(SocialPlatform.INSTAGRAM, ready.platform)
        assertEquals(instagramUrl, ready.originalUrl)
    }

    @Test
    fun startSocialDownload_resolvesHighestQualityAndStartsDownload() = testScope.runTest {
        val mockAsset = SocialMediaAsset(
            originalUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            platform = SocialPlatform.YOUTUBE,
            title = "Rick Astley",
            streamUrl = "https://cdn.googlevideo.com/playback.mp4",
            suggestedFilename = "Rick_Astley.mp4"
        )
        val mockResolver = MockSocialResolver(mockAsset)

        val viewModel = MainViewModel(
            repository = null,
            socialResolverOverride = mockResolver,
            coroutineScopeOverride = this
        )

        viewModel.checkAndPrepareSocialLink("https://youtube.com/watch?v=dQw4w9WgXcQ")
        viewModel.startSocialDownload("https://youtube.com/watch?v=dQw4w9WgXcQ", MediaQuality.MAX)
        advanceUntilIdle()

        assertEquals(1, mockResolver.resolveCount)
        assertEquals(MediaQuality.MAX, mockResolver.lastQuality)
        assertEquals("https://cdn.googlevideo.com/playback.mp4", viewModel.urlInput.value)
        assertEquals(SocialIntakeState.Idle, viewModel.socialIntakeState.value)
    }

    @Test
    fun dismissSocialIntake_resetsStateToIdle() {
        val viewModel = MainViewModel(repository = null)
        viewModel.checkAndPrepareSocialLink("https://www.tiktok.com/@user/video/123456789")
        assertTrue(viewModel.socialIntakeState.value is SocialIntakeState.Ready)

        viewModel.dismissSocialIntake()
        assertEquals(SocialIntakeState.Idle, viewModel.socialIntakeState.value)
    }

    @Test
    fun dismissSocialIntake_cancelsInFlightResolutionBeforeLateCompletion() = testScope.runTest {
        val deferred = CompletableDeferred<Result<SocialMediaAsset>>()
        val delayedResolver = object : SocialMediaResolver() {
            override suspend fun resolve(url: String, quality: MediaQuality): Result<SocialMediaAsset> {
                return deferred.await()
            }
        }
        val viewModel = MainViewModel(
            repository = null,
            socialResolverOverride = delayedResolver,
            coroutineScopeOverride = this
        )

        val tiktokUrl = "https://www.tiktok.com/@user/video/987654"
        viewModel.checkAndPrepareSocialLink(tiktokUrl)
        viewModel.startSocialDownload(tiktokUrl, MediaQuality.HD_720)
        runCurrent()
        assertTrue(viewModel.socialIntakeState.value is SocialIntakeState.Resolving)

        viewModel.dismissSocialIntake()
        assertEquals(SocialIntakeState.Idle, viewModel.socialIntakeState.value)

        // Late completion after dismissal must be ignored
        deferred.complete(
            Result.success(
                SocialMediaAsset(
                    originalUrl = tiktokUrl,
                    platform = SocialPlatform.TIKTOK,
                    title = "Clip",
                    streamUrl = "https://cdn.tiktok.com/video.mp4",
                    suggestedFilename = "clip.mp4"
                )
            )
        )
        advanceUntilIdle()

        assertEquals(SocialIntakeState.Idle, viewModel.socialIntakeState.value)
        assertEquals("", viewModel.urlInput.value)
    }

    @Test
    fun retrySocialResolution_transitionsFromErrorBackToResolved() = testScope.runTest {
        var attempt = 0
        val flakyResolver = object : SocialMediaResolver() {
            override suspend fun resolve(url: String, quality: MediaQuality): Result<SocialMediaAsset> {
                attempt++
                return if (attempt == 1) {
                    Result.failure(IllegalStateException("Temporary network error"))
                } else {
                    Result.success(
                        SocialMediaAsset(
                            originalUrl = url,
                            platform = SocialPlatform.YOUTUBE,
                            title = "Recovered Video",
                            streamUrl = "https://cdn.googlevideo.com/recovered.mp4",
                            suggestedFilename = "recovered.mp4"
                        )
                    )
                }
            }
        }
        val viewModel = MainViewModel(
            repository = null,
            socialResolverOverride = flakyResolver,
            coroutineScopeOverride = this
        )

        val ytUrl = "https://youtube.com/watch?v=abc12345"
        viewModel.checkAndPrepareSocialLink(ytUrl)
        viewModel.startSocialDownload(ytUrl, MediaQuality.HD_720)
        advanceUntilIdle()

        val errorState = viewModel.socialIntakeState.value as? SocialIntakeState.Error
        assertNotNull(errorState)
        assertEquals(SocialPlatform.YOUTUBE, errorState?.platform)
        assertEquals(MediaQuality.HD_720, errorState?.attemptedQuality)

        viewModel.retrySocialResolution(ytUrl, errorState!!.attemptedQuality)
        advanceUntilIdle()

        assertEquals(SocialIntakeState.Idle, viewModel.socialIntakeState.value)
        assertEquals("https://cdn.googlevideo.com/recovered.mp4", viewModel.urlInput.value)
    }
}
