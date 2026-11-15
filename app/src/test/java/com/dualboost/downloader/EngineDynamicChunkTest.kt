package com.dualboost.downloader

import com.dualboost.downloader.data.ChunkConfig
import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineDynamicChunkTest {

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
    fun testEngine_DefaultsToAutoAdaptiveChunkConfig() {
        val config = engine.getChunkConfig()
        assertTrue(config.isAutoAdaptive)
        assertEquals(ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES, config.manualChunkSizeBytes)
    }

    @Test
    fun testEngine_SetsAndRetrievesChunkConfig() {
        val newConfig = ChunkConfig(isAutoAdaptive = false, manualChunkSizeBytes = 16 * 1024 * 1024L)
        engine.setChunkConfig(newConfig)

        val retrieved = engine.getChunkConfig()
        assertFalse(retrieved.isAutoAdaptive)
        assertEquals(16 * 1024 * 1024L, retrieved.manualChunkSizeBytes)
    }

    @Test
    fun testEngine_SaveAndReadMetadata_PreservesCustomChunkSize() {
        val metaFile = File(baseDir, "test_download.zip.dualboost_meta")
        val customChunkSize = 8 * 1024 * 1024L

        engine.saveCompletedChunks(
            metaFile = metaFile,
            url = "https://example.com/test.zip",
            totalBytes = 100 * 1024 * 1024L,
            completedIndices = setOf(0, 1),
            chunkSize = customChunkSize
        )

        val metaInfo = engine.readMetaInfo(metaFile)
        org.junit.Assert.assertNotNull(metaInfo)
        assertEquals(customChunkSize, metaInfo!!.chunkSize)
        assertEquals(setOf(0, 1), metaInfo.completedChunks)
    }

    @Test
    fun testEngine_ResolveEffectiveChunkSize_PreservesMetadataOnResume() {
        // Engine is currently configured for 32 MB manual chunks
        engine.setChunkConfig(ChunkConfig(isAutoAdaptive = false, manualChunkSizeBytes = 32 * 1024 * 1024L))

        val existingMeta = DualDownloaderEngine.MetaInfo(
            url = "https://example.com/resume.bin",
            totalLength = 500 * 1024 * 1024L,
            completedChunks = setOf(0, 1, 2),
            chunkSize = 4 * 1024 * 1024L // Previously downloaded with 4 MB chunks
        )

        // When resuming, existing metadata chunk size MUST be preserved
        val effectiveResumed = engine.resolveEffectiveChunkSize(
            contentLength = 500 * 1024 * 1024L,
            canResume = true,
            existingMeta = existingMeta
        )
        assertEquals(4 * 1024 * 1024L, effectiveResumed)

        // When starting fresh (canResume = false), engine uses configured chunk size (32 MB)
        val effectiveFresh = engine.resolveEffectiveChunkSize(
            contentLength = 500 * 1024 * 1024L,
            canResume = false,
            existingMeta = null
        )
        assertEquals(32 * 1024 * 1024L, effectiveFresh)
    }

    @Test
    fun testRepository_ExposesChunkConfigStateFlowAndUpdates() {
        val repo = DownloadRepository(
            context = dummyContext,
            engineOverride = engine
        )

        // Initial default
        assertTrue(repo.chunkConfig.value.isAutoAdaptive)
        assertEquals(ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES, repo.chunkConfig.value.manualChunkSizeBytes)

        // Update manual chunk size
        repo.updateManualChunkSize(16 * 1024 * 1024L)
        assertEquals(16 * 1024 * 1024L, repo.chunkConfig.value.manualChunkSizeBytes)
        assertEquals(16 * 1024 * 1024L, engine.getChunkConfig().manualChunkSizeBytes)

        // Toggle auto adaptive
        repo.toggleAutoAdaptive(false)
        assertFalse(repo.chunkConfig.value.isAutoAdaptive)
        assertFalse(engine.getChunkConfig().isAutoAdaptive)

        // Reset to default
        repo.resetChunkConfigToDefault()
        assertTrue(repo.chunkConfig.value.isAutoAdaptive)
        assertEquals(ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES, repo.chunkConfig.value.manualChunkSizeBytes)
        assertEquals(ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES, engine.getChunkConfig().manualChunkSizeBytes)
    }

    @Test
    fun testDownloadState_Downloading_IncludesChunkSize() {
        val state = DownloadState.Downloading(
            fileName = "movie.mp4",
            totalBytes = 1000L,
            downloadedBytes = 500L,
            wifiDownloadedBytes = 250L,
            cellularDownloadedBytes = 250L,
            wifiSpeedMBps = 10.0,
            cellularSpeedMBps = 10.0,
            totalSpeedMBps = 20.0,
            progressPercentage = 0.5f,
            supportsRanges = true,
            chunkSize = 8 * 1024 * 1024L
        )

        assertEquals(8 * 1024 * 1024L, state.chunkSize)
    }
}
