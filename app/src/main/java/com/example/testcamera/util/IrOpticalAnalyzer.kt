package com.example.testcamera.util

import android.graphics.Rect
import java.util.Locale
import kotlin.math.sqrt

data class IrMetrics(
    val meanFace: Float,
    val stdDevFace: Float,
    val meanBg: Float,
    val contrast: Float,
    val maxPixel: Int,
    val satRatio: Float,
    val isPass: Boolean,
    val reason: String
) {
    fun toTelemetryString(deltaTimestampMs: Long): String {
        val statusTag = if (isPass) "✅ PASS" else "❌ FAIL"
        return String.format(
            Locale.US,
            "[%s] C:%.2fx | M:%.0f | Bg:%.0f | Sat:%.1f%% | Max:%d | Δt:%dms",
            statusTag,
            contrast,
            meanFace,
            meanBg,
            satRatio * 100f,
            maxPixel,
            deltaTimestampMs
        )
    }
}

object IrOpticalAnalyzer {

    // Các ngưỡng chuẩn đoán thực nghiệm ban đầu
    const val MIN_FACE_MEAN = 18.0f           // Dưới mức này: màn hình điện thoại hoặc bóng tối hoàn toàn
    const val MIN_CONTRAST_RATIO = 1.15f      // Mặt người thật dưới đèn LED IR phải sáng hơn nền ít nhất 15%
    const val MAX_SATURATION_RATIO = 0.20f    // Tỷ lệ pixel bão hòa (>=250) tối đa cho phép (tránh lóa kính màn hình)
    const val MIN_STD_DEV = 4.0f              // Độ lệch chuẩn tối thiểu (tránh mặt phẳng lì một màu)

    /**
     * Phân tích trực tiếp kênh Y trên mảng byte NV21 của Camera IR.
     * Độ phức tạp: O(pixels trong box), thời gian thực thi: < 1ms.
     */
    fun analyze(
        nv21Bytes: ByteArray,
        width: Int,
        height: Int,
        irBox: Rect,
        rowStride: Int = width
    ): IrMetrics {
        if (nv21Bytes.isEmpty() || width <= 0 || height <= 0) {
            return IrMetrics(0f, 0f, 0f, 0f, 0, 0f, false, "Frame IR rỗng")
        }

        val boxLeft = irBox.left.coerceIn(0, width - 1)
        val boxTop = irBox.top.coerceIn(0, height - 1)
        val boxRight = irBox.right.coerceIn(boxLeft + 1, width)
        val boxBottom = irBox.bottom.coerceIn(boxTop + 1, height)

        val boxW = boxRight - boxLeft
        val boxH = boxBottom - boxTop
        if (boxW < 10 || boxH < 10) {
            return IrMetrics(0f, 0f, 0f, 0f, 0, 0f, false, "Vùng BBox IR quá nhỏ")
        }

        // 1. Phân tích vùng mặt (Face Box)
        var sumFace = 0L
        var sumSqFace = 0.0
        var maxVal = 0
        var satCount = 0
        var facePixelCount = 0

        // Lấy mẫu cách 2 pixel nếu vùng mặt lớn để đảm bảo tốc độ cực nhanh
        val step = if (boxW * boxH > 40000) 2 else 1

        for (y in boxTop until boxBottom step step) {
            val rowOffset = y * rowStride
            for (x in boxLeft until boxRight step step) {
                val index = rowOffset + x
                if (index < nv21Bytes.size && index < width * height) {
                    val luma = nv21Bytes[index].toInt() and 0xFF
                    sumFace += luma
                    sumSqFace += (luma * luma)
                    if (luma > maxVal) maxVal = luma
                    if (luma >= 250) satCount++
                    facePixelCount++
                }
            }
        }

        if (facePixelCount == 0) {
            return IrMetrics(0f, 0f, 0f, 0f, 0, 0f, false, "Không có pixel mặt")
        }

        val meanFace = sumFace.toFloat() / facePixelCount
        val varianceFace = (sumSqFace / facePixelCount) - (meanFace * meanFace)
        val stdDevFace = sqrt(varianceFace.coerceAtLeast(0.0)).toFloat()
        val satRatio = satCount.toFloat() / facePixelCount

        // 2. Lấy mẫu vùng nền (Background) quanh 3 phía ngoài BBox
        var sumBg = 0L
        var bgPixelCount = 0
        val bgMargin = 24

        val bgSamples = listOf(
            Rect((boxLeft - bgMargin).coerceAtLeast(0), (boxTop - bgMargin).coerceAtLeast(0), boxRight, boxTop),
            Rect((boxLeft - bgMargin).coerceAtLeast(0), boxTop, boxLeft, boxBottom),
            Rect(boxRight, boxTop, (boxRight + bgMargin).coerceAtMost(width), boxBottom)
        )

        for (r in bgSamples) {
            for (y in r.top until r.bottom step 2) {
                val rowOffset = y * rowStride
                for (x in r.left until r.right step 2) {
                    val index = rowOffset + x
                    if (index < nv21Bytes.size && index < width * height) {
                        sumBg += (nv21Bytes[index].toInt() and 0xFF)
                        bgPixelCount++
                    }
                }
            }
        }

        val meanBg = if (bgPixelCount > 0) sumBg.toFloat() / bgPixelCount else 30.0f
        val contrast = meanFace / maxOf(1.0f, meanBg)

        // 3. Đánh giá tính hợp lệ theo luật vật lý
        val isPass: Boolean
        val reason: String

        when {
            meanFace < MIN_FACE_MEAN -> {
                isPass = false
                reason = "Mặt quá tối trên IR (Mean: ${meanFace.toInt()} < $MIN_FACE_MEAN) - Màn hình điện thoại"
            }
            contrast < MIN_CONTRAST_RATIO -> {
                isPass = false
                reason = String.format(Locale.US, "Độ tương phản thấp (C: %.2fx < %.2fx) - Thiếu bức xạ NIR", contrast, MIN_CONTRAST_RATIO)
            }
            satRatio > MAX_SATURATION_RATIO -> {
                isPass = false
                reason = String.format(Locale.US, "Lóa phản xạ bất thường (Sat: %.1f%% > %.1f%%) - Kính màn hình", satRatio * 100, MAX_SATURATION_RATIO * 100)
            }
            stdDevFace < MIN_STD_DEV -> {
                isPass = false
                reason = String.format(Locale.US, "Mặt đồng nhất bất thường (StdDev: %.1f < %.1f)", stdDevFace, MIN_STD_DEV)
            }
            else -> {
                isPass = true
                reason = String.format(Locale.US, "Đạt chuẩn quang phổ hồng ngoại (Contrast: %.2fx, Mean: %.0f)", contrast, meanFace)
            }
        }

        return IrMetrics(
            meanFace = meanFace,
            stdDevFace = stdDevFace,
            meanBg = meanBg,
            contrast = contrast,
            maxPixel = maxVal,
            satRatio = satRatio,
            isPass = isPass,
            reason = reason
        )
    }
}
