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
    val historyRepositoryOverride: DownloadHistoryRepository? = null,
    val isServiceEnabled: Boolean = (engineOverride == null),
    val thermalStatusProviderOverride: com.dualboost.downloader.thermal.ThermalStatusProvider? = null,
    val queueRepositoryOverride: DownloadQueueRepository? = null,
    val queueCoordinatorOverride: com.dualboost.downloader.engine.QueueCoordinator? = null,
    val concurrencyPreferencesOverride: ConcurrencyPreferences? = null,
    val streamPalettePreferencesOverride: StreamPalettePreferences? = null,
    val storagePreferencesOverride: StoragePreferences? = null,
    val chunkPreferencesOverride: ChunkPreferences? = null,
    val userAgentPreferencesOverride: UserAgentPreferences? = null,
    val timeoutPreferencesOverride: TimeoutPreferences? = null,
    val cellularPreferencesOverride: CellularPreferences? = null
) : com.dualboost.downloader.engine.QueueDownloadDispatcher {

    val cellularPreferences: CellularPreferences by lazy {
        cellularPreferencesOverride ?: CellularPreferences(context)
    }

    private val _cellularNetworkConfig = MutableStateFlow(
        CellularNetworkConfig(
            mode = (cellularPreferencesOverride ?: CellularPreferences(context)).getCellularMode(),
            detectedGeneration = (networkProvider?.cellularGeneration?.value ?: com.dualboost.downloader.network.CellularGeneration.UNKNOWN)
        )
    )
    val cellularNetworkConfig: StateFlow<CellularNetworkConfig> = _cellularNetworkConfig.asStateFlow()

    fun updateCellularMode(mode: CellularNetworkMode) {
        cellularPreferences.setCellularMode(mode)
        val updated = _cellularNetworkConfig.value.copy(mode = mode)
        _cellularNetworkConfig.value = updated
        (networkProvider ?: networkManager).applyCellularConfig(updated)
    }

    fun resetCellularModeToDefault() {
        cellularPreferences.resetToDefault()
        val updated = _cellularNetworkConfig.value.copy(mode = CellularNetworkMode.AUTO)
        _cellularNetworkConfig.value = updated
        (networkProvider ?: networkManager).applyCellularConfig(updated)
    }

    val timeoutPreferences: TimeoutPreferences by lazy {
        timeoutPreferencesOverride ?: TimeoutPreferences(context)
    }

    private val _timeoutConfig = MutableStateFlow(timeoutPreferences.getTimeoutConfig())
    val timeoutConfig: StateFlow<TimeoutConfig> = _timeoutConfig.asStateFlow()

    fun updateTimeoutProfile(profile: TimeoutProfile) {
        timeoutPreferences.setProfile(profile)
        _timeoutConfig.value = timeoutPreferences.getTimeoutConfig()
        downloaderEngine.setTimeoutConfig(_timeoutConfig.value)
        (networkProvider ?: networkManager).applyTimeoutConfig(_timeoutConfig.value)
    }

    fun updateCustomTimeouts(connectSec: Int, readSec: Int, maxRetries: Int, retryBackoffMs: Long) {
        timeoutPreferences.setCustomConfig(connectSec, readSec, maxRetries, retryBackoffMs)
        _timeoutConfig.value = timeoutPreferences.getTimeoutConfig()
        downloaderEngine.setTimeoutConfig(_timeoutConfig.value)
        (networkProvider ?: networkManager).applyTimeoutConfig(_timeoutConfig.value)
    }

    fun resetTimeoutToDefault() {
        timeoutPreferences.resetToDefault()
        _timeoutConfig.value = timeoutPreferences.getTimeoutConfig()
        downloaderEngine.setTimeoutConfig(_timeoutConfig.value)
        (networkProvider ?: networkManager).applyTimeoutConfig(_timeoutConfig.value)
    }

    val userAgentPreferences: UserAgentPreferences by lazy {
        userAgentPreferencesOverride ?: UserAgentPreferences(context)
    }

    private val _userAgentConfig = MutableStateFlow(userAgentPreferences.getUserAgentConfig())
    val userAgentConfig: StateFlow<UserAgentConfig> = _userAgentConfig.asStateFlow()

    fun updateUserAgentPreset(preset: UserAgentPreset) {
        userAgentPreferences.setPreset(preset)
        _userAgentConfig.value = userAgentPreferences.getUserAgentConfig()
        downloaderEngine.setUserAgentConfig(_userAgentConfig.value)
    }

    fun setCustomUserAgent(customUa: String) {
        userAgentPreferences.setCustomUserAgent(customUa)
        _userAgentConfig.value = userAgentPreferences.getUserAgentConfig()
        downloaderEngine.setUserAgentConfig(_userAgentConfig.value)
    }

    fun resetUserAgentToDefault() {
        userAgentPreferences.resetToDefault()
        _userAgentConfig.value = userAgentPreferences.getUserAgentConfig()
        downloaderEngine.setUserAgentConfig(_userAgentConfig.value)
    }

    val chunkPreferences: ChunkPreferences by lazy {
        chunkPreferencesOverride ?: ChunkPreferences(context)
    }

    private val _chunkConfig = MutableStateFlow(chunkPreferences.getChunkConfig())
    val chunkConfig: StateFlow<ChunkConfig> = _chunkConfig.asStateFlow()

    fun updateManualChunkSize(sizeBytes: Long) {
        chunkPreferences.setManualChunkSizeBytes(sizeBytes)
        _chunkConfig.value = chunkPreferences.getChunkConfig()
        downloaderEngine.setChunkConfig(_chunkConfig.value)
    }

    fun toggleAutoAdaptive(enabled: Boolean) {
        chunkPreferences.setAutoAdaptive(enabled)
        _chunkConfig.value = chunkPreferences.getChunkConfig()
        downloaderEngine.setChunkConfig(_chunkConfig.value)
    }

    fun resetChunkConfigToDefault() {
        chunkPreferences.resetToDefault()
        _chunkConfig.value = chunkPreferences.getChunkConfig()
        downloaderEngine.setChunkConfig(_chunkConfig.value)
    }

    val storagePreferences: StoragePreferences by lazy {
        storagePreferencesOverride ?: StoragePreferences(context)
    }

    private val _storageDestination = MutableStateFlow(storagePreferences.getStorageDestination())
    val storageDestination: StateFlow<StorageDestination> = _storageDestination.asStateFlow()

    fun setStorageDestination(destination: StorageDestination) {
        storagePreferences.setStorageDestination(destination)
        _storageDestination.value = storagePreferences.getStorageDestination()
        downloaderEngine.setStorageDestination(destination)
    }

    fun resetStorageDestinationToDefault() {
        storagePreferences.resetToDefault()
        _storageDestination.value = storagePreferences.getStorageDestination()
        downloaderEngine.setStorageDestination(StorageDestination.PRESET_DEFAULT)
    }

    val streamPalettePreferences: StreamPalettePreferences by lazy {
        streamPalettePreferencesOverride ?: StreamPalettePreferences(context)
    }

    private val _streamPalette = MutableStateFlow(streamPalettePreferences.getStreamPalette())
    val streamPalette: StateFlow<StreamPalette> = _streamPalette.asStateFlow()

    fun updateStreamPalette(palette: StreamPalette) {
        streamPalettePreferences.setStreamPalette(palette)
        _streamPalette.value = streamPalettePreferences.getStreamPalette()
    }

    fun resetStreamPaletteToDefault() {
        streamPalettePreferences.resetToDefault()
        _streamPalette.value = streamPalettePreferences.getStreamPalette()
    }

    val concurrencyPreferences: ConcurrencyPreferences by lazy {
        concurrencyPreferencesOverride ?: ConcurrencyPreferences(context)
    }

    private val _concurrencyConfig = MutableStateFlow(concurrencyPreferences.getConcurrencyConfig())
    val concurrencyConfig: StateFlow<ConcurrencyConfig> = _concurrencyConfig.asStateFlow()

    fun updateWifiWorkers(count: Int) {
        concurrencyPreferences.setWifiWorkers(count)
        _concurrencyConfig.value = concurrencyPreferences.getConcurrencyConfig()
    }

    fun updateCellularWorkers(count: Int) {
        concurrencyPreferences.setCellularWorkers(count)
        _concurrencyConfig.value = concurrencyPreferences.getConcurrencyConfig()
    }

    fun toggleAutoScale(enabled: Boolean) {
        concurrencyPreferences.setAutoScaleEnabled(enabled)
        _concurrencyConfig.value = concurrencyPreferences.getConcurrencyConfig()
    }

    fun resetConcurrencyToDefault() {
        concurrencyPreferences.resetToDefault()
        _concurrencyConfig.value = concurrencyPreferences.getConcurrencyConfig()
    }

    val historyRepository: DownloadHistoryRepository by lazy {
        historyRepositoryOverride ?: DownloadHistoryRepository(context)
    }

    val queueRepository: DownloadQueueRepository by lazy {
        queueRepositoryOverride ?: DownloadQueueRepository(context)
    }

    val queueCoordinator: com.dualboost.downloader.engine.QueueCoordinator by lazy {
        queueCoordinatorOverride ?: com.dualboost.downloader.engine.QueueCoordinator(
            queueRepository = queueRepository,
            dispatcher = this,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
        )
    }

    val mediaSnifferEngine: com.dualboost.downloader.sniffer.MediaSnifferEngine by lazy {
        com.dualboost.downloader.sniffer.MediaSnifferEngine()
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

    override val downloadState: StateFlow<DownloadState> get() = downloaderEngine.downloadState
    val isWifiAvailable: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isWifiAvailable
    val isCellularAvailable: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isCellularAvailable
    val isRoamingActive: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isRoamingActive
    val isVpnActive: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isVpnActive
    val cellularCarrierName: StateFlow<String?> get() = (networkProvider ?: networkManager).cellularCarrierName
    val isModemStandbyDetected: StateFlow<Boolean> get() = (networkProvider ?: networkManager).isModemStandbyDetected
    val wifiSignalLevel: StateFlow<Int> get() = (networkProvider ?: networkManager).wifiSignalLevel
    val cellularSignalLevel: StateFlow<Int> get() = (networkProvider ?: networkManager).cellularSignalLevel
    val cellularGeneration: StateFlow<com.dualboost.downloader.network.CellularGeneration> get() = (networkProvider ?: networkManager).cellularGeneration
    val wifiDetails: StateFlow<com.dualboost.downloader.network.WifiDetails> get() = (networkProvider ?: networkManager).wifiDetails
    val networkTopology: StateFlow<com.dualboost.downloader.network.NetworkTopology> get() = (networkProvider ?: networkManager).networkTopology
    val listenerState: StateFlow<com.dualboost.downloader.network.NetworkListenerState> get() = (networkProvider ?: networkManager).listenerState
    val cellularKeepAliveState: StateFlow<com.dualboost.downloader.network.CellularKeepAliveState> get() = (networkProvider ?: networkManager).cellularKeepAliveState
    val enginePhase: StateFlow<com.dualboost.downloader.engine.EnginePhase> get() = downloaderEngine.enginePhase
    val circuitBreakerSnapshot: StateFlow<com.dualboost.downloader.engine.CircuitBreakerSnapshot> get() = downloaderEngine.circuitBreakerSnapshot
    val queuePhase: StateFlow<QueuePhase> get() = queueCoordinator.queuePhase
    val thermalStatus: StateFlow<Int> get() = (thermalStatusProviderOverride ?: downloaderEngine.thermalStatusProvider).thermalStatus
    val historyItems: StateFlow<List<DownloadHistoryItem>> get() = historyRepository.historyItems

    private val _workerTestStatus = MutableStateFlow<WorkerTestStatus>(WorkerTestStatus.Idle)
    val workerTestStatus: StateFlow<WorkerTestStatus> = _workerTestStatus.asStateFlow()

    suspend fun testWorkerConnectivity(rawUrl: String, secretKey: String): WorkerTestStatus {
        if (_workerTestStatus.value is WorkerTestStatus.Testing) {
            return _workerTestStatus.value
        }
        val cleanBase = rawUrl.trim().trimEnd('/')
        if (cleanBase.isBlank()) {
            val emptyErr = WorkerTestStatus.Error("Worker URL cannot be empty")
            _workerTestStatus.value = emptyErr
            return emptyErr
        }
        _workerTestStatus.value = WorkerTestStatus.Testing
        val result = withContext(Dispatchers.IO) {
            try {
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
            downloaderEngine.setStorageDestination(_storageDestination.value)
        } catch (_: Exception) {}
        try {
            downloaderEngine.setChunkConfig(_chunkConfig.value)
        } catch (_: Exception) {}
        try {
            downloaderEngine.setUserAgentConfig(_userAgentConfig.value)
        } catch (_: Exception) {}
        try {
            downloaderEngine.setTimeoutConfig(_timeoutConfig.value)
        } catch (_: Exception) {}
        try {
            (networkProvider ?: networkManager).applyTimeoutConfig(_timeoutConfig.value)
        } catch (_: Exception) {}
        try {
            val currentGen = (networkProvider ?: networkManager).cellularGeneration.value
            _cellularNetworkConfig.value = _cellularNetworkConfig.value.copy(detectedGeneration = currentGen)
            (networkProvider ?: networkManager).applyCellularConfig(_cellularNetworkConfig.value)
        } catch (_: Exception) {}
        try {
            downloaderEngine.restoreIncompleteDownload()
        } catch (e: Exception) {
            Log.w("DownloadRepository", "Could not restore incomplete download: ${e.message}")
        }
    }

    override fun startDownload(url: String, forceSequentialMode: Boolean?) {
        startDownload(url, downloaderEngine.getDefaultDownloadDir(), forceSequentialMode)
    }

    fun startDownload(
        url: String,
        targetDir: File = downloaderEngine.getDefaultDownloadDir(),
        forceSequentialMode: Boolean? = null,
        allowDangerousBypass: Boolean = false,
        concurrencyConfigOverride: ConcurrencyConfig? = null,
        chunkConfigOverride: ChunkConfig? = null,
        userAgentConfigOverride: UserAgentConfig? = null,
        timeoutConfigOverride: TimeoutConfig? = null
    ) {
        val effectiveConcurrency = concurrencyConfigOverride ?: _concurrencyConfig.value
        val effectiveChunkConfig = chunkConfigOverride ?: _chunkConfig.value
        val effectiveUserAgentConfig = userAgentConfigOverride ?: _userAgentConfig.value
        val effectiveTimeoutConfig = timeoutConfigOverride ?: _timeoutConfig.value
        try {
            (networkProvider ?: networkManager).refreshCellularGeneration()
        } catch (_: Exception) {}
        if (isServiceEnabled) {
            try {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = DownloadForegroundService.ACTION_START_DOWNLOAD
                    putExtra(DownloadForegroundService.EXTRA_URL, url)
                    if (forceSequentialMode != null) {
                        putExtra(DownloadForegroundService.EXTRA_FORCE_SEQUENTIAL, forceSequentialMode)
                    }
                    putExtra(DownloadForegroundService.EXTRA_ALLOW_BYPASS, allowDangerousBypass)
                }
                ContextCompat.startForegroundService(context, intent)
                return
            } catch (_: Exception) {
                // Safe fallback in test environments or if ForegroundServiceStartNotAllowedException is thrown
            }
        }
        downloaderEngine.startDownload(url, targetDir, forceSequentialMode, allowDangerousBypass, effectiveConcurrency, effectiveChunkConfig, effectiveUserAgentConfig, effectiveTimeoutConfig)
    }

    override fun pauseDownload() {
        downloaderEngine.pauseDownload()
        try {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_PAUSE_DOWNLOAD
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


