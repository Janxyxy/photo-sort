package com.photosort.eye

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.photosort.MainActivity
import com.photosort.R
import com.photosort.app
import com.photosort.ui.formatCount
import com.photosort.ui.timeLeft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while [EyeScanner] works, so a scan continues with the app closed or the
 * screen off. Shows the progress as a notification with a Pause button and stops itself when the
 * scan ends. The scan itself still lives in [EyeScanner]; this only holds the process.
 */
class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scanner = app.eyeScanner
        // the Pause tap is a start request too, so it has to count or stopSelf(id) would be ignored
        lastStartId = startId
        if (intent?.action == ACTION_PAUSE) {
            scanner.stop()
            // a stale notification can start a fresh instance that has nothing to watch
            if (watcher == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        // must go foreground right away, even if the scan already finished in the meantime
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            progressNotification(scanner.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (watcher == null) {
            watcher = scope.launch {
                scanner.state.collect { state ->
                    when (state) {
                        ScanState.Downloading, is ScanState.Scanning -> notify(progressNotification(state))
                        ScanState.Idle -> finish(if (scanner.pausedByUser) null else doneNotification())
                        is ScanState.Failed -> finish(failedNotification(state.message))
                    }
                }
            }
        }
        // if Android kills the process, the user restarts the scan from the app; it resumes where it stopped
        return START_NOT_STICKY
    }

    /** Android 15 caps data sync services at 6 hours a day; pause rather than get the app killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        app.eyeScanner.stop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Drops the ongoing notification and leaves [last] in its place, if any. */
    private fun finish(last: Notification?) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        last?.let { notify(it, RESULT_NOTIFICATION_ID) }
        watcher?.cancel()
        watcher = null
        // keeps running if a new scan asked for the service in the meantime
        stopSelf(lastStartId)
    }

    private fun notify(notification: Notification, id: Int = NOTIFICATION_ID) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(id, notification)
        }
    }

    private fun base(): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setSilent(true)
    }

    private fun progressNotification(state: ScanState): Notification {
        val pause = PendingIntent.getService(
            this,
            0,
            Intent(this, ScanService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = base()
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Pause", pause)
        return when (state) {
            is ScanState.Scanning -> builder
                .setContentTitle("Analyzing photos")
                .setContentText(
                    "${formatCount(state.done)} of ${formatCount(state.total)}" +
                        (state.msPerPhoto?.let { " · ${timeLeft((state.total - state.done) * it)}" } ?: ""),
                )
                .setProgress(state.total, state.done, false)
                .build()
            else -> builder
                .setContentTitle("Getting ready")
                .setContentText("Downloading the photo model (66 MB)")
                .setProgress(0, 0, true)
                .build()
        }
    }

    private fun doneNotification(): Notification = base()
        .setContentTitle("Photos analyzed")
        .setContentText("Categories are ready")
        .setAutoCancel(true)
        .build()

    private fun failedNotification(message: String): Notification = base()
        .setContentTitle("Photo analysis stopped")
        .setContentText(message)
        .setAutoCancel(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "scan"
        private const val NOTIFICATION_ID = 1
        private const val RESULT_NOTIFICATION_ID = 2
        private const val ACTION_PAUSE = "com.photosort.PAUSE_SCAN"

        /** Call once the scanner state is no longer Idle, or the service stops right away. */
        fun start(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Photo analysis", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Progress while photos are sorted into categories" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java))
        }
    }
}
