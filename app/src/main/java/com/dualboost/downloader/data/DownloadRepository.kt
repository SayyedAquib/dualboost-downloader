package com.dualboost.downloader.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.DualNetworkManager
import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.service.DownloadForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadRepository(
    private val context: Context,
    val networkProvider: NetworkProvider? = null,
    val engineOverride: DualDownloaderEngine? = null,
    val speedTestEngineOverride: com.dualboost.downloader.engine.SpeedTestEngine? = null,
    val historyRepositoryOverride: DownloadHistoryRepository? = null,
    val isServiceEnabled: Boolean = (engineOverride == null),
    val thermalStatusProviderOverride: com.dualboost.downloader.thermal.ThermalStatusProvider? = null
) {

    val historyRepository: DownloadHistoryRepository by lazy {
        historyRepositoryOverride ?: DownloadHistoryRepository(context)
    }

    val networkManager: DualNetworkManager by lazy {
        (networkProvider as? DualNetworkManager) ?: DualNetworkManager(context)
    }

    val thermalStatusProvider: com.dualboost.downloader.thermal.ThermalStatusProvider by lazy {
        thermalStatusProviderOverride ?: com.dualboost.downloader.thermal.SystemThermalStatusProvider(context)
    }

    val downloaderEngine: DualDownloaderEngine by lazy {
        engineOverride ?: DualDownloaderEngine(context, networkProvider ?: networkManager, historyRepository, thermalStatusProvider)
    }

    val speedTestEngine: com.dualboost.downloader.engine.SpeedTestEngine by lazy {
        speedTestEngineOverride ?: com.dualboost.downloader.engine.SpeedTestEngine(networkProvider ?: networkManager)
    }

    val downloadState: StateFlow<DownloadState> get() = downloaderEngine.downloadState
    val speedTestState: StateFlow<SpeedTestState> get() = speedTestEngine.speedTestState
    val isWifiAvailable: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isWifiAvailable
    val isCellularAvailable: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isCellularAvailable
    val isRoamingActive: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isRoamingActive
    val isVpnActive: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isVpnActive
    val cellularCarrierName: StateFlow<String?> get() = (networkProvider ?: networkManager).cellularCarrierName
    val isModemStandbyDetected: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isModemStandbyDetected
    val wifiSignalLevel: StateFlow<Int> get() = (networkProvider ?: networkManager).wifiSignalLevel
    val cellularSignalLevel: StateFlow<Int> get() = (networkProvider ?: networkManager).cellularSignalLevel
    val cellularGeneration: StateFlow<com.dualboost.downloader.network.CellularGeneration> get() = (networkProvider ?: networkManager).cellularGeneration
    val thermalStatus: StateFlow<Int> get() = (thermalStatusProviderOverride ?: downloaderEngine.thermalStatusProvider).thermalStatus
    val historyItems: StateFlow<List<DownloadHistoryItem>> get() = historyRepository.historyItems

    private val _workerTestStatus = MutableStateFlow<WorkerTestStatus>(WorkerTestStatus.Idle)
    val workerTestStatus: StateFlow<WorkerTestStatus> = _workerTestStatus.asStateFlow()

    suspend fun testWorkerConnectivity(rawUrl: String, secretKey: String): WorkerTestStatus {
        _workerTestStatus.value = WorkerTestStatus.Testing
        val result = withContext(Dispatchers.IO) {
            try {
                val cleanBase = rawUrl.trim().trimEnd('/')
                val healthUrl = if (cleanBase.endsWith("/health")) cleanBase else "$cleanBase/health"

                val client = networkProvider?.getDefaultClient() ?: OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()

                // SEC-AUDIT M-1: Health check is an unauthenticated ping; do not expose secret in request headers
                val request = Request.Builder().url(healthUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.contains("DualBoost Cloud Slicer")) {
                            WorkerTestStatus.Success("Worker is live and verified! Ready to slice streams.")
                        } else {
                            WorkerTestStatus.Success("Connected (HTTP ${response.code})")
                        }
                    } else if (response.code == 401) {
                        WorkerTestStatus.Error("HTTP 401: Unauthorized. Please check Secret Key.")
                    } else {
                        WorkerTestStatus.Error("Worker returned HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                WorkerTestStatus.Error("Connection failed: ${e.message ?: "Unknown error"}")
            }
        }
        _workerTestStatus.value = result
        return result
    }

    fun resetWorkerTestStatus() {
        _workerTestStatus.value = WorkerTestStatus.Idle
    }

    fun resetToIdle() {
        downloaderEngine.resetToIdle()
    }

    init {
        try {
            if (networkProvider is DualNetworkManager) {
                networkProvider.startListening()
            } else if (networkProvider == null) {
                networkManager.startListening()
            }
        } catch (e: Exception) {
            Log.w("DownloadRepository", "Could not start network listening: ${e.message}")
        }
        try {
            downloaderEngine.restoreIncompleteDownload()
        } catch (e: Exception) {
            Log.w("DownloadRepository", "Could not restore incomplete download: ${e.message}")
        }
    }

    fun startDownload(
        url: String,
        targetDir: File = downloaderEngine.getDefaultDownloadDir(),
        forceSequentialMode: Boolean? = null
    ) {
        if (isServiceEnabled) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = DownloadForegroundService.ACTION_START_DOWNLOAD
                    putExtra(DownloadForegroundService.EXTRA_URL, url)
                    if (forceSequentialMode != null) {
                        putExtra(DownloadForegroundService.EXTRA_FORCE_SEQUENTIAL, forceSequentialMode)
                    }
                }
                ContextCompat.startForegroundService(context, intent)
                return
            } catch (_: Exception) {
                // Safe fallback in test environments or if ForegroundServiceStartNotAllowedException is thrown
            }
        }
        downloaderEngine.startDownload(url, targetDir, forceSequentialMode)
    }

    fun pauseDownload() {
        downloaderEngine.pauseDownload()
        try {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        } catch (_: Exception) {
            // Safe in test environments without Android service runner
        }
    }

    fun discardDownload(fileName: String? = null, targetDir: File = downloaderEngine.getDefaultDownloadDir()) {
        downloaderEngine.discardDownload(targetDir = targetDir, fileName = fileName)
        try {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_DISCARD_DOWNLOAD
            }
            context.startService(intent)
        } catch (_: Exception) {
            // Safe in test environments without Android service runner
        }
    }

    fun startSpeedTest() {
        speedTestEngine.startSpeedTest()
    }

    fun stopSpeedTest() {
        speedTestEngine.stopSpeedTest()
    }

    suspend fun deleteHistoryItem(itemId: String, deleteFileFromDisk: Boolean): Boolean {
        return historyRepository.deleteItem(itemId, deleteFileFromDisk)
    }

    suspend fun clearAllHistory(deleteFilesFromDisk: Boolean) {
        historyRepository.clearAll(deleteFilesFromDisk)
    }

    fun calculateHistoryStorageBytes(): Long {
        return historyRepository.calculateTotalStorageBytes()
    }

    fun isHistoryFilePresent(item: DownloadHistoryItem): Boolean {
        return historyRepository.isFilePresent(item)
    }
}


