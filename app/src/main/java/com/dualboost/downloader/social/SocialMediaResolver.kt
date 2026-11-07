package com.dualboost.downloader.social

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * High-performance, zero-bloat resolver for social media links.
 * Queries open-source Cobalt/yt-stream API instances with multi-instance
 * failover and direct on-device HTML scraping for Instagram.
 */
open class SocialMediaResolver(
    private val client: OkHttpClient = defaultClient(),
    private val instances: List<String> = DEFAULT_INSTANCES
) {

    companion object {
        private const val TAG = "SocialMediaResolver"

        val DEFAULT_INSTANCES = listOf(
            "https://api.cobalt.tools/",
            "https://cobalt.api.kwiatekm.tokyo/",
            "https://cobalt-backend.canine.tools/",
            "https://api.wuk.sh/"
        )

        private fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        private val OG_VIDEO_PATTERN_1 = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:video[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        private val OG_VIDEO_PATTERN_2 = Pattern.compile("<meta\\s+content=[\"']([^\"']+)[\"']\\s+(?:property|name)=[\"']og:video[\"']", Pattern.CASE_INSENSITIVE)
        private val OG_TITLE_PATTERN = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        private val OG_IMAGE_PATTERN = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:image[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    }

    /**
     * Resolves a social media share URL into a direct CDN media asset.
     */
    open suspend fun resolve(
        url: String,
        quality: MediaQuality = MediaQuality.MAX
    ): Result<SocialMediaAsset> = withContext(Dispatchers.IO) {
        val platform = SocialMediaDetector.detectPlatform(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported or non-social media URL: $url"))

        val cleanedUrl = SocialMediaDetector.cleanUrl(url)

        // 1. Try public resolver instances with failover
        for (instanceUrl in instances) {
            try {
                val asset = queryResolverInstance(instanceUrl, cleanedUrl, platform, quality)
                if (asset != null) {
                    return@withContext Result.success(asset)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Resolver instance $instanceUrl failed: ${e.message}")
            }
        }

        // 2. Direct on-device HTML fallback scraper for Instagram
        if (platform == SocialPlatform.INSTAGRAM) {
            val directScrapeResult = scrapeInstagramDirectly(cleanedUrl)
            if (directScrapeResult.isSuccess) {
                return@withContext directScrapeResult
            }
        }

        Result.failure(IOException("Failed to resolve media stream from available instances"))
    }

    private fun queryResolverInstance(
        instanceUrl: String,
        targetUrl: String,
        platform: SocialPlatform,
        quality: MediaQuality
    ): SocialMediaAsset? {
        val baseUrl = if (instanceUrl.endsWith("/")) instanceUrl else "$instanceUrl/"
        val requestUrl = baseUrl // Cobalt v10 accepts POST / directly

        val isAudioOnly = quality == MediaQuality.AUDIO_ONLY
        val downloadMode = if (isAudioOnly) "audio" else "auto"

        val jsonPayload = """
            {
              "url": "${escapeJson(targetUrl)}",
              "videoQuality": "${quality.apiValue}",
              "audioFormat": "mp3",
              "downloadMode": "$downloadMode"
            }
        """.trimIndent()

        val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "DualBoost-Downloader/1.0 (Android)")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Instance returned HTTP ${response.code}")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            return parseCobaltResponse(responseBody, targetUrl, platform, isAudioOnly)
        }
    }

    /**
     * Pure Kotlin JSON parser to maintain 100% JVM unit test compatibility
     * without Android JSONObject stubs.
     */
    internal fun parseCobaltResponse(
        json: String,
        originalUrl: String,
        platform: SocialPlatform,
        isAudioOnly: Boolean
    ): SocialMediaAsset? {
        // Check for error status
        if (json.contains("\"status\":\"error\"") || json.contains("\"status\": \"error\"")) {
            return null
        }

        // Extract direct stream URL
        var streamUrl: String? = null
        val urlMatch = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
        if (urlMatch.find()) {
            streamUrl = urlMatch.group(1)?.let { unescapeJson(it) }
        }

        if (streamUrl == null) {
            // Check picker array
            val pickerMatch = Pattern.compile("\"picker\"\\s*:\\s*\\[\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
            if (pickerMatch.find()) {
                streamUrl = pickerMatch.group(1)?.let { unescapeJson(it) }
            }
        }

        if (streamUrl.isNullOrBlank()) return null

        // Extract filename
        var filename: String? = null
        val filenameMatch = Pattern.compile("\"filename\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
        if (filenameMatch.find()) {
            filename = filenameMatch.group(1)?.let { unescapeJson(it) }
        }

        val ext = if (isAudioOnly) "mp3" else "mp4"
        val fallbackName = "${platform.name.lowercase()}_${System.currentTimeMillis()}.$ext"
        val suggestedFilename = filename?.takeIf { it.isNotBlank() } ?: fallbackName
        val title = suggestedFilename.substringBeforeLast('.')

        return SocialMediaAsset(
            originalUrl = originalUrl,
            platform = platform,
            title = title,
            streamUrl = streamUrl,
            isAudioOnly = isAudioOnly,
            suggestedFilename = suggestedFilename
        )
    }

    /**
     * Scrapes public Instagram HTML for og:video / og:title tags directly on-device.
     */
    fun scrapeInstagramDirectly(url: String): Result<SocialMediaAsset> {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("Instagram page returned HTTP ${response.code}"))
                }

                val html = response.body?.string() ?: return Result.failure(IOException("Empty HTML body"))

                var videoUrl: String? = null
                val m1 = OG_VIDEO_PATTERN_1.matcher(html)
                if (m1.find()) {
                    videoUrl = m1.group(1)
                } else {
                    val m2 = OG_VIDEO_PATTERN_2.matcher(html)
                    if (m2.find()) {
                        videoUrl = m2.group(1)
                    }
                }

                if (videoUrl.isNullOrBlank()) {
                    return Result.failure(IOException("Could not locate og:video in Instagram HTML"))
                }

                val cleanStreamUrl = unescapeHtml(videoUrl)

                var title = "Instagram Reel"
                val titleMatcher = OG_TITLE_PATTERN.matcher(html)
                if (titleMatcher.find()) {
                    titleMatcher.group(1)?.let { title = unescapeHtml(it) }
                }

                val filename = "instagram_reel_${System.currentTimeMillis()}.mp4"

                Result.success(
                    SocialMediaAsset(
                        originalUrl = url,
                        platform = SocialPlatform.INSTAGRAM,
                        title = title,
                        streamUrl = cleanStreamUrl,
                        isAudioOnly = false,
                        suggestedFilename = filename
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescapeJson(s: String): String {
        return s.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun unescapeHtml(s: String): String {
        return s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
}
