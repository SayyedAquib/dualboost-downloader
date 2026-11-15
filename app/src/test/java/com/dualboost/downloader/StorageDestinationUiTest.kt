package com.dualboost.downloader

import com.dualboost.downloader.data.StorageDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageDestinationUiTest {

    @Test
    fun testStorageUi_FormatsPathPreviewAndPresets() {
        val presets = StorageDestination.PRESETS
        assertEquals(3, presets.size)

        val defaultPreset = StorageDestination.PRESET_DEFAULT
        assertEquals("Standard", defaultPreset.displayName)
        assertEquals("", defaultPreset.relativePath)

        val dualboostPreset = StorageDestination.PRESET_DUALBOOST
        assertEquals("DualBoost", dualboostPreset.displayName)
        assertEquals("DualBoost", dualboostPreset.relativePath)

        val mediaPreset = StorageDestination.PRESET_MEDIA
        assertEquals("Media", mediaPreset.displayName)
        assertEquals("Media", mediaPreset.relativePath)
    }

    @Test
    fun testStorageUi_CustomDestinationFormatting() {
        val customDest = StorageDestination(
            id = StorageDestination.CUSTOM_ID,
            displayName = "USB_Drive",
            customPath = "/storage/ABCD-1234/Downloads"
        )

        assertTrue(customDest.isCustom())
        assertEquals("USB_Drive", customDest.displayName)
        assertEquals("/storage/ABCD-1234/Downloads", customDest.customPath)
    }

    @Test
    fun testStorageUi_PresetMatching() {
        presetsMatch("default", StorageDestination.PRESET_DEFAULT)
        presetsMatch("dualboost", StorageDestination.PRESET_DUALBOOST)
        presetsMatch("media", StorageDestination.PRESET_MEDIA)
    }

    private fun presetsMatch(id: String, expected: StorageDestination) {
        val found = StorageDestination.fromId(id)
        assertEquals(expected.id, found.id)
        assertEquals(expected.displayName, found.displayName)
    }
}
