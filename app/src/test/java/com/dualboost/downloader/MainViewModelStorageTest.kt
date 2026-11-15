package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.StorageDestination
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class MainViewModelStorageTest {

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
        tempDir = File("build/test-storage-vm-${System.currentTimeMillis()}")
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
    fun testViewModel_ExposesStorageDestinationStateFlow() {
        assertEquals(StorageDestination.DEFAULT_ID, viewModel.storageDestination.value.id)
    }

    @Test
    fun testViewModel_UpdatesStorageDestination() {
        viewModel.updateStorageDestination(StorageDestination.PRESET_DUALBOOST)
        assertEquals(StorageDestination.DUALBOOST_ID, viewModel.storageDestination.value.id)
        assertEquals(StorageDestination.DUALBOOST_ID, repository.storageDestination.value.id)
    }

    @Test
    fun testViewModel_ResetsStorageDestinationToDefault() {
        viewModel.updateStorageDestination(StorageDestination.PRESET_MEDIA)
        assertEquals(StorageDestination.MEDIA_ID, viewModel.storageDestination.value.id)

        viewModel.resetStorageDestinationToDefault()
        assertEquals(StorageDestination.DEFAULT_ID, viewModel.storageDestination.value.id)
    }
}
