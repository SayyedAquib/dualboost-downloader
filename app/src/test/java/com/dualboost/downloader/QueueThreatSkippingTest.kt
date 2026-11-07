package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadQueueRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.QueueItem
import com.dualboost.downloader.data.QueueItemStatus
import com.dualboost.downloader.engine.QueueCoordinator
import com.dualboost.downloader.engine.QueueDownloadDispatcher
import com.dualboost.downloader.security.ThreatAssessment
import com.dualboost.downloader.security.ThreatSeverity
import com.dualboost.downloader.security.ThreatType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class QueueThreatSkippingTest {

    private lateinit var tempDir: File
    private lateinit var queueFile: File
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var coordinator: QueueCoordinator? = null

    private class MockThreatDispatcher : QueueDownloadDispatcher {
        val startedUrls = mutableListOf<String>()

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        override val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        override fun startDownload(url: String, forceSequentialMode: Boolean?) {
            startedUrls.add(url)
            _downloadState.value = DownloadState.Downloading(
                fileName = url.substringAfterLast('/'),
                totalBytes = 10_000_000L,
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
            _downloadState.value = DownloadState.Paused()
        }

        fun emitThreatDetected(threat: ThreatAssessment, url: String, fileName: String) {
            _downloadState.value = DownloadState.ThreatDetected(threat, url, fileName)
        }
    }

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "dualboost_threat_queue_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        queueFile = File(tempDir, "threat_queue.json")
    }

    @After
    fun tearDown() {
        coordinator?.stop()
        coordinator = null
        tempDir.deleteRecursively()
    }

    @Test
    fun maliciousItemInQueue_isMarkedThreatDetectedAndQueueAdvancesToNextItem() = testScope.runTest {
        val repo = DownloadQueueRepository(queueFileOverride = queueFile, ioDispatcher = testDispatcher)
        val dispatcher = MockThreatDispatcher()

        val item1Malicious = QueueItem(
            url = "https://moviesleech.media/Mirzapur.4K.mkv.apk",
            fileName = "Mirzapur.4K.mkv.apk"
        )
        val item2Clean = QueueItem(
            url = "https://example.com/episode2.mkv",
            fileName = "episode2.mkv"
        )

        repo.enqueue(item1Malicious)
        repo.enqueue(item2Clean)
        advanceUntilIdle()

        coordinator = QueueCoordinator(repo, dispatcher, testScope)
        coordinator?.startQueue()
        advanceUntilIdle()

        // Verify item 1 was started
        assertEquals(1, dispatcher.startedUrls.size)
        assertEquals("https://moviesleech.media/Mirzapur.4K.mkv.apk", dispatcher.startedUrls[0])

        // Emit ThreatDetected on item 1
        val threat = ThreatAssessment(
            isThreat = true,
            threatType = ThreatType.DOUBLE_EXTENSION,
            severity = ThreatSeverity.CRITICAL,
            description = "Deceptive double extension .mkv.apk",
            originalTarget = "Mirzapur.4K.mkv.apk",
            disguisedTarget = "Mirzapur.4K.mkv.apk"
        )
        dispatcher.emitThreatDetected(threat, item1Malicious.url, item1Malicious.fileName)
        advanceUntilIdle()

        // Verify item 1 is marked THREAT_DETECTED with error message
        val updatedItems = repo.queueItems.value
        val updatedItem1 = updatedItems.first { it.id == item1Malicious.id }
        assertEquals(QueueItemStatus.THREAT_DETECTED, updatedItem1.status)
        assertNotNull(updatedItem1.errorMessage)
        assertTrue(updatedItem1.errorMessage!!.contains("DOUBLE_EXTENSION"))

        // Verify queue automatically advanced to item 2!
        assertEquals(2, dispatcher.startedUrls.size)
        assertEquals("https://example.com/episode2.mkv", dispatcher.startedUrls[1])
        val updatedItem2 = updatedItems.first { it.id == item2Clean.id }
        assertEquals(QueueItemStatus.DOWNLOADING, updatedItem2.status)

        coordinator?.stop()
    }

    @Test
    fun queueItemSerialization_supportsThreatDetectedStatus() {
        val item = QueueItem(
            url = "https://ad-redirector.net/codec.exe",
            fileName = "codec.exe",
            status = QueueItemStatus.THREAT_DETECTED,
            errorMessage = "[VIRUS BLOCKED: DISGUISED_EXECUTABLE] Threat found"
        )

        val json = item.toJsonString()
        assertTrue(json.contains("\"THREAT_DETECTED\""))

        val deserialized = QueueItem.fromJsonString(json)
        assertNotNull(deserialized)
        assertEquals(QueueItemStatus.THREAT_DETECTED, deserialized!!.status)
        assertEquals("[VIRUS BLOCKED: DISGUISED_EXECUTABLE] Threat found", deserialized.errorMessage)
    }
}
