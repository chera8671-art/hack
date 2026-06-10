package com.example.aimai

import kotlin.math.*

object AimMath {
    fun angleBetween(x1: Float, y1: Float, x2: Float, y2: Float): Double = atan2(y2 - y1, x2 - x1)

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)

    fun ghostBall(cueX: Float, cueY: Float, targetX: Float, targetY: Float, ballRadius: Float = 28f): Pair<Float, Float> {
        val angle = angleBetween(cueX, cueY, targetX, targetY)
        val dx = cos(angle).toFloat() * ballRadius * 2
        val dy = sin(angle).toFloat() * ballRadius * 2
        return Pair(targetX - dx, targetY - dy)
    }

    fun pointOnLine(start: Pair<Float,Float>, dir: Pair<Float,Float>, t: Float): Pair<Float,Float> {
        return Pair(start.first + dir.first * t, start.second + dir.second * t)
    }
}
