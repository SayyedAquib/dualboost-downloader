package com.dualboost.downloader.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe, atomic repository managing the Multi-Download Queue.
 * Persists queued items to download_queue.json across app restarts and reboots.
 */
class DownloadQueueRepository(
    private val context: Context? = null,
    private val queueFileOverride: File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val writeMutex = Mutex()

    internal val queueFile: File by lazy {
        queueFileOverride ?: run {
            val ctx = context ?: throw IllegalStateException("Context required when queueFileOverride is null")
            File(ctx.noBackupFilesDir ?: ctx.filesDir, QUEUE_FILE_NAME)
        }
    }

    private val _queueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val queueItems: StateFlow<List<QueueItem>> = _queueItems.asStateFlow()

    init {
        loadQueue()
    }

    fun loadQueue() {
        try {
            if (!queueFile.exists()) {
                _queueItems.value = emptyList()
                return
            }
            val content = queueFile.readText()
            if (content.isBlank()) {
                _queueItems.value = emptyList()
                return
            }
            val items = QueueItem.parseList(content)
            _queueItems.value = items
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load download queue: ${e.message}", e)
            _queueItems.value = emptyList()
        }
    }

    suspend fun enqueueSync(item: QueueItem) {
        writeMutex.withLock {
            val current = _queueItems.value.toMutableList()
            // Avoid exact duplicate URLs in queued state
            val existingIndex = current.indexOfFirst { it.url == item.url && it.status == QueueItemStatus.QUEUED }
            if (existingIndex != -1) {
                current[existingIndex] = item
            } else {
                current.add(item)
            }
            _queueItems.value = current
            saveToDiskLocked(current)
        }
    }

    fun enqueue(item: QueueItem) {
        scope.launch {
            enqueueSync(item)
        }
    }

    suspend fun updateStatusSync(id: String, status: QueueItemStatus, errorMessage: String? = null) {
        writeMutex.withLock {
            val current = _queueItems.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index != -1) {
                val old = current[index]
                current[index] = old.copy(status = status, errorMessage = errorMessage ?: old.errorMessage)
                _queueItems.value = current
                saveToDiskLocked(current)
            }
        }
    }

    fun updateStatus(id: String, status: QueueItemStatus, errorMessage: String? = null) {
        scope.launch {
            updateStatusSync(id, status, errorMessage)
        }
    }

    suspend fun updateProgressSync(id: String, downloadedBytes: Long, totalBytes: Long) {
        writeMutex.withLock {
            val current = _queueItems.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index != -1) {
                val old = current[index]
                val pct = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                current[index] = old.copy(
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    progressPercentage = pct
                )
                _queueItems.value = current
                // Save progress periodically or keep in-memory
            }
        }
    }

    fun updateProgress(id: String, downloadedBytes: Long, totalBytes: Long) {
        scope.launch {
            updateProgressSync(id, downloadedBytes, totalBytes)
        }
    }

    suspend fun removeItemSync(id: String) {
        writeMutex.withLock {
            val current = _queueItems.value.toMutableList()
            val removed = current.removeAll { it.id == id }
            if (removed) {
                _queueItems.value = current
                saveToDiskLocked(current)
            }
        }
    }

    fun removeItem(id: String) {
        scope.launch {
            removeItemSync(id)
        }
    }

    suspend fun reorderSync(fromIndex: Int, toIndex: Int) {
        writeMutex.withLock {
            val current = _queueItems.value.toMutableList()
            if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                _queueItems.value = current
                saveToDiskLocked(current)
            }
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        scope.launch {
            reorderSync(fromIndex, toIndex)
        }
    }

    suspend fun clearCompletedSync() {
        writeMutex.withLock {
            val current = _queueItems.value.toMutableList()
            val removed = current.removeAll { it.status == QueueItemStatus.COMPLETED }
            if (removed) {
                _queueItems.value = current
                saveToDiskLocked(current)
            }
        }
    }

    fun clearCompleted() {
        scope.launch {
            clearCompletedSync()
        }
    }

    suspend fun clearAllSync() {
        writeMutex.withLock {
            _queueItems.value = emptyList()
            saveToDiskLocked(emptyList())
        }
    }

    fun clearAll() {
        scope.launch {
            clearAllSync()
        }
    }

    private fun saveToDiskLocked(items: List<QueueItem>) {
        try {
            val jsonText = QueueItem.serializeList(items)
            val parentDir = queueFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            // Atomic file write using POSIX rename
            val tempFile = File(parentDir, "${queueFile.name}.tmp")
            tempFile.writeText(jsonText)
            val renamed = tempFile.renameTo(queueFile)
            if (!renamed) {
                if (queueFile.exists()) queueFile.delete()
                val retry = tempFile.renameTo(queueFile)
                if (!retry) {
                    tempFile.copyTo(queueFile, overwrite = true)
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist download queue to disk: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "DownloadQueueRepo"
        const val QUEUE_FILE_NAME = "download_queue.json"
    }
}
