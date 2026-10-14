package com.example.testcamera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var faces: List<Face> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val boxRect = RectF()
    private val textBgRect = RectF()

    private val boxPaint = Paint().apply {
        color = 0xFF00E676.toInt() // Bright Green
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textBackgroundPaint = Paint().apply {
        color = 0xAA000000.toInt() // Semi-transparent black
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
    }

    fun setFaces(faceList: List<Face>?, imgWidth: Int, imgHeight: Int) {
        this.faces = faceList ?: emptyList()
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        postInvalidate()
    }

    fun clear() {
        this.faces = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (faces.isEmpty() || imageWidth == 0 || imageHeight == 0) return

        val scaleX = width.toFloat() / imageWidth.toFloat()
        val scaleY = height.toFloat() / imageHeight.toFloat()

        for ((index, face) in faces.withIndex()) {
            val bounds = face.boundingBox

            val left = bounds.left.toFloat() * scaleX
            val top = bounds.top.toFloat() * scaleY
            val right = bounds.right.toFloat() * scaleX
            val bottom = bounds.bottom.toFloat() * scaleY

            boxRect.set(
                left.coerceAtLeast(0f),
                top.coerceAtLeast(0f),
                right.coerceAtMost(width.toFloat()),
                bottom.coerceAtMost(height.toFloat())
            )

            // Draw bounding box
            canvas.drawRect(boxRect, boxPaint)

            // Draw label
            val label = "Khuôn mặt #${index + 1}"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize

            val textTop = if ((top - textHeight - 12f) > 0f) top - textHeight - 12f else top
            val textBottom = if ((top - textHeight - 12f) > 0f) top else top + textHeight + 12f

            textBgRect.set(
                left,
                textTop,
                (left + textWidth + 16f).coerceAtMost(width.toFloat()),
                textBottom
            )
            canvas.drawRect(textBgRect, textBackgroundPaint)

            canvas.drawText(
                label,
                left + 8f,
                if ((top - textHeight - 12f) > 0f) top - 8f else top + textHeight,
                textPaint
            )
        }
    }
}
