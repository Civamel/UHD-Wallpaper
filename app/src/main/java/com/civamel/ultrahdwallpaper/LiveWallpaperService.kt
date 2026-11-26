package com.civamel.ultrahdwallpaper

import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * MP4 live wallpaper engine. Video is cached locally, muted, looped and only
 * played while Android reports the wallpaper as visible.
 */
class LiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = Mp4LiveEngine()

    private inner class Mp4LiveEngine : Engine() {
        private val mainHandler = Handler(Looper.getMainLooper())
        private var player: MediaPlayer? = null
        private var visible = false
        private var surfaceReady = false
        @Volatile private var destroyed = false
        private var loading = false
        private var preparedFile: File? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loadSelectedVideo()
        }

        private fun cacheFile(url: String): File {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            val key = digest.joinToString("") { "%02x".format(it) }
            return File(File(filesDir, "live_video_cache").apply { mkdirs() }, "$key.mp4")
        }

        private fun loadSelectedVideo() {
            if (loading) return
            val videoUrl = getSharedPreferences("wallpaper_preferences", MODE_PRIVATE)
                .getString("selected_live_wallpaper_url", null)?.trim().orEmpty()
            if (!videoUrl.startsWith("https://") || !videoUrl.substringBefore("?").lowercase().endsWith(".mp4")) return
            loading = true
            Thread {
                val target = cacheFile(videoUrl)
                var connection: HttpURLConnection? = null
                var success = false
                try {
                    if (!target.isFile || target.length() <= 0L) {
                        val temp = File(target.parentFile, target.name + ".tmp")
                        temp.delete()
                        try {
                            connection = (URL(videoUrl).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 15000
                                readTimeout = 30000
                                instanceFollowRedirects = true
                                requestMethod = "GET"
                                doInput = true
                            }
                            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
                            if (connection.contentLengthLong > 50L * 1024L * 1024L) throw IllegalStateException("Video exceeds 50 MB")
                            connection.inputStream.use { input ->
                                FileOutputStream(temp).use { output ->
                                    val buffer = ByteArray(16 * 1024)
                                    var total = 0L
                                    while (true) {
                                        if (destroyed) throw InterruptedException("Wallpaper service stopped")
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > 50L * 1024L * 1024L) throw IllegalStateException("Video exceeds 50 MB")
                                        output.write(buffer, 0, count)
                                    }
                                    output.fd.sync()
                                }
                            }
                            if (!temp.renameTo(target)) {
                                temp.copyTo(target, overwrite = true)
                                temp.delete()
                            }
                        } finally {
                            if (temp.exists()) temp.delete()
                        }
                    }
                    success = target.isFile && target.length() > 0L
                } catch (_: Exception) {
                    success = false
                } finally {
                    connection?.disconnect()
                }
                mainHandler.post {
                    loading = false
                    if (destroyed) return@post
                    if (success) {
                        preparedFile = target
                        preparePlayer()
                    }
                }
            }.start()
        }

        private fun preparePlayer() {
            val file = preparedFile ?: return
            if (destroyed || !surfaceReady) return
            releasePlayer()
            try {
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setDisplay(surfaceHolder)
                    isLooping = true
                    setVolume(0f, 0f)
                    setOnPreparedListener { mp ->
                        if (!destroyed && visible && surfaceReady) {
                            try { mp.start() } catch (_: Exception) { }
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        releasePlayer()
                        true
                    }
                    prepareAsync()
                }
            } catch (_: Exception) {
                releasePlayer()
            }
        }

        private fun releasePlayer() {
            val current = player
            player = null
            try { current?.setDisplay(null) } catch (_: Exception) { }
            try { current?.reset() } catch (_: Exception) { }
            try { current?.release() } catch (_: Exception) { }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            if (player == null) preparePlayer()
            else try { player?.setDisplay(holder); if (visible && player?.isPlaying == false) player?.start() } catch (_: Exception) { }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceReady = true
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            try { if (player?.isPlaying == true) player?.pause() } catch (_: Exception) { }
            try { player?.setDisplay(null) } catch (_: Exception) { }
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            val current = player ?: run {
                if (isVisible && preparedFile != null && surfaceReady) preparePlayer()
                return
            }
            try {
                if (isVisible && surfaceReady) {
                    if (current.isPlaying.not()) current.start()
                } else if (current.isPlaying) {
                    current.pause()
                }
            } catch (_: Exception) { }
        }

        override fun onDestroy() {
            destroyed = true
            visible = false
            surfaceReady = false
            releasePlayer()
            super.onDestroy()
        }
    }
}
