package com.dualboost.downloader

import com.dualboost.downloader.data.ChunkStatus
import com.dualboost.downloader.data.DownloadChunk
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.NetworkInterfaceType
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.CellularGeneration
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Collections

class AsymmetricWorkStealingTest {

    private lateinit var tempDir: File
    private lateinit var engine: DualDownloaderEngine

    class MockNetworkProvider(
        private val client: OkHttpClient = OkHttpClient(),
        generation: CellularGeneration = CellularGeneration.FOUR_G
    ) : NetworkProvider {
        override val isWifiAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override val isCellularAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override val cellularGeneration: StateFlow<CellularGeneration> = MutableStateFlow(generation)
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client
    }

    @Before
    fun setUp() {
        tempDir = File("build/test-asymmetric-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        engine = DualDownloaderEngine(DummyTestContext(tempDir), MockNetworkProvider())
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testCalculateBatchSize_TieredScaling() {
        assertEquals("Sub-15 MB/s speeds should batch 1 chunk", 1, engine.calculateBatchSize(0.0))
        assertEquals("Sub-15 MB/s speeds should batch 1 chunk", 1, engine.calculateBatchSize(2.5))
        assertEquals("Sub-15 MB/s speeds should batch 1 chunk", 1, engine.calculateBatchSize(14.99))

        assertEquals("15 to 30 MB/s speeds should batch 2 chunks (8 MB)", 2, engine.calculateBatchSize(15.0))
        assertEquals("15 to 30 MB/s speeds should batch 2 chunks (8 MB)", 2, engine.calculateBatchSize(25.0))
        assertEquals("15 to 30 MB/s speeds should batch 2 chunks (8 MB)", 2, engine.calculateBatchSize(29.99))

        assertEquals("Over 30 MB/s speeds (Gigabit 5G) should batch 4 chunks (16 MB)", 4, engine.calculateBatchSize(30.0))
        assertEquals("Over 30 MB/s speeds (Gigabit 5G) should batch 4 chunks (16 MB)", 4, engine.calculateBatchSize(65.0))
        assertEquals("Over 30 MB/s speeds (Gigabit 5G) should batch 4 chunks (16 MB)", 4, engine.calculateBatchSize(120.0))
    }

    @Test
    fun testClaimContiguousPendingChunks_ClaimsSequentialBlocks() {
        val chunks = (0..9).map { i ->
            DownloadChunk(
                index = i,
                startByte = i * 2 * 1024 * 1024L,
                endByte = (i + 1) * 2 * 1024 * 1024L - 1
            )
        }

        // Cellular (fast) claims batch of 4
        val cellBatch = engine.claimContiguousPendingChunks(chunks, 4, NetworkInterfaceType.CELLULAR)
        assertEquals(4, cellBatch.size)
        assertEquals(listOf(0, 1, 2, 3), cellBatch.map { it.index })
        assertTrue(cellBatch.all { it.status == ChunkStatus.DOWNLOADING })
        assertTrue(cellBatch.all { it.assignedNetwork == NetworkInterfaceType.CELLULAR })

        // Wi-Fi (medium) claims batch of 2
        val wifiBatch = engine.claimContiguousPendingChunks(chunks, 2, NetworkInterfaceType.WIFI)
        assertEquals(2, wifiBatch.size)
        assertEquals(listOf(4, 5), wifiBatch.map { it.index })
        assertTrue(wifiBatch.all { it.status == ChunkStatus.DOWNLOADING })
        assertTrue(wifiBatch.all { it.assignedNetwork == NetworkInterfaceType.WIFI })

        // Cellular requests batch of 8, but only 4 remain
        val cellBatch2 = engine.claimContiguousPendingChunks(chunks, 8, NetworkInterfaceType.CELLULAR)
        assertEquals(4, cellBatch2.size)
        assertEquals(listOf(6, 7, 8, 9), cellBatch2.map { it.index })

        // No more pending chunks
        val emptyBatch = engine.claimContiguousPendingChunks(chunks, 4, NetworkInterfaceType.WIFI)
        assertTrue(emptyBatch.isEmpty())
    }

    @Test
    fun testClaimContiguousPendingChunks_StopsAtNonContiguousGap() {
        val chunks = (0..4).map { i ->
            DownloadChunk(
                index = i,
                startByte = i * 2 * 1024 * 1024L,
                endByte = (i + 1) * 2 * 1024 * 1024L - 1
            )
        }

        // Chunk 0 is DOWNLOADING, Chunk 1 is PENDING, Chunk 2 is COMPLETED, Chunk 3 and 4 are PENDING
        chunks[0].status = ChunkStatus.DOWNLOADING
        chunks[1].status = ChunkStatus.PENDING
        chunks[2].status = ChunkStatus.COMPLETED
        chunks[3].status = ChunkStatus.PENDING
        chunks[4].status = ChunkStatus.PENDING

        // When requesting a batch of 4 starting from chunk 1, it must STOP at chunk 2 because chunk 2 is already completed!
        val batch = engine.claimContiguousPendingChunks(chunks, 4, NetworkInterfaceType.CELLULAR)
        assertEquals("Must stop at non-pending boundary to preserve contiguous byte span", 1, batch.size)
        assertEquals(1, batch[0].index)
    }

    @Test
    fun testFindStragglerCandidate_IdentifiesSlowestOtherInterface() {
        val chunks = (0..3).map { i ->
            DownloadChunk(
                index = i,
                startByte = i * 2 * 1024 * 1024L,
                endByte = (i + 1) * 2 * 1024 * 1024L - 1
            )
        }

        chunks[0].status = ChunkStatus.COMPLETED
        chunks[0].downloadedBytes = chunks[0].totalBytes

        // Chunk 1 on CELLULAR with 1.8 MB unread (downloaded 200 KB)
        chunks[1].status = ChunkStatus.DOWNLOADING
        chunks[1].assignedNetwork = NetworkInterfaceType.CELLULAR
        chunks[1].downloadedBytes = 200 * 1024L

        // Chunk 2 on WIFI with 1.5 MB unread (downloaded 500 KB)
        chunks[2].status = ChunkStatus.DOWNLOADING
        chunks[2].assignedNetwork = NetworkInterfaceType.WIFI
        chunks[2].downloadedBytes = 500 * 1024L

        // Chunk 3 on WIFI with 200 KB unread (downloaded 1.8 MB, below 512 KB threshold)
        chunks[3].status = ChunkStatus.DOWNLOADING
        chunks[3].assignedNetwork = NetworkInterfaceType.WIFI
        chunks[3].downloadedBytes = (2 * 1024 * 1024L) - (200 * 1024L)

        // When WIFI searches for a straggler to hedge, it finds Chunk 1 (on CELLULAR)
        val wifiCandidate = engine.findStragglerCandidate(chunks, NetworkInterfaceType.WIFI)
        assertNotNull("Wi-Fi should find Cellular chunk 1 to hedge", wifiCandidate)
        assertEquals(1, wifiCandidate?.index)

        // When CELLULAR searches for a straggler to hedge, it finds Chunk 2 (on WIFI, >512KB unread)
        val cellCandidate = engine.findStragglerCandidate(chunks, NetworkInterfaceType.CELLULAR)
        assertNotNull("Cellular should find Wi-Fi chunk 2 to hedge", cellCandidate)
        assertEquals(2, cellCandidate?.index)

        // When all chunks are on the same interface, no straggler is returned
        val sameInterfaceCandidate = engine.findStragglerCandidate(listOf(chunks[1]), NetworkInterfaceType.CELLULAR)
        assertNull("Cannot hedge chunks running on the same interface", sameInterfaceCandidate)
    }

    @Test
    fun testBatchedDownload_RangeHeaderCoversMultiChunkSpan_AndAllIndicesCommitted() = runBlocking {
        val server = MockWebServer()
        server.start()
        val requestedRanges = Collections.synchronizedList(mutableListOf<String>())
        val testPayload = ByteArray(16 * 1024 * 1024) { (it % 241).toByte() } // 16 MB = 8 chunks of 2 MB

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val method = request.method ?: "GET"
                if (method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", testPayload.size.toString())
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Disposition", "inline; filename=\"batch_test.bin\"")
                }
                val range = request.getHeader("Range")
                if (range != null && range.startsWith("bytes=")) {
                    requestedRanges.add(range)
                    val parts = range.removePrefix("bytes=").split("-")
                    val start = parts[0].toInt()
                    val end = parts[1].toInt()
                    val sliceLen = (end - start) + 1
                    val slice = Buffer().write(testPayload, start, sliceLen)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${testPayload.size}")
                        .setHeader("Content-Length", sliceLen.toString())
                        .setBody(slice)
                }
                return MockResponse().setResponseCode(400)
            }
        }

        try {
            val netProvider = MockNetworkProvider(generation = CellularGeneration.FIVE_G)
            val batchEngine = DualDownloaderEngine(DummyTestContext(tempDir), netProvider)
            batchEngine.initialSpeedMBps = 20.0

            val url = server.url("/download/batch_test.bin").toString()
            batchEngine.startDownload(url, tempDir)

            var finished = false
            var timeoutMs = 15_000L
            while (!finished && timeoutMs > 0) {
                val state = batchEngine.downloadState.value
                if (state is DownloadState.Completed) {
                    finished = true
                    val file = File(state.filePath)
                    assertTrue("Downloaded file must exist", file.exists())
                    assertEquals("File size must match payload", testPayload.size.toLong(), file.length())
                    assertEquals("Payload SHA-256 must match exactly", sha256(testPayload), sha256(file.readBytes()))
                } else if (state is DownloadState.Failed) {
                    fail("Download failed: ${state.error}")
                }
                delay(50)
                timeoutMs -= 50
            }
            assertTrue("Download timed out", finished)

            // On 5G with 4 MB chunks, at least one batch request spanning multiple chunks (e.g. 8 MB = 2 chunks) must have occurred
            val multiChunkRange = requestedRanges.any { range ->
                val parts = range.removePrefix("bytes=").split("-")
                val span = parts[1].toLong() - parts[0].toLong() + 1
                span > 4 * 1024 * 1024L // spans more than a single 4 MB chunk
            }
            assertTrue("Expected at least one batched multi-chunk HTTP range request in: $requestedRanges", multiChunkRange)
        } finally {
            server.shutdown()
        }
    }
}
