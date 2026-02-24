package com.calorieko.modeltestingapp

import android.content.Context
import android.graphics.Bitmap
// LiteRT ships the Interpreter class under the original org.tensorflow.lite package.
// Only the Gradle dependency group changed (from org.tensorflow to com.google.ai.edge.litert).
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import androidx.core.graphics.scale

class CalorieKoClassifier(context: Context) : AutoCloseable { // 2. Implement AutoCloseable for best practice

    private val modelBuffer = loadModelFile(context)
    private val interpreter = Interpreter(modelBuffer)
    private val labels = loadLabels(context)

    private fun loadModelFile(context: Context): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd("calorieko_model.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(context: Context): List<String> {
        return context.assets.open("labels.txt").bufferedReader().readLines()
    }

    fun classify(bitmap: Bitmap): List<Pair<String, Float>> {
        val input = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2

        val croppedBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
        val resizedBitmap = croppedBitmap.scale(224, 224)

        val intValues = IntArray(224 * 224)
        resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        for (pixel in intValues) {
            input.putFloat(((pixel shr 16) and 0xFF).toFloat())
            input.putFloat(((pixel shr 8) and 0xFF).toFloat())
            input.putFloat((pixel and 0xFF).toFloat())
        }

        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, output)

        return labels.indices.map { labels[it] to output[0][it] }
            .sortedByDescending { it.second }
            .take(3)
    }

    // 3. New Method to Release Native Resources
    override fun close() {
        interpreter.close()
    }
}