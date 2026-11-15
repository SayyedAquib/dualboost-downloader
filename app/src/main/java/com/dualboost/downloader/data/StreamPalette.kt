package com.dualboost.downloader.data

import androidx.compose.ui.graphics.Color

enum class StreamPalette(
    val id: String,
    val displayName: String,
    val description: String,
    val darkWifi: Color,
    val darkCellular: Color,
    val lightWifi: Color,
    val lightCellular: Color
) {
    DYNAMIC_MATERIAL_YOU(
        id = "dynamic_material_you",
        displayName = "Material You",
        description = "Wallpaper Adaptive",
        darkWifi = Color(0xFF64D2FF),
        darkCellular = Color(0xFFD0BCFF),
        lightWifi = Color(0xFF00668B),
        lightCellular = Color(0xFF6750A4)
    ),
    CYBERPUNK_NEON(
        id = "cyberpunk_neon",
        displayName = "Cyberpunk Neon",
        description = "Classic Cyan & Violet",
        darkWifi = Color(0xFF00E5FF),
        darkCellular = Color(0xFFB388FF),
        lightWifi = Color(0xFF00838F),
        lightCellular = Color(0xFF651FFF)
    ),
    EMERALD_AMBER(
        id = "emerald_amber",
        displayName = "Emerald & Amber",
        description = "High Visibility Mint & Amber",
        darkWifi = Color(0xFF00E676),
        darkCellular = Color(0xFFFFD54F),
        lightWifi = Color(0xFF1B5E20),
        lightCellular = Color(0xFFE65100)
    ),
    SUNSET_CORAL(
        id = "sunset_coral",
        displayName = "Sunset Coral",
        description = "Sky Blue & Coral Red",
        darkWifi = Color(0xFF40C4FF),
        darkCellular = Color(0xFFFF5252),
        lightWifi = Color(0xFF0277BD),
        lightCellular = Color(0xFFC62828)
    );

    fun resolveWifiColor(isDark: Boolean, materialPrimary: Color): Color {
        return if (this == DYNAMIC_MATERIAL_YOU) {
            materialPrimary
        } else if (isDark) {
            darkWifi
        } else {
            lightWifi
        }
    }

    fun resolveCellularColor(isDark: Boolean, materialTertiary: Color): Color {
        return if (this == DYNAMIC_MATERIAL_YOU) {
            materialTertiary
        } else if (isDark) {
            darkCellular
        } else {
            lightCellular
        }
    }

    companion object {
        val DEFAULT = DYNAMIC_MATERIAL_YOU

        fun fromId(id: String?): StreamPalette {
            if (id == null) return DEFAULT
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
