package com.example.testcamera

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.example.testcamera.databinding.FragmentKioskCameraBinding
import com.example.testcamera.util.Camera2DiagnosticHelper
import com.example.testcamera.util.UsbDiagnosticHelper
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.CameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.widget.IAspectRatio
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KioskCameraFragment : CameraFragment(), ICameraStateCallBack {

    private var _binding: FragmentKioskCameraBinding? = null
    private val binding get() = _binding!!

    private var isLogPanelVisible = true

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FragmentKioskCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun getCameraView(): IAspectRatio {
        return binding.uvcSurfaceView
    }

    override fun getCameraViewContainer(): ViewGroup {
        return binding.cameraContainer
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appendLog("🚀 Khởi tạo Fragment Kiosk Camera...")

        binding.btnSwitchCamera.setOnClickListener {
            appendLog("🔄 Bấm chuyển camera UVC...")
            try {
                switchCamera()
                appendLog("🔄 Đã gọi switchCamera().")
            } catch (e: Exception) {
                appendLog("❌ Lỗi chuyển camera: ${e.message}")
            }
        }

        binding.btnReopenCamera.setOnClickListener {
            appendLog("▶️ Bấm mở lại camera...")
            try {
                openCamera()
                appendLog("▶️ Đã gọi openCamera().")
            } catch (e: Exception) {
                appendLog("❌ Lỗi mở camera: ${e.message}")
            }
        }

        binding.btnCapturePhoto.setOnClickListener {
            takePhotoTest()
        }

        binding.btnToggleLogView.setOnClickListener {
            toggleLogPanel()
        }

        // Quét thông số & tạo các nút chọn camera
        performFullDiagnostics()
    }

    private fun performFullDiagnostics() {
        val ctx = context ?: return

        appendLog("🔍 Quét danh sách camera USB & Camera2...")

        // 1. Quét thiết bị USB
        val usbDevices = UsbDiagnosticHelper.getConnectedUsbDevices(ctx)
        binding.containerCameraButtons.removeAllViews()

        if (usbDevices.isEmpty()) {
            binding.tvUsbDevicesInfo.text = "⚠️ Không tìm thấy thiết bị USB nào đang cắm."
            appendLog("⚠️ USB Scan: Không phát hiện cổng USB kết nối.")
        } else {
            val uvcDevices = usbDevices.filter { it.isUvcCamera }
            val sb = StringBuilder()

            for ((index, devInfo) in uvcDevices.withIndex()) {
                val permStatus = if (devInfo.hasPermission) "✅ ĐÃ CẤP QUYỀN" else "🚨 CHƯA CẤP QUYỀN (Nhấn để xin quyền)"
                val camLabel = "Cam UVC #${index + 1}: ${devInfo.productName ?: devInfo.deviceName} (${devInfo.getFormattedVidPid()}) - $permStatus"
                sb.append("• $camLabel\n")

                // Tạo nút bấm cho từng camera UVC
                val btn = Button(ctx).apply {
                    val btnText = "Cam #${index + 1} (${String.format("VID:0x%04X, PID:0x%04X", devInfo.vendorId, devInfo.productId)})"
                    text = btnText
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(4, 0, 4, 0)
                    }

                    setOnClickListener {
                        onCameraButtonClicked(devInfo.device, devInfo.hasPermission, index + 1)
                    }
                }
                binding.containerCameraButtons.addView(btn)
            }

            for (devInfo in usbDevices.filter { !it.isUvcCamera }) {
                sb.append("• [Thiết bị USB khác] ${devInfo.productName ?: devInfo.deviceName} (${devInfo.getFormattedVidPid()})\n")
            }

            binding.tvUsbDevicesInfo.text = sb.toString().trimEnd()

            if (uvcDevices.isNotEmpty()) {
                appendLog("✅ Phát hiện ${uvcDevices.size} camera UVC kết nối qua cổng USB.")
                binding.tvStatusBadge.text = "PHÁT HIỆN ${uvcDevices.size} CAM UVC"
                binding.tvStatusBadge.setBackgroundColor(0xFF1565C0.toInt())
            } else {
                appendLog("ℹ️ Có ${usbDevices.size} thiết bị USB, nhưng không thuộc chuẩn UVC Video Class.")
            }
        }

        // 2. Quét camera hệ thống (Camera2)
        val sysCameras = Camera2DiagnosticHelper.getSystemCameraDevices(ctx)
        if (sysCameras.isEmpty()) {
            binding.tvSystemCamerasInfo.text = "⚠️ Không có camera nào được Android OS nhận qua Camera2 (Kiosk chạy UVC qua USB Host)."
        } else {
            val sb = StringBuilder()
            for (cam in sysCameras) {
                val res = if (cam.resolutions.isNotEmpty()) cam.resolutions.joinToString(", ") else "Không rõ"
                sb.append("• Cam ID ${cam.cameraId} - ${cam.facingDirection} (Độ phân giải: $res)\n")
            }
            binding.tvSystemCamerasInfo.text = sb.toString().trimEnd()
        }
    }

    private fun onCameraButtonClicked(device: UsbDevice, hasPerm: Boolean, camNumber: Int) {
        val ctx = context ?: return
        val vidPid = String.format("VID:0x%04X, PID:0x%04X", device.vendorId, device.productId)

        if (!hasPerm) {
            appendLog("🔐 Đang xin quyền USB cho Cam #$camNumber ($vidPid)... Vui lòng nhấn ĐỒNG Ý trên màn hình Kiosk!")
            Toast.makeText(ctx, "Xin quyền USB cho Cam #$camNumber ($vidPid)...", Toast.LENGTH_SHORT).show()
            UsbDiagnosticHelper.requestUsbPermission(ctx, device)
        } else {
            appendLog("🔄 Bấm chọn mở Cam #$camNumber ($vidPid)....")
            try {
                switchCamera()
                appendLog("🟢 Đã gọi chuyển camera UVC sang Cam #$camNumber.")
            } catch (e: Exception) {
                appendLog("❌ Lỗi chuyển camera: ${e.message}")
            }
        }
    }

    fun onUsbPermissionGranted(device: UsbDevice) {
        val devName = device.productName ?: device.deviceName
        val vidPid = String.format("VID:0x%04X, PID:0x%04X", device.vendorId, device.productId)
        appendLog("✅ HĐH Android đã CẤP QUYỀN USB cho thiết bị: $devName ($vidPid)!")

        performFullDiagnostics()

        try {
            openCamera()
            appendLog("🟢 Tự động chuyển/mở camera UVC sau khi được cấp quyền USB.")
        } catch (e: Exception) {
            appendLog("ℹ️ Mở camera: ${e.message}")
        }
    }

    fun onCameraConnected(device: UsbDevice) {
        val devName = device.productName ?: device.deviceName
        val vidPid = String.format("VID:0x%04X, PID:0x%04X", device.vendorId, device.productId)
        appendLog("🔌 Đã phát hiện cắm USB Camera: $devName ($vidPid)")
        performFullDiagnostics()
    }

    fun onCameraDisconnected(device: UsbDevice) {
        val devName = device.productName ?: device.deviceName
        appendLog("🔌 Đã ngắt kết nối USB Camera: $devName")
        performFullDiagnostics()
    }

    private fun takePhotoTest() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val photoFile = File(storageDir, "KIOSK_CAM_$timestamp.jpg")

        appendLog("📸 Đang thực hiện chụp ảnh...")

        captureImage(object : ICaptureCallBack {
            override fun onBegin() {
                appendLog("📸 Đang ghi hình...")
            }

            override fun onComplete(path: String?) {
                val msg = "✅ Chụp ảnh thành công! Đã lưu tại: ${path ?: photoFile.absolutePath}"
                appendLog(msg)
                Toast.makeText(context, "Chụp ảnh thành công!", Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: String?) {
                val msg = "❌ Lỗi chụp ảnh: ${error ?: "Chưa rõ nguyên nhân"}"
                appendLog(msg)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }, photoFile.absolutePath)
    }

    private fun toggleLogPanel() {
        isLogPanelVisible = !isLogPanelVisible
        binding.scrollDiagnosticPanel.visibility = if (isLogPanelVisible) View.VISIBLE else View.GONE
        binding.btnToggleLogView.text = if (isLogPanelVisible) "📊 Ẩn Log" else "📊 Hiện Log"
    }

    override fun onCameraState(
        self: MultiCameraClient.Camera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        when (code) {
            ICameraStateCallBack.State.OPENED -> {
                val logMsg = "🟢 LIVE STREAM ACTIVE! Camera UVC đã mở thành công."
                appendLog(logMsg)
                binding.tvStatusBadge.text = "LIVE STREAM ACTIVE"
                binding.tvStatusBadge.setBackgroundColor(0xFF2E7D32.toInt())
                binding.tvCameraOverlayStatus.visibility = View.GONE
            }
            ICameraStateCallBack.State.CLOSED -> {
                val logMsg = "🔴 Camera đã đóng."
                appendLog(logMsg)
                binding.tvStatusBadge.text = "ĐÃ ĐÓNG"
                binding.tvStatusBadge.setBackgroundColor(0xFF757575.toInt())
            }
            ICameraStateCallBack.State.ERROR -> {
                val logMsg = "❌ Lỗi Camera: ${msg ?: "Lỗi không xác định"}"
                appendLog(logMsg)
                binding.tvStatusBadge.text = "LỖI CAMERA"
                binding.tvStatusBadge.setBackgroundColor(0xFFB71C1C.toInt())
                binding.tvCameraOverlayStatus.visibility = View.VISIBLE
                binding.tvCameraOverlayStatus.text = "Lỗi khởi tạo camera UVC: ${msg ?: "Không mở được video stream"}"
            }
        }
    }

    private fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formattedLog = "[$time] $message\n"
        activity?.runOnUiThread {
            if (_binding != null) {
                binding.tvLogConsole.append(formattedLog)
                binding.scrollDiagnosticPanel.post {
                    binding.scrollDiagnosticPanel.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
