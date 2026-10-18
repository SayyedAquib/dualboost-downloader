package com.dualboost.downloader.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
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
 * Thread-safe, lightweight repository managing completed download records.
 * Uses atomic JSON serialization in app-private sandbox (context.noBackupFilesDir)
 * with zero external dependencies.
 */
class DownloadHistoryRepository(
    private val context: Context,
    private val historyFileOverride: File? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val writeMutex = Mutex()

    internal val historyFile: File by lazy {
        historyFileOverride ?: File(context.noBackupFilesDir ?: context.filesDir, HISTORY_FILE_NAME)
    }

    private val _historyItems = MutableStateFlow<List<DownloadHistoryItem>>(emptyList())
    val historyItems: StateFlow<List<DownloadHistoryItem>> = _historyItems.asStateFlow()

    init {
        loadHistory()
    }

    /**
     * Loads saved history items from disk.
     */
    fun loadHistory() {
        try {
            if (!historyFile.exists()) {
                _historyItems.value = emptyList()
                return
            }
            val content = historyFile.readText()
            if (content.isBlank()) {
                _historyItems.value = emptyList()
                return
            }
            val items = DownloadHistoryItem.parseList(content)
            _historyItems.value = items.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load download history: ${e.message}", e)
            _historyItems.value = emptyList()
        }
    }

    /**
     * Records a completed download. Updates in-memory StateFlow immediately,
     * then commits asynchronously to disk.
     */
    fun recordDownload(item: DownloadHistoryItem) {
        val currentList = _historyItems.value.toMutableList()
        // Remove duplicate entry if it matches same file path or ID
        currentList.removeAll { it.filePath == item.filePath || it.id == item.id }
        currentList.add(0, item)

        // Cap maximum history size to 200 entries to prevent boundless growth
        if (currentList.size > MAX_HISTORY_ITEMS) {
            currentList.subList(MAX_HISTORY_ITEMS, currentList.size).clear()
        }
        _historyItems.value = currentList

        scope.launch {
            writeMutex.withLock {
                withContext(ioDispatcher) {
                    saveToDiskLocked(_historyItems.value)
                }
            }
        }
    }

    /**
     * Synchronous variant for tests and shutdown paths.
     */
    suspend fun recordDownloadSync(item: DownloadHistoryItem) {
        writeMutex.withLock {
            withContext(ioDispatcher) {
                val currentList = _historyItems.value.toMutableList()
                currentList.removeAll { it.filePath == item.filePath || it.id == item.id }
                currentList.add(0, item)
                if (currentList.size > MAX_HISTORY_ITEMS) {
                    currentList.subList(MAX_HISTORY_ITEMS, currentList.size).clear()
                }
                _historyItems.value = currentList
                saveToDiskLocked(currentList)
            }
        }
    }

    /**
     * Deletes a history entry by ID.
     * @param itemId The unique ID of the download record.
     * @param deleteFileFromDisk If true, also permanently deletes the physical file from storage.
     */
    suspend fun deleteItem(itemId: String, deleteFileFromDisk: Boolean): Boolean {
        return writeMutex.withLock {
            withContext(ioDispatcher) {
                val currentList = _historyItems.value.toMutableList()
                val targetIndex = currentList.indexOfFirst { it.id == itemId }
                if (targetIndex == -1) return@withContext false

                val targetItem = currentList[targetIndex]

                if (deleteFileFromDisk) {
                    deletePhysicalFile(targetItem)
                }

                currentList.removeAt(targetIndex)
                saveToDiskLocked(currentList)
                _historyItems.value = currentList
                true
            }
        }
    }

    /**
     * Clears all history entries.
     * @param deleteFilesFromDisk If true, permanently deletes all associated physical files.
     */
    suspend fun clearAll(deleteFilesFromDisk: Boolean) {
        writeMutex.withLock {
            withContext(ioDispatcher) {
                val currentList = _historyItems.value
                if (deleteFilesFromDisk) {
                    for (item in currentList) {
                        deletePhysicalFile(item)
                    }
                }
                saveToDiskLocked(emptyList())
                _historyItems.value = emptyList()
            }
        }
    }

    /**
     * Calculates total storage bytes consumed by active files in history that currently exist on disk.
     */
    fun calculateTotalStorageBytes(): Long {
        return _historyItems.value.fold(0L) { acc, item ->
            val file = File(item.filePath)
            if (file.exists()) acc + file.length() else acc
        }
    }

    /**
     * Checks if the physical file for a given history item exists on disk.
     */
    fun isFilePresent(item: DownloadHistoryItem): Boolean {
        val file = File(item.filePath)
        return file.exists() && file.length() > 0
    }

    private fun deletePhysicalFile(item: DownloadHistoryItem) {
        try {
            val file = File(item.filePath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted physical file ${file.name}: $deleted")
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        null,
                        null
                    )
                } catch (_: Exception) {}
            }

            // Attempt to remove via MediaStore ContentResolver if URI is present
            item.contentUriString?.let { uriStr ->
                try {
                    val uri = Uri.parse(uriStr)
                    context.contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete physical file ${item.filePath}: ${e.message}")
        }
    }

    private fun saveToDiskLocked(items: List<DownloadHistoryItem>) {
        try {
            val jsonText = DownloadHistoryItem.serializeList(items)
            val parentDir = historyFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }

            // Atomic file write using .tmp rename
            val tempFile = File(parentDir, "${historyFile.name}.tmp")
            tempFile.writeText(jsonText)
            if (historyFile.exists()) {
                historyFile.delete()
            }
            tempFile.renameTo(historyFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist download history to disk: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "DownloadHistoryRepo"
        const val HISTORY_FILE_NAME = "download_history.json"
        const val MAX_HISTORY_ITEMS = 200
    }
}
