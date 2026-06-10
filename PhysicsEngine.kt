package com.example.aimai

import kotlin.math.*

class PhysicsEngine(
    private val tableWidth: Float = 1000f,
    private val tableHeight: Float = 1600f
) {

    data class PredictionPath(val points: List<Pair<Float,Float>>, val hitBall: VisionAnalyzer.Ball?)

    fun computeFullPath(cue: Pair<Float,Float>, direction: Pair<Float,Float>,
                        allBalls: List<VisionAnalyzer.Ball>, pockets: List<Point>): PredictionPath {
        val points = mutableListOf(cue)
        var currentPos = cue
        var currentDir = direction
        var hitBall: VisionAnalyzer.Ball? = null
        var bounces = 0
        var steps = 0

        while (bounces < 4 && steps < 200) {
            val next = findNextCollision(currentPos, currentDir, allBalls)
            if (next == null) {
                val far = extendLine(currentPos, currentDir)
                points.add(far)
                break
            }
            points.add(next.point)
            if (next.type == "ball") {
                hitBall = next.ball
                // بعد الاصطدام بالكرة الهدف، نحسب مسارها نحو أقرب حفرة
                val pocketPath = computeBallToPocket(next.ball, pockets)
                points.addAll(pocketPath)
                break
            } else { // edge
                currentPos = next.point
                currentDir = reflectDirection(currentDir, next.edge!!)
                bounces++
            }
            steps++
        }
        return PredictionPath(points, hitBall)
    }

    private data class Collision(val point: Pair<Float,Float>, val type: String, val ball: VisionAnalyzer.Ball?, val edge: String?)

    private fun findNextCollision(pos: Pair<Float,Float>, dir: Pair<Float,Float>,
                                  balls: List<VisionAnalyzer.Ball>): Collision? {
        var closestDist = Float.MAX_VALUE
        var closestBall: VisionAnalyzer.Ball? = null
        for (ball in balls) {
            val toBallX = ball.x - pos.first
            val toBallY = ball.y - pos.second
            val cross = dir.first * toBallY - dir.second * toBallX
            val distToLine = abs(cross) / hypot(dir.first, dir.second)
            if (distToLine < ball.radius) {
                val t = (toBallX * dir.first + toBallY * dir.second) / (dir.first*dir.first + dir.second*dir.second)
                if (t > 0 && t < closestDist) {
                    closestDist = t
                    closestBall = ball
                }
            }
        }
        val edgeCollision = findEdgeCollision(pos, dir)
        if (closestBall != null && closestDist < edgeCollision.distance) {
            val impact = AimMath.pointOnLine(pos, dir, closestDist)
            return Collision(impact, "ball", closestBall, null)
        }
        if (edgeCollision.distance < Float.MAX_VALUE) {
            return Collision(edgeCollision.point, "edge", null, edgeCollision.edge)
        }
        return null
    }

    private data class EdgeCollision(val point: Pair<Float,Float>, val distance: Float, val edge: String)

    private fun findEdgeCollision(pos: Pair<Float,Float>, dir: Pair<Float,Float>): EdgeCollision {
        var bestDist = Float.MAX_VALUE
        var bestPoint = Pair(0f,0f)
        var bestEdge = ""
        // حساب التقاطع مع الحواف الأربعة
        val edges = listOf(
            Triple("left", 0f, 0f, 1f), // x=0
            Triple("right", tableWidth, 0f, 1f),
            Triple("top", 0f, 0f, 0f),
            Triple("bottom", 0f, tableHeight, 0f)
        )
        for (edge in edges) {
            var t = -1f
            var ix = 0f; var iy = 0f
            when (edge.first) {
                "left", "right" -> {
                    if (dir.first != 0f) {
                        t = (edge.second - pos.first) / dir.first
                        iy = pos.second + dir.second * t
                        if (t > 0 && iy in 0f..tableHeight) {
                            if (t < bestDist) { bestDist = t; bestPoint = Pair(edge.second, iy); bestEdge = edge.first }
                        }
                    }
                }
                "top", "bottom" -> {
                    if (dir.second != 0f) {
                        t = (edge.second - pos.second) / dir.second
                        ix = pos.first + dir.first * t
                        if (t > 0 && ix in 0f..tableWidth) {
                            if (t < bestDist) { bestDist = t; bestPoint = Pair(ix, edge.second); bestEdge = edge.first }
                        }
                    }
                }
            }
        }
        return EdgeCollision(bestPoint, bestDist, bestEdge)
    }

    private fun reflectDirection(dir: Pair<Float,Float>, edge: String): Pair<Float,Float> {
        return when (edge) {
            "left", "right" -> Pair(-dir.first, dir.second)
            "top", "bottom" -> Pair(dir.first, -dir.second)
            else -> dir
        }
    }

    private fun extendLine(pos: Pair<Float,Float>, dir: Pair<Float,Float>): Pair<Float,Float> {
        // تمديد الخط حتى يخرج من الشاشة
        var t = Float.MAX_VALUE
        if (dir.first > 0) t = min(t, (tableWidth - pos.first) / dir.first)
        else if (dir.first < 0) t = min(t, (0f - pos.first) / dir.first)
        if (dir.second > 0) t = min(t, (tableHeight - pos.second) / dir.second)
        else if (dir.second < 0) t = min(t, (0f - pos.second) / dir.second)
        return AimMath.pointOnLine(pos, dir, t)
    }

    private fun computeBallToPocket(ball: VisionAnalyzer.Ball, pockets: List<Point>): List<Pair<Float,Float>> {
        val nearest = pockets.minByOrNull { AimMath.distance(ball.x, ball.y, it.x.toFloat(), it.y.toFloat()) }
        return if (nearest != null) listOf(Pair(ball.x, ball.y), Pair(nearest.x.toFloat(), nearest.y.toFloat())) else emptyList()
    }
}
