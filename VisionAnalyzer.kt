package com.example.aimai

import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object VisionAnalyzer {

    data class Ball(val x: Float, val y: Float, val radius: Float, val color: Int, val id: Int = 0)

    fun detectBalls(screenBitmap: Bitmap): List<Ball> {
        val balls = mutableListOf<Ball>()
        val mat = Mat()
        Utils.bitmapToMat(screenBitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(9.0, 9.0), 2.0, 2.0)

        val circles = Mat()
        // ضبط المعلمات حسب حجم الكرات في اللعبة
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0, 30.0,
            200.0, 30.0, 15, 45)

        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i)
            val center = Point(data[0], data[1])
            val radius = data[2].toFloat()
            val color = estimateBallColor(screenBitmap, center.x.toInt(), center.y.toInt())
            balls.add(Ball(center.x.toFloat(), center.y.toFloat(), radius, color))
        }
        circles.release(); gray.release(); mat.release()
        return balls
    }

    fun detectPockets(screenBitmap: Bitmap): List<Point> {
        val pockets = mutableListOf<Point>()
        val mat = Mat()
        Utils.bitmapToMat(screenBitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(9.0, 9.0), 2.0, 2.0)
        val circles = Mat()
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0, 40.0,
            150.0, 35.0, 25, 80)
        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i)
            pockets.add(Point(data[0], data[1]))
        }
        circles.release(); gray.release(); mat.release()
        return pockets
    }

    private fun estimateBallColor(bitmap: Bitmap, x: Int, y: Int): Int {
        if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
            return bitmap.getPixel(x, y) and 0xFFFFFF
        }
        return Color.WHITE
    }
}
