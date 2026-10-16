package com.dualboost.downloader.ui

import androidx.lifecycle.ViewModel
import com.dualboost.downloader.DualBoostApp
import com.dualboost.downloader.data.DownloadRepository
import com.dualboost.downloader.data.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        id = "google_150mb",
        label = "150 MB",
        sizeLabel = "150 MB",
        description = "Google Edge CDN (Full Aggregation Stress Test)",
        url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
        supportsRange = true
    ),
    QuickTestPreset(
        id = "cf_100mb",
        label = "100 MB (Cloudflare)",
        sizeLabel = "100 MB",
        description = "Cloudflare Edge Dynamic Stream (Single-Channel)",
        url = "https://speed.cloudflare.com/__down?bytes=104857600",
        supportsRange = false
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

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _selectedPreset = MutableStateFlow<QuickTestPreset?>(null)
    val selectedPreset: StateFlow<QuickTestPreset?> = _selectedPreset.asStateFlow()

    init {
        val currentState = downloadState.value
        if (currentState is DownloadState.Paused && currentState.url.isNotBlank()) {
            _urlInput.value = currentState.url
            _selectedPreset.value = DEFAULT_PRESETS.firstOrNull { it.url == currentState.url.trim() }
        }
    }

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
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
        repository?.startSpeedTest()
    }

    fun stopSpeedTest() {
        repository?.stopSpeedTest()
    }
}


