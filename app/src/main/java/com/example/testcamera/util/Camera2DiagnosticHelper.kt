package com.example.testcamera.util

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.SurfaceHolder

data class Camera2DeviceInfo(
    val cameraId: String,
    val facingDirection: String,
    val isExternalUvc: Boolean,
    val resolutions: List<String>,
)

object Camera2DiagnosticHelper {

    fun getSystemCameraDevices(context: Context): List<Camera2DeviceInfo> {
        val cameraManager = (context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager)
            ?: return emptyList()

        val result = mutableListOf<Camera2DeviceInfo>()

        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics[CameraCharacteristics.LENS_FACING]

                val facingStr = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Trước (Front)"
                    CameraCharacteristics.LENS_FACING_BACK -> "Sau (Back)"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "Ngoại vi (External UVC)"
                    else -> "Không xác định ($facing)"
                }

                val isExternal = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)

                val configMap = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
                val sizesList = mutableListOf<String>()

                if (configMap != null) {
                    val outputSizes = configMap.getOutputSizes(ImageFormat.JPEG)
                        ?: configMap.getOutputSizes(SurfaceHolder::class.java)

                    outputSizes?.take(5)?.forEach { size ->
                        sizesList.add("${size.width}x${size.height}")
                    }
                }

                result.add(
                    Camera2DeviceInfo(
                        cameraId = id,
                        facingDirection = facingStr,
                        isExternalUvc = isExternal,
                        resolutions = sizesList,
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result
    }
}
