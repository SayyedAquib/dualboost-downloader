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

class EngineComprehensiveTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-comprehensive-${System.currentTimeMillis()}")
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

    // --- Filename Extraction Unit Tests ---

    @Test
    fun testExtractFileName_StandardExtension() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertEquals("test.iso", engine.extractFileName("https://example.com/files/test.iso"))
        assertEquals("100Mb.dat", engine.extractFileName("https://proof.ovh.net/files/100Mb.dat"))
        assertEquals("archive.tar.gz", engine.extractFileName("https://cdn.example.org/archive.tar.gz"))
    }

    @Test
    fun testExtractFileName_WithQueryParams() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertEquals("package.zip", engine.extractFileName("https://example.com/download/package.zip?auth=xyz&exp=9999"))
        assertEquals("setup.exe", engine.extractFileName("https://download.site.com/v1/setup.exe?os=win64&lang=en"))
    }

    @Test
    fun testExtractFileName_CloudflareDownEndpoint() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val cfName = engine.extractFileName("https://speed.cloudflare.com/__down?bytes=104857600")
        assertTrue("Cloudflare endpoint should produce speedtest_*.bin name", cfName.startsWith("speedtest_"))
        assertTrue("Cloudflare endpoint should end with .bin", cfName.endsWith(".bin"))
    }

    @Test
    fun testExtractFileName_NoExtensionAddsBin() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertEquals("rawpayload.bin", engine.extractFileName("https://example.com/api/rawpayload"))
    }

    @Test
    fun testExtractFileName_EmptyPathOrRoot() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val rootName = engine.extractFileName("https://example.com/")
        assertTrue("Root URL should produce fallback name", rootName.startsWith("speedtest_") || rootName.startsWith("download_"))
        assertTrue("Root URL should end with .bin", rootName.endsWith(".bin"))
    }

    @Test
    fun testExtractFileName_MalformedUrlFallback() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val fallbackName = engine.extractFileName("invalid://bad-uri-format:999999")
        assertTrue("Malformed URI should produce fallback name", fallbackName.startsWith("download_"))
        assertTrue("Malformed URI should end with .bin", fallbackName.endsWith(".bin"))
    }

    // --- Content-Disposition Header Tests ---

    @Test
    fun testExtractFileNameFromDisposition_Quoted() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertEquals("document.pdf", engine.extractFileNameFromDisposition("""attachment; filename="document.pdf""""))
        assertEquals("my image (1).png", engine.extractFileNameFromDisposition("""attachment; filename="my image (1).png""""))
    }

    @Test
    fun testExtractFileNameFromDisposition_Unquoted() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertEquals("data.csv", engine.extractFileNameFromDisposition("attachment; filename=data.csv"))
    }

    @Test
    fun testExtractFileNameFromDisposition_CaseInsensitive() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertEquals("archive.zip", engine.extractFileNameFromDisposition("""INLINE; FILENAME="archive.zip""""))
        assertEquals("archive.zip", engine.extractFileNameFromDisposition("""Attachment; FileName="archive.zip""""))
    }

    @Test
    fun testExtractFileNameFromDisposition_MissingFilenameReturnsNull() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertNull(engine.extractFileNameFromDisposition("attachment; size=1048576"))
        assertNull(engine.extractFileNameFromDisposition(""))
    }

    // --- Format Bytes Unit Tests ---

    @Test
    fun testFormatBytes_MegabytesAndGigabytes() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertEquals("0.0 MB", engine.formatBytes(0L))
        assertEquals("10.0 MB", engine.formatBytes(10 * 1024 * 1024L))
        assertEquals("500.0 MB", engine.formatBytes(500 * 1024 * 1024L))
        assertEquals("1.00 GB", engine.formatBytes(1024 * 1024 * 1024L))
        assertEquals("2.50 GB", engine.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    // --- Single-Stream Fallback Speed & Throttling Integration Tests ---

    @Test
    fun testSingleStreamFallback_MetersSpeedAndPreservesDataIntegrity() = runBlocking {
        // 2 MB payload streamed over single-stream HTTP 200 without Range support
        val payload = ByteArray(2 * 1024 * 1024)
        Random(1234).nextBytes(payload)
        val expectedHash = sha256(payload)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                    // No Accept-Ranges header
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/single_stream_speed.bin").toString(), tempDir)

        var finished = false
        var timeoutMs = 10_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertTrue("File must exist", file.exists())
                assertEquals(payload.size.toLong(), file.length())
                assertEquals(expectedHash, sha256(file.readBytes()))
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }

        assertTrue("Single stream download must complete successfully", finished)
    }

    // --- Content-Disposition Override Test ---

    @Test
    fun testContentDispositionHeader_OverridesUrlFilename() = runBlocking {
        val payload = "Small text content".toByteArray()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                        .setHeader("Content-Disposition", """attachment; filename="renamed_by_server.txt"""")
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/original_url_path.bin").toString(), tempDir)

        var finished = false
        var timeoutMs = 5_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                assertEquals("renamed_by_server.txt", state.fileName)
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }

        assertTrue("Download should complete with Content-Disposition filename", finished)
    }
}

