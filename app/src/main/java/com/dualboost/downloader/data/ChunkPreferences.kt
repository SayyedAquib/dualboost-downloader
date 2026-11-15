package com.dualboost.downloader.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent preferences for chunk slicing strategy (ADR-045).
 */
class ChunkPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getChunkConfig(): ChunkConfig {
        val isAuto = prefs.getBoolean(KEY_IS_AUTO_ADAPTIVE, true)
        val manualSize = prefs.getLong(KEY_MANUAL_CHUNK_SIZE, ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES)
        return ChunkConfig(
            isAutoAdaptive = isAuto,
            manualChunkSizeBytes = manualSize
        )
    }

    fun setManualChunkSizeBytes(sizeBytes: Long) {
        val clamped = sizeBytes.coerceIn(ChunkConfig.MIN_CHUNK_SIZE_BYTES, ChunkConfig.MAX_CHUNK_SIZE_BYTES)
        prefs.edit().putLong(KEY_MANUAL_CHUNK_SIZE, clamped).apply()
    }

    fun setAutoAdaptive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_AUTO_ADAPTIVE, enabled).apply()
    }

    fun resetToDefault() {
        prefs.edit()
            .putBoolean(KEY_IS_AUTO_ADAPTIVE, true)
            .putLong(KEY_MANUAL_CHUNK_SIZE, ChunkConfig.DEFAULT_CHUNK_SIZE_BYTES)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "dualboost_chunk_prefs"
        private const val KEY_IS_AUTO_ADAPTIVE = "is_auto_adaptive"
        private const val KEY_MANUAL_CHUNK_SIZE = "manual_chunk_size"
    }
}
