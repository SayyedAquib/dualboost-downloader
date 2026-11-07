package com.dualboost.downloader.sniffer

import java.util.UUID

/**
 * Represents a media stream or direct file intercepted by the in-app browser sniffer.
 */
data class DetectedMediaItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val extension: String,
    val mimeType: String? = null,
    val contentLength: Long? = null,
    val detectedTimestamp: Long = System.currentTimeMillis()
)
