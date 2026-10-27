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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SecurityHardeningPhase1Test {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-sec-phase1-${System.currentTimeMillis()}")
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

    // --- Slice 1.1: H-2a Ranged Chunk HTTP 200 Rejection & Write Boundary Capping ---

    @Test
    fun testRangedChunk_RejectsHttp200OnNonZeroOffset_DoesNotClobberSubsequentChunks() = runBlocking {
        // Total 8 MB (2 chunks: Chunk 0 = 0..4194303, Chunk 1 = 4194304..8388607)
        val totalSize = 8 * 1024 * 1024
        val testBytes = ByteArray(totalSize) { (it % 251).toByte() }
        val expectedSha = sha256(testBytes)

        val rogueResponseSent = AtomicBoolean(false)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", totalSize.toString())
                        .setHeader("Accept-Ranges", "bytes")
                }

                val rangeHeader = request.getHeader("Range")
                if (rangeHeader != null) {
                    val regex = Regex("bytes=(\\d+)-(\\d+)")
                    val match = regex.find(rangeHeader)
                    val start = match?.groupValues?.get(1)?.toInt() ?: 0
                    val end = match?.groupValues?.get(2)?.toInt() ?: (totalSize - 1)

                    // Rogue behavior: if start > 0 and rogue response hasn't fired yet, send HTTP 200 with full 8 MB!
                    if (start > 0 && !rogueResponseSent.getAndSet(true)) {
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Length", totalSize.toString())
                            .setBody(Buffer().write(testBytes))
                    }

                    val slice = testBytes.copyOfRange(start, minOf(end + 1, testBytes.size))
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                        .setHeader("Content-Length", slice.size.toString())
                        .setBody(Buffer().write(slice))
                }

                // Single stream request (fallback or no range)
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", totalSize.toString())
                    .setBody(Buffer().write(testBytes))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))
        val targetUrl = server.url("/h2a_clobber_test.bin").toString()

        engine.startDownload(targetUrl, tempDir)

        var finished = false
        var timeoutMs = 25_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertTrue("Downloaded file must exist", file.exists())
                assertEquals(totalSize.toLong(), file.length())
                assertEquals("File content must match expected hash bit-for-bit without clobbered bytes", expectedSha, sha256(file.readBytes()))
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            kotlinx.coroutines.delay(100)
            timeoutMs -= 100
        }

        assertTrue("Download must complete successfully with verified integrity", finished)
        assertTrue("Rogue 200 response must have been triggered and safely handled", rogueResponseSent.get())
    }

    // --- Slice 1.2: H-2b Single-Stream Truncation Prevention ---

    @Test
    fun testSingleStreamDownload_TruncatedPrematurely_FailsInsteadOfReportingCompleted() = runBlocking {
        val totalDeclaredSize = 5 * 1024 * 1024L // 5 MB
        val truncatedActualSize = 1 * 1024 * 1024 // 1 MB
        val truncatedBytes = ByteArray(truncatedActualSize) { 0x42 }

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", totalDeclaredSize.toString())
                        .setHeader("Accept-Ranges", "none") // Force single-stream
                }

                // Server sends 1MB body and closes connection normally at EOF
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", totalDeclaredSize.toString())
                    .setBody(Buffer().write(truncatedBytes))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))
        val targetUrl = server.url("/truncated_single_stream.bin").toString()

        engine.startDownload(targetUrl, tempDir)

        var reachedTerminalState = false
        var timeoutMs = 15_000L
        while (!reachedTerminalState && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                fail("Vulnerability triggered: Single-stream download reported Completed despite receiving only $truncatedActualSize of $totalDeclaredSize bytes!")
            } else if (state is DownloadState.Failed) {
                reachedTerminalState = true
                assertTrue("Failure error message should indicate truncation", state.error.contains("truncated", ignoreCase = true) || state.error.contains("mismatch", ignoreCase = true))
            }
            kotlinx.coroutines.delay(100)
            timeoutMs -= 100
        }

        assertTrue("Download must terminate in Failed state upon truncation", reachedTerminalState)
    }

    // --- Slice 1.3: H-2c & M-3 Resume Integrity, Fail-Closed Validators & Private Meta ---

    @Test
    fun testResume_WhenPhysicalFileDeleted_DiscardsSidecarAndDownloadsFresh() = runBlocking {
        val totalSize = 8 * 1024 * 1024L // 8 MB (2 chunks)
        val testBytes = ByteArray(totalSize.toInt()) { (it % 199).toByte() }
        val expectedSha = sha256(testBytes)

        val chunk0Requested = AtomicBoolean(false)
        val chunk1Requested = AtomicBoolean(false)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", totalSize.toString())
                        .setHeader("Accept-Ranges", "bytes")
                }

                val rangeHeader = request.getHeader("Range") ?: ""
                val regex = Regex("bytes=(\\d+)-(\\d+)")
                val match = regex.find(rangeHeader)
                val start = match?.groupValues?.get(1)?.toInt() ?: 0
                val end = match?.groupValues?.get(2)?.toInt() ?: (totalSize.toInt() - 1)

                if (start == 0) chunk0Requested.set(true)
                if (start > 0) chunk1Requested.set(true)

                val slice = testBytes.copyOfRange(start, minOf(end + 1, testBytes.size))
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                    .setHeader("Content-Length", slice.size.toString())
                    .setBody(Buffer().write(slice))
            }
        }

        val testContext = DummyTestContext(tempDir)
        val fileName = "resume_test_file.bin"
        val targetFile = File(tempDir, fileName)
        // Ensure physical file is missing (deleted by user/system)
        if (targetFile.exists()) targetFile.delete()

        // Plant an internal sidecar claiming chunk 0 was completed
        val internalMetaDir = File(testContext.getNoBackupFilesDir(), "dualboost_meta").apply { mkdirs() }
        val metaFile = File(internalMetaDir, "$fileName.dualboost_meta")
        val targetUrl = server.url("/$fileName").toString()
        metaFile.writeText(
            """
            url=$targetUrl
            totalLength=$totalSize
            completedChunks=0
            updatedAt=${System.currentTimeMillis()}
            """.trimIndent()
        )

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(testContext, TestNetworkProvider(client))

        // Start download (resuming)
        engine.startDownload(targetUrl, tempDir)

        var finished = false
        var timeoutMs = 25_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertTrue("Target file must exist", file.exists())
                assertEquals(totalSize, file.length())
                assertEquals(expectedSha, sha256(file.readBytes()))
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            kotlinx.coroutines.delay(100)
            timeoutMs -= 100
        }

        assertTrue("Download must finish successfully", finished)
        // Because physical file was missing, chunk 0 MUST have been re-requested, NOT trusted blindly!
        assertTrue("Chunk 0 must be re-requested when physical file was deleted/truncated", chunk0Requested.get())
        assertTrue("Chunk 1 must be requested", chunk1Requested.get())
    }

    @Test
    fun testResume_WhenRemoteDropsOrChangesEtag_PerformsCleanRestart() = runBlocking {
        val totalSize = 8 * 1024 * 1024L
        val testBytesV2 = ByteArray(totalSize.toInt()) { (it % 173).toByte() }
        val expectedSha = sha256(testBytesV2)

        val chunk0Requested = AtomicBoolean(false)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    // Server responds with changed ETag (or no ETag)
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", totalSize.toString())
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("ETag", "\"v2-new-hash\"")
                }

                val rangeHeader = request.getHeader("Range") ?: ""
                val regex = Regex("bytes=(\\d+)-(\\d+)")
                val match = regex.find(rangeHeader)
                val start = match?.groupValues?.get(1)?.toInt() ?: 0
                val end = match?.groupValues?.get(2)?.toInt() ?: (totalSize.toInt() - 1)

                if (start == 0) chunk0Requested.set(true)

                val slice = testBytesV2.copyOfRange(start, minOf(end + 1, testBytesV2.size))
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                    .setHeader("Content-Length", slice.size.toString())
                    .setBody(Buffer().write(slice))
            }
        }

        val testContext = DummyTestContext(tempDir)
        val fileName = "etag_test_file.bin"
        val targetFile = File(tempDir, fileName)
        // Create a 8MB dummy file representing stale V1 file
        targetFile.writeBytes(ByteArray(totalSize.toInt()) { 0x11 })

        // Plant an internal sidecar with old ETag "v1-old-hash"
        val internalMetaDir = File(testContext.getNoBackupFilesDir(), "dualboost_meta").apply { mkdirs() }
        val metaFile = File(internalMetaDir, "$fileName.dualboost_meta")
        val targetUrl = server.url("/$fileName").toString()
        metaFile.writeText(
            """
            url=$targetUrl
            totalLength=$totalSize
            etag="v1-old-hash"
            completedChunks=0
            updatedAt=${System.currentTimeMillis()}
            """.trimIndent()
        )

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(testContext, TestNetworkProvider(client))

        engine.startDownload(targetUrl, tempDir)

        var finished = false
        var timeoutMs = 25_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertEquals(expectedSha, sha256(file.readBytes()))
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            kotlinx.coroutines.delay(100)
            timeoutMs -= 100
        }

        assertTrue("Download must finish successfully", finished)
        assertTrue("Chunk 0 must have been re-downloaded because ETag changed", chunk0Requested.get())
    }

    // --- Slice 1.4: M-4 Capped Retries, AutoRecovering Flow Emission & Escalation to Paused ---

    @Test
    fun testChunkFailures_EmitsAutoRecoveringAndEscalatesToPaused() = runBlocking {
        val totalSize = 8 * 1024 * 1024L

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", totalSize.toString())
                        .setHeader("Accept-Ranges", "bytes")
                }
                // All chunk requests fail with 503 Service Unavailable
                return MockResponse().setResponseCode(503)
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))
        val targetUrl = server.url("/failing_chunks.bin").toString()

        var autoRecoveringObserved = false

        engine.startDownload(targetUrl, tempDir)

        var reachedPausedState = false
        var timeoutMs = 20_000L
        while (!reachedPausedState && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.AutoRecovering) {
                autoRecoveringObserved = true
            } else if (state is DownloadState.Paused) {
                reachedPausedState = true
            } else if (state is DownloadState.Completed) {
                fail("Download must not complete when all chunk requests fail!")
            }
            kotlinx.coroutines.delay(100)
            timeoutMs -= 100
        }

        assertTrue("AutoRecovering state must be emitted when network/chunks encounter consecutive failures", autoRecoveringObserved)
        assertTrue("Engine must escalate to Paused state rather than looping forever", reachedPausedState)
    }

    // --- Slice 1.5: M-5 Unvalidated Delete Paths & Canonical Directory Containment ---

    @Test
    fun testDeletePaths_OutsideAllowedDownloadDirectory_AreRejected() = runBlocking {
        val testContext = DummyTestContext(tempDir)
        val downloadDir = File(tempDir, "downloads").apply { mkdirs() }
        val outsideDir = File("build/test-sec-external-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val sensitiveFile = File(outsideDir, "credentials.json").apply {
                writeText("{\"token\":\"super_secret\"}")
            }
            assertTrue("Sensitive file must exist initially", sensitiveFile.exists())

            val client = OkHttpClient.Builder().build()
            val engine = DualDownloaderEngine(testContext, TestNetworkProvider(client))

            // 1. Attempt discardDownload with path traversal attempting to delete sensitiveFile
            engine.discardDownload(downloadDir, "../../../credentials.json")
            assertTrue("discardDownload must NOT delete files outside allowed download roots", sensitiveFile.exists())

            // 2. Attempt DownloadHistoryRepository deleteItem with outside path
            val repository = com.dualboost.downloader.data.DownloadHistoryRepository(testContext)
            val rogueHistoryItem = com.dualboost.downloader.data.DownloadHistoryItem(
                id = "test-1",
                fileName = "credentials.json",
                filePath = sensitiveFile.absolutePath,
                fileSize = sensitiveFile.length(),
                timestamp = System.currentTimeMillis()
            )
            repository.recordDownloadSync(rogueHistoryItem)
            repository.deleteItem(rogueHistoryItem.id, deleteFileFromDisk = true)

            assertTrue("DownloadHistoryRepository must NOT delete files outside allowed download roots", sensitiveFile.exists())
        } finally {
            outsideDir.deleteRecursively()
        }
    }
}

