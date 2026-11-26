package com.dualboost.downloader.engine

import android.util.Log
import com.dualboost.downloader.data.DownloadQueueRepository
import com.dualboost.downloader.data.DownloadState
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
 * Coordinates execution of the Multi-Download Queue using [QueuePhase] as the single source of truth.
 * Automatically claims the next dispatchable item when an active download completes, fails, or is blocked,
 * while preventing stuck states on empty queues, external pauses, or discards.
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

    private var observerJob: Job? = null

    init {
        startObservingDownloadState()
    }

    private fun setQueuePhaseLocked(next: QueuePhase) {
        val current = _queuePhase.value
        if (!current.canTransitionTo(next)) {
            Log.w(TAG, "Blocked invalid QueuePhase transition: $current -> $next")
            return
        }
        _queuePhase.value = next
        _isQueueActive.value = next.isRunning
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
            val currentActiveId = _queuePhase.value.currentActiveItemId ?: return
            val items = queueRepository.queueItems.value
            if (items.none { it.id == currentActiveId }) {
                setQueuePhaseLocked(QueuePhase.Idle)
                return
            }

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
                    advanceOrFinishLocked()
                }
                is DownloadState.Failed -> {
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.FAILED, errorMessage = state.error)
                    advanceOrFinishLocked()
                }
                is DownloadState.ThreatDetected -> {
                    val threatTypeName = state.threat.threatType?.name ?: "UNKNOWN"
                    val threatMsg = "[VIRUS BLOCKED: $threatTypeName] ${state.threat.description}"
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.THREAT_DETECTED, errorMessage = threatMsg)
                    advanceOrFinishLocked()
                }
                is DownloadState.Paused -> {
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.PAUSED)
                    setQueuePhaseLocked(QueuePhase.Paused)
                }
                is DownloadState.Idle -> {
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.PAUSED)
                    setQueuePhaseLocked(QueuePhase.Paused)
                }
                else -> { /* Probing, AutoRecovering handled seamlessly */ }
            }
        }
    }

    private suspend fun advanceOrFinishLocked() {
        if (!_queuePhase.value.isRunning) {
            setQueuePhaseLocked(QueuePhase.Idle)
            return
        }
        _queuePhase.value = QueuePhase.Running(activeItemId = null)
        val dispatched = dispatchNextQueuedItemLocked()
        if (!dispatched) {
            setQueuePhaseLocked(QueuePhase.Idle)
        }
    }

    fun startQueue() {
        scope.launch {
            mutex.withLock {
                if (_queuePhase.value is QueuePhase.Running && _queuePhase.value.currentActiveItemId != null) {
                    return@withLock
                }
                setQueuePhaseLocked(QueuePhase.Running(activeItemId = null))
                val dispatched = dispatchNextQueuedItemLocked()
                if (!dispatched) {
                    setQueuePhaseLocked(QueuePhase.Idle)
                }
            }
        }
    }

    fun pauseQueue() {
        scope.launch {
            mutex.withLock {
                val currentActiveId = _queuePhase.value.currentActiveItemId
                setQueuePhaseLocked(QueuePhase.Paused)
                if (currentActiveId != null) {
                    dispatcher.pauseDownload()
                    queueRepository.updateStatusSync(currentActiveId, QueueItemStatus.PAUSED)
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
        val phase = _queuePhase.value
        if (phase !is QueuePhase.Running) return false
        if (phase.activeItemId != null) return true

        val items = queueRepository.queueItems.value
        val nextItem = items.firstOrNull { it.status == QueueItemStatus.QUEUED }
            ?: items.firstOrNull { it.status == QueueItemStatus.PAUSED }
            ?: return false

        _queuePhase.value = QueuePhase.Running(activeItemId = nextItem.id)
        _isQueueActive.value = true
        queueRepository.updateStatusSync(nextItem.id, QueueItemStatus.DOWNLOADING)
        dispatcher.startDownload(nextItem.url, forceSequentialMode = nextItem.isSequentialMode)
        return true
    }

    companion object {
        private const val TAG = "QueueCoordinator"
    }
}
