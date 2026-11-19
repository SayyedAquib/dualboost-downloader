package com.dualboost.downloader.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent preferences for Cellular Network Operation Mode (ADR-048).
 */
class CellularPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCellularMode(): CellularNetworkMode {
        val modeId = prefs.getString(KEY_CELLULAR_MODE, CellularNetworkMode.AUTO.id)
        return CellularNetworkMode.fromId(modeId)
    }

    fun setCellularMode(mode: CellularNetworkMode) {
        prefs.edit().putString(KEY_CELLULAR_MODE, mode.id).apply()
    }

    fun resetToDefault() {
        prefs.edit().putString(KEY_CELLULAR_MODE, CellularNetworkMode.AUTO.id).apply()
    }

    companion object {
        private const val PREFS_NAME = "dualboost_cellular_prefs"
        private const val KEY_CELLULAR_MODE = "cellular_network_mode"
    }
}

