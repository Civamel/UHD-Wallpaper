package com.civamel.ultrahdwallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.sin

class LiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = ImageLiveEngine()

    private inner class ImageLiveEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        @Volatile private var wallpaperBitmap: Bitmap? = null
        private var visible = false
        private var surfaceReady = false
        private val drawTask = object : Runnable {
            override fun run() {
                if (visible && surfaceReady) {
                    drawFrame()
                    handler.postDelayed(this, 80L)
                }
            }
        }

        init { loadSelectedWallpaper() }

        private fun loadSelectedWallpaper() {
            val url = getSharedPreferences("wallpaper_preferences", MODE_PRIVATE)
                .getString("selected_live_wallpaper_url", null) ?: return
            Thread {
                var connection: HttpURLConnection? = null
                val bitmap = try {
                    connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 20000
                        doInput = true
                    }
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) {
                    null
                } finally {
                    connection?.disconnect()
                }
                if (bitmap != null && !bitmap.isRecycled) {
                    wallpaperBitmap = bitmap
                    handler.post { if (visible || surfaceReady) drawFrame() }
                }
            }.start()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            drawFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = true
            drawFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(drawTask)
            if (visible && surfaceReady) handler.post(drawTask)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            surfaceReady = false
            handler.removeCallbacks(drawTask)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            surfaceReady = false
            handler.removeCallbacks(drawTask)
            super.onDestroy()
        }

        private fun drawFrame() {
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    val w = canvas.width.toFloat()
                    val h = canvas.height.toFloat()
                    val bitmap = wallpaperBitmap
                    if (bitmap != null && !bitmap.isRecycled) {
                        val scale = max(w / bitmap.width, h / bitmap.height)
                        val scaledW = bitmap.width * scale
                        val scaledH = bitmap.height * scale
                        val overflowX = (scaledW - w).coerceAtLeast(0f)
                        val drift = (sin(SystemClock.uptimeMillis() / 6500.0) * overflowX * 0.18).toFloat()
                        val left = -(overflowX / 2f) + drift
                        val top = -(scaledH - h) / 2f
                        paint.shader = null
                        canvas.drawBitmap(bitmap, null, RectF(left, top, left + scaledW, top + scaledH), paint)
                    } else {
                        val shift = (sin(SystemClock.uptimeMillis() / 1800.0) * w * 0.22).toFloat()
                        paint.shader = LinearGradient(
                            shift, 0f, w - shift, h,
                            intArrayOf(Color.rgb(7, 22, 45), Color.rgb(25, 105, 132), Color.rgb(91, 68, 151)),
                            null, Shader.TileMode.CLAMP
                        )
                        canvas.drawRect(0f, 0f, w, h, paint)
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (canvas != null) {
                    try { surfaceHolder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
                }
            }
        }
    }
}