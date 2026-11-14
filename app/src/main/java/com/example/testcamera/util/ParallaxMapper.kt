package com.example.testcamera.util

import android.graphics.Rect

/**
 * Hiệu chuẩn và ánh xạ tọa độ Bounding Box từ Camera RGB sang Camera IR.
 * Hỗ trợ nội suy thị sai động theo khoảng cách (thông qua kích thước khuôn mặt W):
 * deltaX(W) = paramA * W + paramB
 * deltaY = paramC
 */
object ParallaxMapper {

    // Tham số hiệu chuẩn cho cụm Dual Camera Kiosk ZM-MTW172 (khoảng cách 2 mắt ~3-4 cm)
    // Người dùng đứng xa (W nhỏ ~120px) -> deltaX nhỏ hơn
    // Người dùng đứng gần (W lớn ~260px) -> deltaX lớn hơn
    var paramA: Float = 0.15f        // Hệ số độ dốc theo chiều rộng khuôn mặt
    var paramB: Float = -20.0f       // Hệ số trôi gốc (bias)
    var deltaY: Int = 0              // Độ lệch thẳng đứng giữa 2 ống kính
    var scale: Float = 1.0f          // Tỷ lệ thu phóng (nếu FOV 2 camera chênh lệch)
    var isMirrored: Boolean = false  // Lật gương trục X nếu sensor IR bị lật

    /**
     * Ánh xạ vùng BBox từ RGB sang IR có bù trừ thị sai động.
     */
    fun mapRgbToIr(
        rgbBox: Rect,
        rgbWidth: Int,
        rgbHeight: Int,
        irWidth: Int = 640,
        irHeight: Int = 480
    ): Rect {
        val faceW = rgbBox.width().coerceAtLeast(1)
        val faceH = rgbBox.height().coerceAtLeast(1)

        // Tính độ dịch chuyển ngang động dựa theo độ lớn khuôn mặt (đại diện cho khoảng cách)
        val computedDeltaX = (paramA * faceW + paramB).toInt()
        val computedDeltaY = deltaY

        val rgbCenterX = rgbBox.exactCenterX()
        val rgbCenterY = rgbBox.exactCenterY()

        val irCenterX = if (isMirrored) {
            (rgbWidth - rgbCenterX) + computedDeltaX
        } else {
            rgbCenterX + computedDeltaX
        }
        val irCenterY = rgbCenterY + computedDeltaY

        val irW = (faceW * scale).toInt()
        val irH = (faceH * scale).toInt()

        val halfW = irW / 2
        val halfH = irH / 2

        val left = (irCenterX - halfW).toInt().coerceIn(0, irWidth - 1)
        val top = (irCenterY - halfH).toInt().coerceIn(0, irHeight - 1)
        val right = (irCenterX + halfW).toInt().coerceIn(left + 1, irWidth)
        val bottom = (irCenterY + halfH).toInt().coerceIn(top + 1, irHeight)

        return Rect(left, top, right, bottom)
    }
}
