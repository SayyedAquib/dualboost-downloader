package com.dualboost.downloader.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent preferences for Browser Identity and User-Agent headers (ADR-046).
 */
class UserAgentPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getUserAgentConfig(): UserAgentConfig {
        val presetId = prefs.getString(KEY_PRESET_ID, UserAgentPreset.NATIVE.id) ?: UserAgentPreset.NATIVE.id
        val customUa = prefs.getString(KEY_CUSTOM_UA, "") ?: ""
        return UserAgentConfig(
            preset = UserAgentPreset.fromId(presetId),
            customUserAgentString = customUa
        )
    }

    fun setPreset(preset: UserAgentPreset) {
        prefs.edit().putString(KEY_PRESET_ID, preset.id).apply()
    }

    fun setCustomUserAgent(customUa: String) {
        prefs.edit()
            .putString(KEY_PRESET_ID, UserAgentPreset.CUSTOM.id)
            .putString(KEY_CUSTOM_UA, customUa)
            .apply()
    }

    fun resetToDefault() {
        prefs.edit()
            .putString(KEY_PRESET_ID, UserAgentPreset.NATIVE.id)
            .putString(KEY_CUSTOM_UA, "")
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "dualboost_user_agent_prefs"
        private const val KEY_PRESET_ID = "selected_preset_id"
        private const val KEY_CUSTOM_UA = "custom_user_agent_string"
    }
}
