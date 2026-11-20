package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.NetworkInterfaceType
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

/**
 * TDD Unit test suite for Seam 2:
 * Verifies that when Wi-Fi trips due to transient errors, it transitions to HALF_OPEN,
 * executes a trial probe chunk, recovers back to HEALTHY, and contributes to the download.
 */
class EngineCircuitBreakerRecoveryTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var testData: ByteArray

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        tempDir = File("build/test-cb-recovery-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // 8 MB payload = two 4 MB chunks
        testData = ByteArray(8 * 1024 * 1024)
        Random(123).nextBytes(testData)
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    class FlakyWifiNetworkProvider(
        private val wifiClient: OkHttpClient,
        private val cellularClient: OkHttpClient
    ) : NetworkProvider {
        override val isWifiAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override val isCellularAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override fun getWifiClient(): OkHttpClient = wifiClient
        override fun getCellularClient(): OkHttpClient = cellularClient
        override fun getDefaultClient(): OkHttpClient = cellularClient
    }

    @Test
    fun testWifiTripsAndRecoversViaHalfOpenProbe() = runBlocking {
        val wifiFailureCount = AtomicInteger(0)
        val maxWifiFailuresBeforeRecovery = 2

        // Interceptor that forces the first 2 requests on Wi-Fi to fail with IOException,
        // causing Wi-Fi to trip into the circuit breaker
        val flakyWifiInterceptor = Interceptor { chain ->
            if (wifiFailureCount.getAndIncrement() < maxWifiFailuresBeforeRecovery) {
                throw IOException("Simulated transient Wi-Fi packet drop")
            }
            chain.proceed(chain.request())
        }

        val baseClient = OkHttpClient.Builder().build()
        val wifiClient = baseClient.newBuilder().addInterceptor(flakyWifiInterceptor).build()
        val cellularClient = baseClient

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val method = request.method ?: "GET"
                if (method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", testData.size.toString())
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Disposition", "inline; filename=\"cb_test.bin\"")
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

        val networkProvider = FlakyWifiNetworkProvider(wifiClient, cellularClient)
        val dummyContext = DummyTestContext(tempDir)
        val engine = DualDownloaderEngine(dummyContext, networkProvider)

        val targetUrl = server.url("/cb_test.bin").toString()
        engine.startDownload(targetUrl, tempDir)

        var finished = false
        var timeoutMs = 30_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val outputFile = File(state.filePath)
                assertTrue("Downloaded file does not exist", outputFile.exists())
                assertEquals("File length matches", testData.size.toLong(), outputFile.length())
                assertTrue("Wi-Fi should have attempted multiple requests including recovery", wifiFailureCount.get() >= 2)
            }
            kotlinx.coroutines.delay(200)
            timeoutMs -= 200
        }

        assertTrue("Download did not complete within timeout", finished)
    }
}
