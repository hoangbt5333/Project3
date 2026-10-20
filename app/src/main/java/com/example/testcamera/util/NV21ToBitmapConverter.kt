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
        targetSize: Int = 112
    ): Bitmap? {
        if (width <= 0 || height <= 0 || nv21.isEmpty()) return null

        val cropLeft = boundingBox.left.coerceIn(0, width - 1)
        val cropTop = boundingBox.top.coerceIn(0, height - 1)
        val cropRight = boundingBox.right.coerceIn(cropLeft + 1, width)
        val cropBottom = boundingBox.bottom.coerceIn(cropTop + 1, height)

        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop

        if (cropWidth < 10 || cropHeight < 10) return null

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(cropLeft, cropTop, cropRight, cropBottom), 90, out)
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
