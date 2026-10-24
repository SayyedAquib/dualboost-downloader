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

    private val MAGISK_ARTIFACTS = arrayOf(
        "/sbin/.magisk",
        "/data/adb/magisk",
        "/data/adb/magisk.db",
        "/cache/.disable_magisk",
        "/dev/.magisk.unblock"
    )

    fun isDeviceRooted(): Boolean {
        return checkTestKeys() ||
                checkSuBinaries() ||
                checkSuInPath() ||
                checkMagiskArtifacts() ||
                checkMountPoints() ||
                checkMaps()
    }

    private fun checkTestKeys(): Boolean {
        return Build.TAGS?.contains("test-keys") == true
    }

    private fun checkSuBinaries(): Boolean {
        return KNOWN_ROOT_PATHS.any { path ->
            try {
                File(path).exists()
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun checkSuInPath(): Boolean {
        return try {
            val pathEnv = System.getenv("PATH") ?: ""
            pathEnv.split(":").any { dir ->
                try {
                    File(dir.trim(), "su").exists()
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun checkMagiskArtifacts(): Boolean {
        return MAGISK_ARTIFACTS.any { path ->
            try {
                File(path).exists()
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun checkMountPoints(): Boolean {
        return try {
            val mounts = File("/proc/self/mounts").readText()
            mounts.contains("magisk") || mounts.contains("kernelsu")
        } catch (_: Exception) {
            false
        }
    }

    private fun checkMaps(): Boolean {
        return try {
            val maps = File("/proc/self/maps").readText()
            maps.contains("zygisk") || maps.contains("riru") || maps.contains("libsu")
        } catch (_: Exception) {
            false
        }
    }
}


