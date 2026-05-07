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

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
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

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStart() {
        super.onStart()
        flashlight.start()
        renderTorchState(flashlight.isOn)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(
                shakeDetector,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(shakeDetector)
        super.onPause()
    }

    override fun onStop() {
        flashlight.stop()
        super.onStop()
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
        if (!flashlight.isAvailable) {
            binding.hintText.text = getString(R.string.hint_no_flash)
        } else if (!hasCameraPermission) {
            binding.hintText.text = getString(R.string.hint_need_permission)
        } else {
            binding.hintText.text = getString(R.string.hint_shake_to_toggle)
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
