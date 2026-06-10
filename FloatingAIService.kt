package com.example.aimai

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingAIService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayRenderer: OverlayRenderer
    private lateinit var floatingMenu: View
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private var screenWidth = 0
    private var screenHeight = 0
    private var densityDpi = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAnalyzing = false
    private var currentThickness = 4

    // بيانات التحليل
    private var allBalls: List<VisionAnalyzer.Ball> = emptyList()
    private var pockets: List<Point> = emptyList()
    private var bestPath: PhysicsEngine.PredictionPath? = null
    private var whiteBallManual: Pair<Float,Float>? = null
    private var targetBallManual: Pair<Float,Float>? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        densityDpi = metrics.densityDpi

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
        setupFloatingMenu()
        startForeground(1001, getNotification())
        setupMediaProjection()
    }

    private fun getNotification(): Notification {
        val channelId = "ai_aim_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AI Aim Assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI Aim Assistant")
            .setContentText("يعمل في الخلفية ويحلل الطاولة")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    private fun setupOverlay() {
        overlayRenderer = OverlayRenderer(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayRenderer, params)
    }

    private fun setupFloatingMenu() {
        floatingMenu = LayoutInflater.from(this).inflate(R.layout.floating_menu_ai, null)
        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        menuParams.gravity = Gravity.TOP or Gravity.START
        menuParams.x = 80
        menuParams.y = 200
        windowManager.addView(floatingMenu, menuParams)

        floatingMenu.findViewById<Button>(R.id.btnAnalyze).setOnClickListener {
            runAIAnalysis()
        }
        floatingMenu.findViewById<Button>(R.id.btnSetWhite).setOnClickListener {
            Toast.makeText(this, "انقر على الكرة البيضاء", Toast.LENGTH_SHORT).show()
            // سنقوم بتفعيل وضع اللمس المؤقت (يمكن إضافة ذلك بسهولة)
        }
        floatingMenu.findViewById<Button>(R.id.btnSetTarget).setOnClickListener {
            Toast.makeText(this, "انقر على الكرة الهدف", Toast.LENGTH_SHORT).show()
        }
        floatingMenu.findViewById<Button>(R.id.btnMinus).setOnClickListener {
            if (currentThickness > 2) {
                currentThickness--
                updateThickness()
            }
        }
        floatingMenu.findViewById<Button>(R.id.btnPlus).setOnClickListener {
            if (currentThickness < 15) {
                currentThickness++
                updateThickness()
            }
        }
        floatingMenu.findViewById<Button>(R.id.btnClose).setOnClickListener {
            stopSelf()
        }

        // جعل القائمة قابلة للسحب
        floatingMenu.setOnTouchListener(floatingMenuDragListener)
    }

    private val floatingMenuDragListener = View.OnTouchListener { v, event ->
        val params = v.layoutParams as WindowManager.LayoutParams
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                params.x = (event.rawX - v.width / 2).toInt()
                params.y = (event.rawY - v.height / 2).toInt()
                return@OnTouchListener true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = (event.rawX - v.width / 2).toInt()
                params.y = (event.rawY - v.height / 2).toInt()
                windowManager.updateViewLayout(v, params)
                return@OnTouchListener true
            }
        }
        false
    }

    private fun updateThickness() {
        floatingMenu.findViewById<TextView>(R.id.txtThick).text = "سمك $currentThickness"
        overlayRenderer.invalidate() // سيتم تحديث الرسم لاحقاً
    }

    private fun setupMediaProjection() {
        val intent = intent
        val projectionIntent = intent.getParcelableExtra<Intent>("media_projection_intent")
        if (projectionIntent == null) {
            Toast.makeText(this, "لم يتم العثور على صلاحية التقاط الشاشة", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(Activity.RESULT_OK, projectionIntent)
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null && !isAnalyzing) {
                isAnalyzing = true
                val bitmap = imageToBitmap(image)
                image.close()
                analyzeScreen(bitmap)
            }
        }, mainHandler)
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
            screenWidth, screenHeight, densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null)
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        val bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
    }

    private fun analyzeScreen(bitmap: Bitmap) {
        // 1. كشف الكرات
        val detectedBalls = VisionAnalyzer.detectBalls(bitmap)
        // 2. كشف الحفر
        val detectedPockets = VisionAnalyzer.detectPockets(bitmap)

        mainHandler.post {
            allBalls = detectedBalls
            pockets = detectedPockets
            // إذا كان هناك تعيين يدوي، ندمجه
            // نمرر البيانات إلى overlayRenderer (سيتم تحديثه عند الضغط على AI Analyze)
        }
        isAnalyzing = false
    }

    private fun runAIAnalysis() {
        if (allBalls.isEmpty()) {
            Toast.makeText(this, "لم يتم كشف الكرات بعد، انتظر قليلاً", Toast.LENGTH_SHORT).show()
            return
        }
        val cueBall = allBalls.firstOrNull { it.color == Color.WHITE || it.color == 0xFFFFFFFF.toInt() }
        val targetBalls = allBalls.filter { it != cueBall }
        if (cueBall == null || targetBalls.isEmpty()) {
            Toast.makeText(this, "لم يتم التعرف على الكرة البيضاء أو الكرات الهدف", Toast.LENGTH_SHORT).show()
            return
        }

        // تجربة جميع الكرات الهدف وحساب المسار الأسهل
        var bestScore = Float.MAX_VALUE
        var bestPrediction: PhysicsEngine.PredictionPath? = null
        val engine = PhysicsEngine(screenWidth.toFloat(), screenHeight.toFloat())
        for (target in targetBalls) {
            val direction = Pair(target.x - cueBall.x, target.y - cueBall.y)
            val path = engine.computeFullPath(Pair(cueBall.x, cueBall.y), direction, allBalls, pockets.map { Point(it.x, it.y) })
            // معيار التقييم: عدد النقاط الأقل يعني مساراً أقصر
            val score = path.points.size.toFloat()
            if (score < bestScore) {
                bestScore = score
                bestPrediction = path
            }
        }
        bestPath = bestPrediction
        mainHandler.post {
            overlayRenderer.setPath(bestPath, Pair(cueBall.x, cueBall.y), null)
            Toast.makeText(this, "✅ أفضل مسار محسوب (ارتدادات: ${bestPath?.points?.size?.minus(1)})", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::virtualDisplay.isInitialized) virtualDisplay.release()
        if (::mediaProjection.isInitialized) mediaProjection.stop()
        if (::imageReader.isInitialized) imageReader.close()
        if (::overlayRenderer.isInitialized) windowManager.removeView(overlayRenderer)
        if (::floatingMenu.isInitialized) windowManager.removeView(floatingMenu)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
