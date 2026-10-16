package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

class DownloadRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tempDir = File("build/test-repo-${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    class MockNetworkProvider(private val client: OkHttpClient) : NetworkProvider {
        val wifiAvailableFlow = MutableStateFlow(true)
        val cellAvailableFlow = MutableStateFlow(true)

        override val isWifiAvailable: StateFlow<Boolean> = wifiAvailableFlow
        override val isCellularAvailable: StateFlow<Boolean> = cellAvailableFlow
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client
    }

    @Test
    fun testRepository_InitialStateIsIdle() {
        val client = OkHttpClient.Builder().build()
        val dummyContext = DummyTestContext(tempDir)
        val mockNetwork = MockNetworkProvider(client)
        val engine = DualDownloaderEngine(dummyContext, mockNetwork)

        val repo = DownloadRepository(
            context = dummyContext,
            networkProvider = mockNetwork,
            engineOverride = engine
        )

        assertEquals(DownloadState.Idle, repo.downloadState.value)
    }

    @Test
    fun testRepository_StartDownload_TransitionsToProbing() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Return slow response so we catch Probing state
                Thread.sleep(100)
                return MockResponse().setResponseCode(200).setHeader("Content-Length", "100")
            }
        }

        val client = OkHttpClient.Builder().build()
        val dummyContext = DummyTestContext(tempDir)
        val mockNetwork = MockNetworkProvider(client)
        val engine = DualDownloaderEngine(dummyContext, mockNetwork)

        val repo = DownloadRepository(
            context = dummyContext,
            networkProvider = mockNetwork,
            engineOverride = engine
        )

        val observedStates = mutableListOf<DownloadState>()
        val collectScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val job = collectScope.launch {
            repo.downloadState.collect { observedStates.add(it) }
        }

        repo.startDownload(server.url("/probe_test.bin").toString(), tempDir)

        var waited = 0
        while (observedStates.none { it !is DownloadState.Idle } && waited < 3000) {
            Thread.sleep(50)
            waited += 50
        }
        job.cancel()

        assertTrue(
            "Observed states must transition from Idle to active download states: $observedStates",
            observedStates.any { it is DownloadState.Probing || it is DownloadState.Downloading || it is DownloadState.Completed }
        )


    }

    @Test
    fun testRepository_PauseDownload_TransitionsToPaused() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Return long running stream
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", "104857600")
                    .setBody(okio.Buffer().write(ByteArray(1024 * 1024)))
            }
        }

        val client = OkHttpClient.Builder().build()
        val dummyContext = DummyTestContext(tempDir)
        val mockNetwork = MockNetworkProvider(client)
        val engine = DualDownloaderEngine(dummyContext, mockNetwork)

        val repo = DownloadRepository(
            context = dummyContext,
            networkProvider = mockNetwork,
            engineOverride = engine
        )

        repo.startDownload(server.url("/pause_test.bin").toString(), tempDir)
        Thread.sleep(80)
        repo.pauseDownload()

        assertTrue(repo.downloadState.value is DownloadState.Paused)
    }


    @Test
    fun testRepository_ExposesNetworkStateFlows() {
        val client = OkHttpClient.Builder().build()
        val dummyContext = DummyTestContext(tempDir)
        val mockNetwork = MockNetworkProvider(client)
        val engine = DualDownloaderEngine(dummyContext, mockNetwork)

        val repo = DownloadRepository(
            context = dummyContext,
            networkProvider = mockNetwork,
            engineOverride = engine
        )

        assertNotNull(repo.isWifiAvailable)
        assertNotNull(repo.isCellularAvailable)
        assertTrue(repo.isWifiAvailable.value)
        assertTrue(repo.isCellularAvailable.value)
    }
}
