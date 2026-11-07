package com.dualboost.downloader

import com.dualboost.downloader.security.CloudThreatScanner
import com.dualboost.downloader.security.ThreatSeverity
import com.dualboost.downloader.security.ThreatType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CloudThreatScannerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        okHttpClient = OkHttpClient.Builder().build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun missingApiKey_returnsCleanWithoutNetworkCall() = runBlocking {
        val scanner = CloudThreatScanner(
            okHttpClient = okHttpClient,
            apiKeyProvider = { null },
            apiEndpointOverride = mockWebServer.url("/v4/threatMatches:find").toString()
        )

        val result = scanner.checkUrlReputation("https://any-domain.com/file.zip")

        assertFalse("Missing API key should gracefully return clean", result.isThreat)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun flaggedMaliciousUrl_returnsThreatAssessment() = runBlocking {
        // Mock Google Safe Browsing / Web Risk style threat match response
        val threatJsonResponse = """
            {
                "matches": [
                    {
                        "threatType": "MALWARE",
                        "platformType": "ANDROID",
                        "threatEntryType": "URL",
                        "threat": { "url": "https://malware-payload-server.com/malicious.apk" }
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(threatJsonResponse))

        val scanner = CloudThreatScanner(
            okHttpClient = okHttpClient,
            apiKeyProvider = { "valid-api-key-12345" },
            apiEndpointOverride = mockWebServer.url("/v4/threatMatches:find").toString()
        )

        val result = scanner.checkUrlReputation("https://malware-payload-server.com/malicious.apk")

        assertTrue("Flagged URL should be identified as threat", result.isThreat)
        assertEquals(ThreatType.CLOUD_FLAGGED_MALWARE, result.threatType)
        assertEquals(ThreatSeverity.CRITICAL, result.severity)
        assertTrue(result.description.contains("MALWARE", ignoreCase = true))
    }

    @Test
    fun cleanUrl_returnsCleanAssessment() = runBlocking {
        // Empty matches response from threat intelligence
        val cleanJsonResponse = "{}"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(cleanJsonResponse))

        val scanner = CloudThreatScanner(
            okHttpClient = okHttpClient,
            apiKeyProvider = { "valid-api-key-12345" },
            apiEndpointOverride = mockWebServer.url("/v4/threatMatches:find").toString()
        )

        val result = scanner.checkUrlReputation("https://archive.org/download/test/sample.mp4")

        assertFalse("Clean response should yield isThreat = false", result.isThreat)
    }

    @Test
    fun serverErrorOrTimeout_gracefullyFallsBackToClean() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val scanner = CloudThreatScanner(
            okHttpClient = okHttpClient,
            apiKeyProvider = { "valid-api-key-12345" },
            apiEndpointOverride = mockWebServer.url("/v4/threatMatches:find").toString()
        )

        val result = scanner.checkUrlReputation("https://unknown-server.com/file.mkv")

        assertFalse("Network or server failure must gracefully fall back to clean", result.isThreat)
    }
}
