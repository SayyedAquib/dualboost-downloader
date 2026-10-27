package com.dualboost.downloader

import com.dualboost.downloader.data.DownloadHistoryItem
import com.dualboost.downloader.data.DownloadHistoryRepository
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.NetworkProvider
import com.dualboost.downloader.ui.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SecurityHardeningPhase3Test {

    class DummyNetworkProvider(private val client: OkHttpClient) : NetworkProvider {
        override val isWifiAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override val isCellularAvailable: StateFlow<Boolean> = MutableStateFlow(true)
        override fun getWifiClient(): OkHttpClient = client
        override fun getCellularClient(): OkHttpClient = client
        override fun getDefaultClient(): OkHttpClient = client
    }

    @Test
    fun testLogSanitization_StripsQueryTokensFromUrlsAndExceptions() {
        val tempDir = File("build/test-sec3-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val engine = DualDownloaderEngine(DummyTestContext(tempDir), DummyNetworkProvider(OkHttpClient()))
            val rawMsg = "Connection failed: https://s3.amazonaws.com/bucket/file.zip?AWSAccessKeyId=AKIAIOSFODNN7EXAMPLE&Signature=vjbyPxybdZaNmGa%2ByT272YEAiv4%3D"
            val sanitized = engine.sanitizeLogMessage(rawMsg)

            assertFalse("Sanitized message must not contain secret signature", sanitized.contains("vjbyPxybdZaNmGa"))
            assertTrue("Sanitized message must replace query parameters with redaction tag", sanitized.contains("?[query-redacted]"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testDownloadHistoryRepository_IsFilePresent_ValidatesPresence() {
        val tempDir = File("build/test-sec3-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val testContext = DummyTestContext(tempDir)
            val repository = DownloadHistoryRepository(testContext)

            val existingFile = File(tempDir, "real_download.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val itemExisting = DownloadHistoryItem(
                id = "real-1",
                fileName = "real_download.bin",
                filePath = existingFile.absolutePath,
                fileSize = 3L
            )
            assertTrue("isFilePresent must return true for existing file", repository.isFilePresent(itemExisting))

            val missingFile = File(tempDir, "deleted_download.bin")
            val itemMissing = DownloadHistoryItem(
                id = "deleted-1",
                fileName = "deleted_download.bin",
                filePath = missingFile.absolutePath,
                fileSize = 100L
            )
            assertFalse("isFilePresent must return false when file is deleted and no URI is available", repository.isFilePresent(itemMissing))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

