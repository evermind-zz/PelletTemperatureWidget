package com.example.pelletswaterheater.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.pelletswaterheater.MainActivity
import com.example.pelletswaterheater.utils.Logger
import com.example.pelletswaterheater.utils.TemperatureLogger
import com.example.pelletswaterheater.utils.TemperatureUtils
import com.example.pelletswaterheater.viewmodel.TemperatureViewModelNew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoggingService : Service() {
    private val logger: TemperatureLogger by inject()
    private val dataRepository: TemperatureViewModelNew by inject()
    private val temperatureUtils: TemperatureUtils by inject()
    private var isServiceStarted = false
    private var logCount = 0
    private var loggingIntervalMs = 5000L // Default: 5 seconds
    private var lastLogTime = ""
    private val startTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    private var loggingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_LOGGING) {
            stopLogging()
            return START_NOT_STICKY
        }

        loggingIntervalMs = intent?.getLongExtra(EXTRA_LOGGING_INTERVAL, 5000L) ?: 5000L
        if (!isServiceStarted) {
            startLogging()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Logging Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startLogging() {
        isServiceStarted = true
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        loggingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isServiceStarted) {
                val url = temperatureUtils.getServerUrl()
                val result = dataRepository.fetchValues(url, TAG, true)
                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    if (exception?.message == "network busy") {
                        Logger.d(TAG, "Logging skipped: network busy, will retry next interval")
                    }
                }
                logCount++
                lastLogTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                updateNotification()
                delay(loggingIntervalMs)
            }
        }
    }

    private fun stopLogging() {
        isServiceStarted = false
        loggingJob?.cancel()
        stopForeground(true)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, LoggingService::class.java).apply {
            action = ACTION_STOP_LOGGING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Temperature Logging Service")
            .setContentText("Started: $startTime, Last Log: $lastLogTime, Logs: $logCount")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(
                    "Started: ${startTime}\n" +
                            "Last Log: ${lastLogTime}\n" +
                            "Interval: ${loggingIntervalMs}ms | Count: $logCount"
                )
            )

            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Logging", stopPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open App", openAppPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogging()
    }

    companion object {
        const val CHANNEL_ID = "LoggingServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_LOGGING = "com.example.pelletswaterheater.STOP_LOGGING"
        const val EXTRA_LOGGING_INTERVAL = "com.example.pelletswaterheater.LOGGING_INTERVAL"
        private val TAG = LoggingService::class.java.simpleName
    }
}