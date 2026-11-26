package com.dualboost.downloader.social

enum class SocialPlatform(
    val displayName: String,
    val brandColorHex: Long,
    val lightBrandColorHex: Long = brandColorHex
) {
    YOUTUBE("YouTube", 0xFFFF4D4D, 0xFFC62828),
    INSTAGRAM("Instagram", 0xFFF472B6, 0xFFC2185B),
    TIKTOK("TikTok", 0xFF00F2FE, 0xFF007B83),
    TWITTER("Twitter / X", 0xFF38BDF8, 0xFF0277BD),
    REDDIT("Reddit", 0xFFFF6D3A, 0xFFD84315),
    FACEBOOK("Facebook", 0xFF60A5FA, 0xFF1565C0),
    PINTEREST("Pinterest", 0xFFFF4D67, 0xFFC62828),
    SOUNDCLOUD("SoundCloud", 0xFFFF7A33, 0xFFD84315),
    VIMEO("Vimeo", 0xFF38BDF8, 0xFF0277BD);

    /** Returns a contrast-appropriate accent hex for the current dark/light theme. */
    fun resolveBrandColorHex(isDark: Boolean): Long = if (isDark) brandColorHex else lightBrandColorHex
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
    data class Error(
        val url: String,
        val message: String,
        val platform: SocialPlatform? = null,
        val attemptedQuality: MediaQuality = MediaQuality.MAX
    ) : SocialIntakeState()

    fun canTransitionTo(next: SocialIntakeState): Boolean {
        if (this::class == next::class) return true
        return when (this) {
            is Idle -> next is Ready || next is Resolving
            is Ready -> next is Idle || next is Resolving
            is Resolving -> next is Idle || next is Error || next is Ready
            is Error -> next is Idle || next is Resolving || next is Ready
        }
    }
}
