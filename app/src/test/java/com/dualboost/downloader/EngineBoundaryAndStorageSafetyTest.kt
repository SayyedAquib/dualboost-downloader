package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadChunk
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
import java.io.IOException
import java.security.MessageDigest

class EngineBoundaryAndStorageSafetyTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-boundary-safety-${System.currentTimeMillis()}")
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

    // --- resolveWritableOutputFile Tests ---

    @Test
    fun testResolveWritableOutputFile_FilenameCollisionSequence() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Create base file
        val file1 = File(tempDir, "archive.zip")
        file1.writeText("initial content")

        val candidate1 = engine.resolveWritableOutputFile(tempDir, "archive.zip", "https://example.com/other1.zip")
        assertEquals("archive (1).zip", candidate1.name)

        // Create the collision file
        candidate1.writeText("content 1")
        val candidate2 = engine.resolveWritableOutputFile(tempDir, "archive.zip", "https://example.com/other2.zip")
        assertEquals("archive (2).zip", candidate2.name)

        // Create collision file 2
        candidate2.writeText("content 2")
        val candidate3 = engine.resolveWritableOutputFile(tempDir, "archive.zip", "https://example.com/other3.zip")
        assertEquals("archive (3).zip", candidate3.name)
    }

    @Test
    fun testResolveWritableOutputFile_ReusesExistingFileWhenMetaUrlMatches() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val fileName = "resumable_match.iso"
        val existingFile = File(tempDir, fileName)
        existingFile.writeBytes(ByteArray(1024))

        val testUrl = "https://example.com/resumable_match.iso"
        val metaFile = engine.getMetaFile(tempDir, fileName)
        engine.saveCompletedChunks(metaFile, testUrl, 1024L, setOf(0))

        val resolved = engine.resolveWritableOutputFile(tempDir, fileName, testUrl)
        assertEquals(fileName, resolved.name)
        assertEquals(existingFile.canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun testResolveWritableOutputFile_StrictPathTraversalBoundaryCheck() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val testCases = listOf(
            "../../secret.sh",
            "..\\..\\system.bat",
            "/var/tmp/bad.exe",
            "nested/sub/file.bin"
        )

        for (maliciousName in testCases) {
            val resolved = engine.resolveWritableOutputFile(tempDir, maliciousName, "https://example.com/test")
            assertTrue(
                "Resolved file must strictly reside within target directory: ${resolved.canonicalPath}",
                resolved.canonicalPath.startsWith(tempDir.canonicalPath)
            )
        }
    }

    // --- checkAvailableStorage Tests ---

    @Test
    fun testCheckAvailableStorage_ZeroOrNegativeRequiredBytes_NeverThrows() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Negative or zero required bytes should return immediately without throwing
        try {
            engine.checkAvailableStorage(tempDir, 0L)
            engine.checkAvailableStorage(tempDir, -100L)
        } catch (e: Exception) {
            fail("Storage check should not throw for 0 or negative bytes: ${e.message}")
        }
    }

    @Test
    fun testCheckAvailableStorage_ExistingFileSizeCoversRequired_NeverThrows() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val existingFile = File(tempDir, "preallocated.bin")
        existingFile.writeBytes(ByteArray(10 * 1024 * 1024)) // 10 MB

        // Requiring 10 MB when 10 MB is already on disk -> needed additional is 0
        try {
            engine.checkAvailableStorage(tempDir, 10 * 1024 * 1024L, existingFile)
            engine.checkAvailableStorage(tempDir, 5 * 1024 * 1024L, existingFile) // less than existing
        } catch (e: Exception) {
            fail("Storage check should not throw when existing file size covers required: ${e.message}")
        }
    }

    @Test
    fun testCheckAvailableStorage_64BitOverflow_ThrowsInsufficientStorage() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Overflow condition: neededAdditionalBytes > Long.MAX_VALUE - safetyBuffer
        val overflowSize = Long.MAX_VALUE - 1024L
        try {
            engine.checkAvailableStorage(tempDir, overflowSize)
            fail("Should throw IOException for overflow file size")
        } catch (e: IOException) {
            assertTrue("Exception message must contain 'Insufficient storage'", e.message?.contains("Insufficient storage") == true)
        }
    }

    // --- extractFileName & extractFileNameFromDisposition Extended Tests ---

    @Test
    fun testExtractFileName_PercentDecodingAndSpaceReplacement() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val url1 = "https://example.com/files/My%20Document%20(2026).pdf"
        assertEquals("My Document (2026).pdf", engine.extractFileName(url1))

        val url2 = "https://example.com/files/Test_20File.iso"
        assertEquals("Test File.iso", engine.extractFileName(url2))
    }

    @Test
    fun testExtractFileName_UrlWithQueryContainingDotsAndSlashes() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Path is /download, query parameter is ?file=linux.iso
        val url = "https://example.com/download?file=linux.iso"
        assertEquals("download.bin", engine.extractFileName(url))
    }

    @Test
    fun testExtractFileNameFromDisposition_SemicolonsInsideQuotesSanitizedToUnderscores() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val header = """attachment; filename="data;part1;final.bin"; size=1048576"""
        assertEquals("data_part1_final.bin", engine.extractFileNameFromDisposition(header))
    }

    @Test
    fun testExtractFileNameFromDisposition_EmptyOrDotsOnly_ReturnsNull() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        assertNull(engine.extractFileNameFromDisposition("""attachment; filename="" """))
        assertNull(engine.extractFileNameFromDisposition("""attachment; filename="." """))
        assertNull(engine.extractFileNameFromDisposition("""attachment; filename=".." """))
    }

    // --- computeSha256 Tests ---

    @Test
    fun testComputeSha256_NonExistentFileReturnsNull() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val missing = File(tempDir, "non_existent_file.bin")
        assertNull(engine.computeSha256(missing))
    }

    @Test
    fun testComputeSha256_ZeroByteFileReturnsNull() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val emptyFile = File(tempDir, "empty.bin")
        emptyFile.createNewFile()
        assertEquals(0L, emptyFile.length())
        assertNull(engine.computeSha256(emptyFile))
    }

    @Test
    fun testComputeSha256_AccurateHexDigest() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val payload = "DualBoost2026TestPayload".toByteArray(Charsets.UTF_8)
        val testFile = File(tempDir, "hello.txt")
        testFile.writeBytes(payload)

        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }
        val actual = engine.computeSha256(testFile)
        assertEquals(expectedDigest, actual)
    }

    // --- getMimeType Tests ---

    @Test
    fun testGetMimeType_CaseInsensitiveAndFallback() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        // Uppercase extensions should resolve without crashing
        val apkMime = engine.getMimeType("APP.APK")
        assertNotNull(apkMime)

        // Unknown extensions fallback to application/octet-stream
        assertEquals("application/octet-stream", engine.getMimeType("unknown.customxyz123"))

        // Files with no extension fallback to application/octet-stream
        assertEquals("application/octet-stream", engine.getMimeType("LICENSE"))
    }

    // --- sanitizeUrlForLog Tests ---

    @Test
    fun testSanitizeUrlForLog_VariousFormats() {
        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))

        val urlWithParams = "https://cdn.example.com/asset.zip?token=secretKey&expiry=12345678"
        assertEquals("https://cdn.example.com/asset.zip", engine.sanitizeUrlForLog(urlWithParams))

        val cleanUrl = "https://cdn.example.com/asset.zip"
        assertEquals("https://cdn.example.com/asset.zip", engine.sanitizeUrlForLog(cleanUrl))
    }

    // --- Idempotent Start Download Test ---

    @Test
    fun testStartDownload_WhenAlreadyDownloadingSameUrl_IsIdempotent() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", "10485760")
                        .setHeader("Accept-Ranges", "bytes")
                }
                // Return a throttled response so it stays in Downloading state
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-10485759/10485760")
                    .setHeader("Content-Length", "10485760")
                    .throttleBody(1024, 1, java.util.concurrent.TimeUnit.SECONDS)
                    .setBody(okio.Buffer().write(ByteArray(1024 * 1024)))
            }
        }

        val client = OkHttpClient.Builder().build()
        val engine = DualDownloaderEngine(DummyTestContext(tempDir), TestNetworkProvider(client))
        val targetUrl = server.url("/idempotent_test.bin").toString()

        engine.startDownload(targetUrl, tempDir)

        var waited = 0
        while (engine.downloadState.value !is DownloadState.Downloading && waited < 2000) {
            kotlinx.coroutines.delay(50)
            waited += 50
        }

        val stateBefore = engine.downloadState.value
        assertTrue("Engine should be in Downloading state", stateBefore is DownloadState.Downloading)

        // Call startDownload AGAIN with identical URL - should return immediately without resetting state
        engine.startDownload(targetUrl, tempDir)
        val stateAfter = engine.downloadState.value
        assertTrue("Engine must remain in Downloading state without restart", stateAfter is DownloadState.Downloading)

        engine.pauseDownload()
    }
}
