package com.dualboost.downloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.dualboost.downloader.data.DownloadState
import com.dualboost.downloader.engine.DualDownloaderEngine
import com.dualboost.downloader.network.DualNetworkManager
import com.dualboost.downloader.ui.MainActivity
import kotlinx.coroutines.*
import java.util.Locale

class DownloadForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    lateinit var networkManager: DualNetworkManager
        private set
    lateinit var downloaderEngine: DualDownloaderEngine
        private set

    private lateinit var notificationManager: NotificationManager
    private var stateJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): DownloadForegroundService = this@DownloadForegroundService
    }

    private var lastNotificationTime = 0L

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val repository = com.dualboost.downloader.DualBoostApp.instance.repository
        networkManager = repository.networkManager
        downloaderEngine = repository.downloaderEngine
        observeDownloadState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START_DOWNLOAD) {
            val url = intent.getStringExtra(EXTRA_URL)
            acquireWakeLock()
            startForegroundNotification("Initializing download...")
            val currentState = downloaderEngine.downloadState.value
            if (!url.isNullOrBlank() && currentState !is DownloadState.Downloading && currentState !is DownloadState.Probing) {
                downloaderEngine.startDownload(url)
            }
        } else if (action == ACTION_CANCEL_DOWNLOAD) {
            downloaderEngine.pauseDownload()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DualBoost:DownloadWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(30 * 60 * 1000L /* 30 minutes max */)
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun observeDownloadState() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            downloaderEngine.downloadState.collect { state ->
                val now = System.currentTimeMillis()
                when (state) {
                    is DownloadState.Downloading -> {
                        if (now - lastNotificationTime >= 1000L) {
                            lastNotificationTime = now
                            updateNotification(
                                title = "Downloading: ${state.fileName}",
                                content = String.format(
                                    Locale.US,
                                    "Wi-Fi: %.1f Mbps | 5G: %.1f Mbps (Total: %.1f Mbps)",
                                    state.wifiSpeedMbps,
                                    state.cellularSpeedMbps,
                                    state.totalSpeedMbps
                                ),
                                progress = (state.progressPercentage * 100).toInt(),
                                indeterminate = false
                            )
                        }
                    }
                    is DownloadState.Probing -> {
                        lastNotificationTime = now
                        updateNotification(
                            title = "DualBoost Downloader",
                            content = state.message,
                            progress = 0,
                            indeterminate = true
                        )
                    }
                    is DownloadState.AutoRecovering -> {
                        updateNotification(
                            title = "Reconnecting Networks...",
                            content = "${state.message} (Attempt ${state.retryAttempt})",
                            progress = 0,
                            indeterminate = true
                        )
                    }
                    is DownloadState.Completed -> {
                        releaseWakeLock()
                        updateNotification(
                            title = "Download Complete!",
                            content = "${state.fileName} (${formatBytes(state.totalBytes)})",
                            progress = 100,
                            indeterminate = false
                        )
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                    is DownloadState.Failed -> {
                        releaseWakeLock()
                        updateNotification(
                            title = "Download Failed",
                            content = state.error,
                            progress = 0,
                            indeterminate = false
                        )
                        stopForeground(STOP_FOREGROUND_DETACH)
                    }
                    is DownloadState.Idle, is DownloadState.Paused -> {
                        releaseWakeLock()
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

    private fun updateNotification(title: String, content: String, progress: Int, indeterminate: Boolean) {
        val notification = buildNotification(title, content, progress, indeterminate)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DualBoost Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress and dual Wi-Fi + 5G speeds"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        stateJob?.cancel()
        serviceScope.cancel()
        networkManager.stopListening()
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
        const val CHANNEL_ID = "dualboost_download_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_DOWNLOAD = "com.dualboost.ACTION_START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.dualboost.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_URL = "extra_download_url"
    }
}

