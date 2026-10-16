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

    // Base client template
    private val baseOkHttpClient = OkHttpClient.Builder()
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
            wifiClient = baseOkHttpClient.newBuilder()
                .socketFactory(network.socketFactory)
                .build()
            _isWifiAvailable.value = true
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Wi-Fi network lost: $network")
            if (wifiNetwork == network) {
                wifiNetwork = null
                _isWifiAvailable.value = false
            }
        }
    }

    private val cellularCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Cellular (5G/LTE) network available: $network")
            cellularNetwork = network
            cellularClient = baseOkHttpClient.newBuilder()
                .socketFactory(network.socketFactory)
                .build()
            _isCellularAvailable.value = true
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Cellular (5G/LTE) network lost: $network")
            if (cellularNetwork == network) {
                cellularNetwork = null
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

