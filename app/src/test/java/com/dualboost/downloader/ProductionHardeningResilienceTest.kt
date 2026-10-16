package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.Random

class ProductionHardeningResilienceTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-production-${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    class TestNetworkProvider(private val client: OkHttpClient) : NetworkProvider {
        override val isWifiAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override val isCellularAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client
    }

    @Test
    fun testStoragePreCheck_SucceedsWhenUsableSpaceIsSufficient() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Requesting a small 1 MB allocation on a drive with gigabytes free should succeed without throwing
        try {
            engine.checkAvailableStorage(tempDir, 1024 * 1024L)
        } catch (e: Exception) {
            fail("Storage check should not throw for small allocations: ${e.message}")
        }
    }

    @Test
    fun testStoragePreCheck_FailsWhenStorageExceeded() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Requesting an astronomically huge size that exceeds any physical volume
        val impossibleSize = Long.MAX_VALUE - 1000L
        try {
            engine.checkAvailableStorage(tempDir, impossibleSize)
            fail("Storage check should have thrown IOException for impossible file size")
        } catch (e: IOException) {
            assertTrue("Exception message must mention Insufficient storage", e.message?.contains("Insufficient storage") == true)
        }
    }

    @Test
    fun testStoragePreCheck_SubtractsExistingPartialFileSize() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val partialFile = File(tempDir, "existing.bin")
        partialFile.writeBytes(ByteArray(5 * 1024 * 1024)) // 5 MB already on disk

        // Total required is 6 MB, meaning only 1 MB additional is needed
        try {
            engine.checkAvailableStorage(tempDir, 6 * 1024 * 1024L, partialFile)
        } catch (e: Exception) {
            fail("Storage check with partial file should account for existing bytes: ${e.message}")
        }
    }

    @Test
    fun testMetaInfo_WithETagAndLastModified_RoundTrip() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val metaFile = engine.getMetaFile(tempDir, "sample_etag.bin")
        val testUrl = "https://example.com/asset.iso"
        val totalLength = 50_000_000L
        val completed = setOf(0, 1, 2)
        val etag = "\"9876543210-abcdef\""
        val lastModified = "Thu, 15 Oct 2026 12:00:00 GMT"

        engine.saveCompletedChunks(metaFile, testUrl, totalLength, completed, etag, lastModified)

        assertTrue(metaFile.exists())
        val metaInfo = engine.readMetaInfo(metaFile)
        assertNotNull(metaInfo)
        assertEquals(testUrl, metaInfo!!.url)
        assertEquals(totalLength, metaInfo.totalLength)
        assertEquals(completed, metaInfo.completedChunks)
        assertEquals(etag, metaInfo.etag)
        assertEquals(lastModified, metaInfo.lastModified)
    }

    @Test
    fun testRestoreIncompleteDownload_RestoresPausedStateOnStartup() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val fileName = "recovery_test.bin"
        val metaFile = engine.getMetaFile(tempDir, fileName)
        val testUrl = "https://example.com/recovery.bin"
        val totalLength = 16 * 1024 * 1024L // 16 MB -> 4 chunks (each 4 MB)
        val completed = setOf(0, 1) // 2 chunks completed = 8 MB = 50%

        engine.saveCompletedChunks(metaFile, testUrl, totalLength, completed)

        // Simulate app restart by calling restoreIncompleteDownload
        val restored = engine.restoreIncompleteDownload(tempDir)
        assertTrue("Must return true when incomplete download is found", restored)

        val state = engine.downloadState.value
        assertTrue("State must transition to Paused upon restoration", state is DownloadState.Paused)
        val paused = state as DownloadState.Paused
        assertEquals(fileName, paused.fileName)
        assertEquals(testUrl, paused.url)
        assertEquals(totalLength, paused.totalBytes)
        assertEquals(8 * 1024 * 1024L, paused.downloadedBytes)
        assertEquals(0.5f, paused.progressPercentage, 0.01f)
    }

    @Test
    fun testRestoreIncompleteDownload_WhenNoMetaFilesExist_ReturnsFalse() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val restored = engine.restoreIncompleteDownload(tempDir)
        assertFalse("Must return false when no metadata exists", restored)
        assertTrue("State must remain Idle", engine.downloadState.value is DownloadState.Idle)
    }

    @Test
    fun testMimeTypeResolution_AccurateForCommonExtensions() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val binMime = engine.getMimeType("test.bin")
        assertNotNull(binMime)
        assertTrue(binMime.isNotBlank())

        val noExtMime = engine.getMimeType("no_extension_file")
        assertEquals("application/octet-stream", noExtMime)
    }

    @Test
    fun testDownloadStateCompleted_PreservesContentUriString() {
        val completed = DownloadState.Completed(
            fileName = "movie.mp4",
            filePath = "/storage/emulated/0/Download/movie.mp4",
            totalBytes = 104857600L,
            durationSeconds = 12L,
            contentUriString = "content://media/external/downloads/42"
        )

        assertEquals("movie.mp4", completed.fileName)
        assertEquals("content://media/external/downloads/42", completed.contentUriString)
        assertEquals(104857600L, completed.totalBytes)
        assertEquals(12L, completed.durationSeconds)
    }

    @Test
    fun testETagMismatch_DiscardsStaleChunksAndRestartsCleanly() = runBlocking {
        // Setup 8 MB synthetic test payload
        val totalLength = 8 * 1024 * 1024L
        val testBytes = ByteArray(totalLength.toInt())
        Random(99).nextBytes(testBytes)

        val serverEtag = "\"NEW-SERVER-ETAG-V2\""

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val rangeHeader = request.getHeader("Range")
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", totalLength)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("ETag", serverEtag)
                }
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val parts = rangeHeader.removePrefix("bytes=").split("-")
                    val start = parts[0].toLong()
                    val end = parts[1].toLong()
                    val length = (end - start + 1).toInt()
                    val buffer = Buffer().write(testBytes, start.toInt(), length)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/$totalLength")
                        .setHeader("Content-Length", length)
                        .setHeader("ETag", serverEtag)
                        .setBody(buffer)
                }
                return MockResponse().setResponseCode(400)
            }
        }

        val testUrl = server.url("/stale_etag_test.bin").toString()
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Pre-create metadata with an OLD/DIFFERENT ETag
        val metaFile = engine.getMetaFile(tempDir, "stale_etag_test.bin")
        val staleEtag = "\"OLD-STALE-ETAG-V1\""
        engine.saveCompletedChunks(metaFile, testUrl, totalLength, setOf(0), staleEtag)

        // Create a pre-existing partial file
        val targetFile = File(tempDir, "stale_etag_test.bin")
        targetFile.writeBytes(ByteArray(4 * 1024 * 1024))

        // Execute download - engine will detect ETag mismatch, delete stale file, and restart clean
        engine.startDownload(testUrl, tempDir)

        var attempts = 0
        while (engine.downloadState.value !is DownloadState.Completed && attempts < 100) {
            kotlinx.coroutines.delay(100)
            attempts++
        }

        val finalState = engine.downloadState.value
        assertTrue("Download must succeed even after ETag mismatch reset: $finalState", finalState is DownloadState.Completed)
        assertEquals("Downloaded file size must equal total length", totalLength, targetFile.length())
    }
}

