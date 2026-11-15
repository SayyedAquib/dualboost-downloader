package com.dualboost.downloader

import com.dualboost.downloader.data.StorageDestination
import com.dualboost.downloader.data.StoragePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoragePreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: DummyTestContext
    private lateinit var baseDir: File
    private lateinit var storagePreferences: StoragePreferences

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("app_base")
        dummyContext = DummyTestContext(baseDir)
        storagePreferences = StoragePreferences(dummyContext)
    }

    @Test
    fun testPreferences_DefaultsToDefaultDownloads() {
        val dest = storagePreferences.getStorageDestination()
        assertEquals(StorageDestination.DEFAULT_ID, dest.id)
        assertEquals("Standard", dest.displayName)
        assertEquals("", dest.relativePath)
        assertNull(dest.customPath)
        assertFalse(dest.isCustom())
    }

    @Test
    fun testPreferences_SavesAndRestoresPresetDestination() {
        storagePreferences.setStorageDestination(StorageDestination.PRESET_DUALBOOST)

        val restored = storagePreferences.getStorageDestination()
        assertEquals(StorageDestination.DUALBOOST_ID, restored.id)
        assertEquals("DualBoost", restored.displayName)
        assertEquals("DualBoost", restored.relativePath)
        assertNull(restored.customPath)
    }

    @Test
    fun testPreferences_SavesAndRestoresCustomDestination() {
        val customDir = tempFolder.newFolder("ExternalStorage", "DownloadedFiles")
        val customDest = StorageDestination(
            id = StorageDestination.CUSTOM_ID,
            displayName = "DownloadedFiles",
            customPath = customDir.absolutePath,
            customUriString = "content://com.android.externalstorage.documents/tree/1234-5678%3ADownloadedFiles"
        )

        storagePreferences.setStorageDestination(customDest)

        val restored = storagePreferences.getStorageDestination()
        assertEquals(StorageDestination.CUSTOM_ID, restored.id)
        assertEquals("DownloadedFiles", restored.displayName)
        assertEquals(customDir.absolutePath, restored.customPath)
        assertEquals("content://com.android.externalstorage.documents/tree/1234-5678%3ADownloadedFiles", restored.customUriString)
        assertTrue(restored.isCustom())
    }

    @Test
    fun testPreferences_ResetToDefault_RestoresStandardDownloads() {
        storagePreferences.setStorageDestination(StorageDestination.PRESET_MEDIA)
        assertEquals(StorageDestination.MEDIA_ID, storagePreferences.getStorageDestination().id)

        storagePreferences.resetToDefault()

        val restored = storagePreferences.getStorageDestination()
        assertEquals(StorageDestination.DEFAULT_ID, restored.id)
        assertEquals("Standard", restored.displayName)
        assertEquals("", restored.relativePath)
        assertNull(restored.customPath)
    }
}
