package com.dualboost.downloader.service

import android.content.Intent

/**
 * Explicit lifecycle state machine for [DownloadForegroundService].
 */
enum class ServiceExecutionPhase {
    STOPPED,
    FOREGROUND_ACTIVE,
    PAUSED,
    TERMINATING;

    val holdsLocks: Boolean
        get() = this == FOREGROUND_ACTIVE

    fun canTransitionTo(next: ServiceExecutionPhase): Boolean {
        if (this == next) return true
        return when (this) {
            STOPPED -> next == FOREGROUND_ACTIVE || next == PAUSED || next == TERMINATING
            FOREGROUND_ACTIVE -> next == PAUSED || next == TERMINATING || next == STOPPED
            PAUSED -> next == FOREGROUND_ACTIVE || next == TERMINATING || next == STOPPED
            TERMINATING -> next == STOPPED || next == FOREGROUND_ACTIVE
        }
    }
}

/**
 * Explicit resource lock ownership state for WakeLock, WifiLock, and CellularKeepAlive.
 */
enum class ServiceLockState {
    RELEASED,
    ACQUIRED;

    fun canTransitionTo(next: ServiceLockState): Boolean = this != next
}

/**
 * Typed service command parsed from Android [Intent] actions.
 * Replaces loose string comparisons inside `onStartCommand`.
 */
sealed class ServiceCommand {
    data class StartOrResume(
        val url: String?,
        val forceSequential: Boolean?,
        val allowDangerousBypass: Boolean
    ) : ServiceCommand()

    object Pause : ServiceCommand()
    object Cancel : ServiceCommand()
    object Discard : ServiceCommand()
    object SystemTimeout : ServiceCommand()
    data class Unknown(val action: String?) : ServiceCommand()

    companion object {
        fun fromIntent(intent: Intent?): ServiceCommand {
            val action = intent?.action
            return when (action) {
                DownloadForegroundService.ACTION_START_DOWNLOAD,
                DownloadForegroundService.ACTION_RESUME_DOWNLOAD -> {
                    val url = intent.getStringExtra(DownloadForegroundService.EXTRA_URL)
                    val forceSeq = if (intent.hasExtra(DownloadForegroundService.EXTRA_FORCE_SEQUENTIAL)) {
                        intent.getBooleanExtra(DownloadForegroundService.EXTRA_FORCE_SEQUENTIAL, false)
                    } else null
                    val allowBypass = intent.getBooleanExtra(DownloadForegroundService.EXTRA_ALLOW_BYPASS, false)
                    StartOrResume(url = url, forceSequential = forceSeq, allowDangerousBypass = allowBypass)
                }
                DownloadForegroundService.ACTION_PAUSE_DOWNLOAD -> Pause
                DownloadForegroundService.ACTION_CANCEL_DOWNLOAD -> Cancel
                DownloadForegroundService.ACTION_DISCARD_DOWNLOAD -> Discard
                else -> Unknown(action)
            }
        }
    }
}

/**
 * Production notification update throttler preventing Android OS NotificationManager shedding
 * during 500ms telemetry emissions while guaranteeing immediate dispatch for terminal/lifecycle states.
 */
class NotificationThrottler(private val throttleIntervalMs: Long = 1000L) {
    private var lastDispatchedAtMs: Long = Long.MIN_VALUE
    var notificationsDispatched: Int = 0
        private set

    @Synchronized
    fun shouldDispatch(
        currentTimeMs: Long = System.currentTimeMillis(),
        isTerminalOrLifecycle: Boolean = false
    ): Boolean {
        val elapsed = if (lastDispatchedAtMs == Long.MIN_VALUE) {
            throttleIntervalMs
        } else {
            currentTimeMs - lastDispatchedAtMs
        }
        if (isTerminalOrLifecycle || elapsed >= throttleIntervalMs) {
            lastDispatchedAtMs = currentTimeMs
            notificationsDispatched++
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        lastDispatchedAtMs = Long.MIN_VALUE
        notificationsDispatched = 0
    }
}
