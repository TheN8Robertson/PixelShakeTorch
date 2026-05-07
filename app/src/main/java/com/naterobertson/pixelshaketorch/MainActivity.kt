package com.naterobertson.pixelshaketorch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.naterobertson.pixelshaketorch.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private lateinit var flashlight: FlashlightController
    private lateinit var shakeDetector: ShakeDetector

    private var hasCameraPermission = false
    private var hasNotificationPermission = false
    private var pendingBackgroundEnable = false

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        updateStatus()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted && pendingBackgroundEnable) {
            ShakeService.start(this)
            stopForegroundSensorListening()
        } else if (!granted) {
            binding.backgroundSwitch.isChecked = false
        }
        pendingBackgroundEnable = false
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        flashlight = FlashlightController(this)
        flashlight.onStateChanged = { on -> renderTorchState(on) }

        shakeDetector = ShakeDetector(onShake = { onShakeDetected() })

        binding.toggleButton.setOnClickListener { flashlight.toggle() }
        binding.backgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            onBackgroundToggle(isChecked)
        }

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        hasNotificationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true

        if (!hasCameraPermission) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStart() {
        super.onStart()
        flashlight.start()
        renderTorchState(flashlight.isOn)
        binding.backgroundSwitch.isChecked = ShakeService.isRunning
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (!ShakeService.isRunning) {
            startForegroundSensorListening()
        }
    }

    override fun onPause() {
        stopForegroundSensorListening()
        super.onPause()
    }

    override fun onStop() {
        flashlight.stop()
        super.onStop()
    }

    private fun startForegroundSensorListening() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(
                shakeDetector,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    private fun stopForegroundSensorListening() {
        sensorManager.unregisterListener(shakeDetector)
    }

    private fun onBackgroundToggle(enabled: Boolean) {
        if (enabled) {
            if (!hasCameraPermission) {
                binding.backgroundSwitch.isChecked = false
                cameraPermission.launch(Manifest.permission.CAMERA)
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationPermission
            ) {
                pendingBackgroundEnable = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            ShakeService.start(this)
            stopForegroundSensorListening()
        } else {
            ShakeService.stop(this)
            startForegroundSensorListening()
        }
        updateStatus()
    }

    private fun onShakeDetected() {
        if (!hasCameraPermission) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        if (flashlight.toggle()) {
            buzz()
        }
    }

    private fun renderTorchState(on: Boolean) {
        binding.statusText.text = if (on) {
            getString(R.string.status_on)
        } else {
            getString(R.string.status_off)
        }
        binding.toggleButton.text = if (on) {
            getString(R.string.action_turn_off)
        } else {
            getString(R.string.action_turn_on)
        }
    }

    private fun updateStatus() {
        binding.hintText.text = when {
            !flashlight.isAvailable -> getString(R.string.hint_no_flash)
            !hasCameraPermission -> getString(R.string.hint_need_permission)
            ShakeService.isRunning -> getString(R.string.hint_shake_to_toggle_bg)
            else -> getString(R.string.hint_shake_to_toggle)
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(40)
        }
    }
}
