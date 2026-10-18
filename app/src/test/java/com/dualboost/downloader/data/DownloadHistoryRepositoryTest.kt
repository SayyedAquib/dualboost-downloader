package com.dualboost.downloader.data

import com.dualboost.downloader.DummyTestContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DownloadHistoryRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var dummyContext: DummyTestContext
    private lateinit var historyFile: File
    private lateinit var repository: DownloadHistoryRepository

    @Before
    fun setUp() {
        tempDir = File.createTempFile("dualboost_test_", "_dir").apply {
            delete()
            mkdirs()
        }
        dummyContext = DummyTestContext(tempDir)
        historyFile = File(tempDir, "test_download_history.json")
        repository = DownloadHistoryRepository(
            context = dummyContext,
            historyFileOverride = historyFile,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSerializationRoundTrip() {
        val original = DownloadHistoryItem(
            id = "test-uuid-123",
            fileName = "large_archive.zip",
            filePath = "/sdcard/Download/large_archive.zip",
            contentUriString = "content://media/external/downloads/42",
            fileSize = 104857600L,
            timestamp = 1700000000000L,
            sha256Checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            durationSeconds = 12L,
            averageSpeedMBps = 8.73,
            isDualChannel = true
        )

        val jsonString = original.toJsonString()
        val deserialized = DownloadHistoryItem.fromJsonString(jsonString)

        assertNotNull(deserialized)
        assertEquals(original.id, deserialized!!.id)
        assertEquals(original.fileName, deserialized.fileName)
        assertEquals(original.filePath, deserialized.filePath)
        assertEquals(original.contentUriString, deserialized.contentUriString)
        assertEquals(original.fileSize, deserialized.fileSize)
        assertEquals(original.timestamp, deserialized.timestamp)
        assertEquals(original.sha256Checksum, deserialized.sha256Checksum)
        assertEquals(original.durationSeconds, deserialized.durationSeconds)
        assertEquals(original.averageSpeedMBps, deserialized.averageSpeedMBps, 0.001)
        assertEquals(original.isDualChannel, deserialized.isDualChannel)
    }

    @Test
    fun testRecordAndRetrieveSingleDownload() = runBlocking {
        val item = DownloadHistoryItem(
            id = "item-1",
            fileName = "test_file.iso",
            filePath = File(tempDir, "test_file.iso").apply { writeBytes(ByteArray(1024)) }.absolutePath,
            fileSize = 1024L,
            isDualChannel = true
        )

        repository.recordDownloadSync(item)

        val items = repository.historyItems.value
        assertEquals(1, items.size)
        assertEquals("test_file.iso", items[0].fileName)
        assertTrue(historyFile.exists())

        // Re-read with fresh repository instance to verify persistent JSON reading
        val newRepo = DownloadHistoryRepository(
            context = dummyContext,
            historyFileOverride = historyFile,
            ioDispatcher = Dispatchers.Unconfined
        )
        assertEquals(1, newRepo.historyItems.value.size)
        assertEquals("test_file.iso", newRepo.historyItems.value[0].fileName)
    }

    @Test
    fun testOrderIsDescendingByTimestamp() = runBlocking {
        val olderItem = DownloadHistoryItem(
            id = "item-old",
            fileName = "old.bin",
            filePath = "/fake/old.bin",
            fileSize = 500L,
            timestamp = 1000L
        )
        val newerItem = DownloadHistoryItem(
            id = "item-new",
            fileName = "new.bin",
            filePath = "/fake/new.bin",
            fileSize = 1500L,
            timestamp = 5000L
        )

        repository.recordDownloadSync(olderItem)
        repository.recordDownloadSync(newerItem)

        val items = repository.historyItems.value
        assertEquals(2, items.size)
        assertEquals("new.bin", items[0].fileName)
        assertEquals("old.bin", items[1].fileName)
    }

    @Test
    fun testDuplicateFilePathUpdatesEntry() = runBlocking {
        val file = File(tempDir, "dup.bin").apply { writeBytes(ByteArray(100)) }

        val firstItem = DownloadHistoryItem(
            id = "first-id",
            fileName = "dup.bin",
            filePath = file.absolutePath,
            fileSize = 100L,
            sha256Checksum = "hash1"
        )
        val secondItem = DownloadHistoryItem(
            id = "second-id",
            fileName = "dup.bin",
            filePath = file.absolutePath,
            fileSize = 100L,
            sha256Checksum = "hash2"
        )

        repository.recordDownloadSync(firstItem)
        repository.recordDownloadSync(secondItem)

        val items = repository.historyItems.value
        assertEquals(1, items.size)
        assertEquals("hash2", items[0].sha256Checksum)
    }

    @Test
    fun testDeleteItemWithoutDiskDeletion() = runBlocking {
        val file = File(tempDir, "keep_on_disk.bin").apply { writeBytes(ByteArray(500)) }
        val item = DownloadHistoryItem(
            id = "delete-me",
            fileName = "keep_on_disk.bin",
            filePath = file.absolutePath,
            fileSize = 500L
        )

        repository.recordDownloadSync(item)
        assertEquals(1, repository.historyItems.value.size)

        val deleted = repository.deleteItem("delete-me", deleteFileFromDisk = false)
        assertTrue(deleted)
        assertEquals(0, repository.historyItems.value.size)
        assertTrue("Physical file must still exist on disk", file.exists())
    }

    @Test
    fun testDeleteItemWithDiskDeletion() = runBlocking {
        val file = File(tempDir, "remove_from_disk.bin").apply { writeBytes(ByteArray(500)) }
        val item = DownloadHistoryItem(
            id = "delete-all",
            fileName = "remove_from_disk.bin",
            filePath = file.absolutePath,
            fileSize = 500L
        )

        repository.recordDownloadSync(item)
        assertTrue(file.exists())

        val deleted = repository.deleteItem("delete-all", deleteFileFromDisk = true)
        assertTrue(deleted)
        assertEquals(0, repository.historyItems.value.size)
        assertFalse("Physical file should be deleted from storage", file.exists())
    }

    @Test
    fun testClearAllWithDiskDeletion() = runBlocking {
        val file1 = File(tempDir, "f1.bin").apply { writeBytes(ByteArray(200)) }
        val file2 = File(tempDir, "f2.bin").apply { writeBytes(ByteArray(300)) }

        repository.recordDownloadSync(DownloadHistoryItem(id = "1", fileName = "f1.bin", filePath = file1.absolutePath, fileSize = 200L))
        repository.recordDownloadSync(DownloadHistoryItem(id = "2", fileName = "f2.bin", filePath = file2.absolutePath, fileSize = 300L))

        assertEquals(2, repository.historyItems.value.size)

        repository.clearAll(deleteFilesFromDisk = true)
        assertEquals(0, repository.historyItems.value.size)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    @Test
    fun testCorruptionRecovery() {
        historyFile.writeText("{ this is invalid json [}")

        val recoveringRepo = DownloadHistoryRepository(
            context = dummyContext,
            historyFileOverride = historyFile,
            ioDispatcher = Dispatchers.Unconfined
        )

        assertEquals(0, recoveringRepo.historyItems.value.size)
    }

    @Test
    fun testStorageCalculationAndFileExistence() = runBlocking {
        val existingFile = File(tempDir, "exists.bin").apply { writeBytes(ByteArray(4096)) }
        val missingFilePath = File(tempDir, "missing.bin").absolutePath

        val item1 = DownloadHistoryItem(id = "1", fileName = "exists.bin", filePath = existingFile.absolutePath, fileSize = 4096L)
        val item2 = DownloadHistoryItem(id = "2", fileName = "missing.bin", filePath = missingFilePath, fileSize = 2048L)

        repository.recordDownloadSync(item1)
        repository.recordDownloadSync(item2)

        assertTrue(repository.isFilePresent(item1))
        assertFalse(repository.isFilePresent(item2))

        val totalBytes = repository.calculateTotalStorageBytes()
        assertEquals(4096L, totalBytes)
    }
}
