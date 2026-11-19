package com.dualboost.downloader

import com.dualboost.downloader.data.CellularNetworkConfig
import com.dualboost.downloader.data.CellularNetworkMode
import com.dualboost.downloader.data.CellularPreferences
import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.network.CellularGeneration
import com.dualboost.downloader.network.DualNetworkManager
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Integration tests for Dynamic Cellular Network Configuration in Repository and Engine (ADR-048 Slice 3).
 */
class EngineCellularDynamicIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: DummyTestContext
    private lateinit var preferences: CellularPreferences
    private lateinit var mockNetworkProvider: MockNetworkProvider
    private lateinit var repository: DownloadRepository

    class MockNetworkProvider : NetworkProvider {
        private var client = OkHttpClient.Builder().build()
        override val isWifiAvailable = MutableStateFlow(true)
        override val isCellularAvailable = MutableStateFlow(true)
        override val cellularGeneration = MutableStateFlow(CellularGeneration.FOUR_G)
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client

        var appliedCellularConfig: CellularNetworkConfig = CellularNetworkConfig.DEFAULT
        var refreshCount = 0

        override fun applyCellularConfig(config: CellularNetworkConfig) {
            appliedCellularConfig = config
            cellularGeneration.value = config.effectiveGeneration
        }

        override fun refreshCellularGeneration() {
            refreshCount++
        }
    }

    @Before
    fun setUp() {
        dummyContext = DummyTestContext(tempFolder.newFolder("cellular_integration"))
        preferences = CellularPreferences(dummyContext)
        mockNetworkProvider = MockNetworkProvider()
        repository = DownloadRepository(
            context = dummyContext,
            networkProvider = mockNetworkProvider,
            cellularPreferencesOverride = preferences
        )
    }

    @Test
    fun testRepository_ExposesCellularNetworkConfigStateFlow() {
        val config = repository.cellularNetworkConfig.value
        assertEquals(CellularNetworkMode.AUTO, config.mode)
        assertEquals(CellularGeneration.FOUR_G, config.detectedGeneration)
    }

    @Test
    fun testRepository_UpdateCellularMode_PropagatesToNetworkManager() {
        repository.updateCellularMode(CellularNetworkMode.FORCE_5G)

        assertEquals(CellularNetworkMode.FORCE_5G, preferences.getCellularMode())
        assertEquals(CellularNetworkMode.FORCE_5G, repository.cellularNetworkConfig.value.mode)
        assertEquals(CellularNetworkMode.FORCE_5G, mockNetworkProvider.appliedCellularConfig.mode)
        assertEquals(CellularGeneration.FIVE_G, mockNetworkProvider.cellularGeneration.value)
    }

    @Test
    fun testRepository_ResetCellularMode_RestoresAuto() {
        repository.updateCellularMode(CellularNetworkMode.FORCE_5G)
        assertEquals(CellularNetworkMode.FORCE_5G, repository.cellularNetworkConfig.value.mode)

        repository.resetCellularModeToDefault()
        assertEquals(CellularNetworkMode.AUTO, preferences.getCellularMode())
        assertEquals(CellularNetworkMode.AUTO, repository.cellularNetworkConfig.value.mode)
        assertEquals(CellularNetworkMode.AUTO, mockNetworkProvider.appliedCellularConfig.mode)
    }
}
