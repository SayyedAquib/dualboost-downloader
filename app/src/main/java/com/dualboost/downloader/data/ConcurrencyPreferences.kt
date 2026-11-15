package com.dualboost.downloader.data

import android.content.Context
import android.content.SharedPreferences

class ConcurrencyPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConcurrencyConfig(): ConcurrencyConfig {
        val wifi = prefs.getInt(KEY_WIFI_WORKERS, ConcurrencyConfig.DEFAULT_WIFI_WORKERS)
        val cell = prefs.getInt(KEY_CELLULAR_WORKERS, ConcurrencyConfig.DEFAULT_CELLULAR_WORKERS)
        val autoScale = prefs.getBoolean(KEY_AUTO_SCALE, false)
        return ConcurrencyConfig(
            wifiWorkers = wifi,
            cellularWorkers = cell,
            isAutoScaleEnabled = autoScale
        )
    }

    fun setWifiWorkers(count: Int) {
        val clamped = count.coerceIn(ConcurrencyConfig.MIN_WORKERS, ConcurrencyConfig.MAX_WORKERS)
        prefs.edit().putInt(KEY_WIFI_WORKERS, clamped).apply()
    }

    fun setCellularWorkers(count: Int) {
        val clamped = count.coerceIn(ConcurrencyConfig.MIN_WORKERS, ConcurrencyConfig.MAX_WORKERS)
        prefs.edit().putInt(KEY_CELLULAR_WORKERS, clamped).apply()
    }

    fun setAutoScaleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCALE, enabled).apply()
    }

    fun resetToDefault() {
        prefs.edit()
            .putInt(KEY_WIFI_WORKERS, ConcurrencyConfig.DEFAULT_WIFI_WORKERS)
            .putInt(KEY_CELLULAR_WORKERS, ConcurrencyConfig.DEFAULT_CELLULAR_WORKERS)
            .putBoolean(KEY_AUTO_SCALE, false)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "dualboost_concurrency_prefs"
        private const val KEY_WIFI_WORKERS = "wifi_workers"
        private const val KEY_CELLULAR_WORKERS = "cellular_workers"
        private const val KEY_AUTO_SCALE = "auto_scale_enabled"
    }
}
