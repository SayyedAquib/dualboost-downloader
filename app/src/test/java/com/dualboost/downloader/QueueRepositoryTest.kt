package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadQueueRepository
import com.dualboost.downloader.data.QueueItem
import com.dualboost.downloader.data.QueueItemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class QueueRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var queueFile: File
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "dualboost_queue_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        queueFile = File(tempDir, "test_download_queue.json")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testEnqueue_AddsItemWithQueuedStatusAndTimestamp() = runTest(testDispatcher) {
        val repo = DownloadQueueRepository(
            queueFileOverride = queueFile,
            ioDispatcher = testDispatcher
        )

        val item = QueueItem(
            url = "https://example.com/movie.mkv",
            fileName = "movie.mkv"
        )

        repo.enqueue(item)
        advanceUntilIdle()

        val items = repo.queueItems.value
        assertEquals("Queue must contain exactly 1 item", 1, items.size)
        assertEquals("movie.mkv", items[0].fileName)
        assertEquals("https://example.com/movie.mkv", items[0].url)
        assertEquals(QueueItemStatus.QUEUED, items[0].status)
        assertTrue("Timestamp must be valid", items[0].addedTimestamp > 0)
    }

    @Test
    fun testPersistence_SavesAndRestoresAcrossProcessRestarts() = runTest(testDispatcher) {
        val repo1 = DownloadQueueRepository(
            queueFileOverride = queueFile,
            ioDispatcher = testDispatcher
        )

        val item1 = QueueItem(url = "https://example.com/ep1.mp4", fileName = "ep1.mp4")
        val item2 = QueueItem(url = "https://example.com/ep2.mp4", fileName = "ep2.mp4")

        repo1.enqueue(item1)
        repo1.enqueue(item2)
        advanceUntilIdle()

        assertTrue("Queue file must exist on disk", queueFile.exists())

        // Create a new repository instance pointing to the same file (simulating app restart)
        val repo2 = DownloadQueueRepository(
            queueFileOverride = queueFile,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        val restoredItems = repo2.queueItems.value
        assertEquals("Must restore exactly 2 items", 2, restoredItems.size)
        assertEquals("ep1.mp4", restoredItems[0].fileName)
        assertEquals("ep2.mp4", restoredItems[1].fileName)
    }

    @Test
    fun testFIFO_OrderingPreservedOnRetrieval() = runTest(testDispatcher) {
        val repo = DownloadQueueRepository(
            queueFileOverride = queueFile,
            ioDispatcher = testDispatcher
        )

        repo.enqueue(QueueItem(url = "https://example.com/1.zip", fileName = "1.zip"))
        repo.enqueue(QueueItem(url = "https://example.com/2.zip", fileName = "2.zip"))
        repo.enqueue(QueueItem(url = "https://example.com/3.zip", fileName = "3.zip"))
        advanceUntilIdle()

        val items = repo.queueItems.value
        assertEquals(3, items.size)
        assertEquals("1.zip", items[0].fileName)
        assertEquals("2.zip", items[1].fileName)
        assertEquals("3.zip", items[2].fileName)
    }

    @Test
    fun testStatusUpdates_TransitionsQueuedToDownloadingToCompleted() = runTest(testDispatcher) {
        val repo = DownloadQueueRepository(
            queueFileOverride = queueFile,
            ioDispatcher = testDispatcher
        )

        val item = QueueItem(url = "https://example.com/video.mkv", fileName = "video.mkv")
        repo.enqueue(item)
        advanceUntilIdle()

        val itemId = repo.queueItems.value.first().id

        // Transition to DOWNLOADING
        repo.updateStatus(itemId, QueueItemStatus.DOWNLOADING)
        advanceUntilIdle()
        assertEquals(QueueItemStatus.DOWNLOADING, repo.queueItems.value.first().status)

        // Update progress
        repo.updateProgress(itemId, downloadedBytes = 50_000_000L, totalBytes = 100_000_000L)
        advanceUntilIdle()
        assertEquals(50_000_000L, repo.queueItems.value.first().downloadedBytes)
        assertEquals(100_000_000L, repo.queueItems.value.first().totalBytes)
        assertEquals(0.5f, repo.queueItems.value.first().progressPercentage, 0.001f)

        // Transition to COMPLETED
        repo.updateStatus(itemId, QueueItemStatus.COMPLETED)
        advanceUntilIdle()
        assertEquals(QueueItemStatus.COMPLETED, repo.queueItems.value.first().status)
    }

    @Test
    fun testRemoveAndClearCompleted_MaintainsListIntegrity() = runTest(testDispatcher) {
        val repo = DownloadQueueRepository(
            queueFileOverride = queueFile,
            ioDispatcher = testDispatcher
        )

        val item1 = QueueItem(url = "https://example.com/a.bin", fileName = "a.bin")
        val item2 = QueueItem(url = "https://example.com/b.bin", fileName = "b.bin")
        val item3 = QueueItem(url = "https://example.com/c.bin", fileName = "c.bin")

        repo.enqueue(item1)
        repo.enqueue(item2)
        repo.enqueue(item3)
        advanceUntilIdle()

        // Remove item2 by ID
        repo.removeItem(item2.id)
        advanceUntilIdle()

        var items = repo.queueItems.value
        assertEquals(2, items.size)
        assertEquals(listOf("a.bin", "c.bin"), items.map { it.fileName })

        // Mark item1 completed
        repo.updateStatus(item1.id, QueueItemStatus.COMPLETED)
        advanceUntilIdle()

        // Clear completed
        repo.clearCompleted()
        advanceUntilIdle()

        items = repo.queueItems.value
        assertEquals(1, items.size)
        assertEquals("c.bin", items[0].fileName)
        assertEquals(QueueItemStatus.QUEUED, items[0].status)
    }

    @Test
    fun testReorder_MovesItemToNewIndex() = runTest(testDispatcher) {
        val repo = DownloadQueueRepository(
            queueFileOverride = queueFile,
            ioDispatcher = testDispatcher
        )

        val itemA = QueueItem(url = "https://example.com/a.mkv", fileName = "a.mkv")
        val itemB = QueueItem(url = "https://example.com/b.mkv", fileName = "b.mkv")
        val itemC = QueueItem(url = "https://example.com/c.mkv", fileName = "c.mkv")

        repo.enqueue(itemA)
        repo.enqueue(itemB)
        repo.enqueue(itemC)
        advanceUntilIdle()

        // Move itemC (index 2) to index 0
        repo.reorder(fromIndex = 2, toIndex = 0)
        advanceUntilIdle()

        val items = repo.queueItems.value
        assertEquals(listOf("c.mkv", "a.mkv", "b.mkv"), items.map { it.fileName })
    }
}
