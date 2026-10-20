package com.example.testcamera

import android.graphics.Rect
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.testcamera.data.UserFaceRepository
import com.example.testcamera.databinding.FragmentKioskCameraBinding
import com.example.testcamera.ml.FaceNetModel
import com.example.testcamera.util.NV21ToBitmapConverter
import com.example.testcamera.util.UsbDeviceInfo
import com.example.testcamera.util.UsbDiagnosticHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.ICaptureCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.serenegiant.usb.USBMonitor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class KioskCameraFragment : Fragment() {

    private var _binding: FragmentKioskCameraBinding? = null
    private val binding get() = _binding!!

    private var mMultiCameraClient: MultiCameraClient? = null

    // Panel A (Camera 1 / Thermal)
    private var assignedDeviceA: UsbDevice? = null
    private var mCameraA: MultiCameraClient.Camera? = null
    private var frameCountA = 0
    private var fpsA = 0

    // Panel B (Camera 2 / RGB)
    private var assignedDeviceB: UsbDevice? = null
    private var mCameraB: MultiCameraClient.Camera? = null
    private var frameCountB = 0
    private var fpsB = 0

    // Google ML Kit Face Detection
    private var faceDetector: FaceDetector? = null
    private val isProcessingFaceFrame = AtomicBoolean(false)
    private var faceDetectCount = 0
    private var faceDetectFps = 0

    // Face Recognition (Phase 3)
    private var faceNetModel: FaceNetModel? = null
    private var latestDetectedBox: Rect? = null
    private var latestNv21Bytes: ByteArray? = null
    private var latestFrameWidth = 640
    private var latestFrameHeight = 480
    private var isAutoVerificationEnabled = false

    private var isLogPanelVisible = true
    private val mainHandler = Handler(Looper.getMainLooper())

    private val fpsRunnable = object : Runnable {
        override fun run() {
            fpsA = frameCountA
            frameCountA = 0
            fpsB = frameCountB
            frameCountB = 0

            faceDetectFps = faceDetectCount
            faceDetectCount = 0

            if (_binding != null) {
                updateStatsPanelA()
                updateStatsPanelB()
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKioskCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appendLog("🚀 Khởi tạo Fragment Dual Camera Kiosk & ML Kit...")

        initFaceDetector()
        initFaceRecognitionModel()
        initMultiCameraClient()
        setupListeners()

        mainHandler.post(fpsRunnable)
    }

    private fun initFaceDetector() {
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .build()
            faceDetector = FaceDetection.getClient(options)
            binding.tvFaceStatusB.text = "👤 ML Kit: Sẵn sàng (Ready)"
            appendLog("✅ Khởi tạo Google ML Kit Face Detector thành công.")
        } catch (e: Exception) {
            appendLog("❌ Lỗi khởi tạo ML Kit Face Detector: ${e.message}")
            binding.tvFaceStatusB.text = "⚠️ ML Kit: Lỗi khởi tạo (${e.message})"
        }
    }

    private fun initFaceRecognitionModel() {
        try {
            val ctx = context ?: return
            faceNetModel = FaceNetModel(ctx)
            updateRecognitionStatusUI()
            appendLog("✅ Khởi tạo Face Recognition Model (MobileFaceNet) thành công.")
        } catch (e: Exception) {
            appendLog("⚠️ Khởi tạo Face Recognition Model: ${e.message}")
        }
    }

    private fun initMultiCameraClient() {
        val ctx = context ?: return

        mMultiCameraClient = MultiCameraClient(ctx, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device?.let {
                    appendLog("🔌 USB Attached: ${it.productName ?: it.deviceName} (${getVidPidString(it)})")
                    refreshUsbDevices()
                }
            }

            override fun onDetachDec(device: UsbDevice?) {
                device?.let {
                    appendLog("🔌 USB Detached: ${it.productName ?: it.deviceName}")
                    onDeviceDetached(it)
                }
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                if (device != null && ctrlBlock != null) {
                    appendLog("🟢 USB Connected & Permitted: ${device.productName ?: device.deviceName} (${getVidPidString(device)})")
                    onDeviceConnected(device, ctrlBlock)
                }
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                if (device != null) {
                    appendLog("🔴 USB Disconnected: ${device.productName ?: device.deviceName}")
                    onDeviceDisconnected(device)
                }
            }

            override fun onCancelDev(device: UsbDevice?) {
                if (device != null) {
                    appendLog("⛔ Quyền USB bị từ chối: ${device.productName ?: device.deviceName}")
                    refreshUsbDevices()
                }
            }
        })

        mMultiCameraClient?.register()
        refreshUsbDevices()
    }

    private fun setupListeners() {
        // Panel A Buttons
        binding.btnConnectA.setOnClickListener { connectCameraA() }
        binding.btnStopA.setOnClickListener { closeCameraA() }
        binding.btnCaptureA.setOnClickListener { captureImageA() }

        // Panel B Buttons
        binding.btnConnectB.setOnClickListener { connectCameraB() }
        binding.btnStopB.setOnClickListener { closeCameraB() }
        binding.btnCaptureB.setOnClickListener { captureImageB() }

        // Face Recognition Buttons
        binding.btnRegisterFaceB.setOnClickListener { showRegisterFaceDialog() }
        binding.btnVerifyFaceB.setOnClickListener { toggleAutoVerification() }
        binding.btnClearFacesB.setOnClickListener { clearRegisteredFaceDatabase() }

        // Bottom Bar
        binding.btnRefreshUsb.setOnClickListener {
            appendLog("🔄 Thực hiện quét lại thiết bị USB...")
            refreshUsbDevices()
        }

        binding.btnToggleLogView.setOnClickListener {
            toggleLogPanel()
        }
    }

    fun refreshUsbDevices() {
        val ctx = context ?: return
        val allUsbDevices = UsbDiagnosticHelper.getConnectedUsbDevices(ctx)
        val uvcDevices = allUsbDevices.filter { it.isUvcCamera }

        val sb = StringBuilder()
        sb.append("Phát hiện ${uvcDevices.size} camera UVC (Tổng thiết bị USB: ${allUsbDevices.size}):\n")

        for ((index, devInfo) in uvcDevices.withIndex()) {
            val permText = if (devInfo.hasPermission) "✅ Đã cấp quyền" else "🔐 Chưa cấp quyền"
            sb.append(" • Cam #${index + 1}: ${devInfo.productName ?: devInfo.deviceName} [${devInfo.getFormattedVidPid()}] - $permText\n")
        }

        activity?.runOnUiThread {
            if (_binding != null) {
                binding.tvUsbDevicesInfo.text = sb.toString().trimEnd()

                if (uvcDevices.isEmpty()) {
                    binding.tvMainStatusBadge.text = "KHÔNG CÓ CAM UVC"
                    binding.tvMainStatusBadge.setBackgroundColor(0xFF757575.toInt())
                } else {
                    binding.tvMainStatusBadge.text = "PHÁT HIỆN ${uvcDevices.size} CAM UVC"
                    binding.tvMainStatusBadge.setBackgroundColor(0xFF1565C0.toInt())
                }
            }
        }

        if (uvcDevices.isNotEmpty()) {
            assignedDeviceA = uvcDevices[0].device
            updateDeviceInfoA(uvcDevices[0])
        } else {
            assignedDeviceA = null
            updateDeviceInfoA(null)
        }

        if (uvcDevices.size > 1) {
            assignedDeviceB = uvcDevices[1].device
            updateDeviceInfoB(uvcDevices[1])
        } else {
            assignedDeviceB = null
            updateDeviceInfoB(null)
        }
    }

    private fun updateDeviceInfoA(info: UsbDeviceInfo?) {
        if (info == null) {
            binding.tvDeviceInfoA.text = "Thiết bị: Chưa kết nối"
            binding.tvStatusBadgeA.text = "CHƯA PHÁT HIỆN"
            binding.tvStatusBadgeA.setBackgroundColor(0xFF555555.toInt())
            binding.tvOverlayA.text = "Vui lòng cắm Camera UVC #1"
            binding.tvOverlayA.visibility = View.VISIBLE
        } else {
            binding.tvDeviceInfoA.text = "${info.productName ?: info.deviceName} (${info.getFormattedVidPid()})"
            if (!info.hasPermission) {
                binding.tvStatusBadgeA.text = "CẦN QUYỀN USB"
                binding.tvStatusBadgeA.setBackgroundColor(0xFFE65100.toInt())
                binding.tvOverlayA.text = "Bấm '▶️ Mở' để xin quyền USB"
                binding.tvOverlayA.visibility = View.VISIBLE
            } else if (mCameraA?.isCameraOpened() != true) {
                binding.tvStatusBadgeA.text = "SẴN SÀNG"
                binding.tvStatusBadgeA.setBackgroundColor(0xFF1565C0.toInt())
                binding.tvOverlayA.text = "Bấm '▶️ Mở' để xem luồng video"
                binding.tvOverlayA.visibility = View.VISIBLE
            }
        }
    }

    private fun updateDeviceInfoB(info: UsbDeviceInfo?) {
        if (info == null) {
            binding.tvDeviceInfoB.text = "Thiết bị: Chưa kết nối"
            binding.tvStatusBadgeB.text = "CHƯA PHÁT HIỆN"
            binding.tvStatusBadgeB.setBackgroundColor(0xFF555555.toInt())
            binding.tvOverlayB.text = "Vui lòng cắm Camera UVC #2 (RGB)"
            binding.tvOverlayB.visibility = View.VISIBLE
        } else {
            binding.tvDeviceInfoB.text = "${info.productName ?: info.deviceName} (${info.getFormattedVidPid()})"
            if (!info.hasPermission) {
                binding.tvStatusBadgeB.text = "CẦN QUYỀN USB"
                binding.tvStatusBadgeB.setBackgroundColor(0xFFE65100.toInt())
                binding.tvOverlayB.text = "Bấm '▶️ Mở' để xin quyền USB"
                binding.tvOverlayB.visibility = View.VISIBLE
            } else if (mCameraB?.isCameraOpened() != true) {
                binding.tvStatusBadgeB.text = "SẴN SÀNG"
                binding.tvStatusBadgeB.setBackgroundColor(0xFF1565C0.toInt())
                binding.tvOverlayB.text = "Bấm '▶️ Mở' để xem luồng video"
                binding.tvOverlayB.visibility = View.VISIBLE
            }
        }
    }

    private fun connectCameraA() {
        val device = assignedDeviceA
        if (device == null) {
            Toast.makeText(context, "Chưa tìm thấy thiết bị Camera A!", Toast.LENGTH_SHORT).show()
            appendLog("⚠️ Camera A: Không tìm thấy thiết bị USB.")
            return
        }

        val hasPerm = mMultiCameraClient?.hasPermission(device) == true
        if (!hasPerm) {
            appendLog("🔐 Đang xin quyền USB cho Camera A (${getVidPidString(device)})...")
            mMultiCameraClient?.requestPermission(device)
            return
        }

        appendLog("▶️ Bắt đầu mở luồng video Camera A...")
        mMultiCameraClient?.requestPermission(device)
    }

    private fun connectCameraB() {
        val device = assignedDeviceB
        if (device == null) {
            Toast.makeText(context, "Chưa tìm thấy thiết bị Camera B!", Toast.LENGTH_SHORT).show()
            appendLog("⚠️ Camera B: Không tìm thấy thiết bị USB.")
            return
        }

        val hasPerm = mMultiCameraClient?.hasPermission(device) == true
        if (!hasPerm) {
            appendLog("🔐 Đang xin quyền USB cho Camera B (${getVidPidString(device)})...")
            mMultiCameraClient?.requestPermission(device)
            return
        }

        appendLog("▶️ Bắt đầu mở luồng video Camera B...")
        mMultiCameraClient?.requestPermission(device)
    }

    private fun onDeviceConnected(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        if (assignedDeviceA?.deviceId == device.deviceId || isSameUsbDevice(assignedDeviceA, device)) {
            openCameraPanelA(device, ctrlBlock)
        } else if (assignedDeviceB?.deviceId == device.deviceId || isSameUsbDevice(assignedDeviceB, device)) {
            openCameraPanelB(device, ctrlBlock)
        } else {
            if (assignedDeviceA == null) {
                assignedDeviceA = device
                openCameraPanelA(device, ctrlBlock)
            } else if (assignedDeviceB == null) {
                assignedDeviceB = device
                openCameraPanelB(device, ctrlBlock)
            }
        }
    }

    private fun openCameraPanelA(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        closeCameraA()

        val ctx = context ?: return
        appendLog("🎥 Khởi tạo Camera A: ${device.productName ?: device.deviceName}")

        val camera = MultiCameraClient.Camera(ctx, device)
        camera.setUsbControlBlock(ctrlBlock)
        camera.setCameraStateCallBack(object : ICameraStateCallBack {
            override fun onCameraState(
                self: MultiCameraClient.Camera,
                code: ICameraStateCallBack.State,
                msg: String?
            ) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    when (code) {
                        ICameraStateCallBack.State.OPENED -> {
                            appendLog("🟢 Camera A: Đã mở luồng video thành công!")
                            binding.tvStatusBadgeA.text = "STREAMING"
                            binding.tvStatusBadgeA.setBackgroundColor(0xFF2E7D32.toInt())
                            binding.tvOverlayA.visibility = View.GONE
                        }
                        ICameraStateCallBack.State.CLOSED -> {
                            appendLog("🔴 Camera A: Đã đóng.")
                            binding.tvStatusBadgeA.text = "ĐÃ ĐÓNG"
                            binding.tvStatusBadgeA.setBackgroundColor(0xFF757575.toInt())
                            binding.tvOverlayA.text = "Camera A đã đóng"
                            binding.tvOverlayA.visibility = View.VISIBLE
                        }
                        ICameraStateCallBack.State.ERROR -> {
                            appendLog("❌ Camera A Lỗi: ${msg ?: "Không xác định"}")
                            binding.tvStatusBadgeA.text = "LỖI"
                            binding.tvStatusBadgeA.setBackgroundColor(0xFFB71C1C.toInt())
                            binding.tvOverlayA.text = "Lỗi Camera A: ${msg ?: "Không mở được stream"}"
                            binding.tvOverlayA.visibility = View.VISIBLE
                        }
                    }
                }
            }
        })

        camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
            override fun onPreviewData(data: ByteArray?, format: IPreviewDataCallBack.DataFormat) {
                frameCountA++
            }
        })

        val req = CameraRequest.Builder()
            .setPreviewWidth(640)
            .setPreviewHeight(480)
            .create()

        camera.openCamera(binding.uvcSurfaceViewA, req)
        mCameraA = camera
    }

    private fun openCameraPanelB(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        closeCameraB()

        val ctx = context ?: return
        appendLog("🎥 Khởi tạo Camera B (RGB): ${device.productName ?: device.deviceName}")

        val camera = MultiCameraClient.Camera(ctx, device)
        camera.setUsbControlBlock(ctrlBlock)
        camera.setCameraStateCallBack(object : ICameraStateCallBack {
            override fun onCameraState(
                self: MultiCameraClient.Camera,
                code: ICameraStateCallBack.State,
                msg: String?
            ) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    when (code) {
                        ICameraStateCallBack.State.OPENED -> {
                            appendLog("🟢 Camera B (RGB): Đã mở luồng video thành công!")
                            binding.tvStatusBadgeB.text = "STREAMING"
                            binding.tvStatusBadgeB.setBackgroundColor(0xFF2E7D32.toInt())
                            binding.tvOverlayB.visibility = View.GONE
                            binding.tvFaceStatusB.text = "👤 ML Kit: Đang quét khuôn mặt..."
                        }
                        ICameraStateCallBack.State.CLOSED -> {
                            appendLog("🔴 Camera B: Đã đóng.")
                            binding.tvStatusBadgeB.text = "ĐÃ ĐÓNG"
                            binding.tvStatusBadgeB.setBackgroundColor(0xFF757575.toInt())
                            binding.tvOverlayB.text = "Camera B đã đóng"
                            binding.tvOverlayB.visibility = View.VISIBLE
                            binding.faceOverlayB.clear()
                            binding.tvFaceStatusB.text = "👤 ML Kit: Camera B đã đóng"
                        }
                        ICameraStateCallBack.State.ERROR -> {
                            appendLog("❌ Camera B Lỗi: ${msg ?: "Không xác định"}")
                            binding.tvStatusBadgeB.text = "LỖI"
                            binding.tvStatusBadgeB.setBackgroundColor(0xFFB71C1C.toInt())
                            binding.tvOverlayB.text = "Lỗi Camera B: ${msg ?: "Không mở được stream"}"
                            binding.tvOverlayB.visibility = View.VISIBLE
                            binding.faceOverlayB.clear()
                            binding.tvFaceStatusB.text = "⚠️ ML Kit: Tạm dừng do lỗi Camera B"
                        }
                    }
                }
            }
        })

        camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
            override fun onPreviewData(data: ByteArray?, format: IPreviewDataCallBack.DataFormat) {
                frameCountB++
                if (data != null) {
                    processFaceDetectionFrame(data)
                }
            }
        })

        val req = CameraRequest.Builder()
            .setPreviewWidth(640)
            .setPreviewHeight(480)
            .create()

        camera.openCamera(binding.uvcSurfaceViewB, req)
        mCameraB = camera
    }

    private fun processFaceDetectionFrame(nv21Data: ByteArray) {
        val detector = faceDetector ?: return
        if (isProcessingFaceFrame.get()) {
            return
        }

        val previewSize = mCameraB?.getPreviewSize()
        val width = previewSize?.width ?: 640
        val height = previewSize?.height ?: 480

        if (width <= 0 || height <= 0) return

        isProcessingFaceFrame.set(true)

        try {
            val frameBytes = nv21Data.clone()
            val inputImage = InputImage.fromByteArray(
                frameBytes,
                width,
                height,
                0,
                InputImage.IMAGE_FORMAT_NV21
            )

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    faceDetectCount++
                    activity?.runOnUiThread {
                        if (_binding != null) {
                            if (faces.isEmpty()) {
                                latestDetectedBox = null
                                latestNv21Bytes = null
                                binding.faceOverlayB.clear()
                                binding.tvFaceStatusB.text = "👤 ML Kit: Không có khuôn mặt (0 face) | Detect FPS: $faceDetectFps"
                            } else {
                                val face = faces[0]
                                latestDetectedBox = face.boundingBox
                                latestNv21Bytes = frameBytes
                                latestFrameWidth = width
                                latestFrameHeight = height

                                binding.faceOverlayB.setFaces(faces, width, height)
                                binding.tvFaceStatusB.text = "👤 ML Kit: Phát hiện ${faces.size} khuôn mặt | Detect FPS: $faceDetectFps"

                                if (isAutoVerificationEnabled && UserFaceRepository.getUserCount() > 0) {
                                    performFaceVerification(frameBytes, width, height, face.boundingBox)
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    activity?.runOnUiThread {
                        if (_binding != null) {
                            binding.tvFaceStatusB.text = "⚠️ ML Kit Lỗi: ${e.message}"
                        }
                    }
                }
                .addOnCompleteListener {
                    isProcessingFaceFrame.set(false)
                }
        } catch (_: Exception) {
            isProcessingFaceFrame.set(false)
        }
    }

    private fun showRegisterFaceDialog() {
        val box = latestDetectedBox
        val nv21 = latestNv21Bytes
        if (box == null || nv21 == null) {
            Toast.makeText(context, "Vui lòng đứng trước Camera B để nhận diện khuôn mặt!", Toast.LENGTH_SHORT).show()
            appendLog("⚠️ Đăng ký thất bại: Không tìm thấy khuôn mặt trong khung hình.")
            return
        }

        val ctx = context ?: return
        val inputName = EditText(ctx).apply {
            hint = "Nhập tên người dùng (VD: Nguyễn Văn A)"
            setPadding(32, 24, 32, 24)
        }

        AlertDialog.Builder(ctx)
            .setTitle("📝 Đăng ký Khuôn mặt Mới")
            .setMessage("Hệ thống sẽ trích xuất vector đặc trưng 192 chiều (MobileFaceNet) cho người dùng này:")
            .setView(inputName)
            .setPositiveButton("Lưu Đăng Ký") { _, _ ->
                val name = inputName.text.toString().trim()
                if (name.isNotEmpty()) {
                    registerFaceForUser(name, nv21, latestFrameWidth, latestFrameHeight, box)
                } else {
                    Toast.makeText(ctx, "Tên người dùng không được để trống!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun registerFaceForUser(
        userName: String,
        nv21: ByteArray,
        width: Int,
        height: Int,
        box: Rect
    ) {
        val model = faceNetModel
        if (model == null) {
            Toast.makeText(context, "Model Face Recognition chưa sẵn sàng!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val croppedFace = NV21ToBitmapConverter.cropFaceBitmap(nv21, width, height, box)
            if (croppedFace == null) {
                Toast.makeText(context, "Lỗi trích xuất vùng ảnh khuôn mặt!", Toast.LENGTH_SHORT).show()
                return
            }

            val embedding = model.extractEmbedding(croppedFace)
            croppedFace.recycle()

            val registeredUser = UserFaceRepository.registerUser(userName, embedding)
            appendLog("✅ Đã đăng ký thành công: ${registeredUser.name} [ID: ${registeredUser.id}]")
            Toast.makeText(context, "✅ Đã đăng ký khuôn mặt cho ${registeredUser.name}!", Toast.LENGTH_SHORT).show()

            updateRecognitionStatusUI()
        } catch (e: Exception) {
            appendLog("❌ Lỗi đăng ký khuôn mặt: ${e.message}")
            Toast.makeText(context, "Lỗi đăng ký: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleAutoVerification() {
        if (UserFaceRepository.getUserCount() == 0) {
            Toast.makeText(context, "Chưa có mẫu khuôn mặt nào! Hãy nhấn '📝 Đăng ký' trước.", Toast.LENGTH_SHORT).show()
            appendLog("⚠️ So khớp thất bại: Cơ sở dữ liệu đăng ký đang trống.")
            return
        }

        isAutoVerificationEnabled = !isAutoVerificationEnabled
        if (isAutoVerificationEnabled) {
            binding.btnVerifyFaceB.text = "🔍 Tắt So khớp"
            appendLog("🔍 Bật chế độ tự động So khớp khuôn mặt thời gian thực.")
            Toast.makeText(context, "Bật tự động So khớp khuôn mặt", Toast.LENGTH_SHORT).show()

            val box = latestDetectedBox
            val nv21 = latestNv21Bytes
            if (box != null && nv21 != null) {
                performFaceVerification(nv21, latestFrameWidth, latestFrameHeight, box)
            }
        } else {
            binding.btnVerifyFaceB.text = "🔍 So khớp"
            appendLog("🔍 Tắt chế độ tự động So khớp.")
            Toast.makeText(context, "Tắt So khớp khuôn mặt", Toast.LENGTH_SHORT).show()
            updateRecognitionStatusUI()
        }
    }

    private fun performFaceVerification(
        nv21: ByteArray,
        width: Int,
        height: Int,
        box: Rect
    ) {
        val model = faceNetModel ?: return
        val croppedFace = NV21ToBitmapConverter.cropFaceBitmap(nv21, width, height, box) ?: return

        try {
            val liveEmbedding = model.extractEmbedding(croppedFace)
            croppedFace.recycle()

            val matchResult = UserFaceRepository.findBestMatch(liveEmbedding, threshold = 0.65f)

            if (matchResult != null) {
                val (matchedUser, similarity) = matchResult
                val percentStr = String.format(Locale.getDefault(), "%.1f%%", similarity * 100)
                val resultText = "✅ KHỚP: ${matchedUser.name} (Độ tương đồng: $percentStr)"
                binding.tvRecognitionResultB.text = resultText
                binding.tvRecognitionResultB.setTextColor(0xFF00E676.toInt())
            } else {
                binding.tvRecognitionResultB.text = "❌ Không trùng khớp với người dùng nào trong DB"
                binding.tvRecognitionResultB.setTextColor(0xFFFF5252.toInt())
            }
        } catch (e: Exception) {
            binding.tvRecognitionResultB.text = "⚠️ Lỗi so khớp: ${e.message}"
        }
    }

    private fun clearRegisteredFaceDatabase() {
        val count = UserFaceRepository.getUserCount()
        if (count == 0) {
            Toast.makeText(context, "Cơ sở dữ liệu vốn đã trống!", Toast.LENGTH_SHORT).show()
            return
        }

        UserFaceRepository.clearAllUsers()
        isAutoVerificationEnabled = false
        binding.btnVerifyFaceB.text = "🔍 So khớp"
        appendLog("🗑️ Đã xóa sạch $count mẫu khuôn mặt đã đăng ký.")
        Toast.makeText(context, "Đã xóa sạch cơ sở dữ liệu đăng ký!", Toast.LENGTH_SHORT).show()
        updateRecognitionStatusUI()
    }

    private fun updateRecognitionStatusUI() {
        if (_binding == null) return
        val count = UserFaceRepository.getUserCount()
        if (count == 0) {
            binding.tvRecognitionResultB.text = "🆔 Nhận diện: Chưa có dữ liệu đăng ký"
            binding.tvRecognitionResultB.setTextColor(0xFFFFB74D.toInt())
        } else {
            binding.tvRecognitionResultB.text = "🆔 Đã đăng ký $count mẫu | Chờ so khớp..."
            binding.tvRecognitionResultB.setTextColor(0xFF29B6F6.toInt())
        }
    }

    private fun closeCameraA() {
        try {
            if (mCameraA?.isCameraOpened() == true) {
                mCameraA?.closeCamera()
                appendLog("⏹️ Đã đóng Camera A.")
            }
            mCameraA = null
        } catch (e: Exception) {
            appendLog("⚠️ Lỗi khi đóng Camera A: ${e.message}")
        }
    }

    private fun closeCameraB() {
        try {
            if (mCameraB?.isCameraOpened() == true) {
                mCameraB?.closeCamera()
                appendLog("⏹️ Đã đóng Camera B.")
            }
            mCameraB = null
            if (_binding != null) {
                binding.faceOverlayB.clear()
                binding.tvFaceStatusB.text = "👤 ML Kit: Camera B đã đóng"
                updateRecognitionStatusUI()
            }
        } catch (e: Exception) {
            appendLog("⚠️ Lỗi khi đóng Camera B: ${e.message}")
        }
    }

    private fun captureImageA() {
        val camera = mCameraA
        if (camera?.isCameraOpened() != true) {
            Toast.makeText(context, "Camera A chưa mở luồng video!", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val photoFile = File(storageDir, "KIOSK_CAM_A_$timestamp.jpg")

        appendLog("📸 Camera A: Đang thực hiện chụp ảnh...")
        camera.captureImage(object : ICaptureCallBack {
            override fun onBegin() {
                appendLog("📸 Camera A: Đang ghi hình...")
            }

            override fun onComplete(path: String?) {
                val msg = "✅ Camera A Chụp ảnh thành công! Đã lưu: ${path ?: photoFile.absolutePath}"
                appendLog(msg)
                activity?.runOnUiThread {
                    Toast.makeText(context, "Camera A: Chụp ảnh thành công!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String?) {
                val msg = "❌ Camera A Lỗi chụp ảnh: ${error ?: "Chưa rõ"}"
                appendLog(msg)
                activity?.runOnUiThread {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }, photoFile.absolutePath)
    }

    private fun captureImageB() {
        val camera = mCameraB
        if (camera?.isCameraOpened() != true) {
            Toast.makeText(context, "Camera B chưa mở luồng video!", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val photoFile = File(storageDir, "KIOSK_CAM_B_$timestamp.jpg")

        appendLog("📸 Camera B: Đang thực hiện chụp ảnh...")
        camera.captureImage(object : ICaptureCallBack {
            override fun onBegin() {
                appendLog("📸 Camera B: Đang ghi hình...")
            }

            override fun onComplete(path: String?) {
                val msg = "✅ Camera B Chụp ảnh thành công! Đã lưu: ${path ?: photoFile.absolutePath}"
                appendLog(msg)
                activity?.runOnUiThread {
                    Toast.makeText(context, "Camera B: Chụp ảnh thành công!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String?) {
                val msg = "❌ Camera B Lỗi chụp ảnh: ${error ?: "Chưa rõ"}"
                appendLog(msg)
                activity?.runOnUiThread {
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }, photoFile.absolutePath)
    }

    private fun onDeviceDetached(device: UsbDevice) {
        if (assignedDeviceA?.deviceId == device.deviceId || isSameUsbDevice(assignedDeviceA, device)) {
            appendLog("🔌 Ngắt kết nối USB Camera A")
            closeCameraA()
        }
        if (assignedDeviceB?.deviceId == device.deviceId || isSameUsbDevice(assignedDeviceB, device)) {
            appendLog("🔌 Ngắt kết nối USB Camera B")
            closeCameraB()
        }
        refreshUsbDevices()
    }

    private fun onDeviceDisconnected(device: UsbDevice) {
        if (assignedDeviceA?.deviceId == device.deviceId || isSameUsbDevice(assignedDeviceA, device)) {
            closeCameraA()
        }
        if (assignedDeviceB?.deviceId == device.deviceId || isSameUsbDevice(assignedDeviceB, device)) {
            closeCameraB()
        }
    }

    fun onUsbPermissionGranted(device: UsbDevice) {
        appendLog("✅ Được cấp quyền USB cho: ${device.productName ?: device.deviceName}")
        refreshUsbDevices()
        mMultiCameraClient?.requestPermission(device)
    }

    fun onCameraConnected(device: UsbDevice) {
        appendLog("🔌 MainActivity USB Attached: ${device.productName ?: device.deviceName}")
        refreshUsbDevices()
    }

    fun onCameraDisconnected(device: UsbDevice) {
        appendLog("🔌 MainActivity USB Detached: ${device.productName ?: device.deviceName}")
        onDeviceDetached(device)
    }

    private fun updateStatsPanelA() {
        val size = mCameraA?.getPreviewSize()
        val sizeStr = if (size != null) "${size.width}x${size.height}" else "640x480"
        binding.tvStatsA.text = "Độ phân giải: $sizeStr | FPS: $fpsA"
    }

    private fun updateStatsPanelB() {
        val size = mCameraB?.getPreviewSize()
        val sizeStr = if (size != null) "${size.width}x${size.height}" else "640x480"
        binding.tvStatsB.text = "Độ phân giải: $sizeStr | FPS: $fpsB"
    }

    private fun toggleLogPanel() {
        isLogPanelVisible = !isLogPanelVisible
        binding.scrollDiagnosticPanel.visibility = if (isLogPanelVisible) View.VISIBLE else View.GONE
        binding.btnToggleLogView.text = if (isLogPanelVisible) "📊 Ẩn Log" else "📊 Hiện Log"
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

    private fun getVidPidString(device: UsbDevice): String {
        return String.format("VID:0x%04X, PID:0x%04X", device.vendorId, device.productId)
    }

    private fun isSameUsbDevice(dev1: UsbDevice?, dev2: UsbDevice?): Boolean {
        if (dev1 == null || dev2 == null) return false
        return dev1.vendorId == dev2.vendorId && dev1.productId == dev2.productId && dev1.deviceName == dev2.deviceName
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacks(fpsRunnable)

        closeCameraA()
        closeCameraB()

        try {
            faceNetModel?.close()
            faceNetModel = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            faceDetector?.close()
            faceDetector = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            mMultiCameraClient?.unRegister()
            mMultiCameraClient?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _binding = null
    }
}
