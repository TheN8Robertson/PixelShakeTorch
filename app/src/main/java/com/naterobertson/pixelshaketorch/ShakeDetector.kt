package com.naterobertson.pixelshaketorch

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
    private val onShake: () -> Unit,
    @Volatile var shakeThresholdG: Float = 2.7f,
    private val minPeaks: Int = 3,
    private val peakWindowMs: Long = 1_000L,
    private val cooldownMs: Long = 1_500L,
) : SensorEventListener {

    private val recentPeaks = ArrayDeque<Long>()
    private var lastFiredAt = 0L

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        if (gForce < shakeThresholdG) return

        val now = System.currentTimeMillis()
        if (now - lastFiredAt < cooldownMs) return

        while (recentPeaks.isNotEmpty() && now - recentPeaks.first() > peakWindowMs) {
            recentPeaks.removeFirst()
        }
        recentPeaks.addLast(now)

        if (recentPeaks.size >= minPeaks) {
            lastFiredAt = now
            recentPeaks.clear()
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
