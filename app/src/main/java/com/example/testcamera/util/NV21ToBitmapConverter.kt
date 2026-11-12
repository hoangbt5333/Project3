package com.example.testcamera.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

object NV21ToBitmapConverter {

    fun convertNv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0 || nv21.isEmpty()) return null
        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun cropFaceBitmap(
        nv21: ByteArray,
        width: Int,
        height: Int,
        boundingBox: Rect,
        targetSize: Int = 112,
        paddingFactor: Float = 0.20f
    ): Bitmap? {
        if (width <= 0 || height <= 0 || nv21.isEmpty()) return null

        val rawWidth = boundingBox.width().coerceAtLeast(1)
        val rawHeight = boundingBox.height().coerceAtLeast(1)

        // Minimum face size threshold for biometric recognition
        if (rawWidth < 40 || rawHeight < 40) return null

        val centerX = boundingBox.exactCenterX()
        val centerY = boundingBox.exactCenterY()

        // Create square crop bounding box with padding (1:1 aspect ratio to avoid distortion)
        val maxDim = maxOf(rawWidth, rawHeight)
        val paddedSide = (maxDim * (1.0f + paddingFactor)).coerceAtMost(minOf(width, height).toFloat())
        val halfSide = paddedSide / 2.0f

        var cropLeft = (centerX - halfSide).toInt()
        var cropTop = (centerY - halfSide).toInt()
        var cropRight = (centerX + halfSide).toInt()
        var cropBottom = (centerY + halfSide).toInt()

        // Shift box if touching image edges to preserve square aspect ratio
        if (cropLeft < 0) {
            cropRight += -cropLeft
            cropLeft = 0
        }
        if (cropTop < 0) {
            cropBottom += -cropTop
            cropTop = 0
        }
        if (cropRight > width) {
            val shift = cropRight - width
            cropLeft = (cropLeft - shift).coerceAtLeast(0)
            cropRight = width
        }
        if (cropBottom > height) {
            val shift = cropBottom - height
            cropTop = (cropTop - shift).coerceAtLeast(0)
            cropBottom = height
        }

        // Align to even numbers for NV21 2x2 chroma subsampling (prevent UV color shifting)
        val evenLeft = (cropLeft and 1.inv()).coerceIn(0, width - 2)
        val evenTop = (cropTop and 1.inv()).coerceIn(0, height - 2)
        val evenRight = (cropRight and 1.inv()).coerceIn(evenLeft + 2, width)
        val evenBottom = (cropBottom and 1.inv()).coerceIn(evenTop + 2, height)

        val cropWidth = evenRight - evenLeft
        val cropHeight = evenBottom - evenTop

        if (cropWidth < 40 || cropHeight < 40) return null

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(evenLeft, evenTop, evenRight, evenBottom), 95, out)
            val imageBytes = out.toByteArray()
            val faceBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

            val scaledBitmap = Bitmap.createScaledBitmap(faceBitmap, targetSize, targetSize, true)
            if (scaledBitmap != faceBitmap) {
                faceBitmap.recycle()
            }
            scaledBitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
