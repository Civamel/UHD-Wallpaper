package com.civamel.ultrahdwallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Color

class LiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = AuroraEngine()

    private inner class AuroraEngine : Engine() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            drawFrame()
        }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame()
        }
        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) drawFrame()
        }
        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    paint.shader = LinearGradient(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(),
                        intArrayOf(Color.rgb(8, 25, 48), Color.rgb(31, 100, 125), Color.rgb(90, 70, 145)),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                }
            } catch (_: Exception) {
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
