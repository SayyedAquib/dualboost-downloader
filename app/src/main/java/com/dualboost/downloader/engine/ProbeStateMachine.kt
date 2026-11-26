package com.dualboost.downloader.engine

/**
 * Explicit stages of the multi-phase Range & capability probing pipeline.
 */
enum class ProbeStage(
    val displayMessage: String,
    val isTerminal: Boolean = false
) {
    /** Stage 1a: Fast HTTP HEAD probe using active User-Agent. */
    INITIAL_HEAD("Connecting and probing server capabilities..."),

    /** Stage 1b: Fallback HTTP GET Range: bytes=0-0 probe. */
    INITIAL_GET_RANGE("Testing byte-range support..."),

    /** Stage 2: Windows 11 Chrome Desktop spoofing probe to bypass mobile restrictions. */
    DESKTOP_SPOOF("Adapting desktop mode for multi-channel support..."),

    /** Stage 3: Serverless Personal Cloud Slicer proxy probe. */
    CLOUD_SLICER("Connecting to Personal Cloud Slicer..."),

    /** Capability probe successfully resolved byte ranges and size. */
    RESOLVED_DUAL_STREAM("Multi-channel byte ranges verified", isTerminal = true),

    /** Remote server does not support byte ranges; single-stream fallback required. */
    FALLBACK_SINGLE_STREAM("Server lacks byte-range support; using single-stream mode", isTerminal = true),

    /** Probe aborted early due to pause or discard. */
    ABORTED("Probe cancelled", isTerminal = true);

    fun canTransitionTo(next: ProbeStage): Boolean {
        if (this == next) return true
        if (this.isTerminal) return false
        return when (this) {
            INITIAL_HEAD -> next == INITIAL_GET_RANGE ||
                next == DESKTOP_SPOOF ||
                next == CLOUD_SLICER ||
                next == RESOLVED_DUAL_STREAM ||
                next == FALLBACK_SINGLE_STREAM ||
                next == ABORTED
            INITIAL_GET_RANGE -> next == DESKTOP_SPOOF ||
                next == CLOUD_SLICER ||
                next == RESOLVED_DUAL_STREAM ||
                next == FALLBACK_SINGLE_STREAM ||
                next == ABORTED
            DESKTOP_SPOOF -> next == CLOUD_SLICER ||
                next == RESOLVED_DUAL_STREAM ||
                next == FALLBACK_SINGLE_STREAM ||
                next == ABORTED
            CLOUD_SLICER -> next == RESOLVED_DUAL_STREAM ||
                next == FALLBACK_SINGLE_STREAM ||
                next == ABORTED
            RESOLVED_DUAL_STREAM,
            FALLBACK_SINGLE_STREAM,
            ABORTED -> false
        }
    }
}

/**
 * Pure transition coordinator for the multi-stage probe pipeline.
 * Separates stage-selection state transitions from network I/O side effects.
 */
object ProbeStateMachine {

    fun nextAfterHead(
        supportsRanges: Boolean,
        contentLength: Long,
        forceDesktop: Boolean,
        isCloudSlicerActive: Boolean,
        hasSlicerConfigured: Boolean,
        isExecuting: Boolean
    ): ProbeStage {
        if (!isExecuting) return ProbeStage.ABORTED
        if (supportsRanges && contentLength > 0) return ProbeStage.RESOLVED_DUAL_STREAM
        if (contentLength <= 0 || !supportsRanges) return ProbeStage.INITIAL_GET_RANGE
        return nextAfterGetRange(
            supportsRanges = supportsRanges,
            contentLength = contentLength,
            forceDesktop = forceDesktop,
            isCloudSlicerActive = isCloudSlicerActive,
            hasSlicerConfigured = hasSlicerConfigured,
            isExecuting = isExecuting
        )
    }

    fun nextAfterGetRange(
        supportsRanges: Boolean,
        contentLength: Long,
        forceDesktop: Boolean,
        isCloudSlicerActive: Boolean,
        hasSlicerConfigured: Boolean,
        isExecuting: Boolean
    ): ProbeStage {
        if (!isExecuting) return ProbeStage.ABORTED
        if (supportsRanges && contentLength > 0) return ProbeStage.RESOLVED_DUAL_STREAM
        if (!forceDesktop) return ProbeStage.DESKTOP_SPOOF
        if (!isCloudSlicerActive && hasSlicerConfigured) return ProbeStage.CLOUD_SLICER
        return ProbeStage.FALLBACK_SINGLE_STREAM
    }

    fun nextAfterDesktopSpoof(
        supportsRanges: Boolean,
        contentLength: Long,
        isCloudSlicerActive: Boolean,
        hasSlicerConfigured: Boolean,
        isExecuting: Boolean
    ): ProbeStage {
        if (!isExecuting) return ProbeStage.ABORTED
        if (supportsRanges && contentLength > 0) return ProbeStage.RESOLVED_DUAL_STREAM
        if (!isCloudSlicerActive && hasSlicerConfigured) return ProbeStage.CLOUD_SLICER
        return ProbeStage.FALLBACK_SINGLE_STREAM
    }

    fun nextAfterCloudSlicer(
        supportsRanges: Boolean,
        contentLength: Long,
        isExecuting: Boolean
    ): ProbeStage {
        if (!isExecuting) return ProbeStage.ABORTED
        return if (supportsRanges && contentLength > 0) {
            ProbeStage.RESOLVED_DUAL_STREAM
        } else {
            ProbeStage.FALLBACK_SINGLE_STREAM
        }
    }
}

/**
 * Result of executing server capability probing.
 */
data class ProbeResult(
    val stage: ProbeStage,
    val supportsRanges: Boolean,
    val contentLength: Long,
    val etag: String?,
    val lastModified: String?,
    val fileName: String,
    val isDesktopSpoofed: Boolean,
    val isCloudSlicerActive: Boolean,
    val effectiveUrl: String,
    val effectiveUserAgent: String
)
