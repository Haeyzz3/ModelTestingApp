package com.calorieko.modeltestingapp

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import androidx.core.graphics.scale

class CalorieKoClassifier(context: Context) {
    // 1. Load the model and labels from the assets folder
    private val modelBuffer = loadModelFile(context, "calorieko_model.tflite")
    private val interpreter = Interpreter(modelBuffer)
    private val labels = loadLabels(context, "labels.txt")

    private fun loadModelFile(context: Context, filename: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(context: Context, filename: String): List<String> {
        return context.assets.open(filename).bufferedReader().readLines()
    }

    /**
     * Replicates the predict_dish logic from test_model.py
     */
    fun classify(bitmap: Bitmap): List<Pair<String, Float>> {
        // 2. Prepare Input Tensor (1 x 224 x 224 x 3)
        // 4 bytes per float (Float32)
        val input = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // --- Step A: Manual Center Crop & Resize ---
        // Replicates the 'prepare_image' behavior from your Python script [cite: 31, 32]
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2

        val croppedBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
        val resizedBitmap = croppedBitmap.scale(224, 224)

        val intValues = IntArray(224 * 224)
        resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        // --- Step B: Feed Raw Pixels ---
        // Note: No /255 rescaling because 'include_preprocessing=True' was used
        for (pixel in intValues) {
            input.putFloat(((pixel shr 16) and 0xFF).toFloat()) // Red
            input.putFloat(((pixel shr 8) and 0xFF).toFloat())  // Green
            input.putFloat((pixel and 0xFF).toFloat())         // Blue
        }

        // 3. Prepare Output Buffer (1 x 25 classes)
        val output = Array(1) { FloatArray(labels.size) }

        // 4. Run Inference
        interpreter.run(input, output)

        // 5. Post-process: Get Top 3 Candidates (matching test_model.py) [cite: 34]
        return labels.indices.map { labels[it] to output[0][it] }
            .sortedByDescending { it.second }
            .take(3)
    }
}