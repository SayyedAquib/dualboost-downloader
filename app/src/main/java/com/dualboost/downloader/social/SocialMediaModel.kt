package com.dualboost.downloader.social

enum class SocialPlatform(val displayName: String, val brandColorHex: Long) {
    YOUTUBE("YouTube", 0xFFFF0000),
    INSTAGRAM("Instagram", 0xFFE1306C),
    TIKTOK("TikTok", 0xFF00F2FE),
    TWITTER("Twitter / X", 0xFF1DA1F2),
    REDDIT("Reddit", 0xFFFF4500),
    FACEBOOK("Facebook", 0xFF1877F2),
    PINTEREST("Pinterest", 0xFFE60023),
    SOUNDCLOUD("SoundCloud", 0xFFFF5500),
    VIMEO("Vimeo", 0xFF1AB7EA)
}

enum class MediaQuality(val label: String, val apiValue: String) {
    MAX("Highest Quality (4K / 1080p)", "max"),
    HD_720("Standard HD (720p)", "720"),
    AUDIO_ONLY("Audio Only (MP3)", "audio")
}

data class SocialMediaAsset(
    val originalUrl: String,
    val platform: SocialPlatform,
    val title: String,
    val streamUrl: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val isAudioOnly: Boolean = false,
    val suggestedFilename: String
)

sealed class SocialIntakeState {
    object Idle : SocialIntakeState()
    data class Resolving(val url: String, val platform: SocialPlatform) : SocialIntakeState()
    data class Ready(
        val originalUrl: String,
        val platform: SocialPlatform,
        val availableQualities: List<MediaQuality> = listOf(MediaQuality.MAX, MediaQuality.HD_720, MediaQuality.AUDIO_ONLY)
    ) : SocialIntakeState()
    data class Error(val url: String, val message: String) : SocialIntakeState()
}
