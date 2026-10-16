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
import java.security.MessageDigest
import java.util.Random

class ResumableDownloadTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-resumable-${System.currentTimeMillis()}")
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

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testSaveAndReadCompletedChunks_SavesAndRestoresIndices() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val metaFile = engine.getMetaFile(tempDir, "sample.bin")
        val testUrl = "https://example.com/sample.bin"
        val totalLength = 100_000_000L
        val completed = setOf(0, 1, 3, 7)

        engine.saveCompletedChunks(metaFile, testUrl, totalLength, completed)

        assertTrue("Metadata file must exist on disk", metaFile.exists())
        val restored = engine.readCompletedChunks(metaFile, testUrl, totalLength)
        assertEquals(completed, restored)
    }

    @Test
    fun testMismatchedUrlOrLength_IgnoresStaleMetadata() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val metaFile = engine.getMetaFile(tempDir, "sample.bin")
        val originalUrl = "https://example.com/sample.bin"
        val totalLength = 100_000_000L

        engine.saveCompletedChunks(metaFile, originalUrl, totalLength, setOf(0, 1))

        // Different URL
        val differentUrlResult = engine.readCompletedChunks(metaFile, "https://example.com/other.bin", totalLength)
        assertTrue("Different URL must return empty set", differentUrlResult.isEmpty())

        // Different total length
        val differentLengthResult = engine.readCompletedChunks(metaFile, originalUrl, 50_000_000L)
        assertTrue("Different length must return empty set", differentLengthResult.isEmpty())
    }

    @Test
    fun testCorruptedMetaFile_FailsafeReturnsEmptySet() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val metaFile = engine.getMetaFile(tempDir, "corrupted.bin")
        metaFile.writeText("THIS IS TOTAL GARBAGE NOT KEY VALUE")

        val restored = engine.readCompletedChunks(metaFile, "https://example.com/file", 1000L)
        assertTrue("Corrupted metadata file must safely return empty set", restored.isEmpty())
    }

    @Test
    fun testResumption_SkipsCompletedChunksAndRequestsOnlyRemaining() = runBlocking {
        // 6 MB payload (Chunk 0: 0..4194303, Chunk 1: 4194304..6291455)
        val payload = ByteArray(6 * 1024 * 1024)
        Random(99).nextBytes(payload)
        val expectedHash = sha256(payload)

        val requestedRanges = mutableListOf<String>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                        .setHeader("Accept-Ranges", "bytes")
                }

                val rangeHeader = request.getHeader("Range")
                if (rangeHeader != null) {
                    requestedRanges.add(rangeHeader)
                    val range = rangeHeader.removePrefix("bytes=").split("-")
                    val start = range[0].toInt()
                    val end = range[1].toInt()
                    val slice = Buffer().write(payload, start, (end - start) + 1)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                        .setBody(slice)
                }
                return MockResponse().setResponseCode(400)
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))
        val targetUrl = server.url("/resumed_file.bin").toString()
        val fileName = engine.extractFileName(targetUrl)
        val metaFile = engine.getMetaFile(tempDir, fileName)
        val outputFile = File(tempDir, fileName)

        // Pre-create output file and pre-populate Chunk 0 (4 MB) as already downloaded
        java.io.RandomAccessFile(outputFile, "rw").use { raf ->
            raf.setLength(payload.size.toLong())
            raf.seek(0)
            raf.write(payload, 0, 4 * 1024 * 1024)
        }
        engine.saveCompletedChunks(metaFile, targetUrl, payload.size.toLong(), setOf(0))

        // Start download (should resume and only request Chunk 1)
        engine.startDownload(targetUrl, tempDir)

        var finished = false
        var timeoutMs = 15_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val finalFile = File(state.filePath)
                assertTrue("File exists", finalFile.exists())
                assertEquals(payload.size.toLong(), finalFile.length())
                assertEquals(expectedHash, sha256(finalFile.readBytes()))
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }

        assertTrue("Download must finish successfully", finished)
        // Verify Chunk 0 was NEVER requested from the server
        assertFalse(
            "Chunk 0 should have been skipped, but was requested: $requestedRanges",
            requestedRanges.any { it.contains("bytes=0-") }
        )
        assertTrue(
            "Chunk 1 should have been requested",
            requestedRanges.any { it.contains("bytes=4194304-") }
        )
        // Verify metadata file was deleted upon completion
        assertFalse("Metadata file must be deleted upon download completion", metaFile.exists())
    }

    @Test
    fun testDiscardDownload_DeletesFileAndMetadataAndResetsState() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val fileName = "discard_me.bin"
        val outputFile = File(tempDir, fileName)
        outputFile.writeText("partial bytes")
        val metaFile = engine.getMetaFile(tempDir, fileName)
        metaFile.writeText("metadata")

        assertTrue(outputFile.exists())
        assertTrue(metaFile.exists())

        engine.discardDownload(tempDir, fileName)

        assertFalse("Target file must be deleted on discard", outputFile.exists())
        assertFalse("Metadata file must be deleted on discard", metaFile.exists())
        assertEquals(DownloadState.Idle, engine.downloadState.value)
    }
}

