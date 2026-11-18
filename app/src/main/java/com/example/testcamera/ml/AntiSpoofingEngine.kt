package com.example.testcamera.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import android.util.Log
import com.example.testcamera.util.IrMetrics
import com.example.testcamera.util.IrOpticalAnalyzer
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale

/**
 * Vùng bounding box của khuôn mặt dạng pure Kotlin, độc lập với Android framework stub trong unit test.
 */
data class FasBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun toRect(): Rect = Rect(left, top, right, bottom)
}

/**
 * Các lớp nhận diện tấn công giả mạo của mô hình Silent-Face / MiniFASNet.
 */
enum class FasClass(val id: Int, val label: String) {
    PRINT_2D(0, "Ảnh in 2D (Paper Print)"),
    REAL_FACE(1, "Mặt thật (Live Face)"),
    SCREEN_OR_3D(2, "Màn hình / Mặt nạ 3D (Replay/Mask)");

    companion object {
        fun fromId(id: Int): FasClass = values().find { it.id == id } ?: SCREEN_OR_3D
    }
}

/**
 * Chế độ xác thực Anti-Spoofing:
 * - HYBRID_AND: Chế độ chuẩn an ninh Kiosk (Yêu cầu cả IR quang học AND RGB MiniFASNet).
 * - RGB_ONLY: Dùng để benchmark và đối sánh thuật toán RGB.
 * - IR_ONLY: Dùng để benchmark và đối sánh thuật toán IR.
 */
enum class LivenessMode {
    HYBRID_AND,
    RGB_ONLY,
    IR_ONLY
}

/**
 * Kết quả phân tích độ sống toàn diện từ Dual-Camera Kiosk.
 */
data class LivenessResult(
    val isLive: Boolean,
    val realScore: Float,
    val printScore: Float,
    val screenScore: Float,
    val predictedClass: FasClass,
    val irMetrics: IrMetrics?,
    val irPass: Boolean,
    val rgbFasPass: Boolean,
    val mode: LivenessMode,
    val inferenceTimeMs: Long,
    val rejectionReason: String?
) {
    fun toDiagnosticString(): String {
        val decision = if (isLive) "✅ PASS" else "❌ REJECT"
        val irTag = if (irPass) "PASS" else "FAIL"
        val rgbTag = if (rgbFasPass) "PASS" else "FAIL"
        return String.format(
            Locale.US,
            "[%s] Real:%.1f%% | IR:%s | RGB:%s | Class:%s | %dms%s",
            decision,
            realScore * 100f,
            irTag,
            rgbTag,
            predictedClass.name,
            inferenceTimeMs,
            if (rejectionReason != null) " | Lý do: $rejectionReason" else ""
        )
    }

    fun toCsvRow(timestampMs: Long, trackingId: Int, personLabel: String = "Unknown"): String {
        return String.format(
            Locale.US,
            "%d,%d,%s,%s,%.4f,%.4f,%.4f,%d,%b,%b,%.2f,%.1f,%.1f,%.3f,%d,%s",
            timestampMs,
            trackingId,
            personLabel,
            if (isLive) "LIVE" else "SPOOF",
            realScore,
            printScore,
            screenScore,
            predictedClass.id,
            irPass,
            rgbFasPass,
            irMetrics?.contrast ?: 0f,
            irMetrics?.meanFace ?: 0f,
            irMetrics?.meanBg ?: 0f,
            irMetrics?.satRatio ?: 0f,
            inferenceTimeMs,
            rejectionReason?.replace(",", ";") ?: "NONE"
        )
    }

    companion object {
        const val CSV_HEADER = "timestamp,tracking_id,label,decision,real_score,print_score,screen_score,pred_class,ir_pass,rgb_pass,ir_contrast,ir_mean_face,ir_mean_bg,ir_sat_ratio,latency_ms,rejection_reason"
    }
}

/**
 * Bộ xử lý kiểm tra độ sống khuôn mặt đa phương thức (Multimodal Hybrid Anti-Spoofing Engine).
 *
 * Tích hợp:
 * 1. MiniFASNetV2 (Silent-Face TFLite) trên Camera RGB:
 *    - Scale mở rộng BBox: 2.7x với cơ chế dịch tâm khi chạm viền ảnh.
 *    - Tensor đầu vào: NCHW [1, 3, 80, 80], thứ tự kênh BGR.
 *    - Chuẩn hóa: Giữ nguyên dải float [0.0, 255.0] (KHÔNG chia 255).
 * 2. IrOpticalAnalyzer trên Camera IR:
 *    - Phân tích tương phản hồng ngoại và phản xạ bức xạ NIR từ đèn LED.
 * 3. Cổng quyết định logic AND bảo mật cao.
 */
class AntiSpoofingEngine(
    context: Context,
    var fasThreshold: Float = DEFAULT_THRESHOLD,
    var mode: LivenessMode = LivenessMode.HYBRID_AND
) {
    companion object {
        const val INPUT_SIZE = 80
        const val SCALE_FACTOR = 2.7f
        const val DEFAULT_THRESHOLD = 0.70f
        private const val MODEL_FILENAME = "silentface.tflite"
        private const val TAG = "AntiSpoofingEngine"

        /**
         * Tính toán vùng mở rộng Bounding Box chuẩn xác theo thuật toán Minivision CropImage:
         * - Mở rộng theo tỉ lệ `scale` (mặc định 2.7x cho MiniFASNetV2).
         * - Tự động dịch chuyển vùng crop nếu chạm hoặc vượt ra ngoài biên ảnh để tránh méo khuôn mặt.
         */
        fun calculateExpandedBox(
            srcW: Int,
            srcH: Int,
            boxLeft: Int,
            boxTop: Int,
            boxRight: Int,
            boxBottom: Int,
            scale: Float = SCALE_FACTOR
        ): FasBox {
            val boxW = (boxRight - boxLeft).coerceAtLeast(1)
            val boxH = (boxBottom - boxTop).coerceAtLeast(1)

            val actualScale = minOf(
                (srcH - 1).toFloat() / boxH,
                minOf((srcW - 1).toFloat() / boxW, scale)
            )

            val newWidth = boxW * actualScale
            val newHeight = boxH * actualScale
            val centerX = boxLeft + boxW / 2.0f
            val centerY = boxTop + boxH / 2.0f

            var leftTopX = centerX - newWidth / 2.0f
            var leftTopY = centerY - newHeight / 2.0f
            var rightBottomX = centerX + newWidth / 2.0f
            var rightBottomY = centerY + newHeight / 2.0f

            if (leftTopX < 0) {
                rightBottomX -= leftTopX
                leftTopX = 0f
            }
            if (leftTopY < 0) {
                rightBottomY -= leftTopY
                leftTopY = 0f
            }
            if (rightBottomX > srcW - 1) {
                leftTopX -= (rightBottomX - srcW + 1)
                rightBottomX = (srcW - 1).toFloat()
            }
            if (rightBottomY > srcH - 1) {
                leftTopY -= (rightBottomY - srcH + 1)
                rightBottomY = (srcH - 1).toFloat()
            }

            val left = leftTopX.toInt().coerceIn(0, srcW - 1)
            val top = leftTopY.toInt().coerceIn(0, srcH - 1)
            val right = rightBottomX.toInt().coerceIn(left + 1, srcW)
            val bottom = rightBottomY.toInt().coerceIn(top + 1, srcH)

            return FasBox(left, top, right, bottom)
        }

        fun getExpandedBox(srcW: Int, srcH: Int, bbox: Rect, scale: Float = SCALE_FACTOR): Rect {
            return calculateExpandedBox(srcW, srcH, bbox.left, bbox.top, bbox.right, bbox.bottom, scale).toRect()
        }
    }

    private var interpreter: Interpreter? = null
    var loadError: String? = null
        private set

    private val interpreterLock = Any()

    init {
        try {
            val modelBuffer = loadModelFile(context, MODEL_FILENAME)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            loadError = null
            Log.i(TAG, "✅ SilentFace (MiniFASNetV2) TFLite Interpreter khởi tạo thành công.")
        } catch (e: Exception) {
            interpreter = null
            loadError = e.message ?: e.toString()
            Log.e(TAG, "❌ Không thể nạp mô hình SilentFace TFLite: $loadError", e)
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun isModelReady(): Boolean = interpreter != null

    /**
     * Cắt trích xuất vùng khuôn mặt mở rộng (Scale 2.7x) và resize về 80x80 từ mảng byte NV21.
     */
    fun extractFasCropFromNv21(nv21: ByteArray, width: Int, height: Int, boundingBox: Rect): Bitmap? {
        if (width <= 0 || height <= 0 || nv21.isEmpty()) return null

        val expandedBox = calculateExpandedBox(
            width,
            height,
            boundingBox.left,
            boundingBox.top,
            boundingBox.right,
            boundingBox.bottom,
            SCALE_FACTOR
        )

        // Căn chỉnh số chẵn để bảo toàn lấy mẫu chroma 2x2 trong ảnh YUV/NV21
        val evenLeft = (expandedBox.left and 1.inv()).coerceIn(0, width - 2)
        val evenTop = (expandedBox.top and 1.inv()).coerceIn(0, height - 2)
        val evenRight = ((expandedBox.right + 1) and 1.inv()).coerceIn(evenLeft + 2, width)
        val evenBottom = ((expandedBox.bottom + 1) and 1.inv()).coerceIn(evenTop + 2, height)

        val cropWidth = evenRight - evenLeft
        val cropHeight = evenBottom - evenTop
        if (cropWidth < 20 || cropHeight < 20) return null

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(evenLeft, evenTop, evenRight, evenBottom), 95, out)
            val imageBytes = out.toByteArray()
            val faceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

            val scaledBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
            if (scaledBitmap != faceBitmap) {
                faceBitmap.recycle()
            }
            scaledBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi crop NV21 cho FAS: ${e.message}")
            null
        }
    }

    /**
     * Cắt trích xuất vùng khuôn mặt mở rộng (Scale 2.7x) và resize về 80x80 từ Bitmap nguồn RGB.
     */
    fun extractFasCropFromBitmap(sourceBitmap: Bitmap, boundingBox: Rect): Bitmap? {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        if (width <= 0 || height <= 0) return null

        val expandedBox = calculateExpandedBox(
            width,
            height,
            boundingBox.left,
            boundingBox.top,
            boundingBox.right,
            boundingBox.bottom,
            SCALE_FACTOR
        )
        val cropW = expandedBox.width.coerceAtLeast(1)
        val cropH = expandedBox.height.coerceAtLeast(1)

        return try {
            val cropBmp = Bitmap.createBitmap(sourceBitmap, expandedBox.left, expandedBox.top, cropW, cropH)
            val scaledBmp = Bitmap.createScaledBitmap(cropBmp, INPUT_SIZE, INPUT_SIZE, true)
            if (scaledBmp != cropBmp) {
                cropBmp.recycle()
            }
            scaledBmp
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi crop Bitmap cho FAS: ${e.message}")
            null
        }
    }

    /**
     * Chuyển đổi Bitmap 80x80 sang cấu trúc Tensor NCHW [1, 3, 80, 80], thứ tự kênh BGR,
     * dải pixel float nguyên bản [0.0, 255.0] (KHÔNG chia cho 255).
     */
    fun convertBitmapToNchwBgrByteBuffer(bitmap: Bitmap): ByteBuffer {
        val totalPixels = INPUT_SIZE * INPUT_SIZE
        val byteBuffer = ByteBuffer.allocateDirect(1 * 3 * totalPixels * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.rewind()

        val intValues = IntArray(totalPixels)
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Kênh 0: Blue (BGR order)
        for (i in 0 until totalPixels) {
            val p = intValues[i]
            byteBuffer.putFloat((p and 0xFF).toFloat())
        }

        // Kênh 1: Green
        for (i in 0 until totalPixels) {
            val p = intValues[i]
            byteBuffer.putFloat(((p shr 8) and 0xFF).toFloat())
        }

        // Kênh 2: Red
        for (i in 0 until totalPixels) {
            val p = intValues[i]
            byteBuffer.putFloat(((p shr 16) and 0xFF).toFloat())
        }

        return byteBuffer
    }

    /**
     * Thực thi suy luận mô hình MiniFASNetV2 trên ảnh crop 80x80.
     * Trả về mảng 3 xác suất [Class 0: Print, Class 1: Real, Class 2: Screen].
     */
    fun runRgbFas(scaled80Bitmap: Bitmap): FloatArray {
        val activeInterpreter = interpreter ?: return FloatArray(3)
        val byteBuffer = convertBitmapToNchwBgrByteBuffer(scaled80Bitmap)
        val outputArray = Array(1) { FloatArray(3) }

        synchronized(interpreterLock) {
            try {
                activeInterpreter.run(byteBuffer, outputArray)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi thực thi suy luận SilentFace TFLite: ${e.message}", e)
                return FloatArray(3)
            }
        }

        return outputArray[0]
    }

    /**
     * Phương thức đánh giá Anti-Spoofing chính (Multimodal Liveness Check).
     * Kết hợp cả ảnh RGB (MiniFASNetV2) và ảnh IR (IrOpticalAnalyzer).
     */
    fun classifyLiveness(
        rgbNv21: ByteArray,
        rgbWidth: Int,
        rgbHeight: Int,
        rgbFaceBox: Rect,
        irNv21: ByteArray? = null,
        irWidth: Int = 0,
        irHeight: Int = 0,
        irFaceBox: Rect? = null,
        deltaTimestampMs: Long = 0L
    ): LivenessResult {
        val startTime = SystemClock.elapsedRealtime()

        // 1. Phân tích quang học Camera IR
        val irMetrics: IrMetrics?
        val irPass: Boolean

        if (irNv21 != null && irFaceBox != null && irWidth > 0 && irHeight > 0 && deltaTimestampMs <= 350L) {
            irMetrics = IrOpticalAnalyzer.analyze(irNv21, irWidth, irHeight, irFaceBox)
            irPass = irMetrics.isPass
        } else {
            irMetrics = null
            irPass = false
        }

        // 2. Phân tích vân da & viền màn hình qua MiniFASNetV2 trên Camera RGB
        val cropBmp = extractFasCropFromNv21(rgbNv21, rgbWidth, rgbHeight, rgbFaceBox)
        val preds = if (cropBmp != null) {
            val output = runRgbFas(cropBmp)
            cropBmp.recycle()
            output
        } else {
            FloatArray(3)
        }

        val printScore = preds[0]
        val realScore = preds[1]
        val screenScore = preds[2]

        val maxIndex = when {
            realScore >= printScore && realScore >= screenScore -> 1
            screenScore >= printScore && screenScore >= realScore -> 2
            else -> 0
        }
        val predictedClass = FasClass.fromId(maxIndex)

        // Điều kiện vượt qua RGB: Lớp dự đoán là Mặt thật VÀ điểm tin cậy vượt ngưỡng
        val rgbFasPass = (predictedClass == FasClass.REAL_FACE) && (realScore >= fasThreshold)

        // 3. Cổng quyết định theo chế độ hoạt động
        val isLive: Boolean
        val rejectionReason: String?

        when (mode) {
            LivenessMode.HYBRID_AND -> {
                if (irMetrics == null) {
                    isLive = false
                    rejectionReason = if (deltaTimestampMs > 350L) {
                        String.format(Locale.US, "Lệch khung hình IR-RGB (Δt: %dms > 350ms)", deltaTimestampMs)
                    } else {
                        "Chưa nhận được tín hiệu Camera IR"
                    }
                } else if (!irPass) {
                    isLive = false
                    rejectionReason = "IR từ chối: ${irMetrics.reason}"
                } else if (!rgbFasPass) {
                    isLive = false
                    rejectionReason = when (predictedClass) {
                        FasClass.PRINT_2D -> String.format(Locale.US, "Phát hiện ảnh in 2D (Điểm thật: %.1f%% < %.0f%%)", realScore * 100f, fasThreshold * 100f)
                        FasClass.SCREEN_OR_3D -> String.format(Locale.US, "Phát hiện màn hình/mặt nạ giả (Điểm thật: %.1f%% < %.0f%%)", realScore * 100f, fasThreshold * 100f)
                        else -> String.format(Locale.US, "Điểm độ sống RGB không đạt (%.1f%% < %.0f%%)", realScore * 100f, fasThreshold * 100f)
                    }
                } else {
                    isLive = true
                    rejectionReason = null
                }
            }
            LivenessMode.RGB_ONLY -> {
                isLive = rgbFasPass
                rejectionReason = if (!rgbFasPass) {
                    String.format(Locale.US, "RGB FAS từ chối (Lớp: %s, Điểm thật: %.1f%% < %.0f%%)", predictedClass.label, realScore * 100f, fasThreshold * 100f)
                } else null
            }
            LivenessMode.IR_ONLY -> {
                isLive = irPass
                rejectionReason = if (!irPass) {
                    irMetrics?.reason ?: "Camera IR chưa sẵn sàng"
                } else null
            }
        }

        val totalTimeMs = SystemClock.elapsedRealtime() - startTime

        return LivenessResult(
            isLive = isLive,
            realScore = realScore,
            printScore = printScore,
            screenScore = screenScore,
            predictedClass = predictedClass,
            irMetrics = irMetrics,
            irPass = irPass,
            rgbFasPass = rgbFasPass,
            mode = mode,
            inferenceTimeMs = totalTimeMs,
            rejectionReason = rejectionReason
        )
    }

    /**
     * Phương thức đánh giá Anti-Spoofing hỗ trợ đầu vào trực tiếp từ Bitmap RGB.
     */
    fun classifyLiveness(
        rgbBitmap: Bitmap,
        rgbFaceBox: Rect,
        irNv21: ByteArray? = null,
        irWidth: Int = 0,
        irHeight: Int = 0,
        irFaceBox: Rect? = null,
        deltaTimestampMs: Long = 0L
    ): LivenessResult {
        val startTime = SystemClock.elapsedRealtime()

        val irMetrics: IrMetrics?
        val irPass: Boolean

        if (irNv21 != null && irFaceBox != null && irWidth > 0 && irHeight > 0 && deltaTimestampMs <= 350L) {
            irMetrics = IrOpticalAnalyzer.analyze(irNv21, irWidth, irHeight, irFaceBox)
            irPass = irMetrics.isPass
        } else {
            irMetrics = null
            irPass = false
        }

        val cropBmp = extractFasCropFromBitmap(rgbBitmap, rgbFaceBox)
        val preds = if (cropBmp != null) {
            val output = runRgbFas(cropBmp)
            cropBmp.recycle()
            output
        } else {
            FloatArray(3)
        }

        val printScore = preds[0]
        val realScore = preds[1]
        val screenScore = preds[2]

        val maxIndex = when {
            realScore >= printScore && realScore >= screenScore -> 1
            screenScore >= printScore && screenScore >= realScore -> 2
            else -> 0
        }
        val predictedClass = FasClass.fromId(maxIndex)
        val rgbFasPass = (predictedClass == FasClass.REAL_FACE) && (realScore >= fasThreshold)

        val isLive: Boolean
        val rejectionReason: String?

        when (mode) {
            LivenessMode.HYBRID_AND -> {
                if (irMetrics == null) {
                    isLive = false
                    rejectionReason = if (deltaTimestampMs > 350L) {
                        String.format(Locale.US, "Lệch khung hình IR-RGB (Δt: %dms > 350ms)", deltaTimestampMs)
                    } else {
                        "Chưa nhận được tín hiệu Camera IR"
                    }
                } else if (!irPass) {
                    isLive = false
                    rejectionReason = "IR từ chối: ${irMetrics.reason}"
                } else if (!rgbFasPass) {
                    isLive = false
                    rejectionReason = when (predictedClass) {
                        FasClass.PRINT_2D -> String.format(Locale.US, "Phát hiện ảnh in 2D (Điểm thật: %.1f%% < %.0f%%)", realScore * 100f, fasThreshold * 100f)
                        FasClass.SCREEN_OR_3D -> String.format(Locale.US, "Phát hiện màn hình/mặt nạ giả (Điểm thật: %.1f%% < %.0f%%)", realScore * 100f, fasThreshold * 100f)
                        else -> String.format(Locale.US, "Điểm độ sống RGB không đạt (%.1f%% < %.0f%%)", realScore * 100f, fasThreshold * 100f)
                    }
                } else {
                    isLive = true
                    rejectionReason = null
                }
            }
            LivenessMode.RGB_ONLY -> {
                isLive = rgbFasPass
                rejectionReason = if (!rgbFasPass) {
                    String.format(Locale.US, "RGB FAS từ chối (Lớp: %s, Điểm thật: %.1f%% < %.0f%%)", predictedClass.label, realScore * 100f, fasThreshold * 100f)
                } else null
            }
            LivenessMode.IR_ONLY -> {
                isLive = irPass
                rejectionReason = if (!irPass) {
                    irMetrics?.reason ?: "Camera IR chưa sẵn sàng"
                } else null
            }
        }

        val totalTimeMs = SystemClock.elapsedRealtime() - startTime

        return LivenessResult(
            isLive = isLive,
            realScore = realScore,
            printScore = printScore,
            screenScore = screenScore,
            predictedClass = predictedClass,
            irMetrics = irMetrics,
            irPass = irPass,
            rgbFasPass = rgbFasPass,
            mode = mode,
            inferenceTimeMs = totalTimeMs,
            rejectionReason = rejectionReason
        )
    }

    fun close() {
        synchronized(interpreterLock) {
            interpreter?.close()
            interpreter = null
        }
    }
}
