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
 * Queries specialized resolvers (Invidious API for YouTube, direct HTML scraper for Instagram)
 * and generic multi-instance Cobalt failover pools for other platforms.
 */
open class SocialMediaResolver(
    private val client: OkHttpClient = defaultClient(),
    private val instances: List<String> = DEFAULT_INSTANCES,
    private val youtubeInstances: List<String> = DEFAULT_YOUTUBE_INSTANCES
) {

    companion object {
        private const val TAG = "SocialMediaResolver"

        val DEFAULT_YOUTUBE_INSTANCES = listOf(
            "https://yt.chocolatemoo53.com",
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.tiekoetter.com"
        )

        val DEFAULT_INSTANCES = listOf(
            "https://api.cobalt.tools/",
            "https://cobalt-backend.canine.tools/",
            "https://nuko-c.meowing.de/",
            "https://cobalt-api.clxxped.lol/"
        )

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        private val YOUTUBE_ID_PATTERNS = listOf(
            Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})"),
            Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})"),
            Pattern.compile("/shorts/([a-zA-Z0-9_-]{11})"),
            Pattern.compile("/embed/([a-zA-Z0-9_-]{11})")
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

        fun extractYouTubeVideoId(url: String): String? {
            for (pattern in YOUTUBE_ID_PATTERNS) {
                val matcher = pattern.matcher(url)
                if (matcher.find()) {
                    return matcher.group(1)
                }
            }
            return null
        }
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

        // 1. YouTube direct high-reliability Invidious resolver
        if (platform == SocialPlatform.YOUTUBE && youtubeInstances.isNotEmpty()) {
            val ytResult = resolveYouTubeDirectly(cleanedUrl, quality)
            if (ytResult.isSuccess) {
                return@withContext ytResult
            }
            Log.w(TAG, "Direct YouTube resolver failed: ${ytResult.exceptionOrNull()?.message}")
        }

        // 2. Try generic resolver instances (Cobalt, etc.) with failover
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

        // 3. Direct on-device HTML fallback scraper for Instagram
        if (platform == SocialPlatform.INSTAGRAM) {
            val directScrapeResult = scrapeInstagramDirectly(cleanedUrl)
            if (directScrapeResult.isSuccess) {
                return@withContext directScrapeResult
            }
        }

        Result.failure(IOException("Failed to resolve media stream from available instances"))
    }

    /**
     * Resolves YouTube video directly via Invidious API instances.
     */
    suspend fun resolveYouTubeDirectly(
        cleanedUrl: String,
        quality: MediaQuality
    ): Result<SocialMediaAsset> = withContext(Dispatchers.IO) {
        val videoId = extractYouTubeVideoId(cleanedUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("Could not extract YouTube video ID from $cleanedUrl"))

        // Try configured candidate instances
        for (instanceUrl in youtubeInstances) {
            try {
                val asset = queryInvidiousInstance(instanceUrl, videoId, cleanedUrl, quality)
                if (asset != null) {
                    return@withContext Result.success(asset)
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube instance $instanceUrl failed for video $videoId: ${e.message}")
            }
        }

        // Optional dynamic fallback to active public Invidious directory if using defaults
        if (youtubeInstances == DEFAULT_YOUTUBE_INSTANCES) {
            val dynamicInstances = fetchHealthyYouTubeInstances()
            for (instanceUrl in dynamicInstances) {
                if (youtubeInstances.contains(instanceUrl)) continue
                try {
                    val asset = queryInvidiousInstance(instanceUrl, videoId, cleanedUrl, quality)
                    if (asset != null) {
                        return@withContext Result.success(asset)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Dynamic YouTube instance $instanceUrl failed for video $videoId: ${e.message}")
                }
            }
        }

        Result.failure(IOException("Failed to resolve YouTube media stream from available instances"))
    }

    private fun queryInvidiousInstance(
        instanceUrl: String,
        videoId: String,
        originalUrl: String,
        quality: MediaQuality
    ): SocialMediaAsset? {
        val baseUrl = if (instanceUrl.endsWith("/")) instanceUrl.dropLast(1) else instanceUrl
        val requestUrl = "$baseUrl/api/v1/videos/$videoId"

        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Invidious instance $instanceUrl returned HTTP ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            return parseInvidiousResponse(body, videoId, originalUrl, quality)
        }
    }

    data class InvidiousFormat(
        val url: String,
        val type: String,
        val qualityLabel: String,
        val bitrate: Long,
        val container: String
    )

    internal fun parseInvidiousResponse(
        json: String,
        videoId: String,
        originalUrl: String,
        quality: MediaQuality
    ): SocialMediaAsset? {
        // Extract title
        var title = "youtube_$videoId"
        val titleMatch = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"").matcher(json)
        if (titleMatch.find()) {
            titleMatch.group(1)?.let { title = unescapeJson(it) }
        }

        // Extract duration
        var durationSeconds: Long? = null
        val durationMatch = Pattern.compile("\"lengthSeconds\"\\s*:\\s*(\\d+)").matcher(json)
        if (durationMatch.find()) {
            durationMatch.group(1)?.toLongOrNull()?.let { durationSeconds = it }
        }

        // Extract thumbnail
        var thumbnailUrl: String? = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val thumbMatch = Pattern.compile("\"videoThumbnails\"\\s*:\\s*\\[[^\\]]*\"url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"").matcher(json)
        if (thumbMatch.find()) {
            thumbMatch.group(1)?.let { thumbnailUrl = unescapeJson(it) }
        }

        // Parse format blocks
        val formats = parseInvidiousFormats(json)
        if (formats.isEmpty()) return null

        val isAudioOnly = quality == MediaQuality.AUDIO_ONLY
        val safeTitle = title.replace(Regex("[\\\\/*?:\"<>|]"), "_").trim()

        val chosenStream = if (isAudioOnly) {
            val audioCandidates = formats.filter { it.type.startsWith("audio/mp4") || it.type.contains("mp4a") }
                .ifEmpty { formats.filter { it.type.startsWith("audio/") } }
            audioCandidates.maxByOrNull { it.bitrate } ?: formats.firstOrNull { it.type.startsWith("audio/") }
        } else if (quality == MediaQuality.HD_720) {
            formats.firstOrNull { it.qualityLabel == "720p" && it.type.contains("mp4") }
                ?: formats.firstOrNull { it.qualityLabel == "720p" }
                ?: formats.filter { it.type.startsWith("video/") && it.type.contains("mp4") }
                    .maxByOrNull { parseResolutionHeight(it.qualityLabel) }
                ?: formats.firstOrNull { it.type.startsWith("video/") }
        } else {
            // MAX quality
            formats.filter { it.type.startsWith("video/") && it.type.contains("mp4") }
                .maxByOrNull { parseResolutionHeight(it.qualityLabel) }
                ?: formats.filter { it.type.startsWith("video/") }
                    .maxByOrNull { parseResolutionHeight(it.qualityLabel) }
        }

        if (chosenStream == null || chosenStream.url.isBlank()) return null

        val streamUrl = chosenStream.url
        val ext = if (isAudioOnly) {
            if (chosenStream.type.contains("mp4") || chosenStream.container == "m4a") "m4a" else "webm"
        } else {
            if (chosenStream.container.isNotBlank()) chosenStream.container else "mp4"
        }

        val qualitySuffix = if (!isAudioOnly && chosenStream.qualityLabel.isNotBlank()) {
            "_${chosenStream.qualityLabel}"
        } else ""

        val suggestedFilename = "${safeTitle}${qualitySuffix}.${ext}"

        return SocialMediaAsset(
            originalUrl = originalUrl,
            platform = SocialPlatform.YOUTUBE,
            title = title,
            streamUrl = streamUrl,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            isAudioOnly = isAudioOnly,
            suggestedFilename = suggestedFilename
        )
    }

    private fun extractArrayContent(json: String, key: String): String? {
        val idx = json.indexOf("\"$key\"")
        if (idx == -1) return null

        val bracketStart = json.indexOf('[', idx)
        if (bracketStart == -1) return null

        var depth = 0
        var bracketEnd = -1
        for (i in bracketStart until json.length) {
            val c = json[i]
            if (c == '[') {
                depth++
            } else if (c == ']') {
                depth--
                if (depth == 0) {
                    bracketEnd = i
                    break
                }
            }
        }

        return if (bracketEnd != -1) {
            json.substring(bracketStart + 1, bracketEnd)
        } else null
    }

    internal fun parseInvidiousFormats(json: String): List<InvidiousFormat> {
        val result = mutableListOf<InvidiousFormat>()
        val arrayKeys = listOf("adaptiveFormats", "formatStreams")

        for (key in arrayKeys) {
            val arrayContent = extractArrayContent(json, key) ?: continue

            var depth = 0
            var objStart = -1
            for (i in arrayContent.indices) {
                val c = arrayContent[i]
                if (c == '{') {
                    if (depth == 0) objStart = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && objStart != -1) {
                        val obj = arrayContent.substring(objStart + 1, i)
                        objStart = -1

                        var url: String? = null
                        val urlM = Pattern.compile("\"url\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"").matcher(obj)
                        if (urlM.find()) {
                            url = unescapeJson(urlM.group(1) ?: "")
                        }

                        if (!url.isNullOrBlank()) {
                            var type = ""
                            val typeM = Pattern.compile("\"type\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"").matcher(obj)
                            if (typeM.find()) {
                                type = unescapeJson(typeM.group(1) ?: "")
                            }

                            var qualityLabel = ""
                            val qlM = Pattern.compile("\"qualityLabel\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"").matcher(obj)
                            if (qlM.find()) {
                                qualityLabel = unescapeJson(qlM.group(1) ?: "")
                            }

                            var bitrate: Long = 0
                            val brM = Pattern.compile("\"bitrate\"\\s*:\\s*\"?(\\d+)\"?").matcher(obj)
                            if (brM.find()) {
                                bitrate = brM.group(1)?.toLongOrNull() ?: 0L
                            }

                            var container = ""
                            val ctM = Pattern.compile("\"container\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])+)\"").matcher(obj)
                            if (ctM.find()) {
                                container = unescapeJson(ctM.group(1) ?: "")
                            }

                            result.add(InvidiousFormat(url, type, qualityLabel, bitrate, container))
                        }
                    }
                }
            }
        }
        return result
    }

    private fun parseResolutionHeight(qualityLabel: String): Int {
        val m = Pattern.compile("(\\d+)p").matcher(qualityLabel)
        return if (m.find()) m.group(1)?.toIntOrNull() ?: 0 else 0
    }

    private fun fetchHealthyYouTubeInstances(): List<String> {
        val req = Request.Builder()
            .url("https://api.invidious.io/instances.json?sort_by=health")
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .get()
            .build()

        return try {
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseInvidiousDirectory(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    internal fun parseInvidiousDirectory(json: String): List<String> {
        val instances = mutableListOf<String>()
        var cur = 0
        while (cur < json.length) {
            val idx = json.indexOf("\"uri\":", cur)
            if (idx == -1) break
            val quoteStart = json.indexOf('"', idx + 6)
            if (quoteStart == -1) break
            val quoteEnd = json.indexOf('"', quoteStart + 1)
            if (quoteEnd == -1) break
            val uri = json.substring(quoteStart + 1, quoteEnd)
            if (uri.startsWith("https://")) {
                instances.add(unescapeJson(uri))
            }
            cur = quoteEnd + 1
        }
        return instances
    }

    private fun queryResolverInstance(
        instanceUrl: String,
        targetUrl: String,
        platform: SocialPlatform,
        quality: MediaQuality
    ): SocialMediaAsset? {
        val baseUrl = if (instanceUrl.endsWith("/")) instanceUrl else "$instanceUrl/"
        val requestUrl = baseUrl // Cobalt accepts POST /

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
        if (json.contains("\"status\":\"error\"") || json.contains("\"status\": \"error\"")) {
            return null
        }

        var streamUrl: String? = null
        val urlMatch = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
        if (urlMatch.find()) {
            streamUrl = urlMatch.group(1)?.let { unescapeJson(it) }
        }

        if (streamUrl == null) {
            val pickerMatch = Pattern.compile("\"picker\"\\s*:\\s*\\[\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json)
            if (pickerMatch.find()) {
                streamUrl = pickerMatch.group(1)?.let { unescapeJson(it) }
            }
        }

        if (streamUrl.isNullOrBlank()) return null

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
            .addHeader("User-Agent", BROWSER_USER_AGENT)
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
