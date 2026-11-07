package com.dualboost.downloader.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Cloud threat intelligence client. Queries Google Safe Browsing / Web Risk
 * or threat intelligence endpoints with graceful zero-latency fallback.
 */
class CloudThreatScanner(
    private val okHttpClient: OkHttpClient,
    private val apiKeyProvider: () -> String?,
    private val apiEndpointOverride: String? = null
) {
    companion object {
        private const val DEFAULT_SAFE_BROWSING_ENDPOINT =
            "https://safebrowsing.googleapis.com/v4/threatMatches:find"
    }

    suspend fun checkUrlReputation(url: String): ThreatAssessment = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()?.trim()
        if (apiKey.isNullOrEmpty()) {
            return@withContext ThreatAssessment.clean()
        }

        try {
            val endpoint = apiEndpointOverride ?: "$DEFAULT_SAFE_BROWSING_ENDPOINT?key=$apiKey"
            val requestBodyJson = buildRequestBody(url)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ThreatAssessment.clean()
                }

                val bodyStr = response.body?.string() ?: return@withContext ThreatAssessment.clean()
                return@withContext parseThreatResponse(bodyStr, url)
            }
        } catch (_: Exception) {
            // Graceful fallback: Never block downloads due to network timeouts or cloud downtime
            ThreatAssessment.clean()
        }
    }

    private fun parseThreatResponse(bodyStr: String, targetUrl: String): ThreatAssessment {
        if (bodyStr.contains("\"matches\"") && bodyStr.contains("\"threatType\"")) {
            val threatType = Regex(""""threatType"\s*:\s*"([^"]+)"""").find(bodyStr)?.groupValues?.getOrNull(1) ?: "MALWARE"
            val platform = Regex(""""platformType"\s*:\s*"([^"]+)"""").find(bodyStr)?.groupValues?.getOrNull(1) ?: "ANY_PLATFORM"
            return ThreatAssessment(
                isThreat = true,
                threatType = ThreatType.CLOUD_FLAGGED_MALWARE,
                severity = ThreatSeverity.CRITICAL,
                description = "URL flagged by Cloud Threat Intelligence ($threatType on $platform).",
                originalTarget = targetUrl,
                disguisedTarget = null
            )
        }
        return ThreatAssessment.clean()
    }

    private fun buildRequestBody(targetUrl: String): String {
        return """
            {
              "client": {
                "clientId": "dualboost-downloader",
                "clientVersion": "1.0.0"
              },
              "threatInfo": {
                "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"],
                "platformTypes": ["ANDROID", "ANY_PLATFORM"],
                "threatEntryTypes": ["URL"],
                "threatEntries": [
                  {"url": "$targetUrl"}
                ]
              }
            }
        """.trimIndent()
    }
}
