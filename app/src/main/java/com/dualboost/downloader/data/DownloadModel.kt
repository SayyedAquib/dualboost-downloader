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
    FAILED;

    fun canTransitionTo(next: ChunkStatus): Boolean {
        if (this == next) return true
        return when (this) {
            PENDING -> next == DOWNLOADING
            DOWNLOADING -> next == COMPLETED || next == PENDING || next == FAILED
            COMPLETED -> next == PENDING // Controlled batch rollback on connection drop
            FAILED -> next == PENDING || next == DOWNLOADING
        }
    }
}

data class DownloadChunk(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    @Volatile var status: ChunkStatus = ChunkStatus.PENDING,
    @Volatile var assignedNetwork: NetworkInterfaceType = NetworkInterfaceType.DEFAULT
) {
    val downloadedBytesAtomic = java.util.concurrent.atomic.AtomicLong(0L)

    var downloadedBytes: Long
        get() = downloadedBytesAtomic.get()
        set(value) { downloadedBytesAtomic.set(value) }

    constructor(
        index: Int,
        startByte: Long,
        endByte: Long,
        downloadedBytes: Long,
        status: ChunkStatus = ChunkStatus.PENDING,
        assignedNetwork: NetworkInterfaceType = NetworkInterfaceType.DEFAULT
    ) : this(index, startByte, endByte, status, assignedNetwork) {
        this.downloadedBytes = downloadedBytes
    }

    val totalBytes: Long get() = (endByte - startByte) + 1

    /**
     * Atomically guards and executes valid state transitions for this chunk.
     */
    fun transitionTo(newStatus: ChunkStatus, network: NetworkInterfaceType? = null): Boolean {
        if (!status.canTransitionTo(newStatus)) {
            return false
        }
        status = newStatus
        if (network != null) {
            assignedNetwork = network
        }
        return true
    }
}

data class ChunkSnapshot(
    val index: Int,
    val progress: Float, // 0.0f .. 1.0f
    val networkType: NetworkInterfaceType,
    val status: ChunkStatus
)

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
        val isCloudSlicerActive: Boolean = false,
        val chunkSnapshots: List<ChunkSnapshot> = emptyList(),
        val estimatedSecondsRemaining: Long? = null,
        val resumedBytes: Long = 0L,
        val isSequentialMode: Boolean = false,
        val contiguousPlayableBytes: Long = 0L,
        val isPlayable: Boolean = false,
        val filePath: String = "",
        val contentUriString: String? = null,
        val chunkSize: Long = 4 * 1024 * 1024L,
        val isCustomUserAgent: Boolean = false,
        val timeoutBadge: String = ""
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
        val contentUriString: String? = null,
        val sha256Checksum: String? = null,
        val averageSpeedMBps: Double = 0.0,
        val isDualChannel: Boolean = true
    ) : DownloadState()
    data class AutoRecovering(val message: String, val retryAttempt: Int) : DownloadState()
    data class Failed(val error: String, val url: String = "") : DownloadState()
    data class ThreatDetected(
        val threat: com.dualboost.downloader.security.ThreatAssessment,
        val url: String,
        val suggestedFilename: String
    ) : DownloadState()
    data class Paused(
        val fileName: String = "",
        val filePath: String = "",
        val totalBytes: Long = 0L,
        val downloadedBytes: Long = 0L,
        val progressPercentage: Float = 0f,
        val url: String = "",
        val isDesktopSpoofed: Boolean = false,
        val isCloudSlicerActive: Boolean = false,
        val isCustomUserAgent: Boolean = false,
        val timeoutBadge: String = ""
    ) : DownloadState()
}

/**
 * Validates explicit state transitions between [DownloadState] instances.
 * Prevents race condition clobbering across asynchronous coroutines.
 */
object DownloadStateValidator {
    fun isValidTransition(from: DownloadState, to: DownloadState): Boolean {
        if (from::class == to::class) return true
        return when (from) {
            is DownloadState.Idle -> to is DownloadState.Probing || to is DownloadState.Downloading || to is DownloadState.Paused || to is DownloadState.Failed || to is DownloadState.ThreatDetected
            is DownloadState.Probing -> to is DownloadState.Downloading || to is DownloadState.ThreatDetected || to is DownloadState.Failed || to is DownloadState.Paused || to is DownloadState.Idle
            is DownloadState.Downloading -> to is DownloadState.Completed || to is DownloadState.Paused || to is DownloadState.Failed || to is DownloadState.AutoRecovering || to is DownloadState.ThreatDetected || to is DownloadState.Idle
            is DownloadState.AutoRecovering -> to is DownloadState.Downloading || to is DownloadState.Paused || to is DownloadState.Failed || to is DownloadState.Idle
            is DownloadState.Paused -> to is DownloadState.Probing || to is DownloadState.Downloading || to is DownloadState.Idle || to is DownloadState.Failed
            is DownloadState.Completed -> to is DownloadState.Idle || to is DownloadState.Probing || to is DownloadState.Downloading
            is DownloadState.Failed -> to is DownloadState.Idle || to is DownloadState.Probing || to is DownloadState.Downloading
            is DownloadState.ThreatDetected -> to is DownloadState.Idle || to is DownloadState.Probing || to is DownloadState.Downloading
        }
    }
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

/**
 * Formats seconds remaining into a clean human-readable countdown string (e.g. "45s", "1m 20s", "1h 5m").
 */
fun formatEta(seconds: Long): String {
    return when {
        seconds <= 0L -> "< 1s"
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> {
            val minutes = seconds / 60
            val remainingSecs = seconds % 60
            if (remainingSecs == 0L) "${minutes}m" else "${minutes}m ${remainingSecs}s"
        }
        else -> {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            "${hours}h ${minutes}m"
        }
    }
}


