package com.dualboost.downloader.engine

/**
 * Explicit stages of the multi-phase Range & capability probing pipeline.
 */
enum class ProbeStage {
    /** Stage 1a: Fast HTTP HEAD probe using active User-Agent. */
    INITIAL_HEAD,

    /** Stage 1b: Fallback HTTP GET Range: bytes=0-0 probe. */
    INITIAL_GET_RANGE,

    /** Stage 2: Windows 11 Chrome Desktop spoofing probe to bypass mobile restrictions. */
    DESKTOP_SPOOF,

    /** Stage 3: Serverless Personal Cloud Slicer proxy probe. */
    CLOUD_SLICER,

    /** Capability probe successfully resolved byte ranges and size. */
    RESOLVED_DUAL_STREAM,

    /** Remote server does not support byte ranges; single-stream fallback required. */
    FALLBACK_SINGLE_STREAM
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
