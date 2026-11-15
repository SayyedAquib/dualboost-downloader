package com.dualboost.downloader.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistence for download storage destination settings (ADR-044).
 */
class StoragePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStorageDestination(): StorageDestination {
        val id = prefs.getString(KEY_DESTINATION_ID, StorageDestination.DEFAULT_ID) ?: StorageDestination.DEFAULT_ID
        if (id.equals(StorageDestination.CUSTOM_ID, ignoreCase = true)) {
            val displayName = prefs.getString(KEY_DISPLAY_NAME, "Custom Folder") ?: "Custom Folder"
            val customPath = prefs.getString(KEY_CUSTOM_PATH, null)
            val customUriString = prefs.getString(KEY_CUSTOM_URI, null)
            return StorageDestination(
                id = StorageDestination.CUSTOM_ID,
                displayName = displayName,
                relativePath = "",
                customPath = customPath,
                customUriString = customUriString
            )
        }
        return StorageDestination.fromId(id)
    }

    fun setStorageDestination(destination: StorageDestination) {
        val editor = prefs.edit()
            .putString(KEY_DESTINATION_ID, destination.id)
            .putString(KEY_DISPLAY_NAME, destination.displayName)
            .putString(KEY_RELATIVE_PATH, destination.relativePath)

        if (destination.customPath != null) {
            editor.putString(KEY_CUSTOM_PATH, destination.customPath)
        } else {
            editor.remove(KEY_CUSTOM_PATH)
        }

        if (destination.customUriString != null) {
            editor.putString(KEY_CUSTOM_URI, destination.customUriString)
        } else {
            editor.remove(KEY_CUSTOM_URI)
        }

        editor.apply()
    }

    fun resetToDefault() {
        prefs.edit()
            .putString(KEY_DESTINATION_ID, StorageDestination.DEFAULT_ID)
            .putString(KEY_DISPLAY_NAME, StorageDestination.PRESET_DEFAULT.displayName)
            .putString(KEY_RELATIVE_PATH, "")
            .remove(KEY_CUSTOM_PATH)
            .remove(KEY_CUSTOM_URI)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "dualboost_storage_prefs"
        private const val KEY_DESTINATION_ID = "destination_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_RELATIVE_PATH = "relative_path"
        private const val KEY_CUSTOM_PATH = "custom_path"
        private const val KEY_CUSTOM_URI = "custom_uri"
    }
}
