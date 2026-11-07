package com.dualboost.downloader

import android.content.Context
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.security.ThreatDetectionEngine
import com.dualboost.downloader.security.ThreatType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineThreatInterceptionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context
    private lateinit var networkProvider: NetworkProvider
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var targetDir: File

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        targetDir = tempFolder.newFolder("downloads")
        context = DummyTestContext(targetDir)
        okHttpClient = OkHttpClient.Builder().build()
        networkProvider = AsymmetricWorkStealingTest.MockNetworkProvider(okHttpClient)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun preFlightThreat_blocksDownloadBeforeWorkersStart() = runBlocking {
        val engine = DualDownloaderEngine(
            context = context,
            networkManager = networkProvider,
            threatEngineOverride = ThreatDetectionEngine()
        )

        // URL with double extension trap
        val maliciousUrl = "https://moviesleech.media/Mirzapur.The.Movie.2026.4K.mkv.apk"

        engine.startDownload(
            url = maliciousUrl,
            targetDir = targetDir
        )

        var state: DownloadState = DownloadState.Idle
        var timeoutMs = 5000L
        while (timeoutMs > 0) {
            state = engine.downloadState.value
            if (state !is DownloadState.Idle && state !is DownloadState.Probing) {
                break
            }
            delay(50)
            timeoutMs -= 50
        }

        assertTrue("Download state should be ThreatDetected", state is DownloadState.ThreatDetected)
        val threatState = state as DownloadState.ThreatDetected
        assertEquals(ThreatType.DOUBLE_EXTENSION, threatState.threat.threatType)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun probeHeadersThreat_blocksDownloadOnMaliciousMime() = runBlocking {
        // Mock server sends APK MIME type for a requested MKV file
        val threatResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Length", "10485760")
            .setHeader("Accept-Ranges", "bytes")
            .setHeader("Content-Type", "application/vnd.android.package-archive")
            .setHeader("Content-Disposition", "attachment; filename=\"movie.mkv\"")

        mockWebServer.enqueue(threatResponse)
        mockWebServer.enqueue(threatResponse.clone())

        val engine = DualDownloaderEngine(
            context = context,
            networkManager = networkProvider,
            threatEngineOverride = ThreatDetectionEngine()
        )

        val url = mockWebServer.url("/video/stream.mkv").newBuilder().host("127.0.0.1").build().toString()
        engine.startDownload(url = url, targetDir = targetDir)

        var state: DownloadState = DownloadState.Idle
        var timeoutMs = 5000L
        while (timeoutMs > 0) {
            state = engine.downloadState.value
            if (state !is DownloadState.Idle && state !is DownloadState.Probing) {
                break
            }
            delay(50)
            timeoutMs -= 50
        }

        assertTrue("Expected ThreatDetected state due to APK MIME type", state is DownloadState.ThreatDetected)
        val threatState = state as DownloadState.ThreatDetected
        assertEquals(ThreatType.MALICIOUS_MIME, threatState.threat.threatType)
    }

    @Test
    fun magicBytesThreat_blocksDownloadWhenPayloadIsDisguisedApk() = runBlocking {
        // Head probe responds normally as a video file
        val headResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Length", "1024")
            .setHeader("Accept-Ranges", "bytes")
            .setHeader("Content-Type", "video/x-matroska")
            .setHeader("Content-Disposition", "attachment; filename=\"episode1.mkv\"")

        // First chunk response body starts with PK 0x03 0x04 (ZIP/APK header), disguising an APK as MKV
        val maliciousPayload = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00) + ByteArray(1016)
        val chunkResponse = MockResponse()
            .setResponseCode(206)
            .setHeader("Content-Range", "bytes 0-1023/1024")
            .setHeader("Content-Length", "1024")
            .setBody(okio.Buffer().write(maliciousPayload))

        mockWebServer.enqueue(headResponse)
        mockWebServer.enqueue(chunkResponse)

        val engine = DualDownloaderEngine(
            context = context,
            networkManager = networkProvider,
            threatEngineOverride = ThreatDetectionEngine()
        )

        val url = mockWebServer.url("/media/episode1.mkv").newBuilder().host("127.0.0.1").build().toString()
        engine.startDownload(url = url, targetDir = targetDir)

        var state: DownloadState = DownloadState.Idle
        var timeoutMs = 5000L
        while (timeoutMs > 0) {
            state = engine.downloadState.value
            if (state is DownloadState.ThreatDetected || state is DownloadState.Failed || state is DownloadState.Completed) {
                break
            }
            delay(50)
            timeoutMs -= 50
        }

        assertTrue("Expected ThreatDetected state due to ZIP/APK magic bytes in MKV", state is DownloadState.ThreatDetected)
        val threatState = state as DownloadState.ThreatDetected
        assertEquals(ThreatType.MAGIC_BYTE_MISMATCH, threatState.threat.threatType)
    }

    @Test
    fun allowDangerousBypass_allowsDownloadToProceed() = runBlocking {
        val engine = DualDownloaderEngine(
            context = context,
            networkManager = networkProvider,
            threatEngineOverride = ThreatDetectionEngine()
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "1024")
                .setHeader("Accept-Ranges", "bytes")
        )

        val suspiciousUrl = "https://ad-redirector.net/sample.mp4"
        engine.startDownload(
            url = suspiciousUrl,
            targetDir = targetDir,
            allowDangerousBypass = true
        )

        delay(300)

        val state = engine.downloadState.value
        assertFalse("State should not be ThreatDetected when bypass is enabled", state is DownloadState.ThreatDetected)
    }
}
