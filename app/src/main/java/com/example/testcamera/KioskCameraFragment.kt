package com.example.testcamera

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.testcamera.data.KioskSessionManager
import com.example.testcamera.data.Student
import com.example.testcamera.data.StudentRepository
import com.example.testcamera.data.UserFaceModel
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

    enum class VerificationState {
        CAMERA_CONNECTING,
        IDLE,
        DETECTING,
        VERIFYING,
        SUCCESS,
        UNRECOGNIZED
    }

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

    // Face Recognition (Phase 3 & 4)
    private var faceNetModel: FaceNetModel? = null
    private var latestDetectedBox: Rect? = null
    private var latestNv21Bytes: ByteArray? = null
    private var latestFrameWidth = 640
    private var latestFrameHeight = 480
    private var isAutoVerificationEnabled = true
    private var isNavigatingToProfile = false

    private var currentVerificationState = VerificationState.CAMERA_CONNECTING
    private var isTechModeVisible = false
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

        appendLog("🚀 Khởi tạo Fragment Dual Camera Kiosk & Student DB...")

        val ctx = requireContext()
        UserFaceRepository.init(ctx)
        isNavigatingToProfile = false
        isAutoVerificationEnabled = true

        initFaceDetector()
        initFaceRecognitionModel()
        initMultiCameraClient()
        setupListeners()

        setVerificationState(VerificationState.CAMERA_CONNECTING)

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
            if (faceNetModel?.isModelReady() == true) {
                appendLog("✅ Khởi tạo Face Recognition Model (MobileFaceNet TFLite) thành công.")
            } else {
                appendLog("ℹ️ Khởi tạo Face Recognition Model (Chế độ trích xuất vi phân - sẵn sàng nạp MobileFaceNet TFLite).")
            }
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
        // Navigation Buttons
        binding.btnBackToHome.setOnClickListener {
            (activity as? MainActivity)?.navigateToHome()
        }

        binding.btnToggleTechMode.setOnClickListener {
            toggleTechMode()
        }

        binding.btnRetryVerification.setOnClickListener {
            setVerificationState(VerificationState.IDLE)
            isAutoVerificationEnabled = true
        }

        // Panel A Buttons
        binding.btnConnectA.setOnClickListener { connectCameraA() }
        binding.btnStopA.setOnClickListener { closeCameraA() }
        binding.btnCaptureA.setOnClickListener { captureImageA() }

        // Panel B Buttons
        binding.btnConnectB.setOnClickListener { connectCameraB() }
        binding.btnStopB.setOnClickListener { closeCameraB() }
        binding.btnCaptureB.setOnClickListener { captureImageB() }

        // Shared Face Recognition & Student Registration Buttons
        binding.btnRegisterFaceB.setOnClickListener { showStudentPickerRegistrationDialog() }
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

    private fun toggleTechMode() {
        isTechModeVisible = !isTechModeVisible
        if (isTechModeVisible) {
            binding.layoutTechnicalPanel.visibility = View.VISIBLE
            binding.layoutStudentVerification.visibility = View.GONE
            binding.btnToggleTechMode.text = "👤 Chế độ SV"
            binding.tvCameraScreenTitle.text = "BẢNG KỸ THUẬT & CAMERA"
        } else {
            binding.layoutTechnicalPanel.visibility = View.GONE
            binding.layoutStudentVerification.visibility = View.VISIBLE
            binding.btnToggleTechMode.text = "⚙️ Kỹ thuật"
            binding.tvCameraScreenTitle.text = "XÁC THỰC DANH TÍNH SINH VIÊN"
        }
    }

    private fun setVerificationState(state: VerificationState, student: Student? = null) {
        if (_binding == null) return
        currentVerificationState = state
        val ctx = context ?: return

        when (state) {
            VerificationState.CAMERA_CONNECTING -> {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_face_scan)
                binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.status_info))
                binding.tvStatusTitle.text = "Đang kết nối camera Kiosk..."
                binding.tvStatusMessage.text = "Vui lòng đợi thiết bị khởi tạo kết nối UVC"
                binding.btnRetryVerification.visibility = View.GONE
                binding.layoutSuccessBanner.visibility = View.GONE
            }
            VerificationState.IDLE -> {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_face_scan)
                binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.hust_red))
                binding.tvStatusTitle.text = "Chờ xác thực"
                binding.tvStatusMessage.text = "Vui lòng nhìn thẳng vào khung camera để nhận diện"
                binding.btnRetryVerification.visibility = View.GONE
                binding.layoutSuccessBanner.visibility = View.GONE
                binding.faceOverlayB.setHighlightColor(ContextCompat.getColor(ctx, R.color.hust_gold))
            }
            VerificationState.DETECTING -> {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_face_scan)
                binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.hust_gold))
                binding.tvStatusTitle.text = "Đã phát hiện khuôn mặt"
                binding.tvStatusMessage.text = "Vui lòng giữ nguyên vị trí trong khung hình..."
                binding.btnRetryVerification.visibility = View.GONE
                binding.layoutSuccessBanner.visibility = View.GONE
                binding.faceOverlayB.setHighlightColor(ContextCompat.getColor(ctx, R.color.hust_gold))
            }
            VerificationState.VERIFYING -> {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_face_scan)
                binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.hust_red))
                binding.tvStatusTitle.text = "Đang xác minh danh tính..."
                binding.tvStatusMessage.text = "Đang đối soát dữ liệu với CSDL sinh viên..."
                binding.btnRetryVerification.visibility = View.GONE
                binding.layoutSuccessBanner.visibility = View.GONE
            }
            VerificationState.SUCCESS -> {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.status_success))
                binding.tvStatusTitle.text = "Xác thực thành công!"
                binding.tvStatusMessage.text = if (student != null) "Xin chào: ${student.fullName} (${student.studentId})" else "Nhận diện sinh viên thành công"
                binding.btnRetryVerification.visibility = View.GONE

                if (student != null) {
                    binding.tvSuccessStudentName.text = "Xin chào: ${student.fullName}"
                    binding.tvSuccessStudentId.text = "MSSV: ${student.studentId} • Lớp: ${student.className}"
                }
                binding.layoutSuccessBanner.visibility = View.VISIBLE
                binding.faceOverlayB.setHighlightColor(ContextCompat.getColor(ctx, R.color.status_success))
            }
            VerificationState.UNRECOGNIZED -> {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_alert)
                binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.status_warning))
                binding.tvStatusTitle.text = "Không xác minh được"
                binding.tvStatusMessage.text = "Không tìm thấy thông tin sinh viên phù hợp trong CSDL"
                binding.btnRetryVerification.visibility = View.VISIBLE
                binding.layoutSuccessBanner.visibility = View.GONE
                binding.faceOverlayB.setHighlightColor(ContextCompat.getColor(ctx, R.color.status_error))
            }
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
            binding.tvOverlayA.text = "Chưa cắm Cam A"
            binding.tvOverlayA.visibility = View.VISIBLE
        } else {
            binding.tvDeviceInfoA.text = "${info.productName ?: info.deviceName} (${info.getFormattedVidPid()})"
            if (!info.hasPermission) {
                binding.tvStatusBadgeA.text = "CẦN QUYỀN USB"
                binding.tvStatusBadgeA.setBackgroundColor(0xFFE65100.toInt())
                binding.tvOverlayA.text = "Cần cấp quyền"
                binding.tvOverlayA.visibility = View.VISIBLE
            } else if (mCameraA?.isCameraOpened() != true) {
                binding.tvStatusBadgeA.text = "SẴN SÀNG"
                binding.tvStatusBadgeA.setBackgroundColor(0xFF1565C0.toInt())
                binding.tvOverlayA.text = "Sẵn sàng mở"
                binding.tvOverlayA.visibility = View.VISIBLE
            }
        }
    }

    private fun updateDeviceInfoB(info: UsbDeviceInfo?) {
        if (info == null) {
            binding.tvDeviceInfoB.text = "Thiết bị: Chưa kết nối"
            binding.tvStatusBadgeB.text = "CHƯA PHÁT HIỆN"
            binding.tvStatusBadgeB.setBackgroundColor(0xFF555555.toInt())
            binding.tvOverlayB.text = "Vui lòng cắm Camera UVC RGB"
            binding.tvOverlayB.visibility = View.VISIBLE
            setVerificationState(VerificationState.CAMERA_CONNECTING)
        } else {
            binding.tvDeviceInfoB.text = "${info.productName ?: info.deviceName} (${info.getFormattedVidPid()})"
            if (!info.hasPermission) {
                binding.tvStatusBadgeB.text = "CẦN QUYỀN USB"
                binding.tvStatusBadgeB.setBackgroundColor(0xFFE65100.toInt())
                binding.tvOverlayB.text = "Bấm '⚙️ Kỹ thuật' để cấp quyền USB Camera"
                binding.tvOverlayB.visibility = View.VISIBLE
                setVerificationState(VerificationState.CAMERA_CONNECTING)
            } else if (mCameraB?.isCameraOpened() != true) {
                binding.tvStatusBadgeB.text = "SẴN SÀNG"
                binding.tvStatusBadgeB.setBackgroundColor(0xFF1565C0.toInt())
                binding.tvOverlayB.text = "Camera sẵn sàng. Đang kết nối luồng video..."
                binding.tvOverlayB.visibility = View.VISIBLE
                // Tự động kết nối Camera B nếu đã có quyền
                connectCameraB()
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
                            binding.tvOverlayA.text = "Lỗi Cam A: ${msg ?: "Lỗi"}"
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
                            setVerificationState(VerificationState.IDLE)
                        }
                        ICameraStateCallBack.State.CLOSED -> {
                            appendLog("🔴 Camera B: Đã đóng.")
                            binding.tvStatusBadgeB.text = "ĐÃ ĐÓNG"
                            binding.tvStatusBadgeB.setBackgroundColor(0xFF757575.toInt())
                            binding.tvOverlayB.text = "Camera B đã đóng"
                            binding.tvOverlayB.visibility = View.VISIBLE
                            binding.faceOverlayB.clear()
                            binding.tvFaceStatusB.text = "👤 ML Kit: Camera B đã đóng"
                            setVerificationState(VerificationState.CAMERA_CONNECTING)
                        }
                        ICameraStateCallBack.State.ERROR -> {
                            appendLog("❌ Camera B Lỗi: ${msg ?: "Không xác định"}")
                            binding.tvStatusBadgeB.text = "LỖI"
                            binding.tvStatusBadgeB.setBackgroundColor(0xFFB71C1C.toInt())
                            binding.tvOverlayB.text = "Lỗi Camera B: ${msg ?: "Không mở được stream"}"
                            binding.tvOverlayB.visibility = View.VISIBLE
                            binding.faceOverlayB.clear()
                            binding.tvFaceStatusB.text = "⚠️ ML Kit: Tạm dừng do lỗi Camera B"
                            setVerificationState(VerificationState.CAMERA_CONNECTING)
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
        if (isProcessingFaceFrame.get() || isNavigatingToProfile) {
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

                                if (currentVerificationState == VerificationState.DETECTING || currentVerificationState == VerificationState.VERIFYING) {
                                    setVerificationState(VerificationState.IDLE)
                                }
                            } else {
                                val face = faces[0]
                                latestDetectedBox = face.boundingBox
                                latestNv21Bytes = frameBytes
                                latestFrameWidth = width
                                latestFrameHeight = height

                                binding.faceOverlayB.setFaces(faces, width, height)
                                binding.tvFaceStatusB.text = "👤 ML Kit: Phát hiện ${faces.size} khuôn mặt | Detect FPS: $faceDetectFps"

                                if (currentVerificationState != VerificationState.SUCCESS && !isNavigatingToProfile) {
                                    setVerificationState(VerificationState.DETECTING)
                                }

                                val ctx = context
                                if (isAutoVerificationEnabled && ctx != null && UserFaceRepository.getUserCount(ctx) > 0 && !isNavigatingToProfile && currentVerificationState != VerificationState.UNRECOGNIZED) {
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

    private fun showStudentPickerRegistrationDialog() {
        val box = latestDetectedBox
        val nv21 = latestNv21Bytes
        if (box == null || nv21 == null) {
            Toast.makeText(context, "Vui lòng đứng trước Camera B để nhận diện khuôn mặt!", Toast.LENGTH_SHORT).show()
            appendLog("⚠️ Đăng ký thất bại: Không tìm thấy khuôn mặt trong khung hình.")
            return
        }

        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_student_picker, null)

        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchStudent)
        val containerList = dialogView.findViewById<LinearLayout>(R.id.containerStudentList)
        val cardDetails = dialogView.findViewById<LinearLayout>(R.id.cardSelectedStudentDetails)
        val tvId = dialogView.findViewById<TextView>(R.id.tvSelectedStudentId)
        val tvName = dialogView.findViewById<TextView>(R.id.tvSelectedStudentName)
        val tvClass = dialogView.findViewById<TextView>(R.id.tvSelectedStudentClass)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvSelectedStudentEmail)

        var selectedStudent: Student? = null

        fun renderStudentList(students: List<Student>) {
            containerList.removeAllViews()
            if (students.isEmpty()) {
                val emptyTv = TextView(ctx).apply {
                    text = "Không tìm thấy sinh viên nào khớp từ khóa"
                    setTextColor(0xFFAAAAAA.toInt())
                    setPadding(16, 16, 16, 16)
                    textSize = 12f
                }
                containerList.addView(emptyTv)
                return
            }

            for (st in students) {
                val registeredFace = UserFaceRepository.findUserByStudentId(st.studentId)
                val isReg = registeredFace != null

                val itemView = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 12, 16, 12)
                    setBackgroundColor(if (selectedStudent?.studentId == st.studentId) 0xFF333333.toInt() else Color.TRANSPARENT)

                    val titleTv = TextView(ctx).apply {
                        text = "MSSV: ${st.studentId} - ${st.fullName} ${if (isReg) "✅ (Đã đăng ký)" else ""}"
                        setTextColor(if (isReg) 0xFF00E676.toInt() else Color.WHITE)
                        textSize = 12f
                        paint.isFakeBoldText = true
                    }

                    val subTv = TextView(ctx).apply {
                        text = "Lớp: ${st.className} | Khoa: ${st.faculty}"
                        setTextColor(0xFFBBBBBB.toInt())
                        textSize = 11f
                    }

                    addView(titleTv)
                    addView(subTv)

                    setOnClickListener {
                        selectedStudent = st
                        renderStudentList(students)

                        cardDetails.visibility = View.VISIBLE
                        tvId.text = "Mã SV: ${st.studentId} ${if (isReg) "(Đã có dữ liệu khuôn mặt)" else ""}"
                        tvName.text = "Họ tên: ${st.fullName}"
                        tvClass.text = "Lớp: ${st.className} | Khoa: ${st.faculty}"
                        tvEmail.text = "Email: ${st.email}"
                    }
                }
                containerList.addView(itemView)
            }
        }

        renderStudentList(StudentRepository.getAllStudents())

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                renderStudentList(StudentRepository.searchStudents(query))
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        AlertDialog.Builder(ctx)
            .setView(dialogView)
            .setPositiveButton("Xác nhận Đăng ký") { _, _ ->
                val st = selectedStudent
                if (st != null) {
                    checkAndProcessStudentRegistration(ctx, st, nv21, latestFrameWidth, latestFrameHeight, box)
                } else {
                    Toast.makeText(ctx, "Vui lòng chọn một sinh viên từ danh sách!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun checkAndProcessStudentRegistration(
        ctx: Context,
        student: Student,
        nv21: ByteArray,
        width: Int,
        height: Int,
        box: Rect
    ) {
        val model = faceNetModel
        if (model == null) {
            Toast.makeText(ctx, "Model Face Recognition chưa sẵn sàng!", Toast.LENGTH_SHORT).show()
            return
        }

        val croppedFace = NV21ToBitmapConverter.cropFaceBitmap(nv21, width, height, box)
        if (croppedFace == null) {
            Toast.makeText(ctx, "Lỗi trích xuất vùng ảnh khuôn mặt!", Toast.LENGTH_SHORT).show()
            return
        }

        val embedding = model.extractEmbedding(croppedFace)
        croppedFace.recycle()

        // 1. Check duplicate Student ID
        val existingStudentFace = UserFaceRepository.findUserByStudentId(student.studentId)
        if (existingStudentFace != null) {
            AlertDialog.Builder(ctx)
                .setTitle("⚠️ Sinh Viên Đã Đăng Ký Khuôn Mặt")
                .setMessage("Sinh viên ${student.fullName} (MSSV: ${student.studentId}) đã đăng ký khuôn mặt trước đó!\nBạn có muốn CẬP NHẬT LẠI khuôn mặt mới không?")
                .setPositiveButton("Cập nhật mới") { _, _ ->
                    val updatedUser = UserFaceModel(student.studentId, student.fullName, embedding)
                    UserFaceRepository.updateStudentFace(ctx, updatedUser)
                    appendLog("🔄 Đã cập nhật lại mẫu khuôn mặt cho sinh viên: ${student.fullName} (${student.studentId})")
                    Toast.makeText(ctx, "✅ Đã cập nhật khuôn mặt cho ${student.fullName}!", Toast.LENGTH_SHORT).show()
                    updateRecognitionStatusUI()
                }
                .setNegativeButton("Hủy", null)
                .show()
            return
        }

        // 2. Check duplicate Face Embedding (threshold 0.72)
        val duplicateFaceMatch = UserFaceRepository.checkDuplicateFace(embedding, duplicateThreshold = 0.72f)
        if (duplicateFaceMatch != null) {
            val (dupUser, similarity) = duplicateFaceMatch
            val dupStudent = StudentRepository.findStudentById(dupUser.studentId)
            val dupName = dupStudent?.fullName ?: dupUser.name
            val percentStr = String.format(Locale.getDefault(), "%.1f%%", similarity * 100)

            AlertDialog.Builder(ctx)
                .setTitle("🚨 Cảnh Báo Trùng Khuôn Mặt")
                .setMessage("Khuôn mặt này trùng khớp $percentStr với sinh viên '$dupName' (MSSV: ${dupUser.studentId}) đã đăng ký!\nBạn muốn xử lý thế nào?")
                .setPositiveButton("Cập nhật cho '$dupName'") { _, _ ->
                    val updatedUser = UserFaceModel(dupUser.studentId, dupName, embedding)
                    UserFaceRepository.updateStudentFace(ctx, updatedUser)
                    appendLog("🔄 Đã cập nhật khuôn mặt cho sinh viên cũ: $dupName (${dupUser.studentId})")
                    Toast.makeText(ctx, "✅ Đã cập nhật khuôn mặt cho $dupName!", Toast.LENGTH_SHORT).show()
                    updateRecognitionStatusUI()
                }
                .setNeutralButton("Vẫn liên kết cho '${student.fullName}'") { _, _ ->
                    val registeredUser = UserFaceRepository.registerStudentFace(ctx, student.studentId, student.fullName, embedding)
                    appendLog("✅ Đã liên kết khuôn mặt cho sinh viên mới: ${registeredUser.name} (${registeredUser.studentId})")
                    Toast.makeText(ctx, "✅ Đã đăng ký khuôn mặt cho ${registeredUser.name}!", Toast.LENGTH_SHORT).show()
                    updateRecognitionStatusUI()
                }
                .setNegativeButton("Hủy", null)
                .show()
            return
        }

        // 3. Normal registration
        val registeredUser = UserFaceRepository.registerStudentFace(ctx, student.studentId, student.fullName, embedding)
        appendLog("✅ Đã đăng ký khuôn mặt thành công: ${registeredUser.name} (${registeredUser.studentId})")
        Toast.makeText(ctx, "✅ Đã đăng ký khuôn mặt cho ${registeredUser.name}!", Toast.LENGTH_SHORT).show()
        updateRecognitionStatusUI()
    }

    private fun toggleAutoVerification() {
        val ctx = context ?: return
        if (UserFaceRepository.getUserCount(ctx) == 0) {
            Toast.makeText(ctx, "Chưa có mẫu khuôn mặt nào! Hãy nhấn '📝 Đăng ký' trước.", Toast.LENGTH_SHORT).show()
            appendLog("⚠️ So khớp thất bại: Cơ sở dữ liệu đăng ký đang trống.")
            return
        }

        isAutoVerificationEnabled = !isAutoVerificationEnabled
        if (isAutoVerificationEnabled) {
            binding.btnVerifyFaceB.text = "🔍 Tắt So khớp"
            appendLog("🔍 Bật chế độ tự động So khớp khuôn mặt thời gian thực.")
            Toast.makeText(ctx, "Bật tự động So khớp khuôn mặt", Toast.LENGTH_SHORT).show()

            val box = latestDetectedBox
            val nv21 = latestNv21Bytes
            if (box != null && nv21 != null) {
                performFaceVerification(nv21, latestFrameWidth, latestFrameHeight, box)
            }
        } else {
            binding.btnVerifyFaceB.text = "🔍 So khớp"
            appendLog("🔍 Tắt chế độ tự động So khớp.")
            Toast.makeText(ctx, "Tắt So khớp khuôn mặt", Toast.LENGTH_SHORT).show()
            updateRecognitionStatusUI()
        }
    }

    private fun performFaceVerification(
        nv21: ByteArray,
        width: Int,
        height: Int,
        box: Rect
    ) {
        if (isNavigatingToProfile) return

        val model = faceNetModel
        if (model == null) {
            appendLog("⚠️ [FR] FaceNetModel chưa khởi tạo - từ chối xác thực")
            return
        }

        val ctx = context ?: return
        val userCount = UserFaceRepository.getUserCount(ctx)
        if (userCount == 0) {
            appendLog("⚠️ [FR] Chưa có sinh viên nào đăng ký trong DB - không thực hiện so khớp")
            return
        }

        appendLog("🔍 [FR] Bắt đầu xác thực: Crop ${box.width()}x${box.height()}px | DB có $userCount sinh viên | Ngưỡng: 65.0%")

        val croppedFace = NV21ToBitmapConverter.cropFaceBitmap(nv21, width, height, box)
        if (croppedFace == null) {
            appendLog("⚠️ [FR] Tiền xử lý thất bại: crop kích thước không hợp lệ (<10px) - từ chối xác thực")
            return
        }

        try {
            setVerificationState(VerificationState.VERIFYING)

            val liveEmbedding = model.extractEmbedding(croppedFace)
            croppedFace.recycle()

            // Validate embedding vector
            val isEmbeddingValid = liveEmbedding.size == FaceNetModel.EMBEDDING_SIZE &&
                    liveEmbedding.any { it != 0f } &&
                    liveEmbedding.none { it.isNaN() }

            if (!isEmbeddingValid) {
                appendLog("⚠️ [FR] Trích xuất embedding thất bại: vector rỗng, toàn 0 hoặc NaN - từ chối xác thực")
                binding.tvRecognitionResultB.text = "⚠️ Embedding không hợp lệ"
                binding.tvRecognitionResultB.setTextColor(0xFFFF5252.toInt())
                setVerificationState(VerificationState.UNRECOGNIZED)
                return
            }

            val threshold = 0.65f
            val matchResult = UserFaceRepository.findBestMatch(liveEmbedding, threshold = threshold)

            if (matchResult != null) {
                val (matchedUser, similarity) = matchResult
                val percentStr = String.format(Locale.getDefault(), "%.1f%%", similarity * 100)
                appendLog("✅ [FR] Kết quả MATCH: MSSV=${matchedUser.studentId}, Tương đồng=$percentStr >= ${String.format(Locale.getDefault(), "%.1f%%", threshold * 100)}")

                val student = StudentRepository.findStudentById(matchedUser.studentId)

                if (student != null) {
                    // Lock verification callback to prevent duplicate sessions
                    isNavigatingToProfile = true
                    isAutoVerificationEnabled = false

                    val resultText = "✅ XÁC THỰC THÀNH CÔNG!\nMSSV: ${student.studentId} | Họ tên: ${student.fullName}\nLớp: ${student.className} | Khoa: ${student.faculty} | Tương đồng: $percentStr"
                    binding.tvRecognitionResultB.text = resultText
                    binding.tvRecognitionResultB.setTextColor(0xFF00E676.toInt())

                    // Show success status card & overlay banner
                    setVerificationState(VerificationState.SUCCESS, student)

                    // Start kiosk session with 90s inactivity timeout
                    KioskSessionManager.startSession(student)
                    appendLog("✅ [FR] Xác thực thành công: ${student.fullName} (${student.studentId}) - Chuyển sang Trang cá nhân sau 1.0 giây.")

                    // Hold for exactly 1000ms (0.8s - 1.2s requirement)
                    mainHandler.postDelayed({
                        activity?.runOnUiThread {
                            if (_binding != null && isAdded) {
                                try {
                                    (activity as? MainActivity)?.navigateToFragment(StudentProfileFragment(), addToBackStack = false)
                                } catch (e: Exception) {
                                    appendLog("❌ [FR] Lỗi chuyển hướng trang: ${e.message}")
                                    isNavigatingToProfile = false
                                    setVerificationState(VerificationState.IDLE)
                                }
                            }
                        }
                    }, 1000)
                } else {
                    appendLog("⚠️ [FR] NO MATCH: Mẫu khuôn mặt khớp ID ${matchedUser.studentId} nhưng không có dữ liệu trong StudentRepository")
                    val resultText = "⚠️ Không tìm thấy hồ sơ sinh viên MSSV: ${matchedUser.studentId}"
                    binding.tvRecognitionResultB.text = resultText
                    binding.tvRecognitionResultB.setTextColor(0xFFFF5252.toInt())
                    setVerificationState(VerificationState.UNRECOGNIZED)
                }
            } else {
                appendLog("❌ [FR] Kết quả NO MATCH: Không có sinh viên nào trong DB đạt ngưỡng ${String.format(Locale.getDefault(), "%.1f%%", threshold * 100)}")
                binding.tvRecognitionResultB.text = "❌ Không tìm thấy thông tin sinh viên phù hợp trong DB"
                binding.tvRecognitionResultB.setTextColor(0xFFFF5252.toInt())
                setVerificationState(VerificationState.UNRECOGNIZED)
            }
        } catch (e: Exception) {
            appendLog("❌ [FR] Ngoại lệ khi xác thực: ${e.message}")
            binding.tvRecognitionResultB.text = "⚠️ Lỗi so khớp: ${e.message}"
            setVerificationState(VerificationState.UNRECOGNIZED)
        }
    }

    private fun clearRegisteredFaceDatabase() {
        val ctx = context ?: return
        val count = UserFaceRepository.getUserCount(ctx)
        if (count == 0) {
            Toast.makeText(ctx, "Cơ sở dữ liệu vốn đã trống!", Toast.LENGTH_SHORT).show()
            return
        }

        UserFaceRepository.clearAllUsers(ctx)
        isAutoVerificationEnabled = false
        binding.btnVerifyFaceB.text = "🔍 So khớp"
        appendLog("🗑️ Đã xóa sạch $count mẫu khuôn mặt đã đăng ký.")
        Toast.makeText(ctx, "Đã xóa sạch cơ sở dữ liệu đăng ký!", Toast.LENGTH_SHORT).show()
        updateRecognitionStatusUI()
    }

    private fun updateRecognitionStatusUI() {
        if (_binding == null) return
        val ctx = context
        val count = if (ctx != null) UserFaceRepository.getUserCount(ctx) else 0
        if (count == 0) {
            binding.tvRecognitionResultB.text = "🆔 Nhận diện: Chưa có sinh viên nào đăng ký"
            binding.tvRecognitionResultB.setTextColor(0xFFFFB74D.toInt())
        } else {
            binding.tvRecognitionResultB.text = "🆔 Đã đăng ký khuôn mặt cho $count sinh viên | Sẵn sàng so khớp"
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
