package com.dualboost.downloader.data

import android.os.Build

/**
 * Supported User-Agent presets (ADR-046).
 */
enum class UserAgentPreset(
    val id: String,
    val displayName: String,
    val description: String
) {
    NATIVE(
        id = "native",
        displayName = "Native Device",
        description = "Auto-detected from phone hardware"
    ),
    PIXEL_9(
        id = "pixel_9",
        displayName = "Pixel 9",
        description = "Google Pixel 9 • Android 15"
    ),
    GALAXY_S24(
        id = "galaxy_s24",
        displayName = "Galaxy S24 Ultra",
        description = "Samsung Galaxy S24 Ultra • SM-S928B"
    ),
    DESKTOP_CHROME(
        id = "desktop_chrome",
        displayName = "Desktop Chrome",
        description = "Windows 11 • Chrome 131"
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom",
        description = "User-defined raw User-Agent"
    );

    companion object {
        fun fromId(id: String): UserAgentPreset {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NATIVE
        }
    }
}

/**
 * Domain model representing browser identity and User-Agent configuration (ADR-046).
 */
class UserAgentConfig(
    val preset: UserAgentPreset = UserAgentPreset.NATIVE,
    val customUserAgentString: String = ""
) {

    /**
     * Resolves the full User-Agent string to be sent in HTTP request headers.
     */
    fun resolveUserAgentString(
        deviceModel: String = Build.MODEL ?: "Pixel 9",
        androidVersion: String = Build.VERSION.RELEASE ?: "15"
    ): String {
        return when (preset) {
            UserAgentPreset.NATIVE -> buildNativeUserAgent(deviceModel, androidVersion)
            UserAgentPreset.PIXEL_9 -> PIXEL_9_UA
            UserAgentPreset.GALAXY_S24 -> GALAXY_S24_UA
            UserAgentPreset.DESKTOP_CHROME -> DESKTOP_UA
            UserAgentPreset.CUSTOM -> {
                if (customUserAgentString.isNotBlank()) {
                    customUserAgentString.trim()
                } else {
                    buildNativeUserAgent(deviceModel, androidVersion)
                }
            }
        }
    }

    /**
     * Determines whether this configuration should bypass mobile probing and fast-track to desktop headers.
     */
    fun isDesktopFastTrack(): Boolean {
        return preset == UserAgentPreset.DESKTOP_CHROME ||
                (preset == UserAgentPreset.CUSTOM && customUserAgentString.contains("Windows NT", ignoreCase = true))
    }

    /**
     * Human-readable summary/subtitle for settings UI and cards.
     */
    fun getSubtitleText(
        deviceModel: String = Build.MODEL ?: "Pixel 9",
        androidVersion: String = Build.VERSION.RELEASE ?: "15"
    ): String {
        return when (preset) {
            UserAgentPreset.NATIVE -> "Native Device: " + buildNativeUserAgent(deviceModel, androidVersion)
            UserAgentPreset.PIXEL_9 -> "Google Pixel 9 • Android 15"
            UserAgentPreset.GALAXY_S24 -> "Samsung Galaxy S24 Ultra • SM-S928B"
            UserAgentPreset.DESKTOP_CHROME -> "Windows 11 • Chrome 131 (Desktop Fast-Track)"
            UserAgentPreset.CUSTOM -> {
                if (customUserAgentString.isNotBlank()) {
                    "Custom: $customUserAgentString"
                } else {
                    "Custom (Empty - fallback to Native)"
                }
            }
        }
    }

    /**
     * Compact badge label for active download card in MainActivity.
     */
    fun getBadgeLabel(): String {
        return when (preset) {
            UserAgentPreset.NATIVE -> ""
            UserAgentPreset.PIXEL_9 -> "⚡ Pixel 9 UA"
            UserAgentPreset.GALAXY_S24 -> "⚡ Galaxy S24 UA"
            UserAgentPreset.DESKTOP_CHROME -> "⚡ Desktop UA"
            UserAgentPreset.CUSTOM -> "⚡ Custom UA"
        }
    }

    fun copy(
        preset: UserAgentPreset = this.preset,
        customUserAgentString: String = this.customUserAgentString
    ): UserAgentConfig = UserAgentConfig(preset, customUserAgentString)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserAgentConfig) return false
        return preset == other.preset && customUserAgentString == other.customUserAgentString
    }

    override fun hashCode(): Int {
        var result = preset.hashCode()
        result = 31 * result + customUserAgentString.hashCode()
        return result
    }

    override fun toString(): String =
        "UserAgentConfig(preset=$preset, customUserAgentString='$customUserAgentString')"

    companion object {
        val DEFAULT = UserAgentConfig()

        const val PIXEL_9_UA =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.107 Mobile Safari/537.36"

        const val GALAXY_S24_UA =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.107 Mobile Safari/537.36"

        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun buildNativeUserAgent(deviceModel: String, androidVersion: String): String {
            val cleanModel = deviceModel.ifBlank { "Android" }
            val cleanVersion = androidVersion.ifBlank { "15" }
            return "Mozilla/5.0 (Linux; Android $cleanVersion; $cleanModel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.107 Mobile Safari/537.36"
        }
    }
}
