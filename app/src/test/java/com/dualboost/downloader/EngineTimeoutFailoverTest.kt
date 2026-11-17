package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.TimeoutConfig
import com.dualboost.downloader.data.TimeoutPreferences
import com.dualboost.downloader.data.TimeoutProfile
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.DualNetworkManager
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineTimeoutFailoverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: DummyTestContext
    private lateinit var baseDir: File
    private lateinit var engine: DualDownloaderEngine
    private lateinit var mockNetwork: MockNetworkProvider

    class MockNetworkProvider : NetworkProvider {
        private var client = OkHttpClient.Builder().build()
        override val isWifiAvailable = MutableStateFlow(true)
        override val isCellularAvailable = MutableStateFlow(true)
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client

        var appliedConfig: TimeoutConfig = TimeoutConfig.DEFAULT

        override fun applyTimeoutConfig(config: TimeoutConfig) {
            appliedConfig = config
            client = client.newBuilder()
                .connectTimeout(config.effectiveConnectTimeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(config.effectiveReadTimeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
    }

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("engine_timeout_test")
        dummyContext = DummyTestContext(baseDir)
        mockNetwork = MockNetworkProvider()
        engine = DualDownloaderEngine(dummyContext, networkManager = mockNetwork)
    }

    @Test
    fun testEngine_DefaultsToBalancedTimeoutConfig() {
        val config = engine.getTimeoutConfig()
        assertEquals(TimeoutProfile.BALANCED, config.profile)
        assertEquals(15, config.effectiveConnectTimeoutSeconds)
        assertEquals(30, config.effectiveReadTimeoutSeconds)
        assertEquals(5, config.effectiveMaxChunkRetries)
        assertEquals(300L, config.effectiveRetryBackoffMs)
    }

    @Test
    fun testEngine_SetAndGetTimeoutConfig() {
        val aggressive = TimeoutConfig(profile = TimeoutProfile.AGGRESSIVE)
        engine.setTimeoutConfig(aggressive)
        assertEquals(TimeoutProfile.AGGRESSIVE, engine.getTimeoutConfig().profile)
        assertEquals(5, engine.getTimeoutConfig().effectiveConnectTimeoutSeconds)
        assertEquals(10, engine.getTimeoutConfig().effectiveReadTimeoutSeconds)
        assertEquals(3, engine.getTimeoutConfig().effectiveMaxChunkRetries)
        assertEquals(150L, engine.getTimeoutConfig().effectiveRetryBackoffMs)
    }

    @Test
    fun testNetworkManager_ApplyTimeoutConfig_ReconfiguresClients() {
        val manager = DualNetworkManager(dummyContext)
        val aggressive = TimeoutConfig(profile = TimeoutProfile.AGGRESSIVE)

        manager.applyTimeoutConfig(aggressive)
        val defaultClient = manager.getDefaultClient()
        assertEquals(5000, defaultClient.connectTimeoutMillis)
        assertEquals(10000, defaultClient.readTimeoutMillis)

        val resilient = TimeoutConfig(profile = TimeoutProfile.RESILIENT)
        manager.applyTimeoutConfig(resilient)
        val updatedClient = manager.getDefaultClient()
        assertEquals(30000, updatedClient.connectTimeoutMillis)
        assertEquals(60000, updatedClient.readTimeoutMillis)
    }

    @Test
    fun testRepository_ExposesTimeoutConfigStateFlowAndUpdates() {
        val repo = DownloadRepository(
            context = dummyContext,
            networkProvider = mockNetwork,
            engineOverride = engine
        )

        assertEquals(TimeoutProfile.BALANCED, repo.timeoutConfig.value.profile)

        repo.updateTimeoutProfile(TimeoutProfile.AGGRESSIVE)
        assertEquals(TimeoutProfile.AGGRESSIVE, repo.timeoutConfig.value.profile)
        assertEquals(TimeoutProfile.AGGRESSIVE, engine.getTimeoutConfig().profile)
        assertEquals(TimeoutProfile.AGGRESSIVE, mockNetwork.appliedConfig.profile)

        repo.updateCustomTimeouts(
            connectSec = 7,
            readSec = 14,
            maxRetries = 2,
            retryBackoffMs = 120L
        )
        assertEquals(TimeoutProfile.CUSTOM, repo.timeoutConfig.value.profile)
        assertEquals(7, repo.timeoutConfig.value.effectiveConnectTimeoutSeconds)
        assertEquals(14, repo.timeoutConfig.value.effectiveReadTimeoutSeconds)
        assertEquals(2, repo.timeoutConfig.value.effectiveMaxChunkRetries)
        assertEquals(120L, repo.timeoutConfig.value.effectiveRetryBackoffMs)
        assertEquals(TimeoutProfile.CUSTOM, engine.getTimeoutConfig().profile)

        repo.resetTimeoutToDefault()
        assertEquals(TimeoutProfile.BALANCED, repo.timeoutConfig.value.profile)
        assertEquals(TimeoutProfile.BALANCED, engine.getTimeoutConfig().profile)
    }
}
