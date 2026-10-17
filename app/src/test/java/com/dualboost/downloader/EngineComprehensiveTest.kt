package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.SlicerPreferences
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

    @Test
    fun testExtractFileNameFromDisposition_PathTraversalNeutralized() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Unix path traversal
        assertEquals("malicious.sh", engine.extractFileNameFromDisposition("""attachment; filename="../../malicious.sh""""))
        assertEquals("passwd", engine.extractFileNameFromDisposition("""attachment; filename="../../../../etc/passwd""""))

        // Windows path traversal
        assertEquals("cmd.exe", engine.extractFileNameFromDisposition("""attachment; filename="..\\..\\Windows\\System32\\cmd.exe""""))

        // Pure traversal strings collapse or return null
        assertNull(engine.extractFileNameFromDisposition("""attachment; filename="../../../""""))
        assertNull(engine.extractFileNameFromDisposition("""attachment; filename="..""""))
    }

    @Test
    fun testExtractFileNameFromDisposition_IllegalCharactersSanitized() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Illegal characters replaced with underscores
        val sanitized = engine.extractFileNameFromDisposition("""attachment; filename="test<script>:run*?.zip"""")
        assertEquals("test_script__run__.zip", sanitized)
    }

    @Test
    fun testSanitizeUrlForLog_RemovesQueryParams() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val signedUrl = "https://storage.googleapis.com/bucket/file.mp4?X-Goog-Signature=abcdef123456&Expires=1700000000"
        val sanitized = engine.sanitizeUrlForLog(signedUrl)
        assertEquals("https://storage.googleapis.com/bucket/file.mp4", sanitized)

        val cleanUrl = "https://speed.cloudflare.com/100mb.bin"
        assertEquals(cleanUrl, engine.sanitizeUrlForLog(cleanUrl))
    }

    @Test
    fun testResolveWritableOutputFile_PathTraversalContained() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Attempting to resolve a path traversing above tempDir
        val resolved = engine.resolveWritableOutputFile(tempDir, "../../../evil.bin", "https://example.com/evil.bin")
        assertTrue("Output file must be strictly inside target directory", resolved.canonicalPath.startsWith(tempDir.canonicalPath))
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

    // --- Desktop Header Spoofing Unit & Integration Tests ---

    @Test
    fun testBuildRequest_MobileHeaders() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val req = engine.buildRequest("https://example.com/file.bin", isDesktop = false).build()
        assertEquals(DualDownloaderEngine.MOBILE_USER_AGENT, req.header("User-Agent"))
        assertNull(req.header("Sec-CH-UA"))
        assertNull(req.header("Sec-CH-UA-Mobile"))
        assertNull(req.header("Sec-CH-UA-Platform"))
    }

    @Test
    fun testBuildRequest_DesktopSpoofingHeaders() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val req = engine.buildRequest("https://example.com/file.bin", isDesktop = true).build()
        assertEquals(DualDownloaderEngine.DESKTOP_USER_AGENT, req.header("User-Agent"))
        assertEquals("?0", req.header("Sec-CH-UA-Mobile"))
        assertEquals("\"Windows\"", req.header("Sec-CH-UA-Platform"))
        assertNotNull(req.header("Sec-CH-UA"))
        assertEquals("empty", req.header("Sec-Fetch-Dest"))
        assertEquals("1", req.header("Upgrade-Insecure-Requests"))
    }

    @Test
    fun testAdaptiveDesktopSpoofing_UnlocksHttp206WhenMobileFails() = runBlocking {
        // Server rejects byte ranges when client is Mobile, but unlocks HTTP 206 for Desktop headers
        val payload = ByteArray(512 * 1024) // 512 KB payload
        Random(42).nextBytes(payload)
        val expectedHash = sha256(payload)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ua = request.getHeader("User-Agent") ?: ""
                val isDesktop = ua.contains("Windows") && ua.contains("Chrome")

                if (!isDesktop) {
                    // Mobile client: reject byte ranges, return 200
                    if (request.method == "HEAD") {
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Length", payload.size.toString())
                    }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                        .setBody(Buffer().write(payload))
                } else {
                    // Desktop client: unlock full HTTP 206 Partial Content!
                    val range = request.getHeader("Range")
                    if (range != null && range.startsWith("bytes=")) {
                        val parts = range.removePrefix("bytes=").split("-")
                        val start = parts[0].toIntOrNull() ?: 0
                        val end = parts.getOrNull(1)?.toIntOrNull() ?: (payload.size - 1)
                        val slice = payload.copyOfRange(start, minOf(end + 1, payload.size))

                        return MockResponse()
                            .setResponseCode(206)
                            .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                            .setHeader("Content-Length", slice.size.toString())
                            .setHeader("Accept-Ranges", "bytes")
                            .setBody(Buffer().write(slice))
                    }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                        .setBody(Buffer().write(payload))
                }
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        engine.startDownload(server.url("/adaptive_spoof_test.bin").toString(), tempDir)

        var finished = false
        var timeoutMs = 8_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertTrue("Downloaded file must exist", file.exists())
                assertEquals(payload.size.toLong(), file.length())
                assertEquals(expectedHash, sha256(file.readBytes()))
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }

        assertTrue("Adaptive desktop spoofing should complete download successfully", finished)
    }

    @Test
    fun testMetaInfo_SavesAndRestoresDesktopSpoofedFlag() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val metaFile = engine.getMetaFile(tempDir, "test_resume.bin")
        engine.saveCompletedChunks(
            metaFile = metaFile,
            url = "https://example.com/test_resume.bin",
            totalBytes = 10485760L,
            completedIndices = setOf(0, 1),
            etag = "\"abc123etag\"",
            lastModified = "Wed, 21 Oct 2025 07:28:00 GMT",
            isDesktopSpoofed = true
        )

        val metaInfo = engine.readMetaInfo(metaFile)
        assertNotNull("MetaInfo must be readable", metaInfo)
        assertEquals(true, metaInfo?.isDesktopSpoofed)
        assertEquals(setOf(0, 1), metaInfo?.completedChunks)
        assertEquals("\"abc123etag\"", metaInfo?.etag)

        // Verify restoreIncompleteDownload populates Paused state with isDesktopSpoofed = true
        val restored = engine.restoreIncompleteDownload(tempDir)
        assertTrue("restoreIncompleteDownload should succeed", restored)
        val pausedState = engine.downloadState.value as DownloadState.Paused
        assertTrue("Paused state must preserve isDesktopSpoofed flag", pausedState.isDesktopSpoofed)
    }

    @Test
    fun testSlicerPreferences_SaveAndRetrieveUrlAndSecret() {
        val testContext = DummyTestContext(tempDir)
        assertEquals("", SlicerPreferences.getSlicerUrl(testContext))
        assertEquals("", SlicerPreferences.getSlicerSecret(testContext))

        SlicerPreferences.setSlicerUrl(testContext, "https://my-worker.workers.dev")
        SlicerPreferences.setSlicerSecret(testContext, "secret123")

        assertEquals("https://my-worker.workers.dev", SlicerPreferences.getSlicerUrl(testContext))
        assertEquals("secret123", SlicerPreferences.getSlicerSecret(testContext))
    }

    @Test
    fun testSlicerPreferences_BuildSlicedUrl_WithAndWithoutSecret() {
        val urlWithoutSecret = SlicerPreferences.buildSlicedUrl(
            slicerBaseUrl = "https://my-worker.workers.dev",
            targetUrl = "https://example.com/bigfile.iso"
        )
        assertTrue(urlWithoutSecret.startsWith("https://my-worker.workers.dev/slice?url=https%3A%2F%2Fexample.com%2Fbigfile.iso"))
        assertFalse(urlWithoutSecret.contains("&key="))

        val urlWithSecret = SlicerPreferences.buildSlicedUrl(
            slicerBaseUrl = "https://my-worker.workers.dev/",
            targetUrl = "https://example.com/bigfile.iso",
            secretKey = "myKey@123"
        )
        assertTrue(urlWithSecret.startsWith("https://my-worker.workers.dev/slice?url=https%3A%2F%2Fexample.com%2Fbigfile.iso"))
        assertTrue(urlWithSecret.contains("&key=myKey%40123"))
    }

    @Test
    fun testMetaInfo_SavesAndRestoresCloudSlicerActiveFlag() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val metaFile = engine.getMetaFile(tempDir, "test_slicer_resume.bin")
        engine.saveCompletedChunks(
            metaFile = metaFile,
            url = "https://example.com/test_slicer_resume.bin",
            totalBytes = 10485760L,
            completedIndices = setOf(0, 1),
            etag = "\"slicerEtag\"",
            lastModified = "Wed, 21 Oct 2025 07:28:00 GMT",
            isDesktopSpoofed = false,
            isCloudSlicerActive = true
        )

        val metaInfo = engine.readMetaInfo(metaFile)
        assertNotNull("MetaInfo must be readable", metaInfo)
        assertEquals(true, metaInfo?.isCloudSlicerActive)
        assertEquals(false, metaInfo?.isDesktopSpoofed)
        assertEquals(setOf(0, 1), metaInfo?.completedChunks)

        // Verify restoreIncompleteDownload populates Paused state with isCloudSlicerActive = true
        val restored = engine.restoreIncompleteDownload(tempDir)
        assertTrue("restoreIncompleteDownload should succeed", restored)
        val pausedState = engine.downloadState.value as DownloadState.Paused
        assertTrue("Paused state must preserve isCloudSlicerActive flag", pausedState.isCloudSlicerActive)
    }

    @Test
    fun testAdaptiveProbe_Stage3_CloudSlicerFallback() {
        val payload = ByteArray(8 * 1024 * 1024) { (it % 256).toByte() }
        val expectedHash = sha256(payload)

        // Dispatcher:
        // /origin/stream rejects ranges for both Mobile and Desktop (returns 200 OK without Accept-Ranges or 200 OK on Range: bytes=0-0)
        // /slice endpoint acts as Cloud Slicer: validates X-Slicer-Key (or query param), slices payload for Range requests and returns 206 Partial Content!
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                if (path.startsWith("/origin/stream")) {
                    if (request.method == "HEAD") {
                        return MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Length", payload.size.toString())
                    }
                    // For both Range: bytes=0-0 and full stream, return 200 OK without Accept-Ranges
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                        .setBody(Buffer().write(payload))
                } else if (path.startsWith("/slice")) {
                    val keyHeader = request.getHeader("X-Slicer-Key")
                    val hasKeyInQuery = path.contains("key=TestSecretKey123")
                    if (keyHeader != "TestSecretKey123" && !hasKeyInQuery) {
                        return MockResponse().setResponseCode(401).setBody("Unauthorized")
                    }

                    val range = request.getHeader("Range")
                    if (range != null && range.startsWith("bytes=")) {
                        val parts = range.removePrefix("bytes=").split("-")
                        val start = parts[0].toIntOrNull() ?: 0
                        val end = parts.getOrNull(1)?.toIntOrNull() ?: (payload.size - 1)
                        val slice = payload.copyOfRange(start, minOf(end + 1, payload.size))

                        return MockResponse()
                            .setResponseCode(206)
                            .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                            .setHeader("Content-Length", slice.size.toString())
                            .setHeader("Accept-Ranges", "bytes")
                            .setBody(Buffer().write(slice))
                    }
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                        .setBody(Buffer().write(payload))
                }
                return MockResponse().setResponseCode(404)
            }
        }

        val testContext = DummyTestContext(tempDir)
        val slicerBaseUrl = server.url("").toString().trimEnd('/')
        SlicerPreferences.setSlicerUrl(testContext, slicerBaseUrl)
        SlicerPreferences.setSlicerSecret(testContext, "TestSecretKey123")

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(testContext, TestNetworkProvider(client))

        val originUrl = server.url("/origin/stream/test_slicer.bin").toString()
        engine.startDownload(originUrl, tempDir)

        var finished = false
        var timeoutMs = 10_000L
        while (!finished && timeoutMs > 0) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                finished = true
                val file = File(state.filePath)
                assertTrue("Downloaded file must exist", file.exists())
                assertEquals(payload.size.toLong(), file.length())
                assertEquals(expectedHash, sha256(file.readBytes()))
            } else if (state is DownloadState.Failed) {
                fail("Download failed unexpectedly: ${state.error}")
            }
            Thread.sleep(50)
            timeoutMs -= 50
        }

        assertTrue("Stage 3 Cloud Slicer fallback should complete download successfully", finished)
    }
}

