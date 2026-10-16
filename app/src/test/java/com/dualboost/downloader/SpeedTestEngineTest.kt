package com.dualboost.downloader

import app.cash.turbine.test
import com.dualboost.downloader.data.SpeedTestPhase
import com.dualboost.downloader.data.SpeedTestState
import com.dualboost.downloader.engine.SpeedTestEngine
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SpeedTestEngineTest {

    private lateinit var mockWebServer: MockWebServer
    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    class MockNetworkProvider : NetworkProvider {
        val wifiFlow = MutableStateFlow(false)
        val cellFlow = MutableStateFlow(false)

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        override val isWifiAvailable: StateFlow<Boolean> = wifiFlow
        override val isCellularAvailable: StateFlow<Boolean> = cellFlow

        override fun getWifiClient(): OkHttpClient? = if (wifiFlow.value) client else null
        override fun getCellularClient(): OkHttpClient? = if (cellFlow.value) client else null
        override fun getDefaultClient(): OkHttpClient = client
    }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testSpeedTest_InitialStateIsIdle() {
        val provider = MockNetworkProvider()
        val engine = SpeedTestEngine(provider, mockWebServer.url("/test").toString(), testScope)
        assertTrue("Initial speed test state must be Idle", engine.speedTestState.value is SpeedTestState.Idle)
    }

    @Test
    fun testSpeedTest_FailsWhenNoNetworkAvailable() = runBlocking {
        val provider = MockNetworkProvider()
        provider.wifiFlow.value = false
        provider.cellFlow.value = false

        val engine = SpeedTestEngine(provider, mockWebServer.url("/test").toString(), testScope)
        engine.startSpeedTest()

        val state = engine.speedTestState.value
        assertTrue("Must transition to Error state when both networks are unavailable", state is SpeedTestState.Error)
        val error = state as SpeedTestState.Error
        assertTrue("Error message must mention no active network", error.message.contains("No active network"))
    }

    @Test
    fun testSpeedTest_StopResetsStateToIdle() = runBlocking {
        val provider = MockNetworkProvider()
        provider.wifiFlow.value = true

        val engine = SpeedTestEngine(provider, mockWebServer.url("/test").toString(), testScope)
        engine.startSpeedTest()
        engine.stopSpeedTest()

        assertTrue("Calling stopSpeedTest must immediately return to Idle", engine.speedTestState.value is SpeedTestState.Idle)
    }

    @Test
    fun testSpeedTestModels_DataClassIntegrity() {
        val running = SpeedTestState.Running(
            phase = SpeedTestPhase.TESTING_WIFI,
            currentSpeedMbps = 45.2f,
            overallProgress = 0.25f,
            wifiSpeedMbps = 45.2f,
            cellularSpeedMbps = 0f
        )
        assertEquals(SpeedTestPhase.TESTING_WIFI, running.phase)
        assertEquals(45.2f, running.currentSpeedMbps, 0.01f)
        assertEquals(0.25f, running.overallProgress, 0.01f)
        assertEquals(45.2f, running.wifiSpeedMbps, 0.01f)
        assertEquals(0f, running.cellularSpeedMbps, 0.01f)

        val completed = SpeedTestState.Completed(
            wifiSpeedMbps = 50.0f,
            cellularSpeedMbps = 60.0f,
            dualBoostSpeedMbps = 100.0f,
            boostGainPercent = 66
        )
        assertEquals(50.0f, completed.wifiSpeedMbps, 0.01f)
        assertEquals(60.0f, completed.cellularSpeedMbps, 0.01f)
        assertEquals(100.0f, completed.dualBoostSpeedMbps, 0.01f)
        assertEquals(66, completed.boostGainPercent)

        val error = SpeedTestState.Error("Network timeout")
        assertEquals("Network timeout", error.message)
    }

    @Test
    fun testSpeedTestPhaseEnum_ValuesExist() {
        val phases = SpeedTestPhase.values()
        assertTrue(phases.contains(SpeedTestPhase.IDLE))
        assertTrue(phases.contains(SpeedTestPhase.TESTING_WIFI))
        assertTrue(phases.contains(SpeedTestPhase.TESTING_CELLULAR))
        assertTrue(phases.contains(SpeedTestPhase.TESTING_DUALBOOST))
        assertTrue(phases.contains(SpeedTestPhase.COMPLETED))
        assertTrue(phases.contains(SpeedTestPhase.CANCELLED))
        assertTrue(phases.contains(SpeedTestPhase.FAILED))
    }

    @Test
    fun testTurbine_SpeedTestState_EmitsErrorWhenOffline() = runBlocking {
        val provider = MockNetworkProvider()
        provider.wifiFlow.value = false
        provider.cellFlow.value = false

        val engine = SpeedTestEngine(provider, mockWebServer.url("/test").toString(), testScope)
        engine.speedTestState.test {
            assertEquals(SpeedTestState.Idle, awaitItem()) // 1. Initial State
            engine.startSpeedTest()
            val nextState = awaitItem() // 2. Error State
            assertTrue("Turbine must observe transition to Error state", nextState is SpeedTestState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
