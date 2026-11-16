package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.UserAgentConfig
import com.dualboost.downloader.data.UserAgentPreset
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EngineDynamicUserAgentTest {

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
        baseDir = tempFolder.newFolder("engine_ua_test")
        dummyContext = DummyTestContext(baseDir)
        mockNetwork = MockNetworkProvider()
        engine = DualDownloaderEngine(dummyContext, networkManager = mockNetwork)
    }

    @Test
    fun testEngine_DefaultsToNativeUserAgentConfig() {
        val config = engine.getUserAgentConfig()
        assertEquals(UserAgentPreset.NATIVE, config.preset)
        assertEquals("", config.customUserAgentString)
    }

    @Test
    fun testEngine_AppliesConfiguredUserAgentHeaderToRequest() {
        val customUa = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"
        engine.setUserAgentConfig(UserAgentConfig(UserAgentPreset.CUSTOM, customUa))

        val builder = Request.Builder().url("https://example.com/test.bin")
        engine.applyConfiguredUserAgentHeaders(builder, isDesktop = false)
        val request = builder.build()

        assertEquals(customUa, request.header("User-Agent"))
    }

    @Test
    fun testEngine_DesktopPreset_FastTracksToDesktopModeDirectly() {
        val desktopConfig = UserAgentConfig(UserAgentPreset.DESKTOP_CHROME)
        engine.setUserAgentConfig(desktopConfig)

        val builder = Request.Builder().url("https://example.com/test.bin")
        engine.applyConfiguredUserAgentHeaders(builder, isDesktop = true)
        val request = builder.build()

        assertTrue(request.header("User-Agent")!!.contains("Windows NT 10.0; Win64; x64"))
        assertEquals("?0", request.header("Sec-CH-UA-Mobile"))
        assertEquals("\"Windows\"", request.header("Sec-CH-UA-Platform"))
    }

    @Test
    fun testEngine_SaveAndReadMetadata_PreservesUserAgentOnResume() {
        val metaFile = File(baseDir, "resume_test.bin.dualboost_meta")
        val savedUa = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36"

        engine.saveCompletedChunks(
            metaFile = metaFile,
            url = "https://example.com/resume.bin",
            totalBytes = 50 * 1024 * 1024L,
            completedIndices = setOf(0, 1),
            userAgent = savedUa
        )

        val metaInfo = engine.readMetaInfo(metaFile)
        assertNotNull(metaInfo)
        assertEquals(savedUa, metaInfo!!.userAgent)

        // When resuming, resolveEffectiveUserAgent MUST preserve the saved User-Agent
        // even if engine's global config has since changed to something else
        engine.setUserAgentConfig(UserAgentConfig(UserAgentPreset.PIXEL_9))
        val effectiveUa = engine.resolveEffectiveUserAgent(canResume = true, existingMeta = metaInfo)
        assertEquals(savedUa, effectiveUa)
    }

    @Test
    fun testRepository_ExposesUserAgentConfigStateFlowAndUpdates() {
        val repo = DownloadRepository(
            context = dummyContext,
            engineOverride = engine
        )

        assertEquals(UserAgentPreset.NATIVE, repo.userAgentConfig.value.preset)

        repo.updateUserAgentPreset(UserAgentPreset.GALAXY_S24)
        assertEquals(UserAgentPreset.GALAXY_S24, repo.userAgentConfig.value.preset)
        assertEquals(UserAgentPreset.GALAXY_S24, engine.getUserAgentConfig().preset)

        val customString = "CustomScraper/1.0"
        repo.setCustomUserAgent(customString)
        assertEquals(UserAgentPreset.CUSTOM, repo.userAgentConfig.value.preset)
        assertEquals(customString, repo.userAgentConfig.value.customUserAgentString)
        assertEquals(customString, engine.getUserAgentConfig().customUserAgentString)

        repo.resetUserAgentToDefault()
        assertEquals(UserAgentPreset.NATIVE, repo.userAgentConfig.value.preset)
        assertEquals("", repo.userAgentConfig.value.customUserAgentString)
        assertEquals(UserAgentPreset.NATIVE, engine.getUserAgentConfig().preset)
    }

    @Test
    fun testDownloadState_Downloading_ExposesCustomUserAgentFlag() {
        val state = DownloadState.Downloading(
            fileName = "data.iso",
            totalBytes = 2000L,
            downloadedBytes = 1000L,
            wifiDownloadedBytes = 500L,
            cellularDownloadedBytes = 500L,
            wifiSpeedMBps = 10.0,
            cellularSpeedMBps = 10.0,
            totalSpeedMBps = 20.0,
            progressPercentage = 0.5f,
            supportsRanges = true,
            isCustomUserAgent = true
        )

        assertTrue(state.isCustomUserAgent)
    }
}
