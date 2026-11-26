package com.dualboost.downloader.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.dualboost.downloader.data.CellularNetworkConfig
import com.dualboost.downloader.data.CellularNetworkMode
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class CellularGeneration(
    val shortLabel: String,
    val detailLabel: String,
    val fullDisplayName: String
) {
    UNKNOWN(
        shortLabel = "Cellular",
        detailLabel = "Cellular",
        fullDisplayName = "Cellular"
    ),
    THREE_G_OR_LOWER(
        shortLabel = "3G",
        detailLabel = "3G Cellular",
        fullDisplayName = "3G"
    ),
    FOUR_G(
        shortLabel = "4G",
        detailLabel = "4G LTE Cellular",
        fullDisplayName = "4G LTE"
    ),
    FIVE_G(
        shortLabel = "5G",
        detailLabel = "5G Cellular",
        fullDisplayName = "5G"
    );

    companion object {
        fun fromTelephony(
            networkType: Int,
            overrideNetworkType: Int = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
            downstreamBandwidthKbps: Int = 0
        ): CellularGeneration {
            val is5GDetected = overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                    overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED ||
                    networkType == TelephonyManager.NETWORK_TYPE_NR ||
                    (downstreamBandwidthKbps >= 120_000 && networkType == TelephonyManager.NETWORK_TYPE_LTE)

            if (is5GDetected) {
                return FIVE_G
            }

            return when (networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> FOUR_G
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA,
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN,
                TelephonyManager.NETWORK_TYPE_GSM -> THREE_G_OR_LOWER
                else -> {
                    if (overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA ||
                        overrideNetworkType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO
                    ) {
                        FOUR_G
                    } else {
                        UNKNOWN
                    }
                }
            }
        }
    }
}

enum class WifiGeneration(
    val shortLabel: String,
    val standardName: String
) {
    WIFI_7("Wi-Fi 7", "802.11be"),
    WIFI_6E("Wi-Fi 6E", "802.11ax (6 GHz)"),
    WIFI_6("Wi-Fi 6", "802.11ax"),
    WIFI_5("Wi-Fi 5", "802.11ac"),
    WIFI_4("Wi-Fi 4", "802.11n"),
    LEGACY("Wi-Fi", "802.11a/b/g"),
    UNKNOWN("Wi-Fi", "Wi-Fi");

    companion object {
        fun fromStandard(standard: Int, frequencyMhz: Int = 0): WifiGeneration {
            return when (standard) {
                8 -> WIFI_7
                6 -> if (frequencyMhz in 5925..7125) WIFI_6E else WIFI_6
                5 -> WIFI_5
                4 -> WIFI_4
                1 -> LEGACY
                else -> UNKNOWN
            }
        }
    }
}

data class WifiDetails(
    val generation: WifiGeneration = WifiGeneration.UNKNOWN,
    val frequencyMhz: Int = 0,
    val linkSpeedMbps: Int = 0
) {
    val frequencyBandLabel: String?
        get() = when {
            frequencyMhz in 5925..7125 -> "6 GHz"
            frequencyMhz in 4900..5900 -> "5 GHz"
            frequencyMhz in 2400..2500 -> "2.4 GHz"
            else -> null
        }

    val shortLabel: String
        get() = when {
            generation != WifiGeneration.UNKNOWN && generation != WifiGeneration.LEGACY -> generation.shortLabel
            frequencyBandLabel != null -> "Wi-Fi $frequencyBandLabel"
            else -> "Wi-Fi"
        }

    val detailLabel: String
        get() {
            val band = frequencyBandLabel
            return when {
                generation != WifiGeneration.UNKNOWN && generation != WifiGeneration.LEGACY -> {
                    if (band != null) "${generation.shortLabel} ($band)" else generation.shortLabel
                }
                band != null -> "Wi-Fi ($band)"
                else -> "Wi-Fi"
            }
        }

    val richHeroLabel: String
        get() {
            val band = frequencyBandLabel
            val baseLabel = when {
                generation != WifiGeneration.UNKNOWN && generation != WifiGeneration.LEGACY -> generation.shortLabel
                else -> "Wi-Fi"
            }
            val details = mutableListOf<String>()
            if (band != null) details.add(band)
            if (linkSpeedMbps > 0) details.add("$linkSpeedMbps Mbps")

            return if (details.isNotEmpty()) {
                "$baseLabel (${details.joinToString(" • ")})"
            } else {
                baseLabel
            }
        }

    companion object {
        val DISCONNECTED = WifiDetails(WifiGeneration.UNKNOWN, 0, 0)

        fun fromWifiInfo(standard: Int, frequencyMhz: Int, linkSpeedMbps: Int): WifiDetails {
            val gen = WifiGeneration.fromStandard(standard, frequencyMhz)
            return WifiDetails(
                generation = gen,
                frequencyMhz = maxOf(0, frequencyMhz),
                linkSpeedMbps = maxOf(0, linkSpeedMbps)
            )
        }
    }
}

interface NetworkProvider {
    val isWifiAvailable: StateFlow<Boolean>
    val isCellularAvailable: StateFlow<Boolean>
    val isRoamingActive: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val cellularCarrierName: StateFlow<String?>
        get() = MutableStateFlow(null)
    val isModemStandbyDetected: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    val wifiSignalLevel: StateFlow<Int>
        get() = MutableStateFlow(4)
    val cellularSignalLevel: StateFlow<Int>
        get() = MutableStateFlow(4)
    val cellularGeneration: StateFlow<CellularGeneration>
        get() = MutableStateFlow(CellularGeneration.FIVE_G)
    val wifiDetails: StateFlow<WifiDetails>
        get() = MutableStateFlow(WifiDetails.DISCONNECTED)
    val networkTopology: StateFlow<NetworkTopology>
        get() = MutableStateFlow(
            NetworkTopology.resolve(
                isWifiAvailable = isWifiAvailable.value,
                isCellularAvailable = isCellularAvailable.value,
                isRoamingActive = isRoamingActive.value,
                isVpnActive = isVpnActive.value,
                isModemStandbyDetected = isModemStandbyDetected.value
            )
        )
    val listenerState: StateFlow<NetworkListenerState>
        get() = MutableStateFlow(NetworkListenerState.STOPPED)
    val cellularKeepAliveState: StateFlow<CellularKeepAliveState>
        get() = MutableStateFlow(
            if (isCellularKeepAliveActive.value) CellularKeepAliveState.ACTIVE else CellularKeepAliveState.IDLE
        )
    val isCellularKeepAliveActive: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    fun acquireCellularKeepAlive() {}
    fun releaseCellularKeepAlive() {}
    fun applyTimeoutConfig(config: com.dualboost.downloader.data.TimeoutConfig) {}
    fun applyCellularConfig(config: CellularNetworkConfig) {}
    fun refreshCellularGeneration() {}
    fun getWifiClient(): OkHttpClient?
    fun getCellularClient(): OkHttpClient?
    fun getDefaultClient(): OkHttpClient
}

class DualNetworkManager(private val context: Context) : NetworkProvider {

    private val connectivityManager: ConnectivityManager? = runCatching {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }.getOrNull()

    @Volatile private var wifiNetwork: Network? = null
    @Volatile private var cellularNetwork: Network? = null
    @Volatile private var hasReceivedDisplayInfo = false
    private var telephonyCallback: Any? = null

    private val _isWifiAvailable = MutableStateFlow(false)
    override val isWifiAvailable: StateFlow<Boolean> = _isWifiAvailable.asStateFlow()

    private val _isCellularAvailable = MutableStateFlow(false)
    override val isCellularAvailable: StateFlow<Boolean> = _isCellularAvailable.asStateFlow()

    private val _isRoamingActive = MutableStateFlow(false)
    override val isRoamingActive: StateFlow<Boolean> = _isRoamingActive.asStateFlow()

    private val _isVpnActive = MutableStateFlow(false)
    override val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _cellularCarrierName = MutableStateFlow<String?>(null)
    override val cellularCarrierName: StateFlow<String?> = _cellularCarrierName.asStateFlow()

    private val _isModemStandbyDetected = MutableStateFlow(false)
    override val isModemStandbyDetected: StateFlow<Boolean> = _isModemStandbyDetected.asStateFlow()

    private val _wifiSignalLevel = MutableStateFlow(4)
    override val wifiSignalLevel: StateFlow<Int> = _wifiSignalLevel.asStateFlow()

    private val _wifiDetails = MutableStateFlow(WifiDetails.DISCONNECTED)
    override val wifiDetails: StateFlow<WifiDetails> = _wifiDetails.asStateFlow()

    private val _cellularSignalLevel = MutableStateFlow(4)
    override val cellularSignalLevel: StateFlow<Int> = _cellularSignalLevel.asStateFlow()

    private val _cellularGeneration = MutableStateFlow(CellularGeneration.UNKNOWN)
    override val cellularGeneration: StateFlow<CellularGeneration> = _cellularGeneration.asStateFlow()

    private val _networkTopology = MutableStateFlow(NetworkTopology.DISCONNECTED)
    override val networkTopology: StateFlow<NetworkTopology> = _networkTopology.asStateFlow()

    private fun reevaluateTopology() {
        _networkTopology.value = NetworkTopology.resolve(
            isWifiAvailable = _isWifiAvailable.value,
            isCellularAvailable = _isCellularAvailable.value,
            isRoamingActive = _isRoamingActive.value,
            isVpnActive = _isVpnActive.value,
            isModemStandbyDetected = _isModemStandbyDetected.value
        )
    }

    @Volatile private var latestOverrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
    @Volatile private var cellularConfig = CellularNetworkConfig.DEFAULT
    @Volatile private var lastDetectedGeneration = CellularGeneration.UNKNOWN

    override fun applyCellularConfig(config: CellularNetworkConfig) {
        cellularConfig = config
        if (config.mode != CellularNetworkMode.AUTO) {
            _cellularGeneration.value = config.effectiveGeneration
        } else {
            _cellularGeneration.value = lastDetectedGeneration
            refreshCellularGeneration()
        }
    }

    override fun refreshCellularGeneration() {
        if (cellularConfig.mode != CellularNetworkMode.AUTO) {
            _cellularGeneration.value = cellularConfig.effectiveGeneration
            return
        }
        val cm = connectivityManager ?: return
        val net = cellularNetwork ?: return
        val caps = cm.getNetworkCapabilities(net) ?: return
        reevaluateCellularGeneration(caps)
    }

    private val _listenerState = MutableStateFlow(NetworkListenerState.STOPPED)
    override val listenerState: StateFlow<NetworkListenerState> = _listenerState.asStateFlow()

    private val _cellularKeepAliveState = MutableStateFlow(CellularKeepAliveState.IDLE)
    override val cellularKeepAliveState: StateFlow<CellularKeepAliveState> = _cellularKeepAliveState.asStateFlow()

    private val _isCellularKeepAliveActive = MutableStateFlow(false)
    override val isCellularKeepAliveActive: StateFlow<Boolean> = _isCellularKeepAliveActive.asStateFlow()
    @Volatile private var activeCellularCallback: ConnectivityManager.NetworkCallback? = null

    // SEC-AUDIT H-2: Cloudflare Edge CA pins (*.workers.dev) for Cloud Slicer endpoint
    // Primary: Cloudflare Inc ECC CA-3
    // Backup: DigiCert Global Root G2
    private val cloudflareSlicerPinner = CertificatePinner.Builder()
        .add(
            "*.workers.dev",
            "sha256/Wf0HMiAPHknqFVnwDMLZPGbJ2EFhBj1eQyi7gMS/Vxo=",
            "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="
        )
        .build()

    // SEC-AUDIT M-5: Enforce TLS 1.2+ with modern cipher suites (blocks deprecated TLS 1.0/1.1)
    // CLEARTEXT included for localhost/loopback testing; manifest usesCleartextTraffic=false blocks real cleartext
    @Volatile private var baseOkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
        .certificatePinner(cloudflareSlicerPinner)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var wifiClient: OkHttpClient = baseOkHttpClient
    @Volatile private var cellularClient: OkHttpClient = baseOkHttpClient

    private var currentTimeoutConfig: com.dualboost.downloader.data.TimeoutConfig =
        com.dualboost.downloader.data.TimeoutConfig.DEFAULT

    fun getTimeoutConfig(): com.dualboost.downloader.data.TimeoutConfig = currentTimeoutConfig

    override fun applyTimeoutConfig(config: com.dualboost.downloader.data.TimeoutConfig) {
        currentTimeoutConfig = config
        baseOkHttpClient = baseOkHttpClient.newBuilder()
            .connectTimeout(config.effectiveConnectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(config.effectiveReadTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        wifiNetwork?.let { net ->
            wifiClient = baseOkHttpClient.newBuilder()
                .socketFactory(net.socketFactory)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return net.getAllByName(hostname).toList()
                    }
                })
                .build()
        } ?: run {
            wifiClient = baseOkHttpClient
        }

        cellularNetwork?.let { net ->
            cellularClient = baseOkHttpClient.newBuilder()
                .socketFactory(net.socketFactory)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return net.getAllByName(hostname).toList()
                    }
                })
                .build()
        } ?: run {
            cellularClient = baseOkHttpClient
        }
    }

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            _isVpnActive.value = isVpn
            reevaluateTopology()
        }

        override fun onLost(network: Network) {
            _isVpnActive.value = false
            reevaluateTopology()
        }
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Wi-Fi network available: $network")
            wifiNetwork = network
            // SEC-AUDIT-01: Cryptographically bind both TCP sockets and DNS resolution to Wi-Fi interface
            wifiClient = baseOkHttpClient.newBuilder()
                .socketFactory(network.socketFactory)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return network.getAllByName(hostname).toList()
                    }
                })
                .build()
            _isWifiAvailable.value = true
            reevaluateTopology()
            connectivityManager?.getNetworkCapabilities(network)?.let { updateWifiSignal(it) }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            updateWifiSignal(networkCapabilities)
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Wi-Fi network lost: $network")
            if (wifiNetwork == network) {
                wifiNetwork = null
                wifiClient = baseOkHttpClient
                _isWifiAvailable.value = false
                _wifiSignalLevel.value = 0
                _wifiDetails.value = WifiDetails.DISCONNECTED
                reevaluateTopology()
            }
        }
    }

    private val cellularCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Cellular (5G/LTE) network available: $network")
            cellularNetwork = network
            // SEC-AUDIT-01: Cryptographically bind both TCP sockets and DNS resolution to Cellular interface
            cellularClient = baseOkHttpClient.newBuilder()
                .socketFactory(network.socketFactory)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return network.getAllByName(hostname).toList()
                    }
                })
                .build()
            _isCellularAvailable.value = true
            _isModemStandbyDetected.value = false
            reevaluateTopology()
            connectivityManager?.getNetworkCapabilities(network)?.let { updateCellularDetails(it) }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            updateCellularDetails(networkCapabilities)
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Cellular (5G/LTE) network lost: $network")
            if (cellularNetwork == network) {
                cellularNetwork = null
                cellularClient = baseOkHttpClient
                _isCellularAvailable.value = false
                _cellularSignalLevel.value = 0
                _isRoamingActive.value = false
                hasReceivedDisplayInfo = false
                latestOverrideNetworkType = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE
                lastDetectedGeneration = CellularGeneration.UNKNOWN
                if (cellularConfig.mode == CellularNetworkMode.AUTO) {
                    _cellularGeneration.value = CellularGeneration.UNKNOWN
                }
                if (_isWifiAvailable.value) {
                    _isModemStandbyDetected.value = true
                }
                reevaluateTopology()
            }
        }
    }

    private fun updateWifiSignal(capabilities: NetworkCapabilities) {
        val signalDbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.signalStrength
        } else {
            NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
        }

        val level = if (signalDbm != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
            when {
                signalDbm >= -60 -> 4
                signalDbm >= -70 -> 3
                signalDbm >= -80 -> 2
                signalDbm >= -90 -> 1
                else -> 1
            }
        } else {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val rssi = wifiManager?.connectionInfo?.rssi ?: -60
                WifiManager.calculateSignalLevel(rssi, 5)
            } catch (_: Exception) {
                4
            }
        }
        _wifiSignalLevel.value = level.coerceIn(1, 4)

        val wifiInfo: android.net.wifi.WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? android.net.wifi.WifiInfo
        } else {
            null
        } ?: try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo
        } catch (_: Exception) {
            null
        }

        if (wifiInfo != null) {
            val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { wifiInfo.wifiStandard } catch (_: Exception) { 0 }
            } else {
                0
            }
            val freq = try { wifiInfo.frequency } catch (_: Exception) { 0 }
            val linkSpeed = try { wifiInfo.linkSpeed } catch (_: Exception) { 0 }
            _wifiDetails.value = WifiDetails.fromWifiInfo(standard, freq, linkSpeed)
        }
    }

    private fun updateCellularDetails(capabilities: NetworkCapabilities) {
        val signalDbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.signalStrength
        } else {
            NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED
        }

        val level = if (signalDbm != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) {
            when {
                signalDbm >= -85 -> 4
                signalDbm >= -95 -> 3
                signalDbm >= -105 -> 2
                signalDbm >= -115 -> 1
                else -> 1
            }
        } else {
            4
        }
        _cellularSignalLevel.value = level.coerceIn(1, 4)
        val isRoaming = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        _isRoamingActive.value = isRoaming
        _isModemStandbyDetected.value = false
        reevaluateTopology()
        detectCarrierName()
        reevaluateCellularGeneration(capabilities)
    }

    private fun detectCarrierName() {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simCarrier = telephonyManager?.simOperatorName?.takeIf { it.isNotBlank() }
            val netCarrier = telephonyManager?.networkOperatorName?.takeIf { it.isNotBlank() }
            val carrier = simCarrier ?: netCarrier
            if (!carrier.isNullOrBlank()) {
                _cellularCarrierName.value = carrier
            }
        } catch (_: Exception) {}
    }

    private fun reevaluateCellularGeneration(capabilities: NetworkCapabilities) {
        if (cellularConfig.mode != CellularNetworkMode.AUTO) {
            _cellularGeneration.value = cellularConfig.effectiveGeneration
            return
        }
        val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
        var resolvedGen = CellularGeneration.UNKNOWN

        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null) {
                val networkType = telephonyManager.dataNetworkType
                resolvedGen = CellularGeneration.fromTelephony(
                    networkType = networkType,
                    overrideNetworkType = latestOverrideNetworkType,
                    downstreamBandwidthKbps = downstreamKbps
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        if (resolvedGen == CellularGeneration.UNKNOWN && downstreamKbps >= 120_000) {
            resolvedGen = CellularGeneration.FIVE_G
        }

        if (resolvedGen != CellularGeneration.UNKNOWN) {
            lastDetectedGeneration = resolvedGen
            _cellularGeneration.value = resolvedGen
        }
    }

    fun startListening() {
        val cm = connectivityManager ?: return
        if (!_listenerState.value.canTransitionTo(NetworkListenerState.LISTENING)) return
        _listenerState.value = NetworkListenerState.LISTENING

        // Explicitly request Wi-Fi interface (with registerNetworkCallback fallback)
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.requestNetwork(wifiRequest, wifiCallback)
        } catch (e: Exception) {
            Log.w(TAG, "requestNetwork for Wi-Fi failed, falling back to registerNetworkCallback", e)
            try {
                cm.registerNetworkCallback(wifiRequest, wifiCallback)
            } catch (e2: Exception) {
                Log.e(TAG, "registerNetworkCallback for Wi-Fi failed", e2)
            }
        }

        // Register passive listener for Cellular interface (monitors signal & carrier with 0 idle radio battery drain)
        val cellularRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(cellularRequest, cellularCallback)
        } catch (e: Exception) {
            Log.w(TAG, "registerNetworkCallback for Cellular failed", e)
        }

        // Immediately inspect currently active networks to populate initial state
        try {
            val allNetworks = cm.allNetworks
            for (network in allNetworks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wifiCallback.onAvailable(network)
                        wifiCallback.onCapabilitiesChanged(network, caps)
                    }
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        cellularCallback.onAvailable(network)
                        cellularCallback.onCapabilitiesChanged(network, caps)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Initial active network probe exception", e)
        }

        // Monitor default network for VPN transport encapsulation
        try {
            cm.registerDefaultNetworkCallback(defaultNetworkCallback)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        hasReceivedDisplayInfo = true
                        latestOverrideNetworkType = telephonyDisplayInfo.overrideNetworkType
                        val networkType = telephonyDisplayInfo.networkType
                        val caps = connectivityManager?.getNetworkCapabilities(cellularNetwork)
                        val downstreamKbps = caps?.linkDownstreamBandwidthKbps ?: 0
                        val gen = CellularGeneration.fromTelephony(networkType, latestOverrideNetworkType, downstreamKbps)
                        if (gen != CellularGeneration.UNKNOWN) {
                            lastDetectedGeneration = gen
                            if (cellularConfig.mode == CellularNetworkMode.AUTO) {
                                _cellularGeneration.value = gen
                            }
                        }
                    }
                }
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
                telephonyCallback = callback
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    override fun acquireCellularKeepAlive() {
        if (_cellularKeepAliveState.value.isActiveOrPending || _isCellularKeepAliveActive.value) return
        _cellularKeepAliveState.value = CellularKeepAliveState.REQUESTING
        _isCellularKeepAliveActive.value = true
        Log.d(TAG, "Acquiring on-demand cellular keep-alive request for active transfer")
        try {
            val cellularRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _cellularKeepAliveState.value = CellularKeepAliveState.ACTIVE
                    cellularCallback.onAvailable(network)
                }
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    cellularCallback.onCapabilitiesChanged(network, networkCapabilities)
                }
                override fun onLost(network: Network) {
                    cellularCallback.onLost(network)
                }
                override fun onUnavailable() {
                    _cellularKeepAliveState.value = CellularKeepAliveState.FAILED
                    _isCellularKeepAliveActive.value = false
                }
            }
            activeCellularCallback = cb
            connectivityManager?.requestNetwork(cellularRequest, cb)
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire on-demand cellular request", e)
            activeCellularCallback = null
            _cellularKeepAliveState.value = CellularKeepAliveState.FAILED
            _isCellularKeepAliveActive.value = false
        }
    }

    @Synchronized
    override fun releaseCellularKeepAlive() {
        if (_cellularKeepAliveState.value == CellularKeepAliveState.IDLE && !_isCellularKeepAliveActive.value) return
        _cellularKeepAliveState.value = CellularKeepAliveState.IDLE
        _isCellularKeepAliveActive.value = false
        Log.d(TAG, "Releasing on-demand cellular keep-alive request (allowing modem sleep)")
        activeCellularCallback?.let { cb ->
            try {
                connectivityManager?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Could not unregister on-demand cellular callback", e)
            }
            activeCellularCallback = null
        }
    }

    fun stopListening() {
        if (!_listenerState.value.canTransitionTo(NetworkListenerState.STOPPED)) return
        _listenerState.value = NetworkListenerState.STOPPED
        releaseCellularKeepAlive()
        hasReceivedDisplayInfo = false
        try {
            connectivityManager?.unregisterNetworkCallback(wifiCallback)
        } catch (_: Exception) {}
        try {
            connectivityManager?.unregisterNetworkCallback(cellularCallback)
        } catch (_: Exception) {}
        try {
            connectivityManager?.unregisterNetworkCallback(defaultNetworkCallback)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is TelephonyCallback) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } catch (_: Exception) {}
            telephonyCallback = null
        }

        wifiNetwork = null
        cellularNetwork = null
        _isWifiAvailable.value = false
        _isCellularAvailable.value = false
        _isVpnActive.value = false
        _isRoamingActive.value = false
        _isModemStandbyDetected.value = false
        _wifiDetails.value = WifiDetails.DISCONNECTED
        reevaluateTopology()
    }

    override fun getWifiClient(): OkHttpClient? {
        if (_isVpnActive.value) return baseOkHttpClient
        return if (_isWifiAvailable.value) wifiClient else null
    }

    override fun getCellularClient(): OkHttpClient? {
        if (_isRoamingActive.value) return null
        if (_isVpnActive.value) return baseOkHttpClient
        return if (_isCellularAvailable.value) cellularClient else null
    }

    override fun getDefaultClient(): OkHttpClient {
        return baseOkHttpClient
    }

    companion object {
        private const val TAG = "DualNetworkManager"
    }
}

