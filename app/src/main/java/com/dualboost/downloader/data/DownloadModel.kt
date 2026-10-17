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
        val wifiSpeedMBps: Double,
        val cellularSpeedMBps: Double,
        val totalSpeedMBps: Double,
        val progressPercentage: Float,
        val supportsRanges: Boolean,
        val isDesktopSpoofed: Boolean = false,
        val isCloudSlicerActive: Boolean = false
    ) : DownloadState() {
        val wifiSpeedMbps: Double get() = wifiSpeedMBps
        val cellularSpeedMbps: Double get() = cellularSpeedMBps
        val totalSpeedMbps: Double get() = totalSpeedMBps
    }
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
        val url: String = "",
        val isDesktopSpoofed: Boolean = false,
        val isCloudSlicerActive: Boolean = false
    ) : DownloadState()
}

/**
 * Formats a speed given in Megabytes per second (MB/s).
 * Adapts to KB/s for speeds below 1.0 MB/s, and defaults to 0.0 MB/s when idle.
 */
fun formatSpeed(speedMBps: Double): String {
    return when {
        speedMBps <= 0.0 -> "0.0 MB/s"
        speedMBps < 1.0 -> {
            val kbps = speedMBps * 1024.0
            String.format(java.util.Locale.US, "%.0f KB/s", kbps)
        }
        else -> String.format(java.util.Locale.US, "%.1f MB/s", speedMBps)
    }
}

/**
 * Returns a pair of (numericValueString, unitString) for speedometer hero displays.
 */
fun formatSpeedComponents(speedMBps: Float): Pair<String, String> {
    return when {
        speedMBps <= 0f -> Pair("0.0", "MB/s")
        speedMBps < 1f -> {
            val kbps = speedMBps * 1024f
            Pair(String.format(java.util.Locale.US, "%.0f", kbps), "KB/s")
        }
        else -> Pair(String.format(java.util.Locale.US, "%.1f", speedMBps), "MB/s")
    }
}


