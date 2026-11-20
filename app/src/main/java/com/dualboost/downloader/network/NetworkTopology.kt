package com.dualboost.downloader.network

/**
 * Explicit composite state model representing the aggregated network topology.
 * Synthesizes multiple independent physical network states (Wi-Fi, Cellular, Roaming, VPN, Modem Sleep)
 * into a single unified source of truth for the UI, Foreground Service, Engine, and AI agents.
 */
enum class NetworkTopology(
    val id: String,
    val displayName: String,
    val isDualChannelAvailable: Boolean
) {
    /** No network interfaces with Internet connectivity are available. */
    DISCONNECTED(
        id = "disconnected",
        displayName = "No Internet Connection",
        isDualChannelAvailable = false
    ),

    /** Only Wi-Fi is connected and available for transfer. */
    WIFI_ONLY(
        id = "wifi_only",
        displayName = "Wi-Fi Only",
        isDualChannelAvailable = false
    ),

    /** Only Cellular is connected and available for transfer. */
    CELLULAR_ONLY(
        id = "cellular_only",
        displayName = "Cellular Only",
        isDualChannelAvailable = false
    ),

    /** Both Wi-Fi and Cellular are active, unlocked, and bonding simultaneously. */
    DUAL_CHANNEL(
        id = "dual_channel",
        displayName = "DualBoost Active (Wi-Fi + Cellular)",
        isDualChannelAvailable = true
    ),

    /** Wi-Fi is active, but Cellular is blocked by the international roaming safeguard. */
    ROAMING_SAFEGUARD(
        id = "roaming_safeguard",
        displayName = "Roaming Safeguard Active",
        isDualChannelAvailable = false
    ),

    /** System VPN is active; traffic securely multiplexes through the VPN tunnel. */
    VPN_TUNNEL(
        id = "vpn_tunnel",
        displayName = "VPN Encrypted Multiplexing",
        isDualChannelAvailable = true
    ),

    /** Wi-Fi is active, but the carrier modem is in deep standby sleep. */
    MODEM_STANDBY(
        id = "modem_standby",
        displayName = "Carrier Modem Standby",
        isDualChannelAvailable = false
    );

    companion object {
        fun resolve(
            isWifiAvailable: Boolean,
            isCellularAvailable: Boolean,
            isRoamingActive: Boolean,
            isVpnActive: Boolean,
            isModemStandbyDetected: Boolean
        ): NetworkTopology {
            if (isVpnActive) return VPN_TUNNEL
            if (isWifiAvailable && isCellularAvailable) {
                return if (isRoamingActive) ROAMING_SAFEGUARD else DUAL_CHANNEL
            }
            if (isWifiAvailable) {
                return if (isModemStandbyDetected) MODEM_STANDBY else WIFI_ONLY
            }
            if (isCellularAvailable) {
                return if (isRoamingActive) ROAMING_SAFEGUARD else CELLULAR_ONLY
            }
            return DISCONNECTED
        }
    }
}
