package com.balltrack.ai.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

class PoseFrameAnalyzer(
    private val poseDetector: PoseDetector,
    private val onBitmap: ((Bitmap) -> Unit)? = null // optional: for court detection snapshot only
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = image.yuv420ToBitmap()
            poseDetector.detectAsync(bitmap, image.imageInfo.timestamp)
            onBitmap?.invoke(bitmap) // only used for periodic court detection snapshot
        } catch (_: Exception) {
        } finally {
            image.close() // frame released immediately, never buffered
        }
    }

    private fun ImageProxy.yuv420ToBitmap(): Bitmap {
        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer
        val ySize = yBuf.remaining()
        val uSize = uBuf.remaining()
        val vSize = vBuf.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuf.get(nv21, 0, ySize)
        vBuf.get(nv21, ySize, vSize)
        uBuf.get(nv21, ySize + vSize, uSize)
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, width, height), 85, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
