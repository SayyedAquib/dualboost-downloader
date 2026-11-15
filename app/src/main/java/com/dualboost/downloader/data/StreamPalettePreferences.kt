package com.dualboost.downloader.data

import android.content.Context
import android.content.SharedPreferences

class StreamPalettePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStreamPalette(): StreamPalette {
        val savedId = prefs.getString(KEY_PALETTE_ID, StreamPalette.DEFAULT.id)
        return StreamPalette.fromId(savedId)
    }

    fun setStreamPalette(palette: StreamPalette) {
        prefs.edit().putString(KEY_PALETTE_ID, palette.id).apply()
    }

    fun resetToDefault() {
        prefs.edit().putString(KEY_PALETTE_ID, StreamPalette.DEFAULT.id).apply()
    }

    companion object {
        private const val PREFS_NAME = "dualboost_palette_prefs"
        private const val KEY_PALETTE_ID = "stream_palette_id"
    }
}
