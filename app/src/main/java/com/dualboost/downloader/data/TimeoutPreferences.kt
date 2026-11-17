package com.dualboost.downloader.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent preferences for Network Timeout and Failover policies (ADR-047).
 */
class TimeoutPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTimeoutConfig(): TimeoutConfig {
        val profileId = prefs.getString(KEY_PROFILE_ID, TimeoutProfile.BALANCED.id) ?: TimeoutProfile.BALANCED.id
        val connectSec = prefs.getInt(KEY_CUSTOM_CONNECT_TIMEOUT, 15)
        val readSec = prefs.getInt(KEY_CUSTOM_READ_TIMEOUT, 30)
        val maxRetries = prefs.getInt(KEY_CUSTOM_MAX_RETRIES, 5)
        val retryBackoff = prefs.getLong(KEY_CUSTOM_RETRY_BACKOFF, 300L)

        return TimeoutConfig(
            profile = TimeoutProfile.fromId(profileId),
            customConnectTimeoutSeconds = connectSec,
            customReadTimeoutSeconds = readSec,
            customMaxChunkRetries = maxRetries,
            customRetryBackoffMs = retryBackoff
        )
    }

    fun setProfile(profile: TimeoutProfile) {
        prefs.edit().putString(KEY_PROFILE_ID, profile.id).apply()
    }

    fun setCustomConfig(connectSec: Int, readSec: Int, maxRetries: Int, retryBackoffMs: Long) {
        prefs.edit()
            .putString(KEY_PROFILE_ID, TimeoutProfile.CUSTOM.id)
            .putInt(KEY_CUSTOM_CONNECT_TIMEOUT, connectSec)
            .putInt(KEY_CUSTOM_READ_TIMEOUT, readSec)
            .putInt(KEY_CUSTOM_MAX_RETRIES, maxRetries)
            .putLong(KEY_CUSTOM_RETRY_BACKOFF, retryBackoffMs)
            .apply()
    }

    fun resetToDefault() {
        prefs.edit()
            .putString(KEY_PROFILE_ID, TimeoutProfile.BALANCED.id)
            .putInt(KEY_CUSTOM_CONNECT_TIMEOUT, 15)
            .putInt(KEY_CUSTOM_READ_TIMEOUT, 30)
            .putInt(KEY_CUSTOM_MAX_RETRIES, 5)
            .putLong(KEY_CUSTOM_RETRY_BACKOFF, 300L)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "dualboost_timeout_prefs"
        private const val KEY_PROFILE_ID = "selected_timeout_profile_id"
        private const val KEY_CUSTOM_CONNECT_TIMEOUT = "custom_connect_timeout_seconds"
        private const val KEY_CUSTOM_READ_TIMEOUT = "custom_read_timeout_seconds"
        private const val KEY_CUSTOM_MAX_RETRIES = "custom_max_chunk_retries"
        private const val KEY_CUSTOM_RETRY_BACKOFF = "custom_retry_backoff_ms"
    }
}
