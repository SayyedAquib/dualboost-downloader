package com.dualboost.downloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.DualNetworkManager
import com.dualboost.downloader.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class DownloadForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockRenewalJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null

    lateinit var repository: com.dualboost.downloader.data.DownloadRepository
        private set
    lateinit var networkManager: DualNetworkManager
        private set
    lateinit var downloaderEngine: DualDownloaderEngine
        private set

    private lateinit var notificationManager: NotificationManager
    private var stateJob: Job? = null

    private val _servicePhase = MutableStateFlow(ServiceExecutionPhase.STOPPED)
    val servicePhase: StateFlow<ServiceExecutionPhase> = _servicePhase.asStateFlow()

    private val _lockState = MutableStateFlow(ServiceLockState.RELEASED)
    val lockState: StateFlow<ServiceLockState> = _lockState.asStateFlow()

    private val notificationThrottler = NotificationThrottler(throttleIntervalMs = 1000L)

    inner class LocalBinder : Binder() {
        fun getService(): DownloadForegroundService = this@DownloadForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        repository = com.dualboost.downloader.DualBoostApp.instance.repository
        networkManager = repository.networkManager
        downloaderEngine = repository.downloaderEngine
        observeDownloadState()
    }

    private fun transitionServicePhase(next: ServiceExecutionPhase) {
        val current = _servicePhase.value
        if (!current.canTransitionTo(next)) {
            Log.w(TAG, "Unexpected ServiceExecutionPhase transition: $current -> $next")
        }
        _servicePhase.value = next
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (val command = ServiceCommand.fromIntent(intent)) {
            is ServiceCommand.StartOrResume -> handleStartOrResume(command)
            is ServiceCommand.Pause -> handlePauseCommand()
            is ServiceCommand.Cancel -> handleCancelCommand()
            is ServiceCommand.Discard -> handleDiscardCommand()
            is ServiceCommand.SystemTimeout -> handleTimeoutCommand()
            is ServiceCommand.Unknown -> {
                Log.w(TAG, "Ignoring unknown service command: ${command.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStartOrResume(command: ServiceCommand.StartOrResume) {
        val currentState = downloaderEngine.downloadState.value
        val targetUrl = command.url ?: (currentState as? DownloadState.Paused)?.url
        if (targetUrl.isNullOrBlank()) {
            Log.w(TAG, "StartOrResume received with empty URL; stopping service cleanly.")
            transitionServicePhase(ServiceExecutionPhase.TERMINATING)
            releaseServiceResources()
            stopSelf()
            return
        }

        transitionServicePhase(ServiceExecutionPhase.FOREGROUND_ACTIVE)
        acquireServiceResources()
        startForegroundNotification("Initializing download...")

        if (!currentState.blocksConcurrentStart) {
            downloaderEngine.startDownload(
                url = targetUrl,
                forceSequentialMode = command.forceSequential,
                allowDangerousBypass = command.allowDangerousBypass,
                concurrencyConfig = repository.concurrencyConfig.value,
                chunkConfig = repository.chunkConfig.value,
                userAgentConfig = repository.userAgentConfig.value,
                timeoutConfig = repository.timeoutConfig.value
            )
        }
    }

    private fun handlePauseCommand() {
        val currentState = downloaderEngine.downloadState.value
        if (currentState.isTransferInProgress) {
            downloaderEngine.pauseDownload()
        }
        transitionServicePhase(ServiceExecutionPhase.PAUSED)
        releaseServiceResources()
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun handleCancelCommand() {
        val currentState = downloaderEngine.downloadState.value
        if (currentState.isTransferInProgress) {
            downloaderEngine.pauseDownload()
        }
        transitionServicePhase(ServiceExecutionPhase.TERMINATING)
        releaseServiceResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleDiscardCommand() {
        val currentState = downloaderEngine.downloadState.value
        val fileName = (currentState as? DownloadState.Downloading)?.fileName
            ?: (currentState as? DownloadState.Paused)?.fileName
        transitionServicePhase(ServiceExecutionPhase.TERMINATING)
        downloaderEngine.discardDownload(fileName = fileName)
        releaseServiceResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleTimeoutCommand() {
        transitionServicePhase(ServiceExecutionPhase.PAUSED)
        downloaderEngine.pauseDownload()
        releaseServiceResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireServiceResources() {
        networkManager.acquireCellularKeepAlive()
        acquireLocks()
        _lockState.value = ServiceLockState.ACQUIRED
    }

    private fun releaseServiceResources() {
        networkManager.releaseCellularKeepAlive()
        releaseLocks()
        _lockState.value = ServiceLockState.RELEASED
    }

    // SEC-AUDIT L-3: WakeLock with auto-renewal for downloads exceeding 30 minutes
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(30 * 60 * 1000L /* 30 minutes max */)
        scheduleWakeLockRenewal()
    }

    private fun scheduleWakeLockRenewal() {
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = serviceScope.launch {
            while (isActive) {
                delay(25 * 60 * 1000L) // Renew 5 minutes before expiry
                val state = downloaderEngine.downloadState.value
                if (state.isTransferInProgress) {
                    wakeLock?.acquire(30 * 60 * 1000L)
                }
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = null
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun acquireWifiLock() {
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = try {
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiManager?.createWifiLock(
                    lockMode,
                    WIFI_LOCK_TAG
                )?.apply {
                    setReferenceCounted(false)
                }
            } catch (_: Exception) {
                null
            }
        }
        try {
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun acquireLocks() {
        acquireWakeLock()
        acquireWifiLock()
    }

    private fun releaseLocks() {
        releaseWakeLock()
        releaseWifiLock()
    }

    private fun observeDownloadState() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            downloaderEngine.downloadState.collect { state ->
                val now = System.currentTimeMillis()
                when (state) {
                    is DownloadState.Downloading -> {
                        if (notificationThrottler.shouldDispatch(now, isTerminalOrLifecycle = false)) {
                            val previewPending = if (state.isPlayable && state.filePath.isNotBlank()) {
                                createPreviewPendingIntent(state.filePath)
                            } else null

                            val notificationTitle = if (state.isPlayable) {
                                "▶ Watch Preview: ${state.fileName}"
                            } else {
                                "Downloading: ${state.fileName}"
                            }

                            val cellGen = networkManager.cellularGeneration.value
                            val wifiLabel = networkManager.wifiDetails.value.shortLabel
                            updateNotification(
                                title = notificationTitle,
                                content = formatNotificationSpeedContent(
                                    wifiSpeedMBps = state.wifiSpeedMBps,
                                    cellularSpeedMBps = state.cellularSpeedMBps,
                                    totalSpeedMBps = state.totalSpeedMBps,
                                    cellularGeneration = cellGen,
                                    wifiLabel = wifiLabel
                                ),
                                progress = (state.progressPercentage * 100).toInt(),
                                indeterminate = false,
                                previewIntent = previewPending
                            )
                        }
                    }
                    is DownloadState.Probing -> {
                        if (notificationThrottler.shouldDispatch(now, isTerminalOrLifecycle = true)) {
                            updateNotification(
                                title = "DualBoost Downloader",
                                content = state.message,
                                progress = 0,
                                indeterminate = true
                            )
                        }
                    }
                    is DownloadState.AutoRecovering -> {
                        if (notificationThrottler.shouldDispatch(now, isTerminalOrLifecycle = true)) {
                            updateNotification(
                                title = "Reconnecting Networks...",
                                content = "${state.message} (Attempt ${state.retryAttempt})",
                                progress = 0,
                                indeterminate = true
                            )
                        }
                    }
                    is DownloadState.Completed -> {
                        transitionServicePhase(ServiceExecutionPhase.TERMINATING)
                        releaseServiceResources()
                        notificationThrottler.shouldDispatch(now, isTerminalOrLifecycle = true)
                        updateNotification(
                            title = "Download Complete!",
                            content = "${state.fileName} (${formatBytes(state.totalBytes)})",
                            progress = 100,
                            indeterminate = false,
                            isPaused = false,
                            canControl = false
                        )
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is DownloadState.ThreatDetected -> {
                        transitionServicePhase(ServiceExecutionPhase.TERMINATING)
                        releaseServiceResources()
                        notificationThrottler.shouldDispatch(now, isTerminalOrLifecycle = true)
                        updateNotification(
                            title = "🛑 Threat Detected: Download Blocked",
                            content = "${state.suggestedFilename}: ${state.threat.description}",
                            progress = 0,
                            indeterminate = false,
                            isPaused = false,
                            canControl = false
                        )
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is DownloadState.Failed -> {
                        transitionServicePhase(ServiceExecutionPhase.TERMINATING)
                        releaseServiceResources()
                        notificationThrottler.shouldDispatch(now, isTerminalOrLifecycle = true)
                        updateNotification(
                            title = "Download Failed",
                            content = state.error,
                            progress = 0,
                            indeterminate = false,
                            isPaused = false,
                            canControl = false
                        )
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is DownloadState.Paused -> {
                        transitionServicePhase(ServiceExecutionPhase.PAUSED)
                        releaseServiceResources()
                        notificationThrottler.shouldDispatch(now, isTerminalOrLifecycle = true)
                        updateNotification(
                            title = "Download Paused",
                            content = "${state.fileName} (Tap Resume to continue)",
                            progress = (state.progressPercentage * 100).toInt(),
                            indeterminate = false,
                            isPaused = true,
                            canControl = true
                        )
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                    is DownloadState.Idle -> {
                        releaseServiceResources()
                        notificationThrottler.reset()
                    }
                }
            }
        }
    }

    private fun startForegroundNotification(initialText: String) {
        val notification = buildNotification("DualBoost Downloader", initialText, 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        title: String,
        content: String,
        progress: Int,
        indeterminate: Boolean,
        isPaused: Boolean = false,
        canControl: Boolean = true,
        previewIntent: PendingIntent? = null
    ) {
        val notification = buildNotification(title, content, progress, indeterminate, isPaused, canControl, previewIntent)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createPreviewPendingIntent(filePath: String): PendingIntent? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) return null
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val ext = filePath.substringAfterLast('.', "").lowercase()
            val mimeType = when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "avi" -> "video/x-msvideo"
                "ts" -> "video/mp2t"
                else -> "video/*"
            }
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingIntent.getActivity(
                this,
                4,
                Intent.createChooser(viewIntent, "Watch Video Stream"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int,
        indeterminate: Boolean,
        isPaused: Boolean = false,
        canControl: Boolean = true,
        previewIntent: PendingIntent? = null
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(!isPaused && canControl)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )

        if (canControl) {
            if (previewIntent != null) {
                builder.addAction(android.R.drawable.ic_media_play, "Watch", previewIntent)
            }

            if (isPaused) {
                val resumeIntent = Intent(this, DownloadForegroundService::class.java).apply {
                    action = ACTION_RESUME_DOWNLOAD
                }
                val resumePending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PendingIntent.getForegroundService(
                        this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                } else {
                    PendingIntent.getService(
                        this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
                builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePending)
            } else {
                val pauseIntent = Intent(this, DownloadForegroundService::class.java).apply {
                    action = ACTION_PAUSE_DOWNLOAD
                }
                val pausePending = PendingIntent.getService(
                    this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePending)
            }

            val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
                action = ACTION_DISCARD_DOWNLOAD
            }
            val cancelPending = PendingIntent.getService(
                this, 3, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPending)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DualBoost Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress and aggregated speeds across Wi-Fi and Cellular"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transitionServicePhase(ServiceExecutionPhase.STOPPED)
        releaseServiceResources()
        stateJob?.cancel()
        serviceScope.cancel()
        // Do NOT call networkManager.stopListening() here; networkManager is an application-scoped singleton
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.w(TAG, "Foreground service execution time limit reached (fgsType $fgsType). Pausing download cleanly.")
        handleTimeoutCommand()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1000) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    companion object {
        private const val TAG = "DownloadFGService"
        const val CHANNEL_ID = "dualboost_download_channel"
        const val NOTIFICATION_ID = 1001

        const val WAKE_LOCK_TAG = "DualBoost:DownloadWakeLock"
        const val WIFI_LOCK_TAG = "DualBoost:DownloadWifiLock"

        const val ACTION_START_DOWNLOAD = "com.dualboost.ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.dualboost.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.dualboost.ACTION_PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.dualboost.ACTION_RESUME_DOWNLOAD"
        const val ACTION_DISCARD_DOWNLOAD = "com.dualboost.ACTION_DISCARD_DOWNLOAD"
        const val EXTRA_URL = "extra_download_url"
        const val EXTRA_FORCE_SEQUENTIAL = "extra_force_sequential"
        const val EXTRA_ALLOW_BYPASS = "EXTRA_ALLOW_BYPASS"

        fun formatNotificationSpeedContent(
            wifiSpeedMBps: Double,
            cellularSpeedMBps: Double,
            totalSpeedMBps: Double,
            cellularGeneration: com.dualboost.downloader.network.CellularGeneration,
            wifiLabel: String = "Wi-Fi"
        ): String {
            val cellLabel = cellularGeneration.shortLabel
            return "$wifiLabel: ${com.dualboost.downloader.data.formatSpeed(wifiSpeedMBps)} | $cellLabel: ${com.dualboost.downloader.data.formatSpeed(cellularSpeedMBps)} (Total: ${com.dualboost.downloader.data.formatSpeed(totalSpeedMBps)})"
        }
    }
}
