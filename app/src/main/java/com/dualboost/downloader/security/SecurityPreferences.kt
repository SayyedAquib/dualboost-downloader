package com.dualboost.downloader.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages configuration and persistent user preferences for
 * the on-device Antivirus & Threat Guard system.
 */
object SecurityPreferences {

    private const val PREFS_NAME = "dualboost_security_prefs"
    private const val KEY_THREAT_GUARD_ENABLED = "threat_guard_enabled"
    private const val KEY_SAFE_BROWSING_API_KEY = "safe_browsing_api_key"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if on-device Threat Guard inspection is active.
     * Default: true.
     */
    fun isThreatGuardEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_THREAT_GUARD_ENABLED, true)
    }

    /**
     * Toggles on-device Threat Guard inspection.
     */
    fun setThreatGuardEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_THREAT_GUARD_ENABLED, enabled).apply()
    }

    /**
     * Retrieves optional Google Safe Browsing API Key configured by the user.
     */
    fun getSafeBrowsingApiKey(context: Context): String? {
        val key = getPrefs(context).getString(KEY_SAFE_BROWSING_API_KEY, null)?.trim()
        return if (key.isNullOrBlank()) null else key
    }

    /**
     * Stores or removes user-provided Google Safe Browsing API Key.
     */
    fun setSafeBrowsingApiKey(context: Context, key: String?) {
        val trimmed = key?.trim()
        if (trimmed.isNullOrBlank()) {
            getPrefs(context).edit().remove(KEY_SAFE_BROWSING_API_KEY).apply()
        } else {
            getPrefs(context).edit().putString(KEY_SAFE_BROWSING_API_KEY, trimmed).apply()
        }
    }
}
