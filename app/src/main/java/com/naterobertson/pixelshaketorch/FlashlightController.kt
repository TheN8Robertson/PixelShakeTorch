package com.naterobertson.pixelshaketorch

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

class FlashlightController(context: Context) {

    private val cameraManager =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == backCameraId) {
                isOn = enabled
                onStateChanged?.invoke(enabled)
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == backCameraId) {
                isOn = false
                onStateChanged?.invoke(false)
            }
        }
    }

    private val backCameraId: String? = findBackCameraWithFlash()

    var isOn: Boolean = false
        private set

    var onStateChanged: ((Boolean) -> Unit)? = null

    val isAvailable: Boolean
        get() = backCameraId != null

    fun start() {
        cameraManager.registerTorchCallback(torchCallback, null)
    }

    fun stop() {
        cameraManager.unregisterTorchCallback(torchCallback)
    }

    fun toggle(): Boolean {
        val id = backCameraId ?: return false
        return try {
            cameraManager.setTorchMode(id, !isOn)
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val id = backCameraId ?: return false
        return try {
            cameraManager.setTorchMode(id, enabled)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun findBackCameraWithFlash(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (t: Throwable) {
            null
        }
    }
}
