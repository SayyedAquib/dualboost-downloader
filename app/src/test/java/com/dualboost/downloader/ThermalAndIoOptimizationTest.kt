package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.thermal.ThermalStatus
import com.dualboost.downloader.thermal.ThermalStatusProvider
import kotlinx.coroutines.delay
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

class ThermalAndIoOptimizationTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var testData: ByteArray

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-thermal-io-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // 8 MB payload -> spans two 4 MB chunks
        testData = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    class TestThermalProvider(initialStatus: Int = ThermalStatus.NONE) : ThermalStatusProvider {
        val statusFlow = MutableStateFlow(initialStatus)
        override val thermalStatus: StateFlow<Int> = statusFlow
        var isListening = false
        override fun startListening() { isListening = true }
        override fun stopListening() { isListening = false }
    }

    class TaggedNetworkProvider(
        private val underlyingWifiClient: OkHttpClient,
        private val underlyingCellularClient: OkHttpClient,
        wifiAvailable: Boolean = true,
        cellularAvailable: Boolean = true
    ) : NetworkProvider {
        private val _isWifiAvailable = MutableStateFlow(wifiAvailable)
        private val _isCellularAvailable = MutableStateFlow(cellularAvailable)
        override val isWifiAvailable: StateFlow<Boolean> = _isWifiAvailable
        override val isCellularAvailable: StateFlow<Boolean> = _isCellularAvailable

        fun setWifiAvailable(available: Boolean) { _isWifiAvailable.value = available }
        fun setCellularAvailable(available: Boolean) { _isCellularAvailable.value = available }

        override fun getWifiClient(): OkHttpClient? = if (_isWifiAvailable.value) underlyingWifiClient else null
        override fun getCellularClient(): OkHttpClient? = if (_isCellularAvailable.value) underlyingCellularClient else null
        override fun getDefaultClient(): OkHttpClient = underlyingWifiClient
    }

    private fun setupDispatcher(
        wifiChunkCount: AtomicInteger,
        cellularChunkCount: AtomicInteger
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val iface = request.getHeader("X-Interface-Tag")
                val rangeHeader = request.getHeader("Range")
                if (rangeHeader != null) {
                    if (iface == "WIFI") wifiChunkCount.incrementAndGet()
                    if (iface == "CELLULAR") cellularChunkCount.incrementAndGet()
                }

                val method = request.method ?: "GET"
                if (method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", testData.size.toString())
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Disposition", "inline; filename=\"thermal_test.bin\"")
                }

                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val parts = rangeHeader.removePrefix("bytes=").split("-")
                    val start = parts[0].toInt()
                    val end = parts[1].toInt()
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
    }

    private suspend fun awaitDownloadComplete(engine: DualDownloaderEngine, timeoutMs: Long = 15_000L): DownloadState.Completed {
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) {
                return state
            }
            if (state is DownloadState.Failed) {
                fail("Download unexpectedly failed: ${state.error}")
            }
            delay(50)
            elapsed += 50
        }
        fail("Timed out waiting for download to complete. Current state: ${engine.downloadState.value}")
        throw IllegalStateException()
    }

    @Test
    fun testThermalBackoff_SevereHeat_PausesCellularWhenWifiAvailable() = runBlocking {
        val wifiChunkCount = AtomicInteger(0)
        val cellularChunkCount = AtomicInteger(0)
        setupDispatcher(wifiChunkCount, cellularChunkCount)

        val wifiClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().addHeader("X-Interface-Tag", "WIFI").build()
                chain.proceed(req)
            }.build()

        val cellularClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().addHeader("X-Interface-Tag", "CELLULAR").build()
                chain.proceed(req)
            }.build()

        val netProvider = TaggedNetworkProvider(wifiClient, cellularClient, wifiAvailable = true, cellularAvailable = true)
        val thermalProvider = TestThermalProvider(initialStatus = ThermalStatus.SEVERE)

        val dummyContext = DummyTestContext(tempDir)
        val engine = DualDownloaderEngine(
            context = dummyContext,
            networkManager = netProvider,
            thermalStatusProviderOverride = thermalProvider
        )

        val url = server.url("/download/thermal_test.bin").toString()
        engine.startDownload(url, tempDir)

        val completed = awaitDownloadComplete(engine)
        val file = File(completed.filePath)
        assertTrue(file.exists())
        assertEquals("File size must match test payload exactly", testData.size.toLong(), file.length())
        assertEquals("SHA-256 must match payload exactly (64 KB buffer integrity)", sha256(testData), sha256(file.readBytes()))

        // Under SEVERE heat with Wi-Fi available, cellular MUST be completely shed (0 cellular chunk requests)
        assertEquals("Cellular chunk requests must be 0 under SEVERE thermal status when Wi-Fi is active", 0, cellularChunkCount.get())
        assertTrue("Wi-Fi must handle all chunks under SEVERE thermal status", wifiChunkCount.get() > 0)
    }

    @Test
    fun testThermalBackoff_SevereHeat_FallbackToSingleCellularWorkerWhenWifiUnavailable() = runBlocking {
        val wifiChunkCount = AtomicInteger(0)
        val cellularChunkCount = AtomicInteger(0)
        setupDispatcher(wifiChunkCount, cellularChunkCount)

        val wifiClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().addHeader("X-Interface-Tag", "WIFI").build()
                chain.proceed(req)
            }.build()

        val cellularClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().addHeader("X-Interface-Tag", "CELLULAR").build()
                chain.proceed(req)
            }.build()

        // Wi-Fi is DOWN, only cellular is available
        val netProvider = TaggedNetworkProvider(wifiClient, cellularClient, wifiAvailable = false, cellularAvailable = true)
        val thermalProvider = TestThermalProvider(initialStatus = ThermalStatus.SEVERE)

        val dummyContext = DummyTestContext(tempDir)
        val engine = DualDownloaderEngine(
            context = dummyContext,
            networkManager = netProvider,
            thermalStatusProviderOverride = thermalProvider
        )

        val url = server.url("/download/thermal_test.bin").toString()
        engine.startDownload(url, tempDir)

        val completed = awaitDownloadComplete(engine)
        val file = File(completed.filePath)
        assertEquals(testData.size.toLong(), file.length())
        assertEquals(sha256(testData), sha256(file.readBytes()))

        // When Wi-Fi is unavailable, cellular chunks handle the entire payload safely
        assertEquals(0, wifiChunkCount.get())
        assertTrue(cellularChunkCount.get() > 0)
    }

    @Test
    fun testThermalBackoff_ModerateHeat_DualChannelWith64KBBufferIntegrity() = runBlocking {
        val wifiChunkCount = AtomicInteger(0)
        val cellularChunkCount = AtomicInteger(0)
        setupDispatcher(wifiChunkCount, cellularChunkCount)

        val wifiClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().addHeader("X-Interface-Tag", "WIFI").build()
                chain.proceed(req)
            }.build()

        val cellularClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().addHeader("X-Interface-Tag", "CELLULAR").build()
                chain.proceed(req)
            }.build()

        val netProvider = TaggedNetworkProvider(wifiClient, cellularClient, wifiAvailable = true, cellularAvailable = true)
        val thermalProvider = TestThermalProvider(initialStatus = ThermalStatus.MODERATE)

        val dummyContext = DummyTestContext(tempDir)
        val engine = DualDownloaderEngine(
            context = dummyContext,
            networkManager = netProvider,
            thermalStatusProviderOverride = thermalProvider
        )

        val url = server.url("/download/thermal_test.bin").toString()
        engine.startDownload(url, tempDir)

        val completed = awaitDownloadComplete(engine)
        val file = File(completed.filePath)
        assertEquals(testData.size.toLong(), file.length())
        assertEquals(sha256(testData), sha256(file.readBytes()))
    }
}
