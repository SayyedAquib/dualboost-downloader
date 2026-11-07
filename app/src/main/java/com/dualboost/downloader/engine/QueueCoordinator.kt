package com.dualboost.downloader.engine

import android.util.Log
import com.dualboost.downloader.data.DownloadQueueRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.QueueItem
import com.dualboost.downloader.data.QueueItemStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates execution of the Multi-Download Queue.
 * Automatically claims the next QUEUED item when an active download completes or fails,
 * while managing concurrency limits (1 or 2 active transfers) and pause/resume lifecycle.
 */
class QueueCoordinator(
    private val queueRepository: DownloadQueueRepository,
    private val dispatcher: QueueDownloadDispatcher,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()

    private val _isQueueActive = MutableStateFlow(false)
    val isQueueActive: StateFlow<Boolean> = _isQueueActive.asStateFlow()

    private val _maxConcurrentDownloads = MutableStateFlow(1)
    val maxConcurrentDownloads: StateFlow<Int> = _maxConcurrentDownloads.asStateFlow()

    private var activeItemId: String? = null
    private var observerJob: Job? = null

    init {
        startObservingDownloadState()
    }

    private fun startObservingDownloadState() {
        observerJob?.cancel()
        observerJob = scope.launch {
            dispatcher.downloadState.collectLatest { state ->
                handleDownloadStateChange(state)
            }
        }
    }

    private suspend fun handleDownloadStateChange(state: DownloadState) {
        mutex.withLock {
            val currentActiveId = activeItemId ?: return
            val items = queueRepository.queueItems.value
            val activeItem = items.firstOrNull { it.id == currentActiveId } ?: return

            when (state) {
                is DownloadState.Downloading -> {
                    queueRepository.updateProgressSync(
                        id = currentActiveId,
                        downloadedBytes = state.downloadedBytes,
                        totalBytes = state.totalBytes
                    )
                }
                is DownloadState.Completed -> {
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.COMPLETED)
                    activeItemId = null
                    if (_isQueueActive.value) {
                        dispatchNextQueuedItemLocked()
                    }
                }
                is DownloadState.Failed -> {
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.FAILED, errorMessage = state.error)
                    activeItemId = null
                    if (_isQueueActive.value) {
                        dispatchNextQueuedItemLocked()
                    }
                }
                is DownloadState.ThreatDetected -> {
                    val threatTypeName = state.threat.threatType?.name ?: "UNKNOWN"
                    val threatMsg = "[VIRUS BLOCKED: $threatTypeName] ${state.threat.description}"
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.THREAT_DETECTED, errorMessage = threatMsg)
                    activeItemId = null
                    if (_isQueueActive.value) {
                        dispatchNextQueuedItemLocked()
                    }
                }
                is DownloadState.Paused -> {
                    if (!_isQueueActive.value) {
                        queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.PAUSED)
                    }
                }
                else -> { /* Probing, Idle, AutoRecovering handled seamlessly */ }
            }
        }
    }

    fun startQueue() {
        scope.launch {
            mutex.withLock {
                _isQueueActive.value = true
                dispatchNextQueuedItemLocked()
            }
        }
    }

    fun pauseQueue() {
        scope.launch {
            mutex.withLock {
                _isQueueActive.value = false
                val currentActiveId = activeItemId
                if (currentActiveId != null) {
                    dispatcher.pauseDownload()
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.PAUSED)
                    activeItemId = null
                }
            }
        }
    }

    fun setMaxConcurrentDownloads(limit: Int) {
        _maxConcurrentDownloads.value = limit.coerceIn(1, 2)
    }

    fun stop() {
        observerJob?.cancel()
        observerJob = null
    }

    private suspend fun dispatchNextQueuedItemLocked() {
        if (!_isQueueActive.value) return
        if (activeItemId != null) return

        val items = queueRepository.queueItems.value
        val nextItem = items.firstOrNull { it.status == QueueItemStatus.QUEUED } ?: return

        activeItemId = nextItem.id
        queueRepository.updateStatusSync(nextItem.id, QueueItemStatus.DOWNLOADING)
        dispatcher.startDownload(nextItem.url, forceSequentialMode = nextItem.isSequentialMode)
    }

    companion object {
        private const val TAG = "QueueCoordinator"
    }
}
