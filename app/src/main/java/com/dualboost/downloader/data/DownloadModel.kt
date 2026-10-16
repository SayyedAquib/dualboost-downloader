package com.dualboost.downloader.data

enum class NetworkInterfaceType {
    WIFI,
    CELLULAR,
    DEFAULT
}

enum class ChunkStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class DownloadChunk(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    var downloadedBytes: Long = 0,
    var status: ChunkStatus = ChunkStatus.PENDING,
    var assignedNetwork: NetworkInterfaceType = NetworkInterfaceType.DEFAULT
) {
    val totalBytes: Long get() = (endByte - startByte) + 1
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Probing(val message: String) : DownloadState()
    data class Downloading(
        val fileName: String,
        val totalBytes: Long,
        val downloadedBytes: Long,
        val wifiDownloadedBytes: Long,
        val cellularDownloadedBytes: Long,
        val wifiSpeedMbps: Double,
        val cellularSpeedMbps: Double,
        val totalSpeedMbps: Double,
        val progressPercentage: Float,
        val supportsRanges: Boolean
    ) : DownloadState()
    data class Completed(
        val fileName: String,
        val filePath: String,
        val totalBytes: Long,
        val durationSeconds: Long,
        val contentUriString: String? = null
    ) : DownloadState()
    data class AutoRecovering(val message: String, val retryAttempt: Int) : DownloadState()
    data class Failed(val error: String) : DownloadState()
    data class Paused(
        val fileName: String = "",
        val filePath: String = "",
        val totalBytes: Long = 0L,
        val downloadedBytes: Long = 0L,
        val progressPercentage: Float = 0f,
        val url: String = ""
    ) : DownloadState()
}


