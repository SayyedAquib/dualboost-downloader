package com.dualboost.downloader.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualboost.downloader.DualBoostApp
import com.dualboost.downloader.data.DownloadHistoryItem
import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

open class MainViewModel(
    val repository: DownloadRepository? = runCatching { DualBoostApp.instance.repository }.getOrNull(),
    downloadStateOverride: StateFlow<DownloadState>? = null
) : ViewModel() {

    val downloadState: StateFlow<DownloadState> = downloadStateOverride ?: repository?.downloadState ?: MutableStateFlow(DownloadState.Idle)
    val speedTestState: StateFlow<com.dualboost.downloader.data.SpeedTestState> = repository?.speedTestState ?: MutableStateFlow(com.dualboost.downloader.data.SpeedTestState.Idle)
    val isWifiAvailable: StateFlow<Boolean> = repository?.isWifiAvailable ?: MutableStateFlow(false)
    val isCellularAvailable: StateFlow<Boolean> = repository?.isCellularAvailable ?: MutableStateFlow(false)
    val wifiSignalLevel: StateFlow<Int> = repository?.wifiSignalLevel ?: MutableStateFlow(4)
    val cellularSignalLevel: StateFlow<Int> = repository?.cellularSignalLevel ?: MutableStateFlow(4)
    val cellularGeneration: StateFlow<com.dualboost.downloader.network.CellularGeneration> = repository?.cellularGeneration ?: MutableStateFlow(com.dualboost.downloader.network.CellularGeneration.FIVE_G)
    val isDeviceRooted: Boolean = com.dualboost.downloader.security.RootDetector.isDeviceRooted()
    val historyItems: StateFlow<List<DownloadHistoryItem>> = repository?.historyItems ?: MutableStateFlow(emptyList())

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

    private val _hasClipboardContent = MutableStateFlow(false)
    val hasClipboardContent: StateFlow<Boolean> = _hasClipboardContent.asStateFlow()

    private val _detectedClipboardUrl = MutableStateFlow<String?>(null)
    val detectedClipboardUrl: StateFlow<String?> = _detectedClipboardUrl.asStateFlow()

    fun onClipboardStateChanged(hasText: Boolean) {
        if (_urlInput.value.isBlank()) {
            _hasClipboardContent.value = hasText
        } else {
            _hasClipboardContent.value = false
        }
    }

    fun dismissClipboardSuggestion() {
        _hasClipboardContent.value = false
        _detectedClipboardUrl.value = null
    }

    fun onClipboardChecked(url: String?) {
        val trimmed = url?.trim()
        if (!trimmed.isNullOrBlank() && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            if (_urlInput.value.isBlank() && _detectedClipboardUrl.value != trimmed) {
                _detectedClipboardUrl.value = trimmed
                _hasClipboardContent.value = true
            }
        }
    }

    fun acceptClipboardUrl() {
        _detectedClipboardUrl.value?.let {
            onUrlChanged(it)
            _detectedClipboardUrl.value = null
            _hasClipboardContent.value = false
        }
    }

    fun dismissClipboardUrl() {
        dismissClipboardSuggestion()
    }

    init {
        val currentState = downloadState.value
        if (currentState is DownloadState.Paused && currentState.url.isNotBlank()) {
            _urlInput.value = currentState.url
            _selectedPreset.value = DEFAULT_PRESETS.firstOrNull { it.url == currentState.url.trim() }
        }
    }

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
        if (newUrl.isNotBlank()) {
            _hasClipboardContent.value = false
            _detectedClipboardUrl.value = null
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

    fun startDownload(customUrl: String? = null) {
        val targetUrl = (customUrl ?: _urlInput.value).trim()
        if (targetUrl.isNotBlank()) {
            if (_urlInput.value != targetUrl) {
                _urlInput.value = targetUrl
            }
            repository?.startDownload(targetUrl)
        }
    }

    fun pauseDownload() {
        repository?.pauseDownload()
    }

    fun discardDownload(fileName: String? = null) {
        repository?.discardDownload(fileName)
    }

    fun isStartButtonEnabled(state: DownloadState, input: String): Boolean {
        return input.isNotBlank() && state !is DownloadState.Downloading && state !is DownloadState.Probing
    }

    fun startSpeedTest() {
        speedTestEngine?.startSpeedTest() ?: repository?.startSpeedTest()
    }

    fun stopSpeedTest() {
        speedTestEngine?.stopSpeedTest() ?: repository?.stopSpeedTest()
    }

    private val speedTestEngine get() = repository?.speedTestEngine

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
            if (!file.exists()) return false

            val uri = runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }.getOrNull() ?: item.contentUriString?.let { android.net.Uri.parse(it) } ?: return false

            val ext = file.extension.lowercase(java.util.Locale.ROOT)
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

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
            if (!file.exists()) return false

            val uri = runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }.getOrNull() ?: item.contentUriString?.let { android.net.Uri.parse(it) } ?: return false

            val ext = file.extension.lowercase(java.util.Locale.ROOT)
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }
}


