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
import java.io.File
import java.io.FileOutputStream
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
        @Volatile private var destroyed = false
        private val drawTask = object : Runnable {
            override fun run() {
                if (visible && surfaceReady) {
                    drawFrame()
                    handler.postDelayed(this, 80L)
                }
            }
        }

        init { loadSelectedWallpaper() }

        private fun liveCacheFile(url: String): File {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray(Charsets.UTF_8))
            val key = digest.joinToString("") { "%02x".format(it) }
            return File(File(filesDir, "live_wallpaper_cache").apply { mkdirs() }, "$key.img")
        }

        private fun trimLiveWallpaperCache(keep: File) {
            try {
                val directory = keep.parentFile ?: return
                val files = directory.listFiles()?.filter { it.isFile } ?: return
                // Remove interrupted downloads before accounting for the image cache.
                files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
                val cachedImages = directory.listFiles()
                    ?.filter { it.isFile && it.extension == "img" } ?: return
                var totalBytes = cachedImages.sumOf { it.length() }
                val maxBytes = 120L * 1024L * 1024L
                if (totalBytes <= maxBytes) return
                for (file in cachedImages.filter { it.absolutePath != keep.absolutePath }
                    .sortedBy { it.lastModified() }) {
                    if (totalBytes <= maxBytes) break
                    val size = file.length()
                    if (file.delete()) totalBytes -= size
                }
            } catch (_: Exception) {
                // Cache trimming is best-effort and must not interrupt wallpaper display.
            }
        }

        private fun decodeForDisplay(file: File): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            // A half-resolution decode is ample for a phone display and avoids
            // keeping a huge UHD bitmap in the live wallpaper service's RAM.
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
                inPreferredConfig = Bitmap.Config.RGB_565
                inDither = true
            }
            return BitmapFactory.decodeFile(file.absolutePath, options)
        }

        private fun loadSelectedWallpaper() {
            val url = getSharedPreferences("wallpaper_preferences", MODE_PRIVATE)
                .getString("selected_live_wallpaper_url", null) ?: return
            Thread {
                val cacheFile = liveCacheFile(url)
                var bitmap: Bitmap? = null
                try {
                    if (cacheFile.isFile && cacheFile.length() > 0L) {
                        bitmap = decodeForDisplay(cacheFile)
                        if (bitmap != null) cacheFile.setLastModified(System.currentTimeMillis())
                        else cacheFile.delete()
                    }
                    if (bitmap == null && !destroyed) {
                        var connection: HttpURLConnection? = null
                        try {
                            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 12000
                                readTimeout = 20000
                                doInput = true
                                instanceFollowRedirects = true
                                requestMethod = "GET"
                            }
                            if (connection.responseCode !in 200..299) {
                                throw IllegalStateException("Live wallpaper HTTP " + connection.responseCode)
                            }
                            if (connection.contentLengthLong > 40L * 1024L * 1024L) {
                                throw IllegalStateException("Live wallpaper file too large")
                            }
                            val temp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
                            try {
                                connection.inputStream.use { input ->
                                    FileOutputStream(temp).use { output ->
                                        val buffer = ByteArray(8192)
                                        var total = 0L
                                        while (true) {
                                            if (destroyed) throw InterruptedException("Wallpaper engine stopped")
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            total += count
                                            if (total > 40L * 1024L * 1024L) {
                                                throw IllegalStateException("Live wallpaper file too large")
                                            }
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                }
                                val validated = decodeForDisplay(temp)
                                if (validated != null) {
                                    val stored = temp.renameTo(cacheFile) || try {
                                        temp.copyTo(cacheFile, overwrite = true)
                                        temp.delete()
                                        cacheFile.isFile && cacheFile.length() > 0L
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (stored) {
                                        bitmap = validated
                                        cacheFile.setLastModified(System.currentTimeMillis())
                                    } else {
                                        validated.recycle()
                                    }
                                }
                            } finally {
                                if (temp.exists()) temp.delete()
                            }
                        } finally {
                            connection?.disconnect()
                        }
                    }
                } catch (_: Exception) {
                    bitmap?.recycle()
                    bitmap = null
                }
                trimLiveWallpaperCache(cacheFile)
                val loaded = bitmap
                if (loaded != null) {
                    if (destroyed) {
                        loaded.recycle()
                    } else {
                        wallpaperBitmap = loaded
                        handler.post {
                            if (!destroyed && (visible || surfaceReady)) drawFrame()
                        }
                    }
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
            destroyed = true
            visible = false
            surfaceReady = false
            handler.removeCallbacks(drawTask)
            wallpaperBitmap?.let { if (!it.isRecycled) it.recycle() }
            wallpaperBitmap = null
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