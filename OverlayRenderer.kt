package com.example.aimai

import android.content.Context
import android.graphics.*
import android.view.View

class OverlayRenderer(context: Context) : View(context) {
    private var currentPath: PhysicsEngine.PredictionPath? = null
    private var whiteBallPos: Pair<Float,Float>? = null
    private var targetBallPos: Pair<Float,Float>? = null

    private val paintSolid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val paintDash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(25f, 15f), 0f)
    }
    private val paintPocket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 0)
        style = Paint.Style.FILL
    }
    private val paintGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 0, 0)
        style = Paint.Style.FILL
    }
    private val paintWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val paintTarget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 100, 100)
        style = Paint.Style.FILL
    }

    fun setPath(path: PhysicsEngine.PredictionPath?, white: Pair<Float,Float>? = null, target: Pair<Float,Float>? = null) {
        currentPath = path
        whiteBallPos = white
        targetBallPos = target
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // رسم الكرات إذا كانت محددة يدوياً
        whiteBallPos?.let { canvas.drawCircle(it.first, it.second, 28f, paintWhite) }
        targetBallPos?.let { canvas.drawCircle(it.first, it.second, 28f, paintTarget) }

        val path = currentPath ?: return
        if (path.points.size < 2) return

        for (i in 0 until path.points.size - 1) {
            val p1 = path.points[i]
            val p2 = path.points[i+1]
            val paint = if (i == 0) paintSolid else paintDash
            canvas.drawLine(p1.first, p1.second, p2.first, p2.second, paint)
            // رسم Ghost ball عند الاصطدام
            if (path.hitBall != null && i == path.points.size - 2) {
                canvas.drawCircle(p2.first, p2.second, 30f, paintGhost)
            }
        }
    }
}
