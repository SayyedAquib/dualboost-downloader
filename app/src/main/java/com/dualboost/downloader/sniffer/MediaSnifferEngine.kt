package com.dualboost.downloader.sniffer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * High-performance network request analyzer for the In-App Sniffer Browser.
 * Inspects outgoing and incoming WebView network traffic, blocks adware tracking,
 * and intercepts media streams (.mp4, .mkv, .m3u8, etc.) for 1-tap downloading.
 */
class MediaSnifferEngine {

    private val _detectedMedia = MutableStateFlow<List<DetectedMediaItem>>(emptyList())
    val detectedMedia: StateFlow<List<DetectedMediaItem>> = _detectedMedia.asStateFlow()

    fun inspectRequest(
        url: String,
        headers: Map<String, String> = emptyMap(),
        mimeType: String? = null
    ): DetectedMediaItem? {
        val trimmedUrl = url.trim()
        if (isAdwareOrTracking(trimmedUrl)) return null
        if (isStaticAsset(trimmedUrl)) return null

        val ext = extractExtension(trimmedUrl, headers, mimeType) ?: return null
        val fileName = extractFileName(trimmedUrl, headers, ext)

        // Deduplication: ensure each unique video stream is registered only once
        val current = _detectedMedia.value
        if (current.any { it.url == trimmedUrl || (it.fileName == fileName && it.extension == ext) }) {
            return null
        }

        val contentLength = headers["Content-Length"]?.toLongOrNull()
            ?: headers["content-length"]?.toLongOrNull()

        val item = DetectedMediaItem(
            url = trimmedUrl,
            fileName = fileName,
            extension = ext,
            mimeType = mimeType,
            contentLength = contentLength
        )

        _detectedMedia.value = current + item
        return item
    }

    fun clear() {
        _detectedMedia.value = emptyList()
    }

    private fun isAdwareOrTracking(url: String): Boolean {
        val lower = url.lowercase()
        return EXCLUDED_AD_KEYWORDS.any { lower.contains(it) }
    }

    private fun isStaticAsset(url: String): Boolean {
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase()
        val ext = cleanPath.substringAfterLast('.', "")
        return STATIC_ASSET_EXTENSIONS.contains(ext)
    }

    private fun extractExtension(url: String, headers: Map<String, String>, mimeType: String?): String? {
        // 1. Check URL path extension
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase()
        val pathExt = cleanPath.substringAfterLast('.', "")
        if (pathExt in SUPPORTED_EXTENSIONS) {
            return pathExt
        }

        // 2. Check Content-Disposition header
        val disposition = headers["Content-Disposition"] ?: headers["content-disposition"]
        if (disposition != null) {
            val fileNameMatch = FILENAME_REGEX.find(disposition)
            if (fileNameMatch != null) {
                val name = fileNameMatch.groupValues[1]
                val dispExt = name.substringAfterLast('.', "").lowercase()
                if (dispExt in SUPPORTED_EXTENSIONS) {
                    return dispExt
                }
            }
        }

        // 3. Check Content-Type / MIME type
        val effectiveMime = mimeType ?: headers["Content-Type"] ?: headers["content-type"]
        if (effectiveMime != null) {
            val lowerMime = effectiveMime.lowercase()
            return when {
                lowerMime.contains("video/mp4") -> "mp4"
                lowerMime.contains("video/webm") -> "webm"
                lowerMime.contains("video/x-matroska") || lowerMime.contains("video/mkv") -> "mkv"
                lowerMime.contains("application/x-mpegurl") || lowerMime.contains("application/vnd.apple.mpegurl") -> "m3u8"
                lowerMime.startsWith("video/") -> "mp4"
                lowerMime.startsWith("audio/") -> "mp3"
                else -> null
            }
        }

        return null
    }

    private fun extractFileName(url: String, headers: Map<String, String>, extension: String): String {
        // 1. Check Content-Disposition header
        val disposition = headers["Content-Disposition"] ?: headers["content-disposition"]
        if (disposition != null) {
            val match = FILENAME_REGEX.find(disposition)
            if (match != null) {
                val rawName = match.groupValues[1].trim()
                if (rawName.isNotBlank()) {
                    return sanitizeTitle(rawName)
                }
            }
        }

        // 2. Extract from URL path
        val cleanPath = url.substringBefore('?').substringBefore('#')
        val rawBasename = cleanPath.substringAfterLast('/')
        if (rawBasename.isNotBlank()) {
            val decoded = decodeUrl(rawBasename)
            if (decoded.contains('.')) {
                return decoded
            }
            return "$decoded.$extension"
        }

        return "media_${System.currentTimeMillis()}.$extension"
    }

    private fun decodeUrl(s: String): String {
        return try {
            URLDecoder.decode(s, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            s
        }
    }

    private fun sanitizeTitle(name: String): String {
        return decodeUrl(name.replace("\"", "").replace("'", ""))
    }

    companion object {
        val SUPPORTED_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "mov", "avi", "ts", "m4v", "flv", "3gp", "wmv", "m3u8",
            "mp3", "aac", "wav", "m4a", "flac"
        )

        val STATIC_ASSET_EXTENSIONS = setOf(
            "css", "js", "jpg", "jpeg", "png", "gif", "svg", "ico", "woff", "woff2",
            "ttf", "otf", "json", "html", "htm", "xml", "map"
        )

        val EXCLUDED_AD_KEYWORDS = setOf(
            "googleads", "doubleclick", "adservice", "popads", "adsterra",
            "propellerads", "1xbet", "betting", "bet365", "adclick", "banner",
            "facebook.com/tr", "google-analytics", "telemetry"
        )

        private val FILENAME_REGEX = """filename[*]?=(?:UTF-8'')?["']?([^"';]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
    }
}
