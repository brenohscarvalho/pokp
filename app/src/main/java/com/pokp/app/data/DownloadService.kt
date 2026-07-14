package com.pokp.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pokp.app.MainActivity
import com.pokp.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process alive (and its network available) while downloads
 * run, showing an ongoing progress notification. It performs no work itself — it mirrors
 * [DownloadRepository] state and stops once the queue is idle.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification("Preparando downloads…", 0, 0, indeterminate = true))
        scope.launch {
            DownloadRepository.tasks.collect { tasks ->
                val active = tasks.count { it.status.isActive }
                val queued = tasks.count { it.status == com.pokp.app.domain.DownloadStatus.QUEUED }
                if (active == 0 && queued == 0) {
                    stopForegroundCompat()
                    stopSelf()
                    return@collect
                }
                val current = tasks.firstOrNull { it.status.isActive }
                val remaining = active + queued
                val title = current?.title?.takeIf { it.isNotBlank() } ?: "Baixando…"
                val text = if (remaining > 1) "$remaining na fila · $title" else title
                val progress = current?.progress?.toInt() ?: 0
                notify(buildNotification(text, 100, progress, indeterminate = progress <= 0))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String, max: Int, progress: Int, indeterminate: Boolean): Notification {
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pokp Downloader")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(max, progress, indeterminate)
            .build()
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Progresso dos downloads" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 42
    }
}
