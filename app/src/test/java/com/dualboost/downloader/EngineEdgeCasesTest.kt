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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class EngineEdgeCasesTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-edge-cases-${System.currentTimeMillis()}")
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
    fun testBuildRequest_InjectsRefererOriginAndEncodingHeaders() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Cloudflare URL test
        val cfUrl = "https://speed.cloudflare.com/__down?bytes=104857600"
        val cfReq = engine.buildRequest(cfUrl).build()
        assertEquals("https://speed.cloudflare.com/", cfReq.header("Referer"))
        assertEquals("https://speed.cloudflare.com", cfReq.header("Origin"))
        assertEquals("identity", cfReq.header("Accept-Encoding"))
        assertTrue("User-Agent should contain Mozilla", cfReq.header("User-Agent")!!.contains("Mozilla"))

        // OVH benchmark URL test
        val ovhUrl = "https://proof.ovh.net/files/100Mb.dat"
        val ovhReq = engine.buildRequest(ovhUrl).build()
        assertEquals("https://proof.ovh.net/", ovhReq.header("Referer"))
        assertEquals("https://proof.ovh.net", ovhReq.header("Origin"))

        // Standard HTTP URL test
        val httpUrl = "http://speed.hetzner.de/100MB.bin"
        val httpReq = engine.buildRequest(httpUrl).build()
        assertEquals("http://speed.hetzner.de/", httpReq.header("Referer"))
        assertEquals("http://speed.hetzner.de", httpReq.header("Origin"))
    }

    @Test
    fun test403Forbidden_EmitsFailedStateGracefully() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(403).setBody("Forbidden by CDN")
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/forbidden.bin").toString(), tempDir)

        var finished = false
        var timeoutMs = 5_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Failed) {
                finished = true
                assertTrue("Error should mention 403 code", state.error.contains("403"))
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }
        assertTrue("Engine should have emitted Failed state on 403 Forbidden", finished)
    }

    @Test
    fun test404NotFound_EmitsFailedStateGracefully() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(404).setBody("Not Found")
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/missing.bin").toString(), tempDir)

        var finished = false
        var timeoutMs = 5_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Failed) {
                finished = true
                assertTrue("Error should mention 404 code", state.error.contains("404"))
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }
        assertTrue("Engine should have emitted Failed state on 404 Not Found", finished)
    }

    @Test
    fun test500InternalError_EmitsFailedStateGracefully() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(500).setBody("Internal Server Error")
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/server_error.bin").toString(), tempDir)

        var finished = false
        var timeoutMs = 5_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Failed) {
                finished = true
                assertTrue("Error should mention 500 code", state.error.contains("500"))
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }
        assertTrue("Engine should have emitted Failed state on 500 Server Error", finished)
    }

    @Test
    fun testUserAgentHeader_PresentOnAllRequests() = runBlocking {
        val capturedUserAgents = mutableListOf<String?>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                capturedUserAgents.add(request.getHeader("User-Agent"))
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", "100")
                        .setHeader("Accept-Ranges", "none")
                }
                return MockResponse().setResponseCode(200).setBody("A".repeat(100))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/ua_test.bin").toString(), tempDir)

        var timeoutMs = 5_000L
        while (engine.downloadState.value !is DownloadState.Completed && timeoutMs > 0) {
            Thread.sleep(50)
            timeoutMs -= 50
        }

        assertTrue("Should have captured at least 1 request", capturedUserAgents.isNotEmpty())
        for (ua in capturedUserAgents) {
            assertNotNull("User-Agent must not be null", ua)
            assertTrue("User-Agent must contain Mozilla or Pixel", ua!!.contains("Mozilla") || ua.contains("Pixel"))
        }
    }

    @Test
    fun testSingleStreamFallback_WhenServerLacksAcceptRanges() = runBlocking {
        val payload = "SingleStreamPayloadWithoutByteRangeSupport".toByteArray()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                    // Intentionally omitting Accept-Ranges header
                }
                // GET returns entire payload as 200 OK
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(okio.Buffer().write(payload))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/no_ranges.bin").toString(), tempDir)

        var finished = false
        var timeoutMs = 5_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertTrue("Downloaded file must exist", file.exists())
                assertEquals(payload.size.toLong(), file.length())
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }
        assertTrue("Single-stream fallback must complete successfully", finished)
    }

    @Test
    fun testPauseDownload_CancelsJobAndEmitsPausedState() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Return a massive file that will take time
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", "1000000000")
                    .setBody(okio.Buffer().write(ByteArray(1024 * 1024)))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/pause_test.bin").toString(), tempDir)
        Thread.sleep(100)
        engine.pauseDownload()

        assertEquals("State must transition to Paused", true, engine.downloadState.value is DownloadState.Paused)
    }

    @Test
    fun testPauseDownload_InstantlyClearsActiveNetworkCalls() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", "8388608")
                        .setHeader("Accept-Ranges", "bytes")
                }
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-4194303/8388608")
                    .throttleBody(1024, 1, java.util.concurrent.TimeUnit.SECONDS)
                    .setBody(okio.Buffer().write(ByteArray(4 * 1024 * 1024)))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/slow_stream.bin").toString(), tempDir)

        var waited = 0
        while (engine.downloadState.value is DownloadState.Idle && waited < 2000) {
            Thread.sleep(50)
            waited += 50
        }

        engine.pauseDownload()

        assertTrue(engine.downloadState.value is DownloadState.Paused)

        var callsWait = 0
        while (engine.activeCallsCount > 0 && callsWait < 1000) {
            Thread.sleep(50)
            callsWait += 50
        }
        assertEquals("Active calls must be 0 after pause", 0, engine.activeCallsCount)
    }

    @Test
    fun testInterfaceCircuitBreaker_RecoversGracefullyWhenWifiFails() = runBlocking {
        val totalSize = 8 * 1024 * 1024L
        val dummyPayload = ByteArray((4 * 1024 * 1024).toInt()) { 1 }

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
                val start = match?.groupValues?.get(1)?.toLong() ?: 0L
                val end = match?.groupValues?.get(2)?.toLong() ?: (totalSize - 1)
                val length = (end - start + 1).toInt()

                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                    .setHeader("Content-Length", length.toString())
                    .setBody(okio.Buffer().write(dummyPayload, 0, length))
            }
        }

        val failingWifiClient = OkHttpClient.Builder()
            .addInterceptor { _ ->
                throw java.io.IOException("Simulated Wi-Fi connection timeout (15000ms)")
            }
            .build()

        val workingCellularClient = OkHttpClient.Builder().build()

        class CircuitBreakerNetworkProvider : NetworkProvider {
            override val isWifiAvailable: StateFlow<Boolean> = MutableStateFlow(true)
            override val isCellularAvailable: StateFlow<Boolean> = MutableStateFlow(true)
            override fun getWifiClient(): OkHttpClient = failingWifiClient
            override fun getCellularClient(): OkHttpClient = workingCellularClient
            override fun getDefaultClient(): OkHttpClient = workingCellularClient
        }

        val engine = DualDownloaderEngine(DummyTestContext(tempDir), CircuitBreakerNetworkProvider())
        engine.startDownload(server.url("/resilient_test.bin").toString(), tempDir)

        var finished = false
        var timeoutMs = 15_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertTrue("Downloaded file must exist", file.exists())
                assertEquals(totalSize, file.length())
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            Thread.sleep(100)
            timeoutMs -= 100
        }

        assertTrue("Download should complete via Cellular fallback despite Wi-Fi failures", finished)
    }
}
