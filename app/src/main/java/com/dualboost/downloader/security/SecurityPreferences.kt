package com.dualboost.downloader.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages configuration and persistent user preferences for
 * the on-device Antivirus & Threat Guard system.
 *
 * SEC-AUDIT C-1: Sensitive keys (Safe Browsing API key) are stored with
 * Android Keystore-backed AES-256-GCM encryption via EncryptedSharedPreferences.
 * Non-sensitive booleans (threat_guard_enabled) are co-located for consistency.
 */
object SecurityPreferences {

    private const val TAG = "SecurityPreferences"
    private const val PREFS_NAME = "dualboost_security_prefs_encrypted"
    private const val LEGACY_PREFS_NAME = "dualboost_security_prefs"

    private const val KEY_THREAT_GUARD_ENABLED = "threat_guard_enabled"
    private const val KEY_SAFE_BROWSING_API_KEY = "safe_browsing_api_key"

    // SEC-AUDIT C-1: Use Android Keystore-backed AES-256-GCM encryption
    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            // One-time migration from legacy plaintext SharedPreferences
            migrateLegacyPrefs(context, encPrefs)
            encPrefs
        } catch (e: Throwable) {
            // Graceful fallback: if Keystore is broken or in headless JVM unit tests,
            // fall back to standard prefs to avoid crashing the security system entirely.
            Log.e(TAG, "EncryptedSharedPreferences init failed, falling back: ${e.message}")
            context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun migrateLegacyPrefs(context: Context, encPrefs: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val hasLegacyData = legacy.contains(KEY_THREAT_GUARD_ENABLED) ||
                legacy.contains(KEY_SAFE_BROWSING_API_KEY)
        if (!hasLegacyData) return

        encPrefs.edit().apply {
            // Migrate boolean toggle — only if not already set in encrypted prefs
            if (!encPrefs.contains(KEY_THREAT_GUARD_ENABLED) && legacy.contains(KEY_THREAT_GUARD_ENABLED)) {
                putBoolean(KEY_THREAT_GUARD_ENABLED, legacy.getBoolean(KEY_THREAT_GUARD_ENABLED, true))
            }
            // Migrate API key — only if not already set in encrypted prefs
            val legacyKey = legacy.getString(KEY_SAFE_BROWSING_API_KEY, null)
            if (legacyKey != null && encPrefs.getString(KEY_SAFE_BROWSING_API_KEY, null) == null) {
                putString(KEY_SAFE_BROWSING_API_KEY, legacyKey)
            }
        }.apply()

        // Permanently wipe plaintext credentials from legacy store
        legacy.edit().clear().apply()
        Log.d(TAG, "Migrated SecurityPreferences from plaintext to EncryptedSharedPreferences")
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
