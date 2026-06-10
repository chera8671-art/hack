package com.example.aimai

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjectionIntent: Intent? = null

    private val startMediaProjection =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                mediaProjectionIntent = result.data
                Toast.makeText(this, "تم منح صلاحية التقاط الشاشة", Toast.LENGTH_SHORT).show()
                // حفظ في SharedPreferences
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putString("media_projection_intent", result.data?.toUri(0)).apply()
            } else {
                Toast.makeText(this, "لم يتم منح الصلاحية", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnRequestPermission).setOnClickListener {
            mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                if (mediaProjectionIntent == null) {
                    Toast.makeText(this, "يجب منح صلاحية التقاط الشاشة أولاً", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(this, FloatingAIService::class.java)
                intent.putExtra("media_projection_intent", mediaProjectionIntent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                Toast.makeText(this, "تم تشغيل المساعد الذكي", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingAIService::class.java))
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // استعادة intent من SharedPreferences إذا كان موجوداً
        val savedUri = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("media_projection_intent", null)
        if (savedUri != null) {
            mediaProjectionIntent = Intent.parseUri(savedUri, 0)
        }
    }
}
