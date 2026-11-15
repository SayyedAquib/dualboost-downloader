package com.dualboost.downloader

import com.dualboost.downloader.data.StorageDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageDestinationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dummyContext: DummyTestContext
    private lateinit var baseDir: File

    @Before
    fun setUp() {
        baseDir = tempFolder.newFolder("app_base")
        dummyContext = DummyTestContext(baseDir)
    }

    @Test
    fun testDestination_PresetsArePopulated() {
        assertEquals(3, StorageDestination.PRESETS.size)
        assertEquals("default", StorageDestination.PRESETS[0].id)
        assertEquals("dualboost", StorageDestination.PRESETS[1].id)
        assertEquals("media", StorageDestination.PRESETS[2].id)

        assertEquals("Standard", StorageDestination.PRESET_DEFAULT.displayName)
        assertEquals("DualBoost", StorageDestination.PRESET_DUALBOOST.displayName)
        assertEquals("Media", StorageDestination.PRESET_MEDIA.displayName)
    }

    @Test
    fun testDestination_DefaultResolvesBaseDirectory() {
        val publicDownloads = tempFolder.newFolder("Downloads")
        val destination = StorageDestination.PRESET_DEFAULT

        val resolved = destination.resolveDirectory(dummyContext, basePublicDir = publicDownloads)
        assertEquals(publicDownloads.canonicalPath, resolved.canonicalPath)
        assertTrue(resolved.exists())
    }

    @Test
    fun testDestination_SubfolderResolvesCorrectChildPath() {
        val publicDownloads = tempFolder.newFolder("Downloads")
        val destination = StorageDestination.PRESET_DUALBOOST

        val resolved = destination.resolveDirectory(dummyContext, basePublicDir = publicDownloads)
        val expected = File(publicDownloads, "DualBoost")
        assertEquals(expected.canonicalPath, resolved.canonicalPath)
        assertTrue(resolved.exists())
        assertTrue(resolved.isDirectory)
    }

    @Test
    fun testDestination_CustomPathResolvesCorrectly() {
        val customDir = tempFolder.newFolder("CustomDrive", "MyDownloads")
        val customDest = StorageDestination(
            id = StorageDestination.CUSTOM_ID,
            displayName = "Custom Folder",
            customPath = customDir.absolutePath
        )

        assertTrue(customDest.isCustom())
        val resolved = customDest.resolveDirectory(dummyContext)
        assertEquals(customDir.canonicalPath, resolved.canonicalPath)
        assertTrue(resolved.exists())
    }

    @Test
    fun testDestination_UnwritableFolder_FallsBackSafely() {
        // Point to a completely invalid / unwritable path (e.g. a non-creatable file path)
        val invalidDir = File(tempFolder.newFile("dummy_file"), "sub_dir") // cannot create dir inside a file
        val badDest = StorageDestination(
            id = StorageDestination.CUSTOM_ID,
            displayName = "Bad Drive",
            customPath = invalidDir.absolutePath
        )

        val fallbackDir = tempFolder.newFolder("fallback_downloads")
        val resolved = badDest.resolveDirectory(dummyContext, basePublicDir = fallbackDir)

        // Should safely fall back to fallbackDir or context app directory without crashing
        assertNotNull(resolved)
        assertTrue(resolved.exists())
        assertTrue(resolved.canWrite())
    }

    @Test
    fun testDestination_FindPresetById() {
        assertEquals(StorageDestination.PRESET_DEFAULT, StorageDestination.fromId("default"))
        assertEquals(StorageDestination.PRESET_DUALBOOST, StorageDestination.fromId("dualboost"))
        assertEquals(StorageDestination.PRESET_MEDIA, StorageDestination.fromId("media"))
        assertEquals(StorageDestination.PRESET_DEFAULT, StorageDestination.fromId("unknown_id"))
    }
}
