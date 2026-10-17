package com.dualboost.downloader.engine

import android.util.Log
import com.dualboost.downloader.data.SpeedTestPhase
import com.dualboost.downloader.data.SpeedTestState
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SpeedTestEngine(
    private val networkProvider: NetworkProvider,
    private val testUrl: String = DEFAULT_SPEED_TEST_URL,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SpeedTestEngine"
        const val DEFAULT_SPEED_TEST_URL = "https://speed.cloudflare.com/__down?bytes=50000000"
        const val SINGLE_PHASE_DURATION_MS = 3000L
        const val DUAL_PHASE_DURATION_MS = 4000L
    }

    private val _speedTestState = MutableStateFlow<SpeedTestState>(SpeedTestState.Idle)
    val speedTestState: StateFlow<SpeedTestState> = _speedTestState.asStateFlow()

    private var activeJob: Job? = null
    private val activeCalls = Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())

    fun startSpeedTest() {
        if (_speedTestState.value is SpeedTestState.Running) return

        val wifiOk = networkProvider.isWifiAvailable.value
        val cellOk = networkProvider.isCellularAvailable.value

        if (!wifiOk && !cellOk) {
            _speedTestState.value = SpeedTestState.Error("No active network interfaces available to test.")
            return
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            try {
                var wifiSpeedMBps = 0f
                var cellularSpeedMBps = 0f
                var dualBoostSpeedMBps = 0f

                // --- PHASE 1: Wi-Fi Benchmark (3s) ---
                if (networkProvider.isWifiAvailable.value) {
                    val client = networkProvider.getWifiClient() ?: networkProvider.getDefaultClient()
                    wifiSpeedMBps = runStreamPhase(
                        phase = SpeedTestPhase.TESTING_WIFI,
                        client = client,
                        durationMs = SINGLE_PHASE_DURATION_MS,
                        overallProgressStart = 0.0f,
                        overallProgressSpan = 0.3f,
                        wifiPrev = 0f,
                        cellPrev = 0f
                    )
                }

                // --- PHASE 2: 5G Cellular Benchmark (3s) ---
                if (networkProvider.isCellularAvailable.value) {
                    val client = networkProvider.getCellularClient() ?: networkProvider.getDefaultClient()
                    cellularSpeedMBps = runStreamPhase(
                        phase = SpeedTestPhase.TESTING_CELLULAR,
                        client = client,
                        durationMs = SINGLE_PHASE_DURATION_MS,
                        overallProgressStart = 0.3f,
                        overallProgressSpan = 0.3f,
                        wifiPrev = wifiSpeedMBps,
                        cellPrev = 0f
                    )
                }

                // --- PHASE 3: DualBoost Combined Benchmark (4s) ---
                val canAggregate = networkProvider.isWifiAvailable.value && networkProvider.isCellularAvailable.value
                if (canAggregate) {
                    dualBoostSpeedMBps = runDualStreamPhase(
                        durationMs = DUAL_PHASE_DURATION_MS,
                        wifiPrev = wifiSpeedMBps,
                        cellPrev = cellularSpeedMBps
                    )
                } else {
                    dualBoostSpeedMBps = maxOf(wifiSpeedMBps, cellularSpeedMBps)
                }

                // Calculate Boost Gain
                val bestSingle = maxOf(wifiSpeedMBps, cellularSpeedMBps)
                val boostGainPercent = if (bestSingle > 0.05f && dualBoostSpeedMBps > bestSingle) {
                    (((dualBoostSpeedMBps - bestSingle) / bestSingle) * 100f).toInt()
                } else if (bestSingle > 0.05f && canAggregate) {
                    // Margin calculation
                    (((dualBoostSpeedMBps / bestSingle) - 1f) * 100f).toInt().coerceAtLeast(0)
                } else {
                    0
                }

                _speedTestState.value = SpeedTestState.Completed(
                    wifiSpeedMBps = wifiSpeedMBps,
                    cellularSpeedMBps = cellularSpeedMBps,
                    dualBoostSpeedMBps = dualBoostSpeedMBps,
                    boostGainPercent = boostGainPercent
                )
            } catch (e: CancellationException) {
                Log.d(TAG, "Speed test cancelled by user")
                _speedTestState.value = SpeedTestState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Speed test encountered error: ${e.message}", e)
                _speedTestState.value = SpeedTestState.Error(e.message ?: "Speed test encountered an unexpected error.")
            } finally {
                cancelAllCalls()
            }
        }
    }

    fun stopSpeedTest() {
        activeJob?.cancel()
        activeJob = null
        cancelAllCalls()
        _speedTestState.value = SpeedTestState.Idle
    }

    private suspend fun runStreamPhase(
        phase: SpeedTestPhase,
        client: OkHttpClient,
        durationMs: Long,
        overallProgressStart: Float,
        overallProgressSpan: Float,
        wifiPrev: Float,
        cellPrev: Float
    ): Float {
        val bytesRead = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        var finalSpeedMbps = 0f

        val request = buildSpeedTestRequest(testUrl)

        val call = client.newCall(request)
        activeCalls.add(call)

        try {
            scope.launch {
                try {
                    call.execute().use { response ->
                        if (response.isSuccessful) {
                            val stream = response.body?.byteStream()
                            val buffer = ByteArray(32768)
                            while (stream != null && (System.currentTimeMillis() - startTime) < durationMs) {
                                val read = stream.read(buffer)
                                if (read == -1) break
                                bytesRead.addAndGet(read.toLong())
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Call cancelled or ended
                }
            }

            while ((System.currentTimeMillis() - startTime) < durationMs) {
                delay(150)
                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.1f)
                val currentSpeed = bytesRead.get().toFloat() / (elapsedSec * (1024f * 1024f))
                finalSpeedMbps = currentSpeed
                val phaseProgress = ((System.currentTimeMillis() - startTime).toFloat() / durationMs).coerceIn(0f, 1f)

                _speedTestState.value = SpeedTestState.Running(
                    phase = phase,
                    currentSpeedMBps = currentSpeed,
                    overallProgress = overallProgressStart + (phaseProgress * overallProgressSpan),
                    wifiSpeedMBps = if (phase == SpeedTestPhase.TESTING_WIFI) currentSpeed else wifiPrev,
                    cellularSpeedMBps = if (phase == SpeedTestPhase.TESTING_CELLULAR) currentSpeed else cellPrev
                )
            }
        } finally {
            call.cancel()
            activeCalls.remove(call)
        }

        return finalSpeedMbps
    }

    private suspend fun runDualStreamPhase(
        durationMs: Long,
        wifiPrev: Float,
        cellPrev: Float
    ): Float {
        val wifiBytes = AtomicLong(0)
        val cellBytes = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        var finalSpeedMbps = 0f

        val wifiClient = networkProvider.getWifiClient() ?: networkProvider.getDefaultClient()
        val cellClient = networkProvider.getCellularClient() ?: networkProvider.getDefaultClient()

        val req1 = buildSpeedTestRequest(testUrl)
        val req2 = buildSpeedTestRequest(testUrl)

        val call1 = wifiClient.newCall(req1)
        val call2 = cellClient.newCall(req2)
        activeCalls.add(call1)
        activeCalls.add(call2)

        try {
            val job1 = scope.launch {
                try {
                    call1.execute().use { res ->
                        val stream = res.body?.byteStream()
                        val buffer = ByteArray(32768)
                        while (stream != null && (System.currentTimeMillis() - startTime) < durationMs) {
                            val r = stream.read(buffer)
                            if (r == -1) break
                            wifiBytes.addAndGet(r.toLong())
                        }
                    }
                } catch (_: Exception) {}
            }

            val job2 = scope.launch {
                try {
                    call2.execute().use { res ->
                        val stream = res.body?.byteStream()
                        val buffer = ByteArray(32768)
                        while (stream != null && (System.currentTimeMillis() - startTime) < durationMs) {
                            val r = stream.read(buffer)
                            if (r == -1) break
                            cellBytes.addAndGet(r.toLong())
                        }
                    }
                } catch (_: Exception) {}
            }

            while ((System.currentTimeMillis() - startTime) < durationMs) {
                delay(150)
                val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.1f)
                val totalBytes = wifiBytes.get() + cellBytes.get()
                val currentSpeed = totalBytes.toFloat() / (elapsedSec * (1024f * 1024f))
                finalSpeedMbps = currentSpeed
                val phaseProgress = ((System.currentTimeMillis() - startTime).toFloat() / durationMs).coerceIn(0f, 1f)

                _speedTestState.value = SpeedTestState.Running(
                    phase = SpeedTestPhase.TESTING_DUALBOOST,
                    currentSpeedMBps = currentSpeed,
                    overallProgress = 0.6f + (phaseProgress * 0.4f),
                    wifiSpeedMBps = wifiPrev,
                    cellularSpeedMBps = cellPrev
                )
            }

            job1.cancel()
            job2.cancel()
        } finally {
            call1.cancel()
            call2.cancel()
            activeCalls.remove(call1)
            activeCalls.remove(call2)
        }

        return finalSpeedMbps
    }

    private fun buildSpeedTestRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .header("Sec-CH-UA", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            .header("Sec-CH-UA-Mobile", "?0")
            .header("Sec-CH-UA-Platform", "\"Windows\"")
            .header("Sec-CH-UA-Platform-Version", "\"15.0.0\"")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .build()
    }

    private fun cancelAllCalls() {
        val iterator = activeCalls.iterator()
        while (iterator.hasNext()) {
            val call = iterator.next()
            try {
                call.cancel()
            } catch (_: Exception) {}
            iterator.remove()
        }
    }
}

