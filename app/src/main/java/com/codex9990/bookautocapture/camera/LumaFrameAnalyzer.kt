package com.codex9990.bookautocapture.camera

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.codex9990.bookautocapture.capture.FrameMetrics
import kotlin.math.abs
import kotlin.math.max

class LumaFrameAnalyzer(
    private val onMetrics: (FrameMetrics) -> Unit
) : ImageAnalysis.Analyzer {
    private var previousSamples: IntArray? = null

    override fun analyze(image: ImageProxy) {
        try {
            val samples = sampleLuma(image)
            val previous = previousSamples
            previousSamples = samples

            val difference = if (previous == null) {
                0.0
            } else {
                samples.indices.sumOf { abs(samples[it] - previous[it]).toDouble() } / samples.size
            }

            val averageLuma = samples.average()
            val edgeScore = calculateEdgeScore(samples)

            onMetrics(
                FrameMetrics(
                    timestampMs = SystemClock.elapsedRealtime(),
                    difference = difference,
                    averageLuma = averageLuma,
                    edgeScore = edgeScore
                )
            )
        } catch (error: Throwable) {
            Log.w(TAG, "Frame analysis failed", error)
        } finally {
            image.close()
        }
    }

    fun reset() {
        previousSamples = null
    }

    private fun sampleLuma(image: ImageProxy): IntArray {
        val plane = image.planes.first()
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        val maxIndex = max(0, buffer.limit() - 1)
        val samples = IntArray(GRID_WIDTH * GRID_HEIGHT)

        for (gridY in 0 until GRID_HEIGHT) {
            val sourceY = (gridY * height / GRID_HEIGHT).coerceIn(0, height - 1)
            for (gridX in 0 until GRID_WIDTH) {
                val sourceX = (gridX * width / GRID_WIDTH).coerceIn(0, width - 1)
                val index = (sourceY * rowStride + sourceX * pixelStride).coerceAtMost(maxIndex)
                samples[gridY * GRID_WIDTH + gridX] = buffer.get(index).toInt() and 0xFF
            }
        }

        return samples
    }

    private fun calculateEdgeScore(samples: IntArray): Double {
        var total = 0.0
        var count = 0

        for (y in 0 until GRID_HEIGHT) {
            for (x in 0 until GRID_WIDTH) {
                val current = samples[y * GRID_WIDTH + x]
                if (x + 1 < GRID_WIDTH) {
                    total += abs(current - samples[y * GRID_WIDTH + x + 1])
                    count += 1
                }
                if (y + 1 < GRID_HEIGHT) {
                    total += abs(current - samples[(y + 1) * GRID_WIDTH + x])
                    count += 1
                }
            }
        }

        return if (count == 0) 0.0 else total / count
    }

    companion object {
        private const val TAG = "LumaFrameAnalyzer"
        private const val GRID_WIDTH = 32
        private const val GRID_HEIGHT = 32
    }
}

