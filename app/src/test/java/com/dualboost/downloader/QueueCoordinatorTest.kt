package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadQueueRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.QueueItem
import com.dualboost.downloader.data.QueueItemStatus
import com.dualboost.downloader.engine.QueueCoordinator
import com.dualboost.downloader.engine.QueueDownloadDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class QueueCoordinatorTest {

    private lateinit var tempDir: File
    private lateinit var queueFile: File
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var activeCoordinator: QueueCoordinator? = null

    private class MockDownloadDispatcher : QueueDownloadDispatcher {
        val startedUrls = mutableListOf<String>()
        var pauseCalledCount = 0

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        override fun startDownload(url: String, forceSequentialMode: Boolean?) {
            startedUrls.add(url)
            _downloadState.value = DownloadState.Downloading(
                fileName = url.substringAfterLast('/'),
                totalBytes = 100_000_000L,
                downloadedBytes = 0L,
                wifiDownloadedBytes = 0L,
                cellularDownloadedBytes = 0L,
                wifiSpeedMBps = 10.0,
                cellularSpeedMBps = 10.0,
                totalSpeedMBps = 20.0,
                progressPercentage = 0f,
                supportsRanges = true
            )
        }

        override fun pauseDownload() {
            pauseCalledCount++
            _downloadState.value = DownloadState.Paused()
        }

        fun emitCompleted(fileName: String, url: String) {
            _downloadState.value = DownloadState.Completed(
                fileName = fileName,
                filePath = "/storage/emulated/0/Download/$fileName",
                totalBytes = 100_000_000L,
                durationSeconds = 10,
                averageSpeedMBps = 25.0
            )
        }

        fun emitFailed(error: String, url: String) {
            _downloadState.value = DownloadState.Failed(error = error, url = url)
        }
    }

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "dualboost_coord_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        queueFile = File(tempDir, "coord_queue.json")
    }

    @After
    fun tearDown() {
        activeCoordinator?.stop()
        activeCoordinator = null
        tempDir.deleteRecursively()
    }

    @Test
    fun testQueueCoordinator_StartsFirstItemWhenQueueIsStarted() = testScope.runTest {
        val repo = DownloadQueueRepository(queueFileOverride = queueFile, ioDispatcher = testDispatcher)
        val dispatcher = MockDownloadDispatcher()

        val item1 = QueueItem(url = "https://example.com/ep1.mp4", fileName = "ep1.mp4")
        val item2 = QueueItem(url = "https://example.com/ep2.mp4", fileName = "ep2.mp4")
        repo.enqueue(item1)
        repo.enqueue(item2)
        advanceUntilIdle()

        val coordinator = QueueCoordinator(
            queueRepository = repo,
            dispatcher = dispatcher,
            scope = this
        )
        activeCoordinator = coordinator

        assertFalse("Queue should initially be inactive", coordinator.isQueueActive.value)
        assertEquals(0, dispatcher.startedUrls.size)

        // Start Queue
        coordinator.startQueue()
        advanceUntilIdle()

        assertTrue(coordinator.isQueueActive.value)
        assertEquals(1, dispatcher.startedUrls.size)
        assertEquals("https://example.com/ep1.mp4", dispatcher.startedUrls.first())
        assertEquals(QueueItemStatus.DOWNLOADING, repo.queueItems.value.first().status)
        coordinator.stop()
    }

    @Test
    fun testQueueCoordinator_AutoAdvancesToNextItemOnDownloadCompleted() = testScope.runTest {
        val repo = DownloadQueueRepository(queueFileOverride = queueFile, ioDispatcher = testDispatcher)
        val dispatcher = MockDownloadDispatcher()

        repo.enqueue(QueueItem(url = "https://example.com/ep1.mp4", fileName = "ep1.mp4"))
        repo.enqueue(QueueItem(url = "https://example.com/ep2.mp4", fileName = "ep2.mp4"))
        advanceUntilIdle()

        val coordinator = QueueCoordinator(
            queueRepository = repo,
            dispatcher = dispatcher,
            scope = this
        )
        activeCoordinator = coordinator

        coordinator.startQueue()
        advanceUntilIdle()
        assertEquals(1, dispatcher.startedUrls.size)
        assertEquals("https://example.com/ep1.mp4", dispatcher.startedUrls.last())

        // Simulate ep1 completed
        dispatcher.emitCompleted("ep1.mp4", "https://example.com/ep1.mp4")
        advanceUntilIdle()

        val items = repo.queueItems.value
        assertEquals("ep1 must be marked COMPLETED", QueueItemStatus.COMPLETED, items[0].status)
        assertEquals("Coordinator must auto-start ep2", 2, dispatcher.startedUrls.size)
        assertEquals("https://example.com/ep2.mp4", dispatcher.startedUrls.last())
        assertEquals("ep2 must be marked DOWNLOADING", QueueItemStatus.DOWNLOADING, items[1].status)
        coordinator.stop()
    }

    @Test
    fun testQueueCoordinator_AutoAdvancesOnFailure() = testScope.runTest {
        val repo = DownloadQueueRepository(queueFileOverride = queueFile, ioDispatcher = testDispatcher)
        val dispatcher = MockDownloadDispatcher()

        repo.enqueue(QueueItem(url = "https://example.com/broken.mp4", fileName = "broken.mp4"))
        repo.enqueue(QueueItem(url = "https://example.com/valid.mp4", fileName = "valid.mp4"))
        advanceUntilIdle()

        val coordinator = QueueCoordinator(
            queueRepository = repo,
            dispatcher = dispatcher,
            scope = this
        )
        activeCoordinator = coordinator

        coordinator.startQueue()
        advanceUntilIdle()

        // Simulate failure on broken item
        dispatcher.emitFailed("Server returned 404", "https://example.com/broken.mp4")
        advanceUntilIdle()

        val items = repo.queueItems.value
        assertEquals("First item must be marked FAILED", QueueItemStatus.FAILED, items[0].status)
        assertEquals("Coordinator must advance to valid item", 2, dispatcher.startedUrls.size)
        assertEquals("https://example.com/valid.mp4", dispatcher.startedUrls.last())
        assertEquals(QueueItemStatus.DOWNLOADING, items[1].status)
        coordinator.stop()
    }

    @Test
    fun testQueueCoordinator_PauseQueue_PausesActiveDownload() = testScope.runTest {
        val repo = DownloadQueueRepository(queueFileOverride = queueFile, ioDispatcher = testDispatcher)
        val dispatcher = MockDownloadDispatcher()

        repo.enqueue(QueueItem(url = "https://example.com/ep1.mp4", fileName = "ep1.mp4"))
        advanceUntilIdle()

        val coordinator = QueueCoordinator(
            queueRepository = repo,
            dispatcher = dispatcher,
            scope = this
        )
        activeCoordinator = coordinator

        coordinator.startQueue()
        advanceUntilIdle()
        assertTrue(coordinator.isQueueActive.value)

        // Pause Queue
        coordinator.pauseQueue()
        advanceUntilIdle()

        assertFalse(coordinator.isQueueActive.value)
        assertEquals(1, dispatcher.pauseCalledCount)
        assertEquals(QueueItemStatus.PAUSED, repo.queueItems.value.first().status)
        coordinator.stop()
    }
}
