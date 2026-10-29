package com.dualboost.downloader.security

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashVault(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            recordCrash(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed recording crash in vault: ${e.message}", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun recordCrash(thread: Thread, throwable: Throwable): File {
        val crashDir = context.noBackupFilesDir ?: context.filesDir
        val crashFile = File(crashDir, CRASH_LOG_FILE)

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sanitizeCrashLog(sw.toString())
        val sanitizedMessage = sanitizeCrashLog(throwable.message ?: "")

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val crashDump = """
            =================== CRASH VAULT DUMP ===================
            Timestamp: $timestamp
            Thread: ${thread.name} (id=${thread.id})
            Exception: ${throwable.javaClass.name}: $sanitizedMessage
            Stack Trace:
            $stackTrace
            ========================================================
            
        """.trimIndent()

        crashFile.appendText(crashDump)
        Log.e(TAG, "Crash logged to vault at ${crashFile.absolutePath}")
        return crashFile
    }

    fun getCrashLogs(): String? {
        val crashDir = context.noBackupFilesDir ?: context.filesDir
        val crashFile = File(crashDir, CRASH_LOG_FILE)
        return if (crashFile.exists()) crashFile.readText() else null
    }

    fun clearCrashLogs(): Boolean {
        val crashDir = context.noBackupFilesDir ?: context.filesDir
        val crashFile = File(crashDir, CRASH_LOG_FILE)
        return if (crashFile.exists()) crashFile.delete() else true
    }

    companion object {
        private const val TAG = "CrashVault"
        const val CRASH_LOG_FILE = "crash_vault.log"

        fun install(context: Context): CrashVault {
            val vault = CrashVault(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(vault)
            return vault
        }

        fun sanitizeCrashLog(raw: String): String {
            val urlRegex = Regex("""https?://[^\s"'<>]+""")
            return urlRegex.replace(raw) { match ->
                val fullUrl = match.value
                val schemeEnd = fullUrl.indexOf("://")
                if (schemeEnd != -1) {
                    val scheme = fullUrl.substring(0, schemeEnd)
                    val rest = fullUrl.substring(schemeEnd + 3)
                    val slashIdx = rest.indexOf('/')
                    val host = if (slashIdx != -1) rest.substring(0, slashIdx) else rest
                    "$scheme://$host/[REDACTED]"
                } else {
                    "[REDACTED_URL]"
                }
            }
        }
    }
}

