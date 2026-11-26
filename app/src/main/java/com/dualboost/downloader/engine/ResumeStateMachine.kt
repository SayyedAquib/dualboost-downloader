package com.dualboost.downloader.engine

/**
 * Explicit reason codes for invalidating an interrupted download's resume metadata.
 */
enum class ResumeInvalidationReason(val description: String) {
    NO_META("No sidecar metadata found"),
    PHYSICAL_FILE_MISSING("Target file does not exist on disk"),
    SIZE_MISMATCH("Physical file length does not match server Content-Length"),
    URL_MISMATCH("Metadata URL does not match target URL"),
    ETAG_CHANGED("Server ETag validator changed or disappeared"),
    LAST_MODIFIED_CHANGED("Server Last-Modified validator changed or disappeared")
}

/**
 * Explicit resumption decision state machine for [DualDownloaderEngine].
 * Replaces scattered boolean flags (`canResume`, `etagMismatch`, `lastModifiedMismatch`)
 * with an exhaustive sealed evaluation result.
 */
sealed class ResumeDecision {
    object FreshStart : ResumeDecision()

    data class Resumable(
        val meta: DualDownloaderEngine.MetaInfo
    ) : ResumeDecision()

    data class Invalidated(
        val reason: ResumeInvalidationReason,
        val shouldCleanArtifacts: Boolean
    ) : ResumeDecision()

    val canResume: Boolean
        get() = this is Resumable

    companion object {
        fun evaluate(
            url: String,
            contentLength: Long,
            etag: String?,
            lastModified: String?,
            physicalFileExisted: Boolean,
            physicalFileLength: Long,
            existingMeta: DualDownloaderEngine.MetaInfo?
        ): ResumeDecision {
            if (existingMeta == null) return FreshStart

            if (!physicalFileExisted) {
                return Invalidated(
                    reason = ResumeInvalidationReason.PHYSICAL_FILE_MISSING,
                    shouldCleanArtifacts = true
                )
            }
            if (physicalFileLength != contentLength || existingMeta.totalLength != contentLength) {
                return Invalidated(
                    reason = ResumeInvalidationReason.SIZE_MISMATCH,
                    shouldCleanArtifacts = true
                )
            }
            if (existingMeta.url != url) {
                return Invalidated(
                    reason = ResumeInvalidationReason.URL_MISMATCH,
                    shouldCleanArtifacts = true
                )
            }
            val etagMismatch = existingMeta.etag != null && (etag == null || existingMeta.etag != etag)
            if (etagMismatch) {
                return Invalidated(
                    reason = ResumeInvalidationReason.ETAG_CHANGED,
                    shouldCleanArtifacts = true
                )
            }
            val lastModifiedMismatch = existingMeta.lastModified != null && (lastModified == null || existingMeta.lastModified != lastModified)
            if (lastModifiedMismatch) {
                return Invalidated(
                    reason = ResumeInvalidationReason.LAST_MODIFIED_CHANGED,
                    shouldCleanArtifacts = true
                )
            }
            return Resumable(meta = existingMeta)
        }
    }
}
