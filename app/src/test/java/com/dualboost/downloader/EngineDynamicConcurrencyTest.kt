package com.dualboost.downloader

import com.dualboost.downloader.data.ConcurrencyConfig
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.NetworkInterfaceType
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
import java.util.concurrent.atomic.AtomicInteger

class EngineDynamicConcurrencyTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var testData: ByteArray

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-dynamic-concurrency-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // 16 MB payload -> spans four 4 MB chunks
        testData = ByteArray(16 * 1024 * 1024) { (it % 251).toByte() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
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

        override fun getWifiClient(): OkHttpClient? = if (_isWifiAvailable.value) underlyingWifiClient else null
        override fun getCellularClient(): OkHttpClient? = if (_isCellularAvailable.value) underlyingCellularClient else null
        override fun getDefaultClient(): OkHttpClient = underlyingWifiClient
    }

    private fun setupDispatcher() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val method = request.method ?: "GET"
                if (method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", testData.size.toString())
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Disposition", "inline; filename=\"concurrency_test.bin\"")
                }

                val rangeHeader = request.getHeader("Range")
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val parts = rangeHeader.removePrefix("bytes=").split("-")
                    val start = parts[0].toInt()
                    val end = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toInt() else testData.size - 1
                    val length = (end - start) + 1
                    val chunkData = testData.copyOfRange(start, end + 1)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/${testData.size}")
                        .setHeader("Content-Length", length.toString())
                        .setBody(Buffer().write(chunkData))
                }

                return MockResponse().setResponseCode(200).setBody(Buffer().write(testData))
            }
        }
    }

    private suspend fun awaitDownloadComplete(engine: DualDownloaderEngine, timeoutMs: Long = 10000): DownloadState.Completed {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val state = engine.downloadState.value
            if (state is DownloadState.Completed) return state
            if (state is DownloadState.Failed) throw AssertionError("Download failed: ${state.error}")
            delay(50)
        }
        throw AssertionError("Timeout waiting for download completion. Last state: ${engine.downloadState.value}")
    }

    @Test
    fun testEngine_LaunchesConfiguredNumberOfWorkers() = runBlocking {
        setupDispatcher()

        val wifiClient = OkHttpClient.Builder().build()
        val cellularClient = OkHttpClient.Builder().build()
        val netProvider = TaggedNetworkProvider(wifiClient, cellularClient)
        val thermalProvider = TestThermalProvider(ThermalStatus.NONE)
        val dummyContext = DummyTestContext(tempDir)

        val engine = DualDownloaderEngine(
            context = dummyContext,
            networkManager = netProvider,
            thermalStatusProviderOverride = thermalProvider
        )

        // Configure 3 Wi-Fi workers and 1 Cellular worker
        val config = ConcurrencyConfig(wifiWorkers = 3, cellularWorkers = 1, isAutoScaleEnabled = false)
        val url = server.url("/concurrency_test.bin").toString()
        engine.startDownload(url, tempDir, concurrencyConfig = config)

        val completed = awaitDownloadComplete(engine)
        val file = File(completed.filePath)
        assertTrue(file.exists())
        assertEquals(testData.size.toLong(), file.length())

        // Wi-Fi worker 0, 1, 2 should have executed, worker 3 should have 0 executions
        assertTrue("Wi-Fi worker 0 must execute", engine.getWorkerExecutionCount(NetworkInterfaceType.WIFI, 0) > 0)
        assertEquals("Wi-Fi worker 3 must not exist or execute", 0, engine.getWorkerExecutionCount(NetworkInterfaceType.WIFI, 3))

        // Cellular worker 0 should have executed, worker 1 should have 0 executions
        assertTrue("Cellular worker 0 must execute", engine.getWorkerExecutionCount(NetworkInterfaceType.CELLULAR, 0) > 0)
        assertEquals("Cellular worker 1 must not exist or execute", 0, engine.getWorkerExecutionCount(NetworkInterfaceType.CELLULAR, 1))
    }

    @Test
    fun testEngine_ThermalModerate_StrictlyCapsCellularToOneWorker() = runBlocking {
        setupDispatcher()

        val wifiClient = OkHttpClient.Builder().build()
        val cellularClient = OkHttpClient.Builder().build()
        val netProvider = TaggedNetworkProvider(wifiClient, cellularClient)
        val thermalProvider = TestThermalProvider(ThermalStatus.MODERATE)
        val dummyContext = DummyTestContext(tempDir)

        val engine = DualDownloaderEngine(
            context = dummyContext,
            networkManager = netProvider,
            thermalStatusProviderOverride = thermalProvider
        )

        // Configure 2 Wi-Fi workers and 4 Cellular workers under MODERATE heat
        val config = ConcurrencyConfig(wifiWorkers = 2, cellularWorkers = 4, isAutoScaleEnabled = false)
        val url = server.url("/concurrency_test.bin").toString()
        engine.startDownload(url, tempDir, concurrencyConfig = config)

        val completed = awaitDownloadComplete(engine)
        val file = File(completed.filePath)
        assertTrue(file.exists())
        assertEquals(testData.size.toLong(), file.length())

        // Under MODERATE heat, cellular workers 1, 2, 3 must have 0 chunk transfers
        assertEquals("Cellular worker 1 must be throttled under MODERATE heat", 0, engine.getWorkerExecutionCount(NetworkInterfaceType.CELLULAR, 1))
        assertEquals("Cellular worker 2 must be throttled under MODERATE heat", 0, engine.getWorkerExecutionCount(NetworkInterfaceType.CELLULAR, 2))
        assertEquals("Cellular worker 3 must be throttled under MODERATE heat", 0, engine.getWorkerExecutionCount(NetworkInterfaceType.CELLULAR, 3))
    }

    @Test
    fun testEngine_ThermalSevere_CompletelySuppressesCellularWorkers() = runBlocking {
        setupDispatcher()

        val wifiClient = OkHttpClient.Builder().build()
        val cellularClient = OkHttpClient.Builder().build()
        val netProvider = TaggedNetworkProvider(wifiClient, cellularClient, wifiAvailable = true)
        val thermalProvider = TestThermalProvider(ThermalStatus.SEVERE)
        val dummyContext = DummyTestContext(tempDir)

        val engine = DualDownloaderEngine(
            context = dummyContext,
            networkManager = netProvider,
            thermalStatusProviderOverride = thermalProvider
        )

        // Configure 2 Wi-Fi workers and 4 Cellular workers under SEVERE heat
        val config = ConcurrencyConfig(wifiWorkers = 2, cellularWorkers = 4, isAutoScaleEnabled = false)
        val url = server.url("/concurrency_test.bin").toString()
        engine.startDownload(url, tempDir, concurrencyConfig = config)

        val completed = awaitDownloadComplete(engine)
        val file = File(completed.filePath)
        assertTrue(file.exists())
        assertEquals(testData.size.toLong(), file.length())

        // Under SEVERE heat with Wi-Fi available, all cellular workers (0..3) must have 0 chunk transfers
        for (i in 0 until 4) {
            assertEquals("Cellular worker $i must be completely suppressed under SEVERE heat", 0, engine.getWorkerExecutionCount(NetworkInterfaceType.CELLULAR, i))
        }
    }
}
