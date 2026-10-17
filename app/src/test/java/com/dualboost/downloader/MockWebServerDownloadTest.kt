package com.dualboost.downloader

import android.content.Context
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

class MockWebServerDownloadTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var testData: ByteArray
    private lateinit var expectedSha256: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        tempDir = File("build/test-downloads-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Generate 6 MB synthetic payload (crosses 4 MB chunk boundary into 2 chunks)
        testData = ByteArray(6 * 1024 * 1024)
        Random(42).nextBytes(testData)
        expectedSha256 = sha256(testData)
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        return sha256(file.readBytes())
    }

    class TestNetworkProvider(private val client: OkHttpClient) : NetworkProvider {
        override val isWifiAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override val isCellularAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client
    }

    @Test
    fun testDualChunkSegmentedDownload_Sha256MatchesExactly() = runBlocking {
        // Setup dispatcher to handle HEAD probe and Range GETs
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val method = request.method ?: "GET"
                if (method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", testData.size.toString())
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Disposition", "inline; filename=\"segmented_test.bin\"")
                }

                val rangeHeader = request.getHeader("Range")
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val range = rangeHeader.removePrefix("bytes=").split("-")
                    val start = range[0].toInt()
                    val end = range[1].toInt()

                    val sliceLength = (end - start) + 1
                    val slice = Buffer().write(testData, start, sliceLength)

                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${testData.size}")
                        .setHeader("Content-Length", sliceLength.toString())
                        .setBody(slice)
                }

                return MockResponse().setResponseCode(400)
            }
        }

        val testClient = OkHttpClient.Builder().build()
        val networkProvider = TestNetworkProvider(testClient)
        val dummyContext = DummyTestContext(tempDir)
        val engine = DualDownloaderEngine(dummyContext, networkProvider)

        val targetUrl = server.url("/segmented_test.bin").toString()
        engine.startDownload(targetUrl, tempDir)

        // Wait for download to finish
        var finished = false
        var timeoutMs = 30_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val outputFile = File(state.filePath)
                assertTrue("Downloaded file does not exist", outputFile.exists())
                assertEquals("Downloaded file size mismatch", testData.size.toLong(), outputFile.length())

                val actualHash = sha256(outputFile)
                assertEquals("SHA-256 hash mismatch! File is corrupted", expectedSha256, actualHash)
            } else if (state is DownloadState.Failed) {
                fail("Download failed: ${state.error}")
            }
            kotlinx.coroutines.delay(100)
            timeoutMs -= 100
        }

        assertTrue("Download timed out", finished)
    }

    @Test
    fun testFallbackToSingleStream_WhenNoRangesSupported() = runBlocking {
        // Server does NOT provide Accept-Ranges or 206
        val payload = "Single stream test content with full response".toByteArray()
        val expectedHash = sha256(payload)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val method = request.method ?: "GET"
                val response = MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setHeader("Content-Disposition", "inline; filename=\"fallback_test.txt\"")
                if (method != "HEAD") {
                    response.setBody(Buffer().write(payload))
                }
                return response
            }
        }

        val testClient = OkHttpClient.Builder().build()
        val networkProvider = TestNetworkProvider(testClient)
        val dummyContext = DummyTestContext(tempDir)
        val engine = DualDownloaderEngine(dummyContext, networkProvider)

        val targetUrl = server.url("/fallback_test.txt").toString()
        engine.startDownload(targetUrl, tempDir)

        var finished = false
        var timeoutMs = 10_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val outputFile = File(state.filePath)
                assertTrue(outputFile.exists())
                assertEquals(payload.size.toLong(), outputFile.length())
                assertEquals(expectedHash, sha256(outputFile))
            } else if (state is DownloadState.Failed) {
                fail("Fallback download failed: ${state.error}")
            }
            kotlinx.coroutines.delay(100)
            timeoutMs -= 100
        }

        assertTrue("Fallback download timed out", finished)
    }
}

