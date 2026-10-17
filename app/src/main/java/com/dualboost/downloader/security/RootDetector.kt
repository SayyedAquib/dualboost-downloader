package com.dualboost.downloader.security

import android.os.Build
import java.io.File

/**
 * Advisory Root & Tamper Detector (OWASP MASVS-RESILIENCE)
 * Identifies rooted or custom firmware environments (Magisk, KernelSU, test-keys, su binaries)
 * to provide non-blocking security awareness to users.
 */
object RootDetector {

    private val KNOWN_ROOT_PATHS = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su"
    )

    fun isDeviceRooted(): Boolean {
        // 1. Check for test-keys build tags indicating custom or unofficial firmware
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // 2. Check for standard su binary installations
        for (path in KNOWN_ROOT_PATHS) {
            try {
                if (File(path).exists()) return true
            } catch (_: Exception) {}
        }

        // 3. Check for su binary present anywhere in system execution PATH
        try {
            val pathEnv = System.getenv("PATH") ?: ""
            for (dir in pathEnv.split(":")) {
                val candidate = File(dir.trim(), "su")
                if (candidate.exists()) return true
            }
        } catch (_: Exception) {}

        return false
    }
}

