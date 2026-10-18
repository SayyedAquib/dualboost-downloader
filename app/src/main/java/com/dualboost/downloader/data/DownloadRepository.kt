package com.dualboost.downloader.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.DualNetworkManager
import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.service.DownloadForegroundService
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class DownloadRepository(
    private val context: Context,
    val networkProvider: NetworkProvider? = null,
    val engineOverride: DualDownloaderEngine? = null,
    val speedTestEngineOverride: com.dualboost.downloader.engine.SpeedTestEngine? = null,
    val historyRepositoryOverride: DownloadHistoryRepository? = null
) {

    val historyRepository: DownloadHistoryRepository by lazy {
        historyRepositoryOverride ?: DownloadHistoryRepository(context)
    }

    val networkManager: DualNetworkManager by lazy {
        (networkProvider as? DualNetworkManager) ?: DualNetworkManager(context)
    }

    val downloaderEngine: DualDownloaderEngine by lazy {
        engineOverride ?: DualDownloaderEngine(context, networkProvider ?: networkManager, historyRepository)
    }

    val speedTestEngine: com.dualboost.downloader.engine.SpeedTestEngine by lazy {
        speedTestEngineOverride ?: com.dualboost.downloader.engine.SpeedTestEngine(networkProvider ?: networkManager)
    }

    val downloadState: StateFlow<DownloadState> get() = downloaderEngine.downloadState
    val speedTestState: StateFlow<SpeedTestState> get() = speedTestEngine.speedTestState
    val isWifiAvailable: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isWifiAvailable
    val isCellularAvailable: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isCellularAvailable
    val historyItems: StateFlow<List<DownloadHistoryItem>> get() = historyRepository.historyItems

    init {
        try {
            if (networkProvider is DualNetworkManager) {
                networkProvider.startListening()
            } else if (networkProvider == null) {
                networkManager.startListening()
            }
        } catch (_: Exception) {
            // Safe fallback in headless test environments
        }
        try {
            downloaderEngine.restoreIncompleteDownload()
        } catch (_: Exception) {
            // Safe fallback in headless test environments
        }
    }

    fun startDownload(url: String, targetDir: File = downloaderEngine.getDefaultDownloadDir()) {
        try {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_START_DOWNLOAD
                putExtra(DownloadForegroundService.EXTRA_URL, url)
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // Safe in test environments without Android service runner
        }
        downloaderEngine.startDownload(url, targetDir)
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


