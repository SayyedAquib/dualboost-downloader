package com.dualboost.downloader.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.dualboost.downloader.data.ChunkSnapshot
import com.dualboost.downloader.data.ChunkStatus
import com.dualboost.downloader.data.DownloadChunk
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.data.NetworkInterfaceType
import com.dualboost.downloader.data.SlicerPreferences
import com.dualboost.downloader.network.NetworkProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

class DualDownloaderEngine(
    private val context: Context,
    private val networkManager: NetworkProvider,
    historyRepositoryOverride: com.dualboost.downloader.data.DownloadHistoryRepository? = null
) {
    val historyRepository: com.dualboost.downloader.data.DownloadHistoryRepository by lazy {
        historyRepositoryOverride ?: com.dualboost.downloader.data.DownloadHistoryRepository(context)
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active calls tracking to guarantee instantaneous cancellation upon Pause / Cancel
    private val activeCalls = ConcurrentHashMap.newKeySet<Call>()
    private val isPaused = AtomicBoolean(false)
    private val isDiscarding = AtomicBoolean(false)
    internal val activeCallsCount: Int get() = activeCalls.size

    // Active download tracking for seamless resume and discard
    private var currentOutputFile: File? = null
    private var currentUrl: String? = null

    private fun registerActiveCall(call: Call): Boolean {
        if (isPaused.get() || downloadJob?.isActive != true) {
            try { call.cancel() } catch (_: Exception) {}
            return false
        }
        activeCalls.add(call)
        if (isPaused.get() || downloadJob?.isActive != true) {
            try { call.cancel() } catch (_: Exception) {}
            activeCalls.remove(call)
            return false
        }
        return true
    }

    private val CHUNK_SIZE = 4 * 1024 * 1024L // 4 MB chunks

    internal fun isDirWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val testFile = File(dir, ".dualboost_perm_${System.currentTimeMillis()}.tmp")
            if (testFile.createNewFile()) {
                testFile.delete()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun getDefaultDownloadDir(): File {
        val publicDir = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (_: Throwable) {
            null
        }
        return if (publicDir != null && (publicDir.exists() || publicDir.mkdirs()) && isDirWritable(publicDir)) {
            publicDir
        } else {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!appDir.exists()) appDir.mkdirs()
            appDir
        }
    }

    internal fun resolveWritableOutputFile(targetDir: File, initialFileName: String, url: String): File {
        val safeDir = if (isDirWritable(targetDir)) {
            targetDir
        } else {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!appDir.exists()) appDir.mkdirs()
            appDir
        }

        val safeBaseCanonical = safeDir.canonicalPath

        fun findAvailableFile(dir: File): File {
            var candidateName = initialFileName
            var file = File(dir, candidateName)
            val metaFile = getMetaFile(dir, candidateName)
            val existingMeta = readMetaInfo(metaFile)

            // If file exists and matches our own interrupted download for this URL, check if writable and reuse!
            if (file.exists() && existingMeta != null && existingMeta.url == url) {
                try {
                    RandomAccessFile(file, "rw").use { }
                    return file
                } catch (_: Throwable) {
                    // If existing file is locked, fall through to generate unique name
                }
            }

            // If file exists or cannot be opened in "rw" mode, generate non-conflicting unique name: "name (1).ext"
            if (file.exists()) {
                val baseName = initialFileName.substringBeforeLast('.')
                val extension = initialFileName.substringAfterLast('.', "")
                val extSuffix = if (extension.isNotEmpty() && extension != initialFileName) ".$extension" else ""
                // SEC-AUDIT H-5: Cap filename counter to prevent unbounded filesystem scan DoS
                var counter = 1
                while (file.exists() && counter <= 999) {
                    candidateName = "$baseName ($counter)$extSuffix"
                    file = File(dir, candidateName)
                    counter++
                }
                if (file.exists()) {
                    candidateName = "${baseName}_${System.currentTimeMillis()}$extSuffix"
                    file = File(dir, candidateName)
                }
            }
            return file
        }

        var candidate = findAvailableFile(safeDir)

        // Strict canonical path boundary check: prevent path traversal out of safeDir
        try {
            if (!candidate.canonicalPath.startsWith(safeBaseCanonical)) {
                val safeFallbackName = "download_${System.currentTimeMillis()}.bin"
                candidate = File(safeDir, safeFallbackName)
            }
        } catch (_: Exception) {
            val safeFallbackName = "download_${System.currentTimeMillis()}.bin"
            candidate = File(safeDir, safeFallbackName)
        }

        // Verify write access to candidate file
        val canWrite = try {
            if (!candidate.exists()) {
                if (candidate.createNewFile()) {
                    candidate.delete()
                    true
                } else {
                    false
                }
            } else {
                RandomAccessFile(candidate, "rw").use { }
                true
            }
        } catch (_: Throwable) {
            false
        }

        if (!canWrite) {
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            candidate = findAvailableFile(fallbackDir)
            try {
                if (!candidate.canonicalPath.startsWith(fallbackDir.canonicalPath)) {
                    val safeFallbackName = "download_${System.currentTimeMillis()}.bin"
                    candidate = File(fallbackDir, safeFallbackName)
                }
            } catch (_: Exception) {
                val safeFallbackName = "download_${System.currentTimeMillis()}.bin"
                candidate = File(fallbackDir, safeFallbackName)
            }
        }

        return candidate
    }

    internal fun getInternalMetaDir(): File {
        val base = context.noBackupFilesDir ?: context.filesDir
        val dir = File(base, "dualboost_meta")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    internal fun getMetaFile(targetDir: File, fileName: String): File {
        val internalDir = getInternalMetaDir()
        val privateMeta = File(internalDir, "$fileName.dualboost_meta")
        val legacyMeta = File(targetDir, "$fileName.dualboost_meta")
        return if (!privateMeta.exists() && legacyMeta.exists()) {
            legacyMeta
        } else {
            privateMeta
        }
    }

    data class MetaInfo(
        val url: String,
        val totalLength: Long,
        val completedChunks: Set<Int>,
        val etag: String? = null,
        val lastModified: String? = null,
        val isDesktopSpoofed: Boolean = false,
        val isCloudSlicerActive: Boolean = false
    )

    internal fun readMetaInfo(metaFile: File): MetaInfo? {
        if (!metaFile.exists()) return null
        return try {
            val lines = metaFile.readLines()
            val metaMap = lines.mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap()

            val savedUrl = metaMap["url"] ?: return null
            val savedTotal = metaMap["totalLength"]?.toLongOrNull() ?: return null
            val chunksStr = metaMap["completedChunks"] ?: ""
            val completed = if (chunksStr.isNotBlank()) {
                chunksStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            } else emptySet()

            val isDesktop = metaMap["isDesktopSpoofed"]?.toBoolean() ?: false
            val isSlicer = metaMap["isCloudSlicerActive"]?.toBoolean() ?: false

            MetaInfo(
                url = savedUrl,
                totalLength = savedTotal,
                completedChunks = completed,
                etag = metaMap["etag"],
                lastModified = metaMap["lastModified"],
                isDesktopSpoofed = isDesktop,
                isCloudSlicerActive = isSlicer
            )
        } catch (_: Exception) {
            null
        }
    }

    internal fun readCompletedChunks(metaFile: File, url: String, totalBytes: Long): Set<Int> {
        val info = readMetaInfo(metaFile) ?: return emptySet()
        return if (info.url == url && info.totalLength == totalBytes) info.completedChunks else emptySet()
    }

    internal fun saveCompletedChunks(
        metaFile: File,
        url: String,
        totalBytes: Long,
        completedIndices: Set<Int>,
        etag: String? = null,
        lastModified: String? = null,
        isDesktopSpoofed: Boolean = false,
        isCloudSlicerActive: Boolean = false
    ) {
        try {
            val content = buildString {
                appendLine("url=$url")
                appendLine("totalLength=$totalBytes")
                if (!etag.isNullOrBlank()) appendLine("etag=$etag")
                if (!lastModified.isNullOrBlank()) appendLine("lastModified=$lastModified")
                appendLine("isDesktopSpoofed=$isDesktopSpoofed")
                appendLine("isCloudSlicerActive=$isCloudSlicerActive")
                appendLine("completedChunks=${completedIndices.sorted().joinToString(",")}")
                appendLine("updatedAt=${System.currentTimeMillis()}")
            }
            val parent = metaFile.parentFile ?: return
            val tempFile = File(parent, "${metaFile.name}.tmp")
            tempFile.writeText(content)
            tempFile.renameTo(metaFile)
        } catch (_: Exception) {}
    }

    fun restoreIncompleteDownload(targetDir: File = getDefaultDownloadDir()): Boolean {
        try {
            val candidateDirs = mutableListOf<File>()
            val internalMetaDir = getInternalMetaDir()
            if (internalMetaDir.exists()) candidateDirs.add(internalMetaDir)
            if (targetDir.exists()) candidateDirs.add(targetDir)
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (appDir != null && appDir.exists() && appDir != targetDir) {
                candidateDirs.add(appDir)
            }
            if (candidateDirs.isEmpty()) return false

            val metaFiles = candidateDirs.flatMap { dir ->
                dir.listFiles { _, name -> name.endsWith(".dualboost_meta") }?.toList() ?: emptyList()
            }
            if (metaFiles.isEmpty()) return false
            val latestMeta = metaFiles.maxByOrNull { it.lastModified() } ?: return false
            val metaInfo = readMetaInfo(latestMeta) ?: return false

            val fileName = latestMeta.name.removeSuffix(".dualboost_meta")
            val targetFile = if (File(targetDir, fileName).exists()) {
                File(targetDir, fileName)
            } else {
                val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (appDir != null && File(appDir, fileName).exists()) {
                    File(appDir, fileName)
                } else {
                    File(targetDir, fileName)
                }
            }
            val totalBytes = metaInfo.totalLength

            val completedBytes = metaInfo.completedChunks.sumOf { chunkIndex ->
                val start = chunkIndex * CHUNK_SIZE
                val end = min(start + CHUNK_SIZE - 1, totalBytes - 1)
                max(0L, (end - start) + 1)
            }
            val progress = if (totalBytes > 0) completedBytes.toFloat() / totalBytes.toFloat() else 0f

            currentOutputFile = targetFile
            currentUrl = metaInfo.url

            _downloadState.value = DownloadState.Paused(
                fileName = fileName,
                filePath = targetFile.absolutePath,
                totalBytes = totalBytes,
                downloadedBytes = completedBytes,
                progressPercentage = progress,
                url = metaInfo.url,
                isDesktopSpoofed = metaInfo.isDesktopSpoofed,
                isCloudSlicerActive = metaInfo.isCloudSlicerActive
            )
            return true
        } catch (_: Exception) {
            return false
        }
    }

    internal fun checkAvailableStorage(targetDir: File, requiredBytes: Long, existingFile: File? = null) {
        if (requiredBytes <= 0) return
        val existingFileSize = if (existingFile != null && existingFile.exists()) existingFile.length() else 0L
        val neededAdditionalBytes = max(0L, requiredBytes - existingFileSize)
        if (neededAdditionalBytes <= 0) return

        val usableBytes = targetDir.usableSpace
        val safetyBuffer = 20 * 1024 * 1024L // 20 MB safety margin
        val isInsufficient = if (usableBytes <= 0) {
            false
        } else if (neededAdditionalBytes > Long.MAX_VALUE - safetyBuffer) {
            true // Overflow prevention: required exceeds maximum possible Long
        } else {
            usableBytes < (neededAdditionalBytes + safetyBuffer)
        }

        if (isInsufficient) {
            throw IOException(
                "Insufficient storage: requires ${formatBytes(neededAdditionalBytes)}, available ${formatBytes(usableBytes)}"
            )
        }
    }

    fun startDownload(url: String, targetDir: File = getDefaultDownloadDir()) {
        val rawTrimmed = url.trim()
        val parsedHttpUrl = runCatching { rawTrimmed.toHttpUrlOrNull() }.getOrNull()
        val host = parsedHttpUrl?.host?.lowercase(Locale.ROOT)
        val isLoopbackOrLocal = host == "localhost" || host == "127.0.0.1" || host == "::1" || host?.endsWith(".local") == true
        val sanitizedUrl = if (rawTrimmed.startsWith("http://", ignoreCase = true) && !isLoopbackOrLocal) {
            rawTrimmed.replaceFirst("http://", "https://", ignoreCase = true)
        } else {
            rawTrimmed
        }

        val previousState = _downloadState.value
        if (previousState is DownloadState.Downloading && currentUrl == sanitizedUrl) return

        isDiscarding.set(false)
        isPaused.set(true)
        downloadJob?.cancel()
        activeCalls.forEach { try { it.cancel() } catch (_: Exception) {} }
        activeCalls.clear()

        isPaused.set(false)
        downloadJob = scope.launch {
            try {
                executeDownload(sanitizedUrl, targetDir)
            } catch (e: CancellationException) {
                if (!isDiscarding.get()) {
                    val lastState = _downloadState.value
                    if (lastState is DownloadState.Downloading) {
                        _downloadState.value = DownloadState.Paused(
                            fileName = lastState.fileName,
                            filePath = currentOutputFile?.absolutePath ?: "",
                            totalBytes = lastState.totalBytes,
                            downloadedBytes = lastState.downloadedBytes,
                            progressPercentage = lastState.progressPercentage,
                            url = currentUrl ?: sanitizedUrl,
                            isDesktopSpoofed = lastState.isDesktopSpoofed,
                            isCloudSlicerActive = lastState.isCloudSlicerActive
                        )
                    } else if (lastState !is DownloadState.Paused && lastState !is DownloadState.Idle) {
                        _downloadState.value = DownloadState.Paused(url = currentUrl ?: sanitizedUrl)
                    }
                    Log.d(TAG, "Download paused/cancelled")
                } else {
                    _downloadState.value = DownloadState.Idle
                    Log.d(TAG, "Download discarded")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                val errorMessage = if (e.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                    "Unencrypted HTTP communication is blocked by security policy. Target server must support HTTPS."
                } else {
                    e.localizedMessage ?: "Unknown error"
                }
                _downloadState.value = DownloadState.Failed(errorMessage)
            }
        }
    }

    fun pauseDownload() {
        val lastState = _downloadState.value
        isPaused.set(true)
        downloadJob?.cancel()
        activeCalls.forEach { call ->
            try {
                call.cancel()
            } catch (_: Exception) {}
        }
        activeCalls.clear()

        if (lastState is DownloadState.Downloading) {
            _downloadState.value = DownloadState.Paused(
                fileName = lastState.fileName,
                filePath = currentOutputFile?.absolutePath ?: "",
                totalBytes = lastState.totalBytes,
                downloadedBytes = lastState.downloadedBytes,
                progressPercentage = lastState.progressPercentage,
                url = currentUrl ?: "",
                isDesktopSpoofed = lastState.isDesktopSpoofed,
                isCloudSlicerActive = lastState.isCloudSlicerActive
            )
        } else if (lastState !is DownloadState.Paused && lastState !is DownloadState.Idle) {
            _downloadState.value = DownloadState.Paused(url = currentUrl ?: "")
        }
    }

    fun discardDownload(targetDir: File = getDefaultDownloadDir(), fileName: String? = null) {
        isDiscarding.set(true)
        isPaused.set(true)
        downloadJob?.cancel()
        activeCalls.forEach { call ->
            try { call.cancel() } catch (_: Exception) {}
        }
        activeCalls.clear()

        val target = currentOutputFile ?: if (fileName != null) File(targetDir, fileName) else null
        try {
            target?.let { file ->
                if (file.exists()) file.delete()
                val meta = getMetaFile(file.parentFile ?: targetDir, file.name)
                if (meta.exists()) meta.delete()
                val tmp = File(meta.parentFile ?: (file.parentFile ?: targetDir), "${meta.name}.tmp")
                if (tmp.exists()) tmp.delete()
                val internalMeta = File(getInternalMetaDir(), "${file.name}.dualboost_meta")
                if (internalMeta.exists()) internalMeta.delete()
                val internalTmp = File(getInternalMetaDir(), "${file.name}.dualboost_meta.tmp")
                if (internalTmp.exists()) internalTmp.delete()
            }
            if (fileName != null) {
                val orphan = File(targetDir, fileName)
                if (orphan.exists()) orphan.delete()
                val meta = getMetaFile(targetDir, fileName)
                if (meta.exists()) meta.delete()
                val tmp = File(meta.parentFile ?: targetDir, "${meta.name}.tmp")
                if (tmp.exists()) tmp.delete()
                val internalMeta = File(getInternalMetaDir(), "$fileName.dualboost_meta")
                if (internalMeta.exists()) internalMeta.delete()
                val internalTmp = File(getInternalMetaDir(), "$fileName.dualboost_meta.tmp")
                if (internalTmp.exists()) internalTmp.delete()
            }
        } catch (_: Exception) {}
        currentOutputFile = null
        currentUrl = null
        _downloadState.value = DownloadState.Idle
    }


    private fun executeProbeWithFallback(request: Request): Response {
        // Try default client first (normally Wi-Fi)
        return try {
            val client = networkManager.getDefaultClient()
            val call = client.newCall(request)
            if (!registerActiveCall(call)) throw CancellationException("Download paused")
            try {
                call.execute()
            } finally {
                activeCalls.remove(call)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Default probe failed (${e.message}), attempting Cellular fallback...")
            val cellularClient = networkManager.getCellularClient()
            if (cellularClient != null) {
                val call = cellularClient.newCall(request)
                if (!registerActiveCall(call)) throw CancellationException("Download paused")
                try {
                    call.execute()
                } finally {
                    activeCalls.remove(call)
                }
            } else {
                throw e
            }
        }
    }

    private suspend fun executeDownload(url: String, targetDir: File) {
        _downloadState.value = DownloadState.Probing("Connecting and probing server capabilities...")

        var contentLength: Long = -1L
        var supportsRanges = false
        var fileName = extractFileName(url)
        var etag: String? = null
        var lastModified: String? = null
        var isDesktopSpoofed = false
        var isCloudSlicerActive = false
        var effectiveUrl = url

        // Check if existing sidecar metadata indicates previous desktop spoofing or cloud slicer
        val initialMetaFile = getMetaFile(targetDir, fileName)
        var existingInitialMeta = readMetaInfo(initialMetaFile)
        if (existingInitialMeta == null) {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (appDir != null && appDir.exists()) {
                existingInitialMeta = readMetaInfo(getMetaFile(appDir, fileName))
            }
        }
        val forceDesktop = existingInitialMeta?.isDesktopSpoofed == true
        if (forceDesktop) {
            isDesktopSpoofed = true
        }
        val forceCloudSlicer = existingInitialMeta?.isCloudSlicerActive == true
        if (forceCloudSlicer) {
            val slicerUrl = SlicerPreferences.getSlicerUrl(context)
            if (slicerUrl.isNotBlank()) {
                val slicerSecret = SlicerPreferences.getSlicerSecret(context)
                effectiveUrl = SlicerPreferences.buildSlicedUrl(slicerUrl, url, slicerSecret)
                isCloudSlicerActive = true
            }
        }

        // --- STAGE 1: Standard Probe (Mobile by default, or Desktop/Slicer if previously engaged) ---
        try {
            val headRequest = if (isCloudSlicerActive) {
                val secret = SlicerPreferences.getSlicerSecret(context)
                Request.Builder().url(effectiveUrl).head().apply {
                    if (secret.isNotBlank()) header("X-Slicer-Key", secret)
                }.build()
            } else {
                buildRequest(effectiveUrl, isDesktop = isDesktopSpoofed).head().build()
            }

            executeProbeWithFallback(headRequest).use { response ->
                if (response.isSuccessful) {
                    contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    val acceptRanges = response.header("Accept-Ranges")
                    supportsRanges = acceptRanges != null && acceptRanges.contains("bytes", ignoreCase = true)
                    response.header("ETag")?.let { etag = it.trim() }
                    response.header("Last-Modified")?.let { lastModified = it.trim() }
                    response.header("Content-Disposition")?.let { disposition ->
                        extractFileNameFromDisposition(disposition)?.let { fileName = it }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD probe failed (${e.message}), falling back to GET range probe")
        }

        // If HEAD didn't yield range or length, test with GET range 0-0
        if (contentLength <= 0 || !supportsRanges) {
            val rangeProbeRequest = if (isCloudSlicerActive) {
                val secret = SlicerPreferences.getSlicerSecret(context)
                Request.Builder().url(effectiveUrl).header("Range", "bytes=0-0").apply {
                    if (secret.isNotBlank()) header("X-Slicer-Key", secret)
                }.build()
            } else {
                buildRequest(effectiveUrl, isDesktop = isDesktopSpoofed)
                    .addHeader("Range", "bytes=0-0")
                    .build()
            }

            try {
                executeProbeWithFallback(rangeProbeRequest).use { response ->
                    if (response.code == 206) {
                        supportsRanges = true
                        val contentRange = response.header("Content-Range")
                        contentRange?.substringAfterLast('/')?.toLongOrNull()?.let {
                            contentLength = it
                        }
                        if (etag == null) response.header("ETag")?.let { etag = it.trim() }
                        if (lastModified == null) response.header("Last-Modified")?.let { lastModified = it.trim() }
                        response.header("Content-Disposition")?.let { disposition ->
                            extractFileNameFromDisposition(disposition)?.let { fileName = it }
                        }
                    } else if (response.code == 200 && contentLength <= 0) {
                        contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                        if (etag == null) response.header("ETag")?.let { etag = it.trim() }
                        if (lastModified == null) response.header("Last-Modified")?.let { lastModified = it.trim() }
                        response.header("Content-Disposition")?.let { disposition ->
                            extractFileNameFromDisposition(disposition)?.let { fileName = it }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Initial range probe failed: ${e.message}")
            }
        }

        // --- STAGE 2: Adaptive Desktop Probe ---
        // If mobile probe did not unlock byte ranges and we didn't already force desktop, retry with Windows Chrome Desktop headers!
        if (!supportsRanges && !forceDesktop) {
            Log.i(TAG, "Server rejected byte ranges on mobile headers. Triggering Stage 2 probe with Windows 11 Desktop spoofing...")
            _downloadState.value = DownloadState.Probing("Adapting desktop mode for multi-channel support...")

            var desktopRanges = false
            var desktopLength = contentLength

            // 1. Try GET Range 0-0 with Desktop headers
            try {
                val desktopRangeReq = buildRequest(url, isDesktop = true)
                    .addHeader("Range", "bytes=0-0")
                    .build()

                executeProbeWithFallback(desktopRangeReq).use { response ->
                    if (response.code == 206) {
                        desktopRanges = true
                        val contentRange = response.header("Content-Range")
                        contentRange?.substringAfterLast('/')?.toLongOrNull()?.let {
                            desktopLength = it
                        }
                        if (etag == null) response.header("ETag")?.let { etag = it.trim() }
                        if (lastModified == null) response.header("Last-Modified")?.let { lastModified = it.trim() }
                        response.header("Content-Disposition")?.let { disposition ->
                            extractFileNameFromDisposition(disposition)?.let { fileName = it }
                        }
                    } else if (response.code == 200 && desktopLength <= 0) {
                        desktopLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stage 2 desktop range probe failed: ${e.message}")
            }

            // 2. Try HEAD with Desktop headers if range didn't succeed
            if (!desktopRanges) {
                try {
                    val desktopHeadReq = buildRequest(url, isDesktop = true).head().build()
                    executeProbeWithFallback(desktopHeadReq).use { response ->
                        if (response.isSuccessful) {
                            val acceptRanges = response.header("Accept-Ranges")
                            val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
                            if (len > 0) desktopLength = len
                            if (acceptRanges != null && acceptRanges.contains("bytes", ignoreCase = true)) {
                                desktopRanges = true
                            }
                            response.header("ETag")?.let { etag = it.trim() }
                            response.header("Last-Modified")?.let { lastModified = it.trim() }
                            response.header("Content-Disposition")?.let { disposition ->
                                extractFileNameFromDisposition(disposition)?.let { fileName = it }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Stage 2 desktop HEAD probe failed: ${e.message}")
                }
            }

            if (desktopRanges && desktopLength > 0) {
                Log.i(TAG, "Desktop Browser Spoofing successfully unlocked HTTP 206 byte ranges! ($desktopLength bytes)")
                supportsRanges = true
                contentLength = desktopLength
                isDesktopSpoofed = true
            }
        }

        // --- STAGE 3: Personal Cloud Slicer Probe ---
        // If server still rejected byte ranges after Desktop spoofing, check if user configured a personal Cloud Slicer worker
        if (!supportsRanges && !isCloudSlicerActive) {
            val slicerUrl = SlicerPreferences.getSlicerUrl(context)
            if (slicerUrl.isNotBlank()) {
                val slicerSecret = SlicerPreferences.getSlicerSecret(context)
                val targetSlicedUrl = SlicerPreferences.buildSlicedUrl(slicerUrl, url, slicerSecret)
                Log.i(TAG, "Server rejected direct byte ranges. Triggering Stage 3 probe via Personal Cloud Slicer: ${sanitizeUrlForLog(slicerUrl)}")
                _downloadState.value = DownloadState.Probing("Connecting to Personal Cloud Slicer...")

                var slicerRanges = false
                var slicerLength = contentLength

                try {
                    val slicerProbeReq = Request.Builder()
                        .url(targetSlicedUrl)
                        .header("Range", "bytes=0-0")
                        .apply {
                            if (slicerSecret.isNotBlank()) {
                                header("X-Slicer-Key", slicerSecret)
                            }
                        }
                        .build()

                    executeProbeWithFallback(slicerProbeReq).use { response ->
                        if (response.code == 206) {
                            slicerRanges = true
                            val contentRange = response.header("Content-Range")
                            contentRange?.substringAfterLast('/')?.toLongOrNull()?.let {
                                if (it > 0) slicerLength = it
                            }
                            if (slicerLength <= 0) {
                                response.header("Content-Length")?.toLongOrNull()?.let {
                                    if (it > 0) slicerLength = it
                                }
                            }
                            if (etag == null) response.header("ETag")?.let { etag = it.trim() }
                            if (lastModified == null) response.header("Last-Modified")?.let { lastModified = it.trim() }
                            response.header("Content-Disposition")?.let { disposition ->
                                extractFileNameFromDisposition(disposition)?.let { fileName = it }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Stage 3 Cloud Slicer probe failed: ${e.message}")
                }

                if (slicerRanges && slicerLength > 0) {
                    Log.i(TAG, "Personal Cloud Slicer successfully unlocked HTTP 206 byte ranges! ($slicerLength bytes)")
                    supportsRanges = true
                    contentLength = slicerLength
                    isCloudSlicerActive = true
                    effectiveUrl = targetSlicedUrl
                }
            }
        }

        val outputFile = resolveWritableOutputFile(targetDir, fileName, url)
        val actualTargetDir = outputFile.parentFile ?: targetDir
        fileName = outputFile.name
        currentOutputFile = outputFile
        currentUrl = url
        val metaFile = getMetaFile(actualTargetDir, fileName)
        val startTime = System.currentTimeMillis()

        if (!supportsRanges || contentLength <= 0) {
            Log.w(TAG, "Server lacks range support or length is unknown. Falling back to single-stream download.")
            executeSingleStreamDownload(url, outputFile, contentLength, isDesktopSpoofed, isCloudSlicerActive)
            return
        }

        Log.i(TAG, "Starting dual-network segmented download: size = $contentLength bytes ($fileName)")

        // Pre-flight disk space validation
        checkAvailableStorage(actualTargetDir, contentLength, outputFile)

        // Pre-allocate or verify file size with RandomAccessFile
        RandomAccessFile(outputFile, "rw").use { raf ->
            if (raf.length() != contentLength) {
                raf.setLength(contentLength)
            }
        }

        // Divide file into chunks
        val chunks = mutableListOf<DownloadChunk>()
        var offset = 0L
        var index = 0
        while (offset < contentLength) {
            val end = min(offset + CHUNK_SIZE - 1, contentLength - 1)
            chunks.add(DownloadChunk(index++, offset, end))
            offset = end + 1
        }

        // Load previously completed chunks from sidecar metadata with ETag validation
        val completedIndices = ConcurrentHashMap.newKeySet<Int>()
        val existingMeta = readMetaInfo(metaFile)

        if (existingMeta != null && existingMeta.url == url && existingMeta.totalLength == contentLength) {
            val etagMismatch = existingMeta.etag != null && etag != null && existingMeta.etag != etag
            val lastModifiedMismatch = existingMeta.lastModified != null && lastModified != null && existingMeta.lastModified != lastModified

            if (etagMismatch || lastModifiedMismatch) {
                Log.w(TAG, "Server file changed since last pause (ETag: $etag vs ${existingMeta.etag}). Restarting clean.")
                try {
                    if (outputFile.exists()) outputFile.delete()
                    if (metaFile.exists()) metaFile.delete()
                } catch (_: Exception) {}
            } else {
                completedIndices.addAll(existingMeta.completedChunks)
            }
        }

        var initialCompletedBytes = 0L
        for (chunk in chunks) {
            if (completedIndices.contains(chunk.index)) {
                chunk.status = ChunkStatus.COMPLETED
                chunk.downloadedBytes = chunk.totalBytes
                initialCompletedBytes += chunk.totalBytes
            }
        }

        val pendingChunks = chunks.toMutableList()
        val queueMutex = Mutex()
        val fileWriteMutex = Mutex()

        val totalDownloadedBytes = AtomicLong(initialCompletedBytes)
        val wifiDownloadedBytes = AtomicLong(initialCompletedBytes)
        val cellularDownloadedBytes = AtomicLong(0L)


        // Interface Circuit-Breaker consecutive failure counters
        val wifiFailures = AtomicInteger(0)
        val cellularFailures = AtomicInteger(0)

        // Speed calculation meters
        var lastWifiBytes = 0L
        var lastCellularBytes = 0L
        var lastSampleTime = System.currentTimeMillis()
        var wifiSpeedMBps = 0.0
        var cellularSpeedMBps = 0.0

        // Use coroutineScope so all workers & monitor are direct children of downloadJob
        coroutineScope {
            // Real-time telemetry monitor
            val monitorJob = launch {
                while (isActive) {
                    delay(500)
                    val now = System.currentTimeMillis()
                    val deltaSec = max((now - lastSampleTime) / 1000.0, 0.001)

                    val currentWifi = wifiDownloadedBytes.get()
                    val currentCell = cellularDownloadedBytes.get()

                    wifiSpeedMBps = (currentWifi - lastWifiBytes).toDouble() / (deltaSec * (1024.0 * 1024.0))
                    cellularSpeedMBps = (currentCell - lastCellularBytes).toDouble() / (deltaSec * (1024.0 * 1024.0))

                    lastWifiBytes = currentWifi
                    lastCellularBytes = currentCell
                    lastSampleTime = now

                    val downloaded = totalDownloadedBytes.get()
                    val progress = if (contentLength > 0) downloaded.toFloat() / contentLength.toFloat() else 0f
                    val totalSpeed = max(0.0, wifiSpeedMBps + cellularSpeedMBps)
                    val etaSeconds = if (contentLength > 0 && totalSpeed > 0.05) {
                        val remainingBytes = max(0L, contentLength - downloaded)
                        (remainingBytes / (totalSpeed * 1024.0 * 1024.0)).toLong()
                    } else {
                        null
                    }

                    val snapshots = chunks.map { c: DownloadChunk ->
                        val cTotal = c.totalBytes.coerceAtLeast(1L)
                        ChunkSnapshot(
                            index = c.index,
                            progress = (c.downloadedBytes.toFloat() / cTotal.toFloat()).coerceIn(0f, 1f),
                            networkType = c.assignedNetwork,
                            status = c.status
                        )
                    }

                    _downloadState.value = DownloadState.Downloading(
                        fileName = fileName,
                        totalBytes = contentLength,
                        downloadedBytes = downloaded,
                        wifiDownloadedBytes = currentWifi,
                        cellularDownloadedBytes = currentCell,
                        wifiSpeedMBps = max(0.0, wifiSpeedMBps),
                        cellularSpeedMBps = max(0.0, cellularSpeedMBps),
                        totalSpeedMBps = totalSpeed,
                        progressPercentage = progress,
                        supportsRanges = true,
                        isDesktopSpoofed = isDesktopSpoofed,
                        isCloudSlicerActive = isCloudSlicerActive,
                        chunkSnapshots = snapshots,
                        estimatedSecondsRemaining = etaSeconds
                    )
                }
            }

            // Resilient Worker coroutine function
            suspend fun runWorker(
                interfaceType: NetworkInterfaceType,
                clientProvider: () -> OkHttpClient?,
                sharedRandomAccessFile: RandomAccessFile
            ) {
                while (isActive) {
                    // 1. Check if the entire file is already completed
                    val allCompleted = queueMutex.withLock {
                        pendingChunks.all { it.status == ChunkStatus.COMPLETED }
                    }
                    if (allCompleted) break

                    // 2. Check interface circuit-breaker
                    val isCircuitBroken = if (interfaceType == NetworkInterfaceType.WIFI) {
                        wifiFailures.get() >= 2
                    } else {
                        cellularFailures.get() >= 2
                    }

                    if (isCircuitBroken) {
                        val otherHealthy = if (interfaceType == NetworkInterfaceType.WIFI) {
                            cellularFailures.get() < 2 && networkManager.isCellularAvailable.value
                        } else {
                            wifiFailures.get() < 2 && networkManager.isWifiAvailable.value
                        }

                        if (otherHealthy) {
                            // Other interface is healthy; back off this interface to avoid holding up chunks
                            delay(1500)
                            continue
                        }
                    }

                    // 3. Acquire next available chunk
                    val chunk: DownloadChunk? = queueMutex.withLock {
                        val available = pendingChunks.firstOrNull { it.status == ChunkStatus.PENDING }
                        available?.apply {
                            status = ChunkStatus.DOWNLOADING
                            assignedNetwork = interfaceType
                        }
                    }

                    if (chunk == null) {
                        // In-flight chunks still downloading on other workers; wait and retry
                        delay(200)
                        continue
                    }

                    // 4. Resolve client for this interface
                    val client = clientProvider() ?: (if (interfaceType == NetworkInterfaceType.CELLULAR) networkManager.getDefaultClient() else null)
                    if (client == null) {
                        queueMutex.withLock {
                            chunk.status = ChunkStatus.PENDING
                            chunk.assignedNetwork = NetworkInterfaceType.DEFAULT
                        }
                        delay(1000)
                        continue
                    }

                    var success = false

                    try {
                        val request = if (isCloudSlicerActive) {
                            val slicerSecret = SlicerPreferences.getSlicerSecret(context)
                            Request.Builder()
                                .url(effectiveUrl)
                                .header("Range", "bytes=${chunk.startByte}-${chunk.endByte}")
                                .apply {
                                    if (slicerSecret.isNotBlank()) {
                                        header("X-Slicer-Key", slicerSecret)
                                    }
                                }
                                .build()
                        } else {
                            buildRequest(url, isDesktop = isDesktopSpoofed)
                                .addHeader("Range", "bytes=${chunk.startByte}-${chunk.endByte}")
                                .build()
                        }

                        val call = client.newCall(request)
                        if (!registerActiveCall(call)) throw CancellationException("Worker paused")

                        try {
                            call.execute().use { response ->
                                if (response.code == 206 || response.code == 200) {
                                    val body = response.body ?: throw IOException("Empty body for chunk ${chunk.index}")
                                    val inputStream = body.byteStream()
                                    val buffer = ByteArray(32 * 1024)
                                    var bytesRead: Int
                                    var chunkOffset = chunk.startByte
                                    val expectedChunkBytes = (chunk.endByte - chunk.startByte) + 1
                                    var chunkBytesReceived = 0L

                                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                        if (!isActive) throw CancellationException("Worker cancelled")

                                        fileWriteMutex.withLock {
                                            sharedRandomAccessFile.seek(chunkOffset)
                                            sharedRandomAccessFile.write(buffer, 0, bytesRead)
                                        }
                                        chunkOffset += bytesRead
                                        chunkBytesReceived += bytesRead
                                        totalDownloadedBytes.addAndGet(bytesRead.toLong())

                                        if (interfaceType == NetworkInterfaceType.WIFI) {
                                            wifiDownloadedBytes.addAndGet(bytesRead.toLong())
                                        } else {
                                            cellularDownloadedBytes.addAndGet(bytesRead.toLong())
                                        }
                                    }

                                    if (chunkBytesReceived == expectedChunkBytes) {
                                        success = true
                                        // Reset circuit breaker on success
                                        if (interfaceType == NetworkInterfaceType.WIFI) {
                                            wifiFailures.set(0)
                                        } else {
                                            cellularFailures.set(0)
                                        }
                                    } else {
                                        // Truncated chunk! Roll back byte counters
                                        totalDownloadedBytes.addAndGet(-chunkBytesReceived)
                                        if (interfaceType == NetworkInterfaceType.WIFI) {
                                            wifiDownloadedBytes.addAndGet(-chunkBytesReceived)
                                        } else {
                                            cellularDownloadedBytes.addAndGet(-chunkBytesReceived)
                                        }
                                        throw IOException("Chunk ${chunk.index} truncated: expected $expectedChunkBytes bytes, got $chunkBytesReceived")
                                    }
                                } else {
                                    throw IOException("HTTP ${response.code} on chunk ${chunk.index}")
                                }
                            }
                        } finally {
                            activeCalls.remove(call)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Chunk ${chunk.index} failed on $interfaceType: ${e.message}")
                        if (interfaceType == NetworkInterfaceType.WIFI) {
                            wifiFailures.incrementAndGet()
                        } else {
                            cellularFailures.incrementAndGet()
                        }
                    }

                    queueMutex.withLock {
                        if (success) {
                            chunk.status = ChunkStatus.COMPLETED
                            completedIndices.add(chunk.index)
                            saveCompletedChunks(metaFile, url, contentLength, completedIndices, etag, lastModified, isDesktopSpoofed, isCloudSlicerActive)
                        } else {
                            // Return chunk to pending queue for immediate re-dispatch
                            chunk.status = ChunkStatus.PENDING
                            chunk.assignedNetwork = NetworkInterfaceType.DEFAULT
                        }
                    }

                    if (!success) {
                        delay(300)
                    }
                }
            }

            // Launch parallel coroutine workers as children of this coroutineScope using a single shared RAF
            val sharedRaf = RandomAccessFile(outputFile, "rw")
            try {
                val workers = listOf(
                    launch { runWorker(NetworkInterfaceType.WIFI, { networkManager.getWifiClient() }, sharedRaf) },
                    launch { runWorker(NetworkInterfaceType.WIFI, { networkManager.getWifiClient() }, sharedRaf) },
                    launch { runWorker(NetworkInterfaceType.CELLULAR, { networkManager.getCellularClient() }, sharedRaf) },
                    launch { runWorker(NetworkInterfaceType.CELLULAR, { networkManager.getCellularClient() }, sharedRaf) }
                )

                workers.joinAll()
            } finally {
                try {
                    sharedRaf.close()
                } catch (_: Exception) {}
            }
            monitorJob.cancel()

            // Verify file integrity
            val allFinished = queueMutex.withLock { pendingChunks.all { it.status == ChunkStatus.COMPLETED } }
            if (!allFinished) {
                throw IOException("Download incomplete: some segments could not be transferred.")
            }
        }

        try {
            if (metaFile.exists()) metaFile.delete()
            val tmp = File(actualTargetDir, "${metaFile.name}.tmp")
            if (tmp.exists()) tmp.delete()
            val internalMeta = File(getInternalMetaDir(), "$fileName.dualboost_meta")
            if (internalMeta.exists()) internalMeta.delete()
            val internalTmp = File(getInternalMetaDir(), "$fileName.dualboost_meta.tmp")
            if (internalTmp.exists()) internalTmp.delete()
            val legacyMeta = File(actualTargetDir, "$fileName.dualboost_meta")
            if (legacyMeta.exists()) legacyMeta.delete()
        } catch (_: Exception) {}

        val sha256 = computeSha256(outputFile)
        val contentUri = exportToMediaStore(outputFile, fileName)
        val elapsedSeconds = max(1L, (System.currentTimeMillis() - startTime) / 1000L)
        val avgSpeed = if (elapsedSeconds > 0) (contentLength.toDouble() / (1024.0 * 1024.0)) / elapsedSeconds else 0.0
        val isDual = cellularDownloadedBytes.get() > 0 && wifiDownloadedBytes.get() > 0

        val completedState = DownloadState.Completed(
            fileName = fileName,
            filePath = outputFile.absolutePath,
            totalBytes = contentLength,
            durationSeconds = elapsedSeconds,
            contentUriString = contentUri?.toString(),
            sha256Checksum = sha256,
            averageSpeedMBps = avgSpeed,
            isDualChannel = isDual
        )
        _downloadState.value = completedState

        try {
            historyRepository.recordDownload(
                com.dualboost.downloader.data.DownloadHistoryItem(
                    fileName = fileName,
                    filePath = outputFile.absolutePath,
                    contentUriString = contentUri?.toString(),
                    fileSize = contentLength,
                    sha256Checksum = sha256,
                    durationSeconds = elapsedSeconds,
                    averageSpeedMBps = avgSpeed,
                    isDualChannel = isDual
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record download in history: ${e.message}")
        }
    }

    private suspend fun executeSingleStreamDownload(
        url: String,
        outputFile: File,
        totalLength: Long,
        isDesktopSpoofed: Boolean = false,
        isCloudSlicerActive: Boolean = false
    ) {
        if (totalLength > 0) {
            checkAvailableStorage(outputFile.parentFile ?: getDefaultDownloadDir(), totalLength, outputFile)
        }

        val client = networkManager.getDefaultClient()
        val request = if (isCloudSlicerActive) {
            val slicerSecret = SlicerPreferences.getSlicerSecret(context)
            Request.Builder()
                .url(url)
                .apply {
                    if (slicerSecret.isNotBlank()) {
                        header("X-Slicer-Key", slicerSecret)
                    }
                }
                .build()
        } else {
            buildRequest(url, isDesktop = isDesktopSpoofed).build()
        }
        val call = client.newCall(request)
        if (!registerActiveCall(call)) throw CancellationException("Download paused")

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Server returned code ${response.code}")
                val body = response.body ?: throw IOException("Empty response body")

                val startTime = System.currentTimeMillis()
                var lastEmitTime = startTime
                var lastBytes = 0L
                var currentSpeedMBps = 0.0

                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    val inputStream = body.byteStream()
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!currentCoroutineContext().isActive) throw CancellationException("Download cancelled")

                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        val isComplete = (totalLength > 0 && totalRead >= totalLength)
                        if (now - lastEmitTime >= 300 || isComplete) {
                            val deltaSec = max((now - lastEmitTime) / 1000.0, 0.001)
                            currentSpeedMBps = max(0.0, (totalRead - lastBytes).toDouble() / (deltaSec * (1024.0 * 1024.0)))
                            lastEmitTime = now
                            lastBytes = totalRead

                            val isWifi = networkManager.isWifiAvailable.value
                            val progress = if (totalLength > 0) totalRead.toFloat() / totalLength.toFloat() else 0f
                            val eta = if (totalLength > 0 && currentSpeedMBps > 0.05) {
                                val rem = max(0L, totalLength - totalRead)
                                (rem / (currentSpeedMBps * 1024.0 * 1024.0)).toLong()
                            } else {
                                null
                            }

                            _downloadState.value = DownloadState.Downloading(
                                fileName = outputFile.name,
                                totalBytes = totalLength,
                                downloadedBytes = totalRead,
                                wifiDownloadedBytes = if (isWifi) totalRead else 0L,
                                cellularDownloadedBytes = if (isWifi) 0L else totalRead,
                                wifiSpeedMBps = if (isWifi) currentSpeedMBps else 0.0,
                                cellularSpeedMBps = if (isWifi) 0.0 else currentSpeedMBps,
                                totalSpeedMBps = currentSpeedMBps,
                                progressPercentage = progress,
                                supportsRanges = false,
                                isDesktopSpoofed = isDesktopSpoofed,
                                isCloudSlicerActive = isCloudSlicerActive,
                                chunkSnapshots = emptyList(),
                                estimatedSecondsRemaining = eta
                            )
                        }
                    }
                }

                val sha256 = computeSha256(outputFile)
                val contentUri = exportToMediaStore(outputFile, outputFile.name)
                val elapsedSeconds = max(1L, (System.currentTimeMillis() - startTime) / 1000L)
                val avgSpeed = if (elapsedSeconds > 0 && totalLength > 0) (totalLength.toDouble() / (1024.0 * 1024.0)) / elapsedSeconds else 0.0

                val completedState = DownloadState.Completed(
                    fileName = outputFile.name,
                    filePath = outputFile.absolutePath,
                    totalBytes = totalLength,
                    durationSeconds = elapsedSeconds,
                    contentUriString = contentUri?.toString(),
                    sha256Checksum = sha256,
                    averageSpeedMBps = avgSpeed,
                    isDualChannel = false
                )
                _downloadState.value = completedState

                try {
                    historyRepository.recordDownload(
                        com.dualboost.downloader.data.DownloadHistoryItem(
                            fileName = outputFile.name,
                            filePath = outputFile.absolutePath,
                            contentUriString = contentUri?.toString(),
                            fileSize = totalLength,
                            sha256Checksum = sha256,
                            durationSeconds = elapsedSeconds,
                            averageSpeedMBps = avgSpeed,
                            isDualChannel = false
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to record single-stream download in history: ${e.message}")
                }
            }
        } finally {
            activeCalls.remove(call)
        }
    }

    internal fun computeSha256(file: File): String? {
        if (!file.exists() || file.length() <= 0) return null
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    internal fun buildRequest(url: String, isDesktop: Boolean = false): Request.Builder {
        val builder = Request.Builder()
            .url(url)

        if (isDesktop) {
            builder
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Sec-CH-UA", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                .header("Sec-CH-UA-Mobile", "?0")
                .header("Sec-CH-UA-Platform", "\"Windows\"")
                .header("Sec-CH-UA-Platform-Version", "\"15.0.0\"")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Upgrade-Insecure-Requests", "1")
        } else {
            builder.header("User-Agent", MOBILE_USER_AGENT)
        }

        builder
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")

        try {
            val parsedUrl = java.net.URL(url)
            val host = parsedUrl.host
            if (!host.isNullOrBlank()) {
                val protocol = parsedUrl.protocol ?: "https"
                builder.header("Referer", "$protocol://$host/")
                builder.header("Origin", "$protocol://$host")
            }
        } catch (_: Exception) {
            // Fallback for non-standard URIs
        }

        return builder
    }

    internal fun extractFileName(url: String): String {
        return try {
            val path = java.net.URL(url).path
            val rawName = path.substringAfterLast('/')
            val baseName = File(rawName).name.substringAfterLast('/').substringAfterLast('\\')
            val clean = baseName.replace(Regex("[^a-zA-Z0-9._\\-\\s()]"), "_").trim()
            if (clean.isBlank() || clean == "." || clean == ".." || clean == "__down") {
                "speedtest_${System.currentTimeMillis()}.bin"
            } else if (!clean.contains('.')) {
                "$clean.bin"
            } else {
                clean
            }
        } catch (_: Exception) {
            "download_${System.currentTimeMillis()}.bin"
        }
    }

    internal fun extractFileNameFromDisposition(disposition: String): String? {
        val filenameTag = "filename="
        val index = disposition.indexOf(filenameTag, ignoreCase = true)
        if (index != -1) {
            var raw = disposition.substring(index + filenameTag.length).trim()
            if (raw.startsWith("\"")) {
                val endQuote = raw.indexOf('"', 1)
                raw = if (endQuote != -1) raw.substring(1, endQuote) else raw.substring(1)
            } else {
                val semi = raw.indexOf(';')
                if (semi != -1) {
                    raw = raw.substring(0, semi).trim()
                }
            }
            // Strip any directory traversal components (both / and \)
            val baseName = File(raw).name.substringAfterLast('/').substringAfterLast('\\')
            // Sanitize illegal filename characters
            val clean = baseName.replace(Regex("[^a-zA-Z0-9._\\-\\s()]"), "_").trim()
            return if (clean.isNotBlank() && clean != "." && clean != "..") clean else null
        }
        return null
    }

    internal fun sanitizeUrlForLog(url: String): String {
        return try {
            val qIndex = url.indexOf('?')
            if (qIndex != -1) url.substring(0, qIndex) else url
        } catch (_: Exception) {
            "[redacted-url]"
        }
    }

    internal fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1000) {
            String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        }
    }


    internal fun exportToMediaStore(sourceFile: File, fileName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val publicDownloads = try {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                } catch (_: Throwable) { null }

                val isInPublicDownloads = publicDownloads != null &&
                    try { sourceFile.canonicalPath.startsWith(publicDownloads.canonicalPath) } catch (_: Exception) { false }

                if (isInPublicDownloads) {
                    val completableFuture = CompletableFuture<Uri?>()
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(sourceFile.absolutePath),
                        arrayOf(getMimeType(fileName))
                    ) { _, uri ->
                        completableFuture.complete(uri)
                    }
                    return try {
                        completableFuture.get(3, TimeUnit.SECONDS)
                    } catch (_: Exception) {
                        null
                    }
                }

                // If in app-specific directory, check if there's enough storage to copy to MediaStore
                val fileSize = sourceFile.length()
                val freeSpace = (publicDownloads ?: context.filesDir).usableSpace
                if (fileSize > 0 && freeSpace < fileSize + 50 * 1024 * 1024L) {
                    Log.w(TAG, "Not enough free space ($freeSpace bytes) to duplicate $fileSize bytes into MediaStore. Retaining in app storage.")
                    return null
                }

                val resolver = context.contentResolver ?: return null
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
                resolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                try {
                    sourceFile.delete()
                } catch (_: Exception) {}

                uri
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore export skipped or failed: ${e.message}")
                null
            }
        }
        return null
    }

    internal fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return if (extension.isNotEmpty()) {
            try {
                MimeTypeMap.getSingleton()?.getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
            } catch (_: Exception) {
                "application/octet-stream"
            }
        } else {
            "application/octet-stream"
        }
    }

    companion object {
        private const val TAG = "DualDownloaderEngine"
        const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.107 Mobile Safari/537.36"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
