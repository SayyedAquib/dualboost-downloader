package com.dualboost.downloader.data

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Domain model representing a configured download target directory (ADR-044).
 * Supports public root Downloads, clean subfolders (DualBoost, Media), or
 * custom external directories / SD card paths chosen via Android SAF.
 */
data class StorageDestination(
    val id: String,
    val displayName: String,
    val relativePath: String = "",
    val customPath: String? = null,
    val customUriString: String? = null
) {
    fun isCustom(): Boolean = id.equals(CUSTOM_ID, ignoreCase = true) || customPath != null

    /**
     * Resolves the target storage directory with guaranteed fallback.
     * If the target directory cannot be created or is not writable, safely falls back
     * to the standard public Downloads or app-specific files directory without failing downloads.
     */
    fun resolveDirectory(context: Context, basePublicDir: File? = null): File {
        val targetCandidate: File = when {
            !customPath.isNullOrBlank() -> {
                File(customPath)
            }
            relativePath.isNotBlank() -> {
                val publicBase = basePublicDir ?: getPublicDownloadsDir()
                if (publicBase != null) {
                    File(publicBase, relativePath)
                } else {
                    File(getFallbackDir(context), relativePath)
                }
            }
            else -> {
                basePublicDir ?: getPublicDownloadsDir() ?: getFallbackDir(context)
            }
        }

        if (isDirWritable(targetCandidate)) {
            return targetCandidate
        }

        // Safe fallback if target candidate is unwritable or inaccessible
        val publicFallback = basePublicDir ?: getPublicDownloadsDir()
        if (publicFallback != null && isDirWritable(publicFallback)) {
            return publicFallback
        }

        val appFallback = getFallbackDir(context)
        if (!appFallback.exists()) {
            appFallback.mkdirs()
        }
        return appFallback
    }

    private fun isDirWritable(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            val testFile = File(dir, ".dualboost_storage_test_${System.currentTimeMillis()}_${(0..9999).random()}")
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

    private fun getPublicDownloadsDir(): File? {
        return try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getFallbackDir(context: Context): File {
        return try {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        } catch (_: Throwable) {
            val tmp = System.getProperty("java.io.tmpdir") ?: "."
            File(tmp, "downloads").apply { mkdirs() }
        }
    }

    companion object {
        const val DEFAULT_ID = "default"
        const val DUALBOOST_ID = "dualboost"
        const val MEDIA_ID = "media"
        const val CUSTOM_ID = "custom"

        val PRESET_DEFAULT = StorageDestination(
            id = DEFAULT_ID,
            displayName = "Standard",
            relativePath = ""
        )

        val PRESET_DUALBOOST = StorageDestination(
            id = DUALBOOST_ID,
            displayName = "DualBoost",
            relativePath = "DualBoost"
        )

        val PRESET_MEDIA = StorageDestination(
            id = MEDIA_ID,
            displayName = "Media",
            relativePath = "Media"
        )

        val PRESETS = listOf(PRESET_DEFAULT, PRESET_DUALBOOST, PRESET_MEDIA)

        fun fromId(id: String?): StorageDestination {
            return PRESETS.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PRESET_DEFAULT
        }
    }
}
