package com.dualboost.downloader.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualboost.downloader.DualBoostApp
import com.dualboost.downloader.data.DownloadHistoryItem
import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.QueuePhase
import com.dualboost.downloader.engine.CircuitBreakerSnapshot
import com.dualboost.downloader.engine.EnginePhase
import com.dualboost.downloader.network.CellularKeepAliveState
import com.dualboost.downloader.network.NetworkListenerState
import com.dualboost.downloader.social.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class QuickTestPreset(
    val id: String,
    val label: String,
    val sizeLabel: String,
    val description: String,
    val url: String,
    val supportsRange: Boolean
)

val DEFAULT_PRESETS = listOf(
    QuickTestPreset(
        id = "google_10mb",
        label = "10 MB",
        sizeLabel = "10 MB",
        description = "Google Edge CDN (Fast Dual-Channel Aggregation)",
        url = "https://dl.google.com/android/repository/platform-tools_r34.0.5-windows.zip",
        supportsRange = true
    ),
    QuickTestPreset(
        id = "python_25mb",
        label = "25 MB",
        sizeLabel = "25 MB",
        description = "Fastly Mumbai Edge (Dual-Channel Boost)",
        url = "https://www.python.org/ftp/python/3.12.7/python-3.12.7-amd64.exe",
        supportsRange = true
    ),
    QuickTestPreset(
        id = "google_50mb",
        label = "50 MB",
        sizeLabel = "50 MB",
        description = "Google Edge CDN (Dual-Channel Quick Test)",
        url = "https://dl.google.com/android/repository/platform-35_r02.zip",
        supportsRange = true
    ),
    QuickTestPreset(
        id = "cf_100mb",
        label = "100 MB (Cloudflare)",
        sizeLabel = "100 MB",
        description = "Cloudflare Edge Dynamic Stream (Single-Channel)",
        url = "https://speed.cloudflare.com/__down?bytes=104857600",
        supportsRange = false
    ),
    QuickTestPreset(
        id = "google_150mb",
        label = "150 MB",
        sizeLabel = "150 MB",
        description = "Google Edge CDN (Full Aggregation Stress Test)",
        url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
        supportsRange = true
    ),
    QuickTestPreset(
        id = "google_500mb",
        label = "500 MB",
        sizeLabel = "500 MB",
        description = "Google Edge CDN (High-Throughput Aggregation)",
        url = "https://dl.google.com/android/repository/emulator-windows_x64-16555280.zip",
        supportsRange = true
    ),
    QuickTestPreset(
        id = "google_1gb",
        label = "1 GB",
        sizeLabel = "1 GB",
        description = "Google Edge CDN (Massive 1.2 GB Endurance Stress Test)",
        url = "https://dl.google.com/android/studio/install/2024.2.1.11/android-studio-2024.2.1.11-windows.exe",
        supportsRange = true
    )
)

interface PowerExemptionChecker {
    fun isIgnoringBatteryOptimizations(): Boolean
    fun isAggressiveOem(): Boolean
}

class SystemPowerExemptionChecker(private val context: Context?) : PowerExemptionChecker {
    override fun isIgnoringBatteryOptimizations(): Boolean {
        if (context == null) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    override fun isAggressiveOem(): Boolean {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        return manufacturer in AGGRESSIVE_OEMS
    }

    companion object {
        val AGGRESSIVE_OEMS = setOf(
            "samsung", "xiaomi", "redmi", "poco", "huawei", "honor",
            "oppo", "oneplus", "realme", "vivo", "transsion", "infinix", "tecno"
        )
    }
}

open class MainViewModel(
    val repository: DownloadRepository? = runCatching { DualBoostApp.instance.repository }.getOrNull(),
    downloadStateOverride: StateFlow<DownloadState>? = null,
    val powerExemptionChecker: PowerExemptionChecker = SystemPowerExemptionChecker(runCatching { DualBoostApp.instance }.getOrNull()),
    socialResolverOverride: SocialMediaResolver? = null,
    coroutineScopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScopeOverride ?: viewModelScope
    private val socialResolver: SocialMediaResolver = socialResolverOverride ?: SocialMediaResolver()
    private var socialResolveJob: Job? = null

    private val _socialIntakeState = MutableStateFlow<SocialIntakeState>(SocialIntakeState.Idle)
    val socialIntakeState: StateFlow<SocialIntakeState> = _socialIntakeState.asStateFlow()

    private fun setSocialIntakeState(next: SocialIntakeState) {
        val current = _socialIntakeState.value
        if (current.canTransitionTo(next)) {
            _socialIntakeState.value = next
        }
    }

    fun checkAndPrepareSocialLink(url: String): Boolean {
        val platform = SocialMediaDetector.detectPlatform(url) ?: return false
        socialResolveJob?.cancel()
        socialResolveJob = null
        setSocialIntakeState(
            SocialIntakeState.Ready(
                originalUrl = url.trim(),
                platform = platform
            )
        )
        return true
    }

    fun dismissSocialIntake() {
        socialResolveJob?.cancel()
        socialResolveJob = null
        setSocialIntakeState(SocialIntakeState.Idle)
    }

    fun startSocialDownload(url: String, quality: MediaQuality = MediaQuality.MAX) {
        val platform = SocialMediaDetector.detectPlatform(url) ?: return
        socialResolveJob?.cancel()
        setSocialIntakeState(SocialIntakeState.Resolving(url, platform))
        socialResolveJob = scope.launch {
            val result = socialResolver.resolve(url, quality)
            if (!isActive || _socialIntakeState.value !is SocialIntakeState.Resolving) return@launch
            result.onSuccess { asset ->
                setSocialIntakeState(SocialIntakeState.Idle)
                _urlInput.value = asset.streamUrl
                startDownload(asset.streamUrl)
            }.onFailure { err ->
                setSocialIntakeState(
                    SocialIntakeState.Error(
                        url = url,
                        message = err.message ?: "Failed to resolve media stream",
                        platform = platform,
                        attemptedQuality = quality
                    )
                )
            }
        }
    }

    fun enqueueSocialDownload(url: String, quality: MediaQuality = MediaQuality.MAX) {
        val platform = SocialMediaDetector.detectPlatform(url) ?: return
        socialResolveJob?.cancel()
        setSocialIntakeState(SocialIntakeState.Resolving(url, platform))
        socialResolveJob = scope.launch {
            val result = socialResolver.resolve(url, quality)
            if (!isActive || _socialIntakeState.value !is SocialIntakeState.Resolving) return@launch
            result.onSuccess { asset ->
                setSocialIntakeState(SocialIntakeState.Idle)
                enqueueDownload(
                    url = asset.streamUrl,
                    fileName = asset.suggestedFilename,
                    isSequentialMode = !asset.isAudioOnly
                )
            }.onFailure { err ->
                setSocialIntakeState(
                    SocialIntakeState.Error(
                        url = url,
                        message = err.message ?: "Failed to resolve media stream",
                        platform = platform,
                        attemptedQuality = quality
                    )
                )
            }
        }
    }

    fun retrySocialResolution(url: String, quality: MediaQuality = MediaQuality.MAX) {
        startSocialDownload(url, quality)
    }

    private val _batteryGuidanceState = MutableStateFlow(BatteryGuidanceState.NOT_REQUIRED)
    val batteryGuidanceState: StateFlow<BatteryGuidanceState> = _batteryGuidanceState.asStateFlow()

    private val _showBatteryGuidance = MutableStateFlow(false)
    val showBatteryGuidance: StateFlow<Boolean> = _showBatteryGuidance.asStateFlow()

    private fun updateBatteryGuidanceState(next: BatteryGuidanceState) {
        if (_batteryGuidanceState.value.canTransitionTo(next)) {
            _batteryGuidanceState.value = next
            _showBatteryGuidance.value = next.shouldShowBanner
        }
    }

    fun refreshBatteryGuidance() {
        val isAggressive = powerExemptionChecker.isAggressiveOem()
        val isExempt = powerExemptionChecker.isIgnoringBatteryOptimizations()
        val next = when {
            !isAggressive -> BatteryGuidanceState.NOT_REQUIRED
            isExempt -> BatteryGuidanceState.EXEMPTED
            _batteryGuidanceState.value == BatteryGuidanceState.DISMISSED -> BatteryGuidanceState.DISMISSED
            else -> BatteryGuidanceState.ACTION_REQUIRED
        }
        updateBatteryGuidanceState(next)
    }

    fun dismissBatteryGuidance() {
        updateBatteryGuidanceState(BatteryGuidanceState.DISMISSED)
    }

    private val _dialogState = MutableStateFlow<MainDialogState>(MainDialogState.None)
    val dialogState: StateFlow<MainDialogState> = _dialogState.asStateFlow()

    fun openCancelOrDiscardDialog(fileName: String?, isPaused: Boolean) {
        _dialogState.value = MainDialogState.ConfirmCancelOrDiscard(fileName = fileName, isPaused = isPaused)
    }

    fun openBackgroundExitDialog() {
        _dialogState.value = MainDialogState.ConfirmBackgroundExit
    }

    fun dismissDialog() {
        _dialogState.value = MainDialogState.None
    }

    fun confirmCancelOrDiscard() {
        val current = _dialogState.value as? MainDialogState.ConfirmCancelOrDiscard
        discardDownload(current?.fileName)
        _dialogState.value = MainDialogState.None
    }

    val enginePhase: StateFlow<EnginePhase> =
        repository?.enginePhase ?: MutableStateFlow(EnginePhase.IDLE)
    val circuitBreakerSnapshot: StateFlow<CircuitBreakerSnapshot> =
        repository?.circuitBreakerSnapshot ?: MutableStateFlow(CircuitBreakerSnapshot())
    val queuePhase: StateFlow<QueuePhase> =
        repository?.queuePhase ?: MutableStateFlow(QueuePhase.Idle)
    val listenerState: StateFlow<NetworkListenerState> =
        repository?.listenerState ?: MutableStateFlow(NetworkListenerState.STOPPED)
    val cellularKeepAliveState: StateFlow<CellularKeepAliveState> =
        repository?.cellularKeepAliveState ?: MutableStateFlow(CellularKeepAliveState.IDLE)

    val downloadState: StateFlow<DownloadState> = downloadStateOverride ?: repository?.downloadState ?: MutableStateFlow(DownloadState.Idle)
    val isWifiAvailable: StateFlow<Boolean> = repository?.isWifiAvailable ?: MutableStateFlow(false)
    val isCellularAvailable: StateFlow<Boolean> = repository?.isCellularAvailable ?: MutableStateFlow(false)
    val isRoamingActive: StateFlow<Boolean> = repository?.isRoamingActive ?: MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = repository?.isVpnActive ?: MutableStateFlow(false)
    val cellularCarrierName: StateFlow<String?> = repository?.cellularCarrierName ?: MutableStateFlow(null)
    val isModemStandbyDetected: StateFlow<Boolean> = repository?.isModemStandbyDetected ?: MutableStateFlow(false)
    val wifiSignalLevel: StateFlow<Int> = repository?.wifiSignalLevel ?: MutableStateFlow(4)
    val cellularSignalLevel: StateFlow<Int> = repository?.cellularSignalLevel ?: MutableStateFlow(4)
    val cellularGeneration: StateFlow<com.dualboost.downloader.network.CellularGeneration> = repository?.cellularGeneration ?: MutableStateFlow(com.dualboost.downloader.network.CellularGeneration.FIVE_G)
    val wifiDetails: StateFlow<com.dualboost.downloader.network.WifiDetails> = repository?.wifiDetails ?: MutableStateFlow(com.dualboost.downloader.network.WifiDetails.DISCONNECTED)
    val networkTopology: StateFlow<com.dualboost.downloader.network.NetworkTopology> = repository?.networkTopology ?: MutableStateFlow(com.dualboost.downloader.network.NetworkTopology.DISCONNECTED)
    val isDeviceRooted: Boolean = com.dualboost.downloader.security.RootDetector.isDeviceRooted()
    val historyItems: StateFlow<List<DownloadHistoryItem>> = repository?.historyItems ?: MutableStateFlow(emptyList())
    val workerTestStatus: StateFlow<com.dualboost.downloader.data.WorkerTestStatus> = repository?.workerTestStatus ?: MutableStateFlow(com.dualboost.downloader.data.WorkerTestStatus.Idle)

    val concurrencyConfig: StateFlow<com.dualboost.downloader.data.ConcurrencyConfig> =
        repository?.concurrencyConfig ?: MutableStateFlow(com.dualboost.downloader.data.ConcurrencyConfig.DEFAULT)

    fun updateWifiWorkers(count: Int) {
        repository?.updateWifiWorkers(count)
    }

    fun updateCellularWorkers(count: Int) {
        repository?.updateCellularWorkers(count)
    }

    fun toggleAutoScale(enabled: Boolean) {
        repository?.toggleAutoScale(enabled)
    }

    fun resetConcurrencyToDefault() {
        repository?.resetConcurrencyToDefault()
    }

    val streamPalette: StateFlow<com.dualboost.downloader.data.StreamPalette> =
        repository?.streamPalette ?: MutableStateFlow(com.dualboost.downloader.data.StreamPalette.DEFAULT)

    fun updateStreamPalette(palette: com.dualboost.downloader.data.StreamPalette) {
        repository?.updateStreamPalette(palette)
    }

    fun resetStreamPaletteToDefault() {
        repository?.resetStreamPaletteToDefault()
    }

    val storageDestination: StateFlow<com.dualboost.downloader.data.StorageDestination> =
        repository?.storageDestination ?: MutableStateFlow(com.dualboost.downloader.data.StorageDestination.PRESET_DEFAULT)

    fun updateStorageDestination(destination: com.dualboost.downloader.data.StorageDestination) {
        repository?.setStorageDestination(destination)
    }

    fun resetStorageDestinationToDefault() {
        repository?.resetStorageDestinationToDefault()
    }

    val chunkConfig: StateFlow<com.dualboost.downloader.data.ChunkConfig> =
        repository?.chunkConfig ?: MutableStateFlow(com.dualboost.downloader.data.ChunkConfig())

    fun updateManualChunkSize(sizeBytes: Long) {
        repository?.updateManualChunkSize(sizeBytes)
    }

    fun toggleAutoAdaptive(enabled: Boolean) {
        repository?.toggleAutoAdaptive(enabled)
    }

    fun resetChunkConfigToDefault() {
        repository?.resetChunkConfigToDefault()
    }

    val userAgentConfig: StateFlow<com.dualboost.downloader.data.UserAgentConfig> =
        repository?.userAgentConfig ?: MutableStateFlow(com.dualboost.downloader.data.UserAgentConfig.DEFAULT)

    fun updateUserAgentPreset(preset: com.dualboost.downloader.data.UserAgentPreset) {
        repository?.updateUserAgentPreset(preset)
    }

    fun setCustomUserAgent(customUa: String) {
        repository?.setCustomUserAgent(customUa)
    }

    fun resetUserAgentToDefault() {
        repository?.resetUserAgentToDefault()
    }

    val timeoutConfig: StateFlow<com.dualboost.downloader.data.TimeoutConfig> =
        repository?.timeoutConfig ?: MutableStateFlow(com.dualboost.downloader.data.TimeoutConfig.DEFAULT)

    fun updateTimeoutProfile(profile: com.dualboost.downloader.data.TimeoutProfile) {
        repository?.updateTimeoutProfile(profile)
    }

    fun updateCustomTimeouts(connectSec: Int, readSec: Int, maxRetries: Int, retryBackoffMs: Long) {
        repository?.updateCustomTimeouts(connectSec, readSec, maxRetries, retryBackoffMs)
    }

    fun resetTimeoutToDefault() {
        repository?.resetTimeoutToDefault()
    }

    val cellularNetworkConfig: StateFlow<com.dualboost.downloader.data.CellularNetworkConfig> =
        repository?.cellularNetworkConfig ?: MutableStateFlow(com.dualboost.downloader.data.CellularNetworkConfig.DEFAULT)

    fun updateCellularMode(mode: com.dualboost.downloader.data.CellularNetworkMode) {
        repository?.updateCellularMode(mode)
    }

    fun resetCellularModeToDefault() {
        repository?.resetCellularModeToDefault()
    }

    val queueItems: StateFlow<List<com.dualboost.downloader.data.QueueItem>> =
        repository?.queueRepository?.queueItems ?: MutableStateFlow(emptyList())

    val isQueueActive: StateFlow<Boolean> =
        repository?.queueCoordinator?.isQueueActive ?: MutableStateFlow(false)

    val maxConcurrentDownloads: StateFlow<Int> =
        repository?.queueCoordinator?.maxConcurrentDownloads ?: MutableStateFlow(1)

    val detectedMedia: StateFlow<List<com.dualboost.downloader.sniffer.DetectedMediaItem>> =
        repository?.mediaSnifferEngine?.detectedMedia ?: MutableStateFlow(emptyList())

    fun enqueueDownload(
        url: String,
        fileName: String? = null,
        isSequentialMode: Boolean = true
    ) {
        val targetUrl = url.trim()
        if (targetUrl.isBlank()) return
        val cleanName = fileName ?: targetUrl.substringBefore('?').substringAfterLast('/')
            .takeIf { it.isNotBlank() } ?: "download_${System.currentTimeMillis()}"

        val item = com.dualboost.downloader.data.QueueItem(
            url = targetUrl,
            fileName = cleanName,
            isSequentialMode = isSequentialMode
        )
        repository?.queueRepository?.enqueue(item)
    }

    fun removeQueueItem(id: String) {
        repository?.queueRepository?.removeItem(id)
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        repository?.queueRepository?.reorder(fromIndex, toIndex)
    }

    fun clearCompletedQueue() {
        repository?.queueRepository?.clearCompleted()
    }

    fun startQueue() {
        repository?.queueCoordinator?.startQueue()
    }

    fun pauseQueue() {
        repository?.queueCoordinator?.pauseQueue()
    }

    fun setMaxConcurrentDownloads(limit: Int) {
        repository?.queueCoordinator?.setMaxConcurrentDownloads(limit)
    }

    fun inspectBrowserRequest(
        url: String,
        headers: Map<String, String> = emptyMap(),
        mimeType: String? = null
    ): com.dualboost.downloader.sniffer.DetectedMediaItem? {
        return repository?.mediaSnifferEngine?.inspectRequest(url, headers, mimeType)
    }

    fun clearDetectedMedia() {
        repository?.mediaSnifferEngine?.clear()
    }

    fun getSignalLabel(level: Int): String {
        return when (level) {
            4 -> "Excellent • 4/4 bars"
            3 -> "Good • 3/4 bars"
            2 -> "Fair • 2/4 bars"
            1 -> "Weak • 1/4 bar"
            else -> "Connecting..."
        }
    }

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _selectedPreset = MutableStateFlow<QuickTestPreset?>(null)
    val selectedPreset: StateFlow<QuickTestPreset?> = _selectedPreset.asStateFlow()

    private val _clipboardSuggestionState = MutableStateFlow<ClipboardSuggestionState>(ClipboardSuggestionState.Hidden)
    val clipboardSuggestionState: StateFlow<ClipboardSuggestionState> = _clipboardSuggestionState.asStateFlow()

    private val _hasClipboardContent = MutableStateFlow(false)
    val hasClipboardContent: StateFlow<Boolean> = _hasClipboardContent.asStateFlow()

    private val _detectedClipboardUrl = MutableStateFlow<String?>(null)
    val detectedClipboardUrl: StateFlow<String?> = _detectedClipboardUrl.asStateFlow()

    private fun updateClipboardSuggestion(state: ClipboardSuggestionState) {
        _clipboardSuggestionState.value = state
        when (state) {
            is ClipboardSuggestionState.Hidden -> {
                _hasClipboardContent.value = false
                _detectedClipboardUrl.value = null
            }
            is ClipboardSuggestionState.TextAvailable -> {
                _hasClipboardContent.value = true
                _detectedClipboardUrl.value = null
            }
            is ClipboardSuggestionState.UrlDetected -> {
                _hasClipboardContent.value = true
                _detectedClipboardUrl.value = state.url
            }
        }
    }

    fun onClipboardStateChanged(hasText: Boolean) {
        if (_urlInput.value.isBlank() && hasText) {
            val current = _clipboardSuggestionState.value
            if (current !is ClipboardSuggestionState.UrlDetected) {
                updateClipboardSuggestion(ClipboardSuggestionState.TextAvailable)
            }
        } else {
            updateClipboardSuggestion(ClipboardSuggestionState.Hidden)
        }
    }

    fun dismissClipboardSuggestion() {
        updateClipboardSuggestion(ClipboardSuggestionState.Hidden)
    }

    fun onClipboardChecked(url: String?) {
        val trimmed = url?.trim()
        if (!trimmed.isNullOrBlank() && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            if (_urlInput.value.isBlank()) {
                val current = _clipboardSuggestionState.value
                if (current !is ClipboardSuggestionState.UrlDetected || current.url != trimmed) {
                    updateClipboardSuggestion(ClipboardSuggestionState.UrlDetected(trimmed))
                }
            }
        }
    }

    fun acceptClipboardUrl() {
        val detected = (_clipboardSuggestionState.value as? ClipboardSuggestionState.UrlDetected)?.url
            ?: _detectedClipboardUrl.value
        detected?.let {
            onUrlChanged(it)
            updateClipboardSuggestion(ClipboardSuggestionState.Hidden)
        }
    }

    fun dismissClipboardUrl() {
        dismissClipboardSuggestion()
    }

    init {
        if (!powerExemptionChecker.isIgnoringBatteryOptimizations() && powerExemptionChecker.isAggressiveOem()) {
            _batteryGuidanceState.value = BatteryGuidanceState.ACTION_REQUIRED
            _showBatteryGuidance.value = true
        } else if (powerExemptionChecker.isAggressiveOem()) {
            _batteryGuidanceState.value = BatteryGuidanceState.EXEMPTED
        }
        val currentState = downloadState.value
        if (currentState is DownloadState.Paused && currentState.url.isNotBlank()) {
            _urlInput.value = currentState.url
            _selectedPreset.value = DEFAULT_PRESETS.firstOrNull { it.url == currentState.url.trim() }
        }
    }

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
        if (newUrl.isNotBlank()) {
            updateClipboardSuggestion(ClipboardSuggestionState.Hidden)
            checkAndPrepareSocialLink(newUrl)
        }
        _selectedPreset.value = DEFAULT_PRESETS.firstOrNull { it.url == newUrl.trim() }
    }

    fun onPresetSelected(preset: QuickTestPreset) {
        _selectedPreset.value = preset
        _urlInput.value = preset.url
    }

    fun onPresetSelected(presetUrl: String) {
        _urlInput.value = presetUrl
        _selectedPreset.value = DEFAULT_PRESETS.firstOrNull { it.url == presetUrl.trim() }
    }

    fun clearUrl() {
        _urlInput.value = ""
        _selectedPreset.value = null
    }

    private val _sequentialStreamMode = MutableStateFlow(SequentialStreamMode.AUTO)
    val sequentialStreamMode: StateFlow<SequentialStreamMode> = _sequentialStreamMode.asStateFlow()

    private val _isSequentialStreamRequested = MutableStateFlow<Boolean?>(null)
    val isSequentialStreamRequested: StateFlow<Boolean?> = _isSequentialStreamRequested.asStateFlow()

    fun setSequentialStreamMode(mode: SequentialStreamMode) {
        _sequentialStreamMode.value = mode
        _isSequentialStreamRequested.value = mode.legacyValue
    }

    fun setSequentialStreamMode(enabled: Boolean?) {
        setSequentialStreamMode(SequentialStreamMode.fromLegacy(enabled))
    }

    fun resetSequentialStreamToAuto() {
        setSequentialStreamMode(SequentialStreamMode.AUTO)
    }

    open fun startDownload(customUrl: String? = null) {
        val targetUrl = (customUrl ?: _urlInput.value).trim()
        if (targetUrl.isNotBlank()) {
            if (_urlInput.value != targetUrl) {
                _urlInput.value = targetUrl
            }
            repository?.startDownload(targetUrl, forceSequentialMode = _isSequentialStreamRequested.value)
        }
    }

    fun startDownloadWithBypass(customUrl: String? = null) {
        val targetUrl = (customUrl ?: _urlInput.value).trim()
        if (targetUrl.isNotBlank()) {
            if (_urlInput.value != targetUrl) {
                _urlInput.value = targetUrl
            }
            repository?.startDownload(
                url = targetUrl,
                forceSequentialMode = _isSequentialStreamRequested.value,
                allowDangerousBypass = true
            )
        }
    }

    fun retryDownload() {
        val lastState = downloadState.value
        val urlToRetry = if (lastState is DownloadState.Failed && lastState.url.isNotBlank()) {
            lastState.url
        } else {
            _urlInput.value
        }
        if (urlToRetry.isNotBlank()) {
            startDownload(urlToRetry)
        }
    }

    fun resetToIdle() {
        repository?.resetToIdle()
    }

    fun testWorkerConnectivity(rawUrl: String, secretKey: String) {
        viewModelScope.launch {
            repository?.testWorkerConnectivity(rawUrl, secretKey)
        }
    }

    fun resetWorkerTestStatus() {
        repository?.resetWorkerTestStatus()
    }

    fun pauseDownload() {
        repository?.pauseDownload()
    }

    fun discardDownload(fileName: String? = null) {
        repository?.discardDownload(fileName)
    }

    fun isStartButtonEnabled(state: DownloadState, input: String): Boolean {
        return PrimaryActionUiState.resolve(state, input).isEnabled
    }

    fun deleteHistoryItem(item: DownloadHistoryItem, deleteFileFromDisk: Boolean) {
        viewModelScope.launch {
            repository?.deleteHistoryItem(item.id, deleteFileFromDisk)
        }
    }

    fun clearAllHistory(deleteFilesFromDisk: Boolean) {
        viewModelScope.launch {
            repository?.clearAllHistory(deleteFilesFromDisk)
        }
    }

    fun calculateTotalStorageBytes(): Long {
        return repository?.calculateHistoryStorageBytes() ?: 0L
    }

    fun isFilePresent(item: DownloadHistoryItem): Boolean {
        return repository?.isHistoryFilePresent(item) ?: false
    }

    fun openFile(context: Context, item: DownloadHistoryItem): Boolean {
        return try {
            val file = File(item.filePath)
            val uri = if (file.exists()) {
                runCatching {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }.getOrNull()
            } else {
                null
            } ?: item.contentUriString?.let { android.net.Uri.parse(it) } ?: return false

            val ext = item.fileName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val mimeType = if (ext.isNotEmpty()) {
                android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            } else {
                "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareFile(context: Context, item: DownloadHistoryItem): Boolean {
        return try {
            val file = File(item.filePath)
            val uri = if (file.exists()) {
                runCatching {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }.getOrNull()
            } else {
                null
            } ?: item.contentUriString?.let { android.net.Uri.parse(it) } ?: return false

            val ext = item.fileName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val mimeType = if (ext.isNotEmpty()) {
                android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            } else {
                "*/*"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${item.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }
}


