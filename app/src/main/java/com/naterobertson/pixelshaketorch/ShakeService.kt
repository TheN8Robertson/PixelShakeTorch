package com.naterobertson.pixelshaketorch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class ShakeService : Service() {

    private lateinit var sensorManager: SensorManager
    private lateinit var flashlight: FlashlightController
    private lateinit var shakeDetector: ShakeDetector
    private var wakeLock: PowerManager.WakeLock? = null
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        flashlight = FlashlightController(this).also { it.start() }
        shakeDetector = ShakeDetector(onShake = { flashlight.toggle() })

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PixelShakeTorch:ShakeWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()
        startListening()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        flashlight.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        isRunning = false
        super.onDestroy()
    }

    private fun startListening() {
        if (listening) return
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(
                shakeDetector,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            listening = true
        }
    }

    private fun stopListening() {
        if (!listening) return
        sensorManager.unregisterListener(shakeDetector)
        listening = false
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, ShakeService::class.java).setAction(ACTION_STOP),
            pendingFlags
        )
        val openAppPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            pendingFlags
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_notif_bolt)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppPi)
            .addAction(0, getString(R.string.notif_action_stop), stopPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        const val ACTION_STOP = "com.naterobertson.pixelshaketorch.action.STOP"
        private const val CHANNEL_ID = "shake_detection"
        private const val NOTIF_ID = 1

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, ShakeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShakeService::class.java))
        }
    }
}
