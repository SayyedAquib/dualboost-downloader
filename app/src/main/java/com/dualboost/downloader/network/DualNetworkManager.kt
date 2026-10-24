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
    val wifiSignalLevel: StateFlow<Int>
        get() = MutableStateFlow(4)
    val cellularSignalLevel: StateFlow<Int>
        get() = MutableStateFlow(4)
    val cellularGeneration: StateFlow<CellularGeneration>
        get() = MutableStateFlow(CellularGeneration.FIVE_G)
    fun getWifiClient(): OkHttpClient?
    fun getCellularClient(): OkHttpClient?
    fun getDefaultClient(): OkHttpClient
}

class DualNetworkManager(private val context: Context) : NetworkProvider {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var wifiNetwork: Network? = null
    private var cellularNetwork: Network? = null
    private var telephonyCallback: Any? = null

    private val _isWifiAvailable = MutableStateFlow(false)
    override val isWifiAvailable: StateFlow<Boolean> = _isWifiAvailable.asStateFlow()

    private val _isCellularAvailable = MutableStateFlow(false)
    override val isCellularAvailable: StateFlow<Boolean> = _isCellularAvailable.asStateFlow()

    private val _wifiSignalLevel = MutableStateFlow(4)
    override val wifiSignalLevel: StateFlow<Int> = _wifiSignalLevel.asStateFlow()

    private val _cellularSignalLevel = MutableStateFlow(4)
    override val cellularSignalLevel: StateFlow<Int> = _cellularSignalLevel.asStateFlow()

    private val _cellularGeneration = MutableStateFlow(CellularGeneration.FIVE_G)
    override val cellularGeneration: StateFlow<CellularGeneration> = _cellularGeneration.asStateFlow()

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

    private var wifiClient: OkHttpClient = baseOkHttpClient
    private var cellularClient: OkHttpClient = baseOkHttpClient

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
        detectInitialCellularGeneration(capabilities)
    }

    private fun detectInitialCellularGeneration(capabilities: NetworkCapabilities) {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager != null) {
                val networkType = telephonyManager.dataNetworkType
                if (networkType == TelephonyManager.NETWORK_TYPE_NR) {
                    _cellularGeneration.value = CellularGeneration.FIVE_G
                    return
                } else if (networkType == TelephonyManager.NETWORK_TYPE_LTE) {
                    _cellularGeneration.value = CellularGeneration.FOUR_G
                    return
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {}

        val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
        if (downstreamKbps in 1..99_999) {
            _cellularGeneration.value = CellularGeneration.FOUR_G
        } else if (downstreamKbps >= 100_000) {
            _cellularGeneration.value = CellularGeneration.FIVE_G
        }
    }

    fun startListening() {
        // Explicitly request Wi-Fi interface
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.requestNetwork(wifiRequest, wifiCallback)

        // Explicitly request Cellular interface (keeps mobile data alive while Wi-Fi is on)
        val cellularRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.requestNetwork(cellularRequest, cellularCallback)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        val override = telephonyDisplayInfo.overrideNetworkType
                        val is5G = override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                                override == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED ||
                                telephonyDisplayInfo.networkType == TelephonyManager.NETWORK_TYPE_NR

                        _cellularGeneration.value = if (is5G) CellularGeneration.FIVE_G else CellularGeneration.FOUR_G
                    }
                }
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
                telephonyCallback = callback
            } catch (_: Exception) {}
        }
    }

    fun stopListening() {
        try {
            connectivityManager.unregisterNetworkCallback(wifiCallback)
        } catch (_: Exception) {}
        try {
            connectivityManager.unregisterNetworkCallback(cellularCallback)
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
        return if (_isWifiAvailable.value) wifiClient else null
    }

    override fun getCellularClient(): OkHttpClient? {
        return if (_isCellularAvailable.value) cellularClient else null
    }

    override fun getDefaultClient(): OkHttpClient {
        return baseOkHttpClient
    }

    companion object {
        private const val TAG = "DualNetworkManager"
    }
}

