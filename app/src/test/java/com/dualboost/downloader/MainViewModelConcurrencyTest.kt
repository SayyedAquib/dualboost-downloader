package com.dualboost.downloader

import com.dualboost.downloader.data.ConcurrencyConfig
import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class MainViewModelConcurrencyTest {

    private lateinit var tempDir: File
    private lateinit var dummyContext: DummyTestContext
    private lateinit var repository: DownloadRepository
    private lateinit var viewModel: MainViewModel

    class MockNetworkProvider(private val client: OkHttpClient) : NetworkProvider {
        private val _wifiAvailable = MutableStateFlow(true)
        private val _cellAvailable = MutableStateFlow(true)
        override val isWifiAvailable: StateFlow<Boolean> = _wifiAvailable
        override val isCellularAvailable: StateFlow<Boolean> = _cellAvailable
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client
    }

    @Before
    fun setUp() {
        tempDir = File("build/test-concurrency-vm-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        dummyContext = DummyTestContext(tempDir)
        val client = OkHttpClient.Builder().build()
        val mockNetwork = MockNetworkProvider(client)
        val engine = DualDownloaderEngine(dummyContext, mockNetwork)

        repository = DownloadRepository(
            context = dummyContext,
            networkProvider = mockNetwork,
            engineOverride = engine,
            isServiceEnabled = false
        )
        viewModel = MainViewModel(repository = repository)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testViewModel_ExposesDefaultConcurrencyConfig() {
        val config = viewModel.concurrencyConfig.value
        assertEquals("Default Wi-Fi workers must be 2", 2, config.wifiWorkers)
        assertEquals("Default Cellular workers must be 2", 2, config.cellularWorkers)
        assertTrue("Auto-Scale must be enabled by default", config.isAutoScaleEnabled)
        assertEquals("Default total workers must be 4", 4, config.totalWorkers)
    }

    @Test
    fun testViewModel_UpdateWifiWorkers_PersistsAndEmits() {
        viewModel.updateWifiWorkers(5)
        assertEquals(5, viewModel.concurrencyConfig.value.wifiWorkers)
        assertEquals(5, repository.concurrencyPreferences.getConcurrencyConfig().wifiWorkers)

        // Clamping upper bound
        viewModel.updateWifiWorkers(12)
        assertEquals(8, viewModel.concurrencyConfig.value.wifiWorkers)

        // Clamping lower bound
        viewModel.updateWifiWorkers(0)
        assertEquals(1, viewModel.concurrencyConfig.value.wifiWorkers)
    }

    @Test
    fun testViewModel_UpdateCellularWorkers_PersistsAndEmits() {
        viewModel.updateCellularWorkers(6)
        assertEquals(6, viewModel.concurrencyConfig.value.cellularWorkers)
        assertEquals(6, repository.concurrencyPreferences.getConcurrencyConfig().cellularWorkers)

        // Clamping upper bound
        viewModel.updateCellularWorkers(99)
        assertEquals(8, viewModel.concurrencyConfig.value.cellularWorkers)

        // Clamping lower bound
        viewModel.updateCellularWorkers(-3)
        assertEquals(1, viewModel.concurrencyConfig.value.cellularWorkers)
    }

    @Test
    fun testViewModel_ToggleAutoScale_PersistsAndEmits() {
        viewModel.toggleAutoScale(true)
        assertTrue(viewModel.concurrencyConfig.value.isAutoScaleEnabled)
        assertTrue(repository.concurrencyPreferences.getConcurrencyConfig().isAutoScaleEnabled)

        viewModel.toggleAutoScale(false)
        assertFalse(viewModel.concurrencyConfig.value.isAutoScaleEnabled)
        assertFalse(repository.concurrencyPreferences.getConcurrencyConfig().isAutoScaleEnabled)
    }

    @Test
    fun testViewModel_ResetConcurrencyToDefault_RestoresDefaults() {
        viewModel.updateWifiWorkers(7)
        viewModel.updateCellularWorkers(6)
        viewModel.toggleAutoScale(true)

        viewModel.resetConcurrencyToDefault()
        val config = viewModel.concurrencyConfig.value
        assertEquals(ConcurrencyConfig.DEFAULT, config)
        assertEquals(ConcurrencyConfig.DEFAULT, repository.concurrencyPreferences.getConcurrencyConfig())
    }
}
