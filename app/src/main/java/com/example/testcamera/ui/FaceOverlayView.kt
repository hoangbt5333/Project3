package com.example.testcamera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.testcamera.R
import com.google.mlkit.vision.face.Face

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var faces: List<Face> = emptyList()
    private var customRects: List<Rect> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val boxRect = RectF()

    private var frameColor: Int = ContextCompat.getColor(context, R.color.hust_gold)

    private val cornerPaint = Paint().apply {
        color = frameColor
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val guidePaint = Paint().apply {
        color = 0x66F5A623.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    fun setFaces(faceList: List<Face>?, imgWidth: Int, imgHeight: Int) {
        this.faces = faceList ?: emptyList()
        this.customRects = emptyList()
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        postInvalidate()
    }

    fun setCustomRects(rectList: List<Rect>?, imgWidth: Int, imgHeight: Int) {
        this.customRects = rectList ?: emptyList()
        this.faces = emptyList()
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        postInvalidate()
    }

    fun setHighlightColor(color: Int) {
        this.frameColor = color
        this.cornerPaint.color = color
        postInvalidate()
    }

    fun clear() {
        this.faces = emptyList()
        this.customRects = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if ((faces.isEmpty() && customRects.isEmpty()) || imageWidth == 0 || imageHeight == 0) return

        val scaleX = width.toFloat() / imageWidth.toFloat()
        val scaleY = height.toFloat() / imageHeight.toFloat()

        val boundsList = if (faces.isNotEmpty()) {
            faces.map { it.boundingBox }
        } else {
            customRects
        }

        for (bounds in boundsList) {
            val left = (bounds.left.toFloat() * scaleX).coerceAtLeast(0f)
            val top = (bounds.top.toFloat() * scaleY).coerceAtLeast(0f)
            val right = (bounds.right.toFloat() * scaleX).coerceAtMost(width.toFloat())
            val bottom = (bounds.bottom.toFloat() * scaleY).coerceAtMost(height.toFloat())

            boxRect.set(left, top, right, bottom)

            // Draw thin outline
            canvas.drawRoundRect(boxRect, 20f, 20f, guidePaint)

            // Draw sleek corner brackets
            val cornerLength = ((right - left) * 0.2f).coerceIn(12f, 60f)

            // Top-Left
            canvas.drawLine(left, top + cornerLength, left, top + 16f, cornerPaint)
            canvas.drawArc(left, top, left + 32f, top + 32f, 180f, 90f, false, cornerPaint)
            canvas.drawLine(left + 16f, top, left + cornerLength, top, cornerPaint)

            // Top-Right
            canvas.drawLine(right - cornerLength, top, right - 16f, top, cornerPaint)
            canvas.drawArc(right - 32f, top, right, top + 32f, 270f, 90f, false, cornerPaint)
            canvas.drawLine(right, top + 16f, right, top + cornerLength, cornerPaint)

            // Bottom-Left
            canvas.drawLine(left, bottom - cornerLength, left, bottom - 16f, cornerPaint)
            canvas.drawArc(left, bottom - 32f, left + 32f, bottom, 90f, 90f, false, cornerPaint)
            canvas.drawLine(left + 16f, bottom, left + cornerLength, bottom, cornerPaint)

            // Bottom-Right
            canvas.drawLine(right - cornerLength, bottom, right - 16f, bottom, cornerPaint)
            canvas.drawArc(right - 32f, bottom - 32f, right, bottom, 0f, 90f, false, cornerPaint)
            canvas.drawLine(right, bottom - 16f, right, bottom - cornerLength, cornerPaint)
        }
    }
}
