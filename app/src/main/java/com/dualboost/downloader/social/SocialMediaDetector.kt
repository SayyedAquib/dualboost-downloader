package com.dualboost.downloader.social

import java.net.URI
import java.util.Locale

/**
 * High-performance URL classifier and tracking-param sanitizer
 * for the Expanded Social Media Hub.
 */
object SocialMediaDetector {

    private val TRACKING_PARAMS = setOf(
        "si", "igsh", "utm_source", "utm_medium", "utm_campaign", "utm_term",
        "utm_content", "fbclid", "gclid", "feature", "ref", "ref_src",
        "share_id", "s", "t"
    )

    /**
     * Identifies the social media platform from a raw URL.
     * Returns null if the URL belongs to a general web file or unsupported domain.
     */
    fun detectPlatform(url: String): SocialPlatform? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return null
        }

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null

        return when {
            // YouTube: youtube.com, youtu.be, m.youtube.com, music.youtube.com
            host == "youtu.be" || host.endsWith(".youtube.com") || host == "youtube.com" -> SocialPlatform.YOUTUBE

            // Instagram: instagram.com, instagr.am
            host == "instagr.am" || host.endsWith(".instagram.com") || host == "instagram.com" -> SocialPlatform.INSTAGRAM

            // TikTok: tiktok.com, vm.tiktok.com, m.tiktok.com
            host == "vm.tiktok.com" || host.endsWith(".tiktok.com") || host == "tiktok.com" -> SocialPlatform.TIKTOK

            // Twitter / X: twitter.com, x.com, t.co
            host == "x.com" || host == "twitter.com" || host == "t.co" || host.endsWith(".twitter.com") || host.endsWith(".x.com") -> SocialPlatform.TWITTER

            // Reddit: reddit.com, v.redd.it, redd.it
            host == "v.redd.it" || host == "redd.it" || host.endsWith(".reddit.com") || host == "reddit.com" -> SocialPlatform.REDDIT

            // Facebook: facebook.com, fb.watch, fb.com
            host == "fb.watch" || host == "fb.com" || host.endsWith(".facebook.com") || host == "facebook.com" -> SocialPlatform.FACEBOOK

            // Pinterest: pinterest.com, pin.it
            host == "pin.it" || host.endsWith(".pinterest.com") || host == "pinterest.com" -> SocialPlatform.PINTEREST

            // SoundCloud: soundcloud.com, on.soundcloud.com
            host == "on.soundcloud.com" || host.endsWith(".soundcloud.com") || host == "soundcloud.com" -> SocialPlatform.SOUNDCLOUD

            // Vimeo: vimeo.com
            host.endsWith(".vimeo.com") || host == "vimeo.com" -> SocialPlatform.VIMEO

            else -> null
        }
    }

    /**
     * Returns true if the URL is recognized as social media content.
     */
    fun isSocialMediaUrl(url: String): Boolean {
        return detectPlatform(url) != null
    }

    /**
     * Cleans tracking and telemetry parameters while preserving essential identifiers.
     */
    fun cleanUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val queryIndex = trimmed.indexOf('?')
        if (queryIndex == -1) return trimmed

        val base = trimmed.substring(0, queryIndex)
        val queryString = trimmed.substring(queryIndex + 1)
        val platform = detectPlatform(trimmed)

        val preservedParams = mutableListOf<String>()
        val queryParts = queryString.split('&')

        for (part in queryParts) {
            val key = part.substringBefore('=').lowercase(Locale.ROOT)
            // Preserve YouTube 'v' query param
            if (platform == SocialPlatform.YOUTUBE && (key == "v" || key == "list")) {
                preservedParams.add(part)
            } else if (platform == SocialPlatform.FACEBOOK && key == "v") {
                preservedParams.add(part)
            } else if (!TRACKING_PARAMS.contains(key) && !key.startsWith("utm_")) {
                preservedParams.add(part)
            }
        }

        return if (preservedParams.isEmpty()) {
            base
        } else {
            "$base?${preservedParams.joinToString("&")}"
        }
    }
}
