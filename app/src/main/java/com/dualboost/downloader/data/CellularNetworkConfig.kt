package com.dualboost.downloader.data

import com.dualboost.downloader.network.CellularGeneration

/**
 * Supported Cellular Network Operation Modes (ADR-048).
 */
enum class CellularNetworkMode(
    val id: String,
    val displayName: String,
    val description: String
) {
    AUTO(
        id = "auto",
        displayName = "Auto-Detect (Dynamic)",
        description = "Dynamically identifies 5G NR NSA/SA and 4G LTE with real-time radio handoffs"
    ),
    FORCE_5G(
        id = "force_5g",
        displayName = "Force 5G Mode",
        description = "Always treats cellular as 5G New Radio, engaging 5G telemetry and optimizations"
    ),
    FORCE_4G(
        id = "force_4g",
        displayName = "Force 4G Mode",
        description = "Limits cellular profile and telemetry to 4G LTE"
    );

    companion object {
        fun fromId(id: String?): CellularNetworkMode {
            if (id == null) return AUTO
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
        }
    }
}

/**
 * Domain model representing dynamic cellular network detection and user override state (ADR-048).
 */
data class CellularNetworkConfig(
    val mode: CellularNetworkMode = CellularNetworkMode.AUTO,
    val detectedGeneration: CellularGeneration = CellularGeneration.UNKNOWN
) {
    val effectiveGeneration: CellularGeneration
        get() = when (mode) {
            CellularNetworkMode.FORCE_5G -> CellularGeneration.FIVE_G
            CellularNetworkMode.FORCE_4G -> CellularGeneration.FOUR_G
            CellularNetworkMode.AUTO -> detectedGeneration
        }

    fun getBadgeLabel(): String {
        return when (mode) {
            CellularNetworkMode.FORCE_5G -> "⚡ Force 5G"
            CellularNetworkMode.FORCE_4G -> "⚡ Force 4G"
            CellularNetworkMode.AUTO -> ""
        }
    }

    companion object {
        val DEFAULT = CellularNetworkConfig()
    }
}

