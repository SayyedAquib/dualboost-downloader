package com.dualboost.downloader.data

/**
 * Domain model representing dynamic chunk sizing strategy and parameters (ADR-045).
 * Supports Auto-Adaptive payload & speed scaling alongside manual overrides from 1 MB to 64 MB.
 */
class ChunkConfig(
    val isAutoAdaptive: Boolean = true,
    manualChunkSizeBytes: Long = DEFAULT_CHUNK_SIZE
) {
    val manualChunkSizeBytes: Long = manualChunkSizeBytes.coerceIn(MIN_CHUNK_SIZE, MAX_CHUNK_SIZE)

    /**
     * Resolves the optimal chunk size in bytes based on file size and connection capabilities.
     */
    fun resolveChunkSize(
        contentLength: Long,
        isWifi7OrGigabit: Boolean = false,
        isWeakOrSpotty: Boolean = false
    ): Long {
        if (!isAutoAdaptive) {
            return manualChunkSizeBytes
        }

        if (contentLength <= 0) {
            return DEFAULT_CHUNK_SIZE
        }

        val baseSize: Long = when {
            contentLength < 8 * MB -> 1 * MB
            contentLength < 16 * MB -> 2 * MB
            contentLength < 200 * MB -> 4 * MB
            contentLength < 1000 * MB -> 8 * MB
            contentLength < 4000 * MB -> 16 * MB
            else -> 32 * MB
        }

        val networkAdjusted: Long = when {
            isWeakOrSpotty -> minOf(baseSize, 2 * MB)
            isWifi7OrGigabit -> {
                when {
                    contentLength >= 2048 * MB -> 64 * MB
                    contentLength >= 500 * MB -> 16 * MB
                    contentLength >= 100 * MB -> 8 * MB
                    else -> baseSize
                }
            }
            else -> baseSize
        }

        return networkAdjusted.coerceIn(MIN_CHUNK_SIZE, MAX_CHUNK_SIZE)
    }

    fun copy(
        isAutoAdaptive: Boolean = this.isAutoAdaptive,
        manualChunkSizeBytes: Long = this.manualChunkSizeBytes
    ): ChunkConfig = ChunkConfig(isAutoAdaptive, manualChunkSizeBytes)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkConfig) return false
        return isAutoAdaptive == other.isAutoAdaptive && manualChunkSizeBytes == other.manualChunkSizeBytes
    }

    override fun hashCode(): Int {
        var result = isAutoAdaptive.hashCode()
        result = 31 * result + manualChunkSizeBytes.hashCode()
        return result
    }

    override fun toString(): String =
        "ChunkConfig(isAutoAdaptive=$isAutoAdaptive, manualChunkSizeBytes=$manualChunkSizeBytes)"

    companion object {
        const val MB: Long = 1024 * 1024L
        const val MIN_CHUNK_SIZE: Long = 1 * MB
        const val MAX_CHUNK_SIZE: Long = 64 * MB
        const val DEFAULT_CHUNK_SIZE: Long = 4 * MB

        const val MIN_CHUNK_SIZE_BYTES: Long = MIN_CHUNK_SIZE
        const val MAX_CHUNK_SIZE_BYTES: Long = MAX_CHUNK_SIZE
        const val DEFAULT_CHUNK_SIZE_BYTES: Long = DEFAULT_CHUNK_SIZE

        val SUPPORTED_TIERS: List<Long> = listOf(
            1 * MB,
            2 * MB,
            4 * MB,
            8 * MB,
            16 * MB,
            32 * MB,
            64 * MB
        )

        fun formatChunkSizeLabel(bytes: Long): String {
            val mb = bytes / MB
            return "$mb MB"
        }
    }
}
