package com.dualboost.downloader.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface NetworkProvider {
    val isWifiAvailable: StateFlow<Boolean>
    val isCellularAvailable: StateFlow<Boolean>
    fun getWifiClient(): OkHttpClient?
    fun getCellularClient(): OkHttpClient?
    fun getDefaultClient(): OkHttpClient
}

class DualNetworkManager(context: Context) : NetworkProvider {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var wifiNetwork: Network? = null
    private var cellularNetwork: Network? = null

    private val _isWifiAvailable = MutableStateFlow(false)
    override val isWifiAvailable: StateFlow<Boolean> = _isWifiAvailable.asStateFlow()

    private val _isCellularAvailable = MutableStateFlow(false)
    override val isCellularAvailable: StateFlow<Boolean> = _isCellularAvailable.asStateFlow()

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
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Wi-Fi network lost: $network")
            if (wifiNetwork == network) {
                wifiNetwork = null
                wifiClient = baseOkHttpClient
                _isWifiAvailable.value = false
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
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Cellular (5G/LTE) network lost: $network")
            if (cellularNetwork == network) {
                cellularNetwork = null
                cellularClient = baseOkHttpClient
                _isCellularAvailable.value = false
            }
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
    }

    fun stopListening() {
        try {
            connectivityManager.unregisterNetworkCallback(wifiCallback)
        } catch (_: Exception) {}
        try {
            connectivityManager.unregisterNetworkCallback(cellularCallback)
        } catch (_: Exception) {}
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

