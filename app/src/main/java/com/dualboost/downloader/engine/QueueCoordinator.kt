package com.dualboost.downloader.engine

import android.util.Log
import com.dualboost.downloader.data.DownloadQueueRepository
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.QueueItem
import com.dualboost.downloader.data.QueueItemStatus
import com.dualboost.downloader.data.QueuePhase
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

    private val _queuePhase = MutableStateFlow<QueuePhase>(QueuePhase.Idle)
    val queuePhase: StateFlow<QueuePhase> = _queuePhase.asStateFlow()

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
                        val dispatched = dispatchNextQueuedItemLocked()
                        if (!dispatched) {
                            _queuePhase.value = QueuePhase.Idle
                            _isQueueActive.value = false
                        }
                    } else {
                        _queuePhase.value = QueuePhase.Idle
                    }
                }
                is DownloadState.Failed -> {
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.FAILED, errorMessage = state.error)
                    activeItemId = null
                    if (_isQueueActive.value) {
                        val dispatched = dispatchNextQueuedItemLocked()
                        if (!dispatched) {
                            _queuePhase.value = QueuePhase.Idle
                            _isQueueActive.value = false
                        }
                    } else {
                        _queuePhase.value = QueuePhase.Idle
                    }
                }
                is DownloadState.ThreatDetected -> {
                    val threatTypeName = state.threat.threatType?.name ?: "UNKNOWN"
                    val threatMsg = "[VIRUS BLOCKED: $threatTypeName] ${state.threat.description}"
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.THREAT_DETECTED, errorMessage = threatMsg)
                    activeItemId = null
                    if (_isQueueActive.value) {
                        val dispatched = dispatchNextQueuedItemLocked()
                        if (!dispatched) {
                            _queuePhase.value = QueuePhase.Idle
                            _isQueueActive.value = false
                        }
                    } else {
                        _queuePhase.value = QueuePhase.Idle
                    }
                }
                is DownloadState.Paused -> {
                    if (!_isQueueActive.value) {
                        queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.PAUSED)
                        _queuePhase.value = QueuePhase.Paused
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
                _queuePhase.value = QueuePhase.Running(activeItemId)
                dispatchNextQueuedItemLocked()
            }
        }
    }

    fun pauseQueue() {
        scope.launch {
            mutex.withLock {
                _isQueueActive.value = false
                _queuePhase.value = QueuePhase.Paused
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

    private suspend fun dispatchNextQueuedItemLocked(): Boolean {
        if (!_isQueueActive.value) return false
        if (activeItemId != null) return true

        val items = queueRepository.queueItems.value
        val nextItem = items.firstOrNull { it.status == QueueItemStatus.QUEUED } ?: return false

        activeItemId = nextItem.id
        _queuePhase.value = QueuePhase.Running(activeItemId)
        queueRepository.updateStatusSync(nextItem.id, QueueItemStatus.DOWNLOADING)
        dispatcher.startDownload(nextItem.url, forceSequentialMode = nextItem.isSequentialMode)
        return true
    }

    companion object {
        private const val TAG = "QueueCoordinator"
    }
}
