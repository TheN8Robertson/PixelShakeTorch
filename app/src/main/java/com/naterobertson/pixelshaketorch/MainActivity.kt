package com.naterobertson.pixelshaketorch

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
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
    private lateinit var prefs: SharedPreferences

    private var hasNotificationPermission = false
    private var pendingBackgroundEnable = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (pendingBackgroundEnable) {
            // Even if the user denied notifications, we still start the service —
            // Android will fail the FGS notification silently but shake detection
            // continues to work; this is preferable to leaving background mode off.
            enableBackgroundService()
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs.get(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        flashlight = FlashlightController(this)
        flashlight.onStateChanged = { on -> renderTorchState(on) }

        shakeDetector = ShakeDetector(
            onShake = { onShakeDetected() },
            shakeThresholdG = Prefs.threshold(prefs)
        )

        binding.toggleButton.setOnClickListener { flashlight.toggle() }
        attachSwitchListener()
        setupSensitivitySlider()

        hasNotificationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true

        val isFirstLaunch = !prefs.getBoolean(Prefs.KEY_FIRST_LAUNCH_DONE, false)
        if (isFirstLaunch && flashlight.isAvailable) {
            prefs.edit().putBoolean(Prefs.KEY_FIRST_LAUNCH_DONE, true).apply()
            pendingBackgroundEnable = true
            requestNextPermissionForBackground()
        }
    }

    private fun setupSensitivitySlider() {
        val current = Prefs.threshold(prefs).coerceIn(Prefs.MIN_THRESHOLD_G, Prefs.MAX_THRESHOLD_G)
        binding.sensitivitySlider.valueFrom = Prefs.MIN_THRESHOLD_G
        binding.sensitivitySlider.valueTo = Prefs.MAX_THRESHOLD_G
        binding.sensitivitySlider.stepSize = Prefs.THRESHOLD_STEP_G
        binding.sensitivitySlider.value = current
        renderSensitivity(current)

        binding.sensitivitySlider.addOnChangeListener { _, value, _ ->
            renderSensitivity(value)
            shakeDetector.shakeThresholdG = value
            prefs.edit().putFloat(Prefs.KEY_SHAKE_THRESHOLD_G, value).apply()
        }
    }

    private fun renderSensitivity(value: Float) {
        binding.sensitivityLabel.text = getString(R.string.sensitivity_label, value)
    }

    override fun onStart() {
        super.onStart()
        flashlight.start()
        renderTorchState(flashlight.isOn)
        setSwitchSilently(ShakeService.isRunning)
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
            if (!flashlight.isAvailable) {
                setSwitchSilently(false)
                return
            }
            pendingBackgroundEnable = true
            requestNextPermissionForBackground()
        } else {
            pendingBackgroundEnable = false
            ShakeService.stop(this)
            startForegroundSensorListening()
            updateStatus()
        }
    }

    private fun requestNextPermissionForBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        enableBackgroundService()
    }

    private fun enableBackgroundService() {
        ShakeService.start(this)
        stopForegroundSensorListening()
        setSwitchSilently(true)
        pendingBackgroundEnable = false
        updateStatus()
    }

    private fun onShakeDetected() {
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
            ShakeService.isRunning -> getString(R.string.hint_shake_to_toggle_bg)
            else -> getString(R.string.hint_shake_to_toggle)
        }
    }

    private fun attachSwitchListener() {
        binding.backgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            onBackgroundToggle(isChecked)
        }
    }

    private fun setSwitchSilently(checked: Boolean) {
        if (binding.backgroundSwitch.isChecked == checked) return
        binding.backgroundSwitch.setOnCheckedChangeListener(null)
        binding.backgroundSwitch.isChecked = checked
        attachSwitchListener()
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
