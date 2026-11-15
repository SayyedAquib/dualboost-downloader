package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.StreamPalette
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

class MainViewModelPaletteTest {

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
        tempDir = File("build/test-palette-vm-${System.currentTimeMillis()}")
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
    fun testViewModel_ExposesStreamPaletteStateFlow() {
        assertEquals("Default palette must be DYNAMIC_MATERIAL_YOU", StreamPalette.DYNAMIC_MATERIAL_YOU, viewModel.streamPalette.value)
    }

    @Test
    fun testViewModel_UpdateStreamPalette_PersistsAndEmits() {
        viewModel.updateStreamPalette(StreamPalette.EMERALD_AMBER)
        assertEquals(StreamPalette.EMERALD_AMBER, viewModel.streamPalette.value)
        assertEquals(StreamPalette.EMERALD_AMBER, repository.streamPalettePreferences.getStreamPalette())

        viewModel.updateStreamPalette(StreamPalette.SUNSET_CORAL)
        assertEquals(StreamPalette.SUNSET_CORAL, viewModel.streamPalette.value)
        assertEquals(StreamPalette.SUNSET_CORAL, repository.streamPalettePreferences.getStreamPalette())
    }

    @Test
    fun testViewModel_ResetStreamPalette_RestoresDefault() {
        viewModel.updateStreamPalette(StreamPalette.CYBERPUNK_NEON)
        assertEquals(StreamPalette.CYBERPUNK_NEON, viewModel.streamPalette.value)

        viewModel.resetStreamPaletteToDefault()
        assertEquals(StreamPalette.DYNAMIC_MATERIAL_YOU, viewModel.streamPalette.value)
        assertEquals(StreamPalette.DYNAMIC_MATERIAL_YOU, repository.streamPalettePreferences.getStreamPalette())
    }
}
