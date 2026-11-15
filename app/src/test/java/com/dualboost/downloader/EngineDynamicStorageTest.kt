package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.StorageDestination
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineDynamicStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: DummyTestContext
    private lateinit var baseDir: File
    private lateinit var engine: DualDownloaderEngine
    private lateinit var mockNetwork: NetworkProvider

    class MockNetworkProvider : NetworkProvider {
        private val client = OkHttpClient()
        override val isWifiAvailable = MutableStateFlow(true)
        override val isCellularAvailable = MutableStateFlow(true)
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client
    }

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("app_base")
        dummyContext = DummyTestContext(baseDir)
        mockNetwork = MockNetworkProvider()
        engine = DualDownloaderEngine(dummyContext, networkManager = mockNetwork)
    }

    @Test
    fun testEngine_DefaultsToDefaultStorageDestination() {
        assertEquals(StorageDestination.DEFAULT_ID, engine.getStorageDestination().id)
        val defaultDir = engine.getDefaultDownloadDir()
        assertNotNull(defaultDir)
        assertTrue(defaultDir.exists())
    }

    @Test
    fun testEngine_SetsAndUsesConfiguredSubfolderDestination() {
        engine.setStorageDestination(StorageDestination.PRESET_DUALBOOST)
        assertEquals(StorageDestination.DUALBOOST_ID, engine.getStorageDestination().id)

        val resolved = engine.getDefaultDownloadDir()
        assertTrue(resolved.name.contains("DualBoost", ignoreCase = true) || resolved.canonicalPath.contains("DualBoost"))
        assertTrue(resolved.exists())
    }

    @Test
    fun testEngine_SetsAndUsesCustomStorageDestination() {
        val customFolder = tempFolder.newFolder("MyCustomStorage")
        val customDest = StorageDestination(
            id = StorageDestination.CUSTOM_ID,
            displayName = "Custom Folder",
            customPath = customFolder.absolutePath
        )

        engine.setStorageDestination(customDest)
        assertEquals(StorageDestination.CUSTOM_ID, engine.getStorageDestination().id)

        val resolved = engine.getDefaultDownloadDir()
        assertEquals(customFolder.canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun testEngine_UnwritableCustomStorage_FallsBackToSafeDirectory() {
        // Point to an unwritable directory
        val invalidDir = File(tempFolder.newFile("dummy_file_barrier"), "sub_dir")
        val badDest = StorageDestination(
            id = StorageDestination.CUSTOM_ID,
            displayName = "Unwritable Folder",
            customPath = invalidDir.absolutePath
        )

        engine.setStorageDestination(badDest)

        val resolved = engine.getDefaultDownloadDir()
        assertNotNull(resolved)
        assertTrue(resolved.exists())
        assertTrue(resolved.canWrite())
        assertFalse(resolved.canonicalPath == invalidDir.canonicalPath)
    }

    @Test
    fun testRepository_ExposesStorageDestinationStateFlowAndUpdates() {
        val repository = DownloadRepository(
            context = dummyContext,
            networkProvider = mockNetwork,
            engineOverride = engine,
            isServiceEnabled = false
        )

        assertEquals(StorageDestination.DEFAULT_ID, repository.storageDestination.value.id)

        repository.setStorageDestination(StorageDestination.PRESET_MEDIA)
        assertEquals(StorageDestination.MEDIA_ID, repository.storageDestination.value.id)
        assertEquals(StorageDestination.MEDIA_ID, engine.getStorageDestination().id)

        repository.resetStorageDestinationToDefault()
        assertEquals(StorageDestination.DEFAULT_ID, repository.storageDestination.value.id)
        assertEquals(StorageDestination.DEFAULT_ID, engine.getStorageDestination().id)
    }
}
