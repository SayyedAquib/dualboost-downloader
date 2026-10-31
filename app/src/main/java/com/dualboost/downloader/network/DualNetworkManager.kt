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
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class CellularGeneration {
    UNKNOWN,
    FOUR_G,
    FIVE_G
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
    val isCellularKeepAliveActive: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    fun acquireCellularKeepAlive() {}
    fun releaseCellularKeepAlive() {}
    fun getWifiClient(): OkHttpClient?
    fun getCellularClient(): OkHttpClient?
    fun getDefaultClient(): OkHttpClient
}

class DualNetworkManager(private val context: Context) : NetworkProvider {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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

    private val _cellularSignalLevel = MutableStateFlow(4)
    override val cellularSignalLevel: StateFlow<Int> = _cellularSignalLevel.asStateFlow()

    private val _cellularGeneration = MutableStateFlow(CellularGeneration.FOUR_G)
    override val cellularGeneration: StateFlow<CellularGeneration> = _cellularGeneration.asStateFlow()

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
    private val baseOkHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT))
        .certificatePinner(cloudflareSlicerPinner)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var wifiClient: OkHttpClient = baseOkHttpClient
    @Volatile private var cellularClient: OkHttpClient = baseOkHttpClient

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val isVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            _isVpnActive.value = isVpn
        }

        override fun onLost(network: Network) {
            _isVpnActive.value = false
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
            connectivityManager.getNetworkCapabilities(network)?.let { updateWifiSignal(it) }
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
            connectivityManager.getNetworkCapabilities(network)?.let { updateCellularDetails(it) }
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
                if (_isWifiAvailable.value) {
                    _isModemStandbyDetected.value = true
                }
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
        detectCarrierName()
        if (!hasReceivedDisplayInfo) {
            detectInitialCellularGeneration(capabilities)
        }
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

    private fun detectInitialCellularGeneration(capabilities: NetworkCapabilities) {
        if (hasReceivedDisplayInfo) return
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null) {
                val networkType = telephonyManager.dataNetworkType
                if (networkType == TelephonyManager.NETWORK_TYPE_NR) {
                    _cellularGeneration.value = CellularGeneration.FIVE_G
                    return
                } else if (networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    _cellularGeneration.value = CellularGeneration.FOUR_G
                    return
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    fun startListening() {
        // Explicitly request Wi-Fi interface (with registerNetworkCallback fallback)
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.requestNetwork(wifiRequest, wifiCallback)
        } catch (e: Exception) {
            Log.w(TAG, "requestNetwork for Wi-Fi failed, falling back to registerNetworkCallback", e)
            try {
                connectivityManager.registerNetworkCallback(wifiRequest, wifiCallback)
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
            connectivityManager.registerNetworkCallback(cellularRequest, cellularCallback)
        } catch (e: Exception) {
            Log.w(TAG, "registerNetworkCallback for Cellular failed", e)
        }

        // Immediately inspect currently active networks to populate initial state
        try {
            val allNetworks = connectivityManager.allNetworks
            for (network in allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
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
            connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        hasReceivedDisplayInfo = true
                        val override = telephonyDisplayInfo.overrideNetworkType
                        val is5GDetected = override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                                override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED ||
                                telephonyDisplayInfo.networkType == TelephonyManager.NETWORK_TYPE_NR

                        _cellularGeneration.value = if (is5GDetected) CellularGeneration.FIVE_G else CellularGeneration.FOUR_G
                    }
                }
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
                telephonyCallback = callback
            } catch (_: Exception) {}
        }
    }

    override fun acquireCellularKeepAlive() {
        if (_isCellularKeepAliveActive.value) return
        _isCellularKeepAliveActive.value = true
        Log.d(TAG, "Acquiring on-demand cellular keep-alive request for active transfer")
        try {
            val cellularRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    cellularCallback.onAvailable(network)
                }
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    cellularCallback.onCapabilitiesChanged(network, networkCapabilities)
                }
                override fun onLost(network: Network) {
                    cellularCallback.onLost(network)
                }
            }
            activeCellularCallback = cb
            connectivityManager.requestNetwork(cellularRequest, cb)
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire on-demand cellular request", e)
        }
    }

    override fun releaseCellularKeepAlive() {
        if (!_isCellularKeepAliveActive.value) return
        _isCellularKeepAliveActive.value = false
        Log.d(TAG, "Releasing on-demand cellular keep-alive request (allowing modem sleep)")
        activeCellularCallback?.let { cb ->
            try {
                connectivityManager.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Could not unregister on-demand cellular callback", e)
            }
            activeCellularCallback = null
        }
    }

    fun stopListening() {
        releaseCellularKeepAlive()
        hasReceivedDisplayInfo = false
        try {
            connectivityManager.unregisterNetworkCallback(wifiCallback)
        } catch (_: Exception) {}
        try {
            connectivityManager.unregisterNetworkCallback(cellularCallback)
        } catch (_: Exception) {}
        try {
            connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback is TelephonyCallback) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
            } catch (_: Exception) {}
            telephonyCallback = null
        }
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

