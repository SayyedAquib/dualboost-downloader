package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.SlicerPreferences
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.security.RootDetector
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
import java.util.concurrent.atomic.AtomicInteger

class SecurityAndResilienceEdgeCasesTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-security-resilience-${System.currentTimeMillis()}")
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

    // --- RootDetector Tests ---

    @Test
    fun testRootDetector_ExecutesSafelyWithoutCrashing() {
        // Must complete without throwing any uncaught exceptions (e.g. SecurityException, NPE)
        val isRooted = RootDetector.isDeviceRooted()
        // On standard dev machine / emulator / JVM test harness, should evaluate cleanly
        assertNotNull(isRooted)

        // Multiple invocations must remain consistent and idempotent
        val isRootedSecondTime = RootDetector.isDeviceRooted()
        assertEquals(isRooted, isRootedSecondTime)
    }

    // --- SlicerPreferences URL Construction Tests ---

    @Test
    fun testSlicerPreferences_BuildSlicedUrl_EndpointsAndSlashes() {
        val target = "https://cdn.example.com/asset.iso"

        // Base URL without /slice or trailing slash
        val url1 = SlicerPreferences.buildSlicedUrl("https://slicer.workers.dev", target)
        assertEquals("https://slicer.workers.dev/slice?url=https%3A%2F%2Fcdn.example.com%2Fasset.iso", url1)

        // Base URL with trailing slash
        val url2 = SlicerPreferences.buildSlicedUrl("https://slicer.workers.dev/", target)
        assertEquals("https://slicer.workers.dev/slice?url=https%3A%2F%2Fcdn.example.com%2Fasset.iso", url2)

        // Base URL already containing /slice
        val url3 = SlicerPreferences.buildSlicedUrl("https://slicer.workers.dev/slice", target)
        assertEquals("https://slicer.workers.dev/slice?url=https%3A%2F%2Fcdn.example.com%2Fasset.iso", url3)

        // Base URL containing /slice/ with trailing slash
        val url4 = SlicerPreferences.buildSlicedUrl("https://slicer.workers.dev/slice/", target)
        assertEquals("https://slicer.workers.dev/slice?url=https%3A%2F%2Fcdn.example.com%2Fasset.iso", url4)
    }

    @Test
    fun testSlicerPreferences_BuildSlicedUrl_SpecialCharactersInTarget() {
        val complexTarget = "https://cdn.example.com/files/My Package (2026).tar.gz?auth=key&sig=abc#part1"
        val sliced = SlicerPreferences.buildSlicedUrl("https://slicer.workers.dev", complexTarget)

        assertTrue(sliced.startsWith("https://slicer.workers.dev/slice?url="))
        // Query characters and spaces must be properly percent-encoded
        assertFalse(sliced.contains(" "))
        assertFalse(sliced.contains("#"))
    }

    @Test
    fun testSlicerPreferences_StorageAndTrimming() {
        val testContext = DummyTestContext(tempDir)

        SlicerPreferences.setSlicerUrl(testContext, "   https://my-slicer.workers.dev   ")
        assertEquals("https://my-slicer.workers.dev", SlicerPreferences.getSlicerUrl(testContext))

        SlicerPreferences.setSlicerSecret(testContext, "   mySecretToken123   ")
        assertEquals("mySecretToken123", SlicerPreferences.getSlicerSecret(testContext))
    }

    // --- Truncated Chunk Detection & Auto-Recovery Integration Test ---

    @Test
    fun testTruncatedChunk_TriggersRollbackAndSuccessfulRetry() = runBlocking {
        // Setup 8 MB payload (2 chunks of 4 MB)
        val totalSize = 8 * 1024 * 1024
        val testBytes = ByteArray(totalSize) { (it % 128).toByte() }
        val expectedSha = sha256(testBytes)

        val chunk0AttemptCount = AtomicInteger(0)

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
                val end = match?.groupValues?.get(2)?.toInt() ?: (totalSize - 1)
                val expectedChunkLength = (end - start + 1)

                // Simulate truncation on the first attempt of chunk 0 (0 - 4194303)
                if (start == 0 && chunk0AttemptCount.incrementAndGet() == 1) {
                    // Send only 512 KB instead of full 4 MB, then close stream
                    val truncatedLength = 512 * 1024
                    val truncatedSlice = testBytes.copyOfRange(start, start + truncatedLength)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                        .setHeader("Content-Length", expectedChunkLength.toString())
                        .setBody(Buffer().write(truncatedSlice))
                }

                // Normal response for full chunk
                val slice = testBytes.copyOfRange(start, minOf(end + 1, testBytes.size))
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                    .setHeader("Content-Length", slice.size.toString())
                    .setBody(Buffer().write(slice))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))
        val targetUrl = server.url("/truncated_chunk_test.bin").toString()

        engine.startDownload(targetUrl, tempDir)

        var finished = false
        var timeoutMs = 20_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertTrue("Downloaded file must exist", file.exists())
                assertEquals(totalSize.toLong(), file.length())
                assertEquals(expectedSha, sha256(file.readBytes()))
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            kotlinx.coroutines.delay(100)
            timeoutMs -= 100
        }

        assertTrue("Download should recover and complete after chunk truncation", finished)
        assertTrue("Chunk 0 should have been attempted at least twice (initial truncated + retry)", chunk0AttemptCount.get() >= 2)
    }
}
