package com.example.testcamera.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceNetModel(context: Context) {

    companion object {
        const val INPUT_IMAGE_SIZE = 112
        const val EMBEDDING_SIZE = 192
        private const val MODEL_FILENAME = "mobilefacenet.tflite"
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = loadModelFile(context, MODEL_FILENAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (_: Exception) {
            // Model file will be loaded when added to assets folder
            interpreter = null
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

    fun extractEmbedding(faceBitmap: Bitmap): FloatArray {
        val scaledBitmap = if (faceBitmap.width != INPUT_IMAGE_SIZE || faceBitmap.height != INPUT_IMAGE_SIZE) {
            Bitmap.createScaledBitmap(faceBitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true)
        } else {
            faceBitmap
        }

        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)
        val embedding = FloatArray(EMBEDDING_SIZE)

        val activeInterpreter = interpreter
        if (activeInterpreter != null) {
            val outputMap = Array(1) { FloatArray(EMBEDDING_SIZE) }
            activeInterpreter.run(byteBuffer, outputMap)
            System.arraycopy(outputMap[0], 0, embedding, 0, EMBEDDING_SIZE)
        } else {
            // Fallback feature vector calculation from pixel channels if tflite asset is missing
            extractFallbackEmbedding(scaledBitmap, embedding)
        }

        if (scaledBitmap != faceBitmap) {
            scaledBitmap.recycle()
        }

        return l2Normalize(embedding)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val imgData = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())
        imgData.rewind()

        val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until INPUT_IMAGE_SIZE) {
            for (j in 0 until INPUT_IMAGE_SIZE) {
                val valPixel = intValues[pixel++]
                // Normalize RGB pixel values to [-1, 1]
                imgData.putFloat((((valPixel shr 16) and 0xFF) - 127.5f) / 127.5f)
                imgData.putFloat((((valPixel shr 8) and 0xFF) - 127.5f) / 127.5f)
                imgData.putFloat(((valPixel and 0xFF) - 127.5f) / 127.5f)
            }
        }
        return imgData
    }

    private fun extractFallbackEmbedding(bitmap: Bitmap, outputArray: FloatArray) {
        val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val step = (intValues.size / EMBEDDING_SIZE).coerceAtLeast(1)
        for (i in 0 until EMBEDDING_SIZE) {
            val idx = (i * step).coerceAtMost(intValues.size - 1)
            val px = intValues[idx]
            val r = ((px shr 16) and 0xFF) / 255.0f
            val g = ((px shr 8) and 0xFF) / 255.0f
            val b = (px and 0xFF) / 255.0f
            outputArray[i] = (r * 0.299f + g * 0.587f + b * 0.114f)
        }
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var sum = 0f
        for (v in embedding) {
            sum += v * v
        }
        val norm = sqrt(sum.toDouble()).toFloat()
        if (norm == 0f) return embedding
        for (i in embedding.indices) {
            embedding[i] /= norm
        }
        return embedding
    }

    fun close() {
        try {
            interpreter?.close()
            interpreter = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
