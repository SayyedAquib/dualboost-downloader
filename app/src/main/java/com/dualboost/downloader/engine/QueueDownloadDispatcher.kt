package com.dualboost.downloader.engine

import com.dualboost.downloader.data.DownloadState
import kotlinx.coroutines.flow.StateFlow

/**
 * Seam abstraction for starting and pausing downloads from the QueueCoordinator.
 * Allows pure unit testing without tying the queue loop to Android ForegroundService or OkHttp.
 */
interface QueueDownloadDispatcher {
    fun startDownload(url: String, forceSequentialMode: Boolean? = true)
    fun pauseDownload()
    val downloadState: StateFlow<DownloadState>
}
