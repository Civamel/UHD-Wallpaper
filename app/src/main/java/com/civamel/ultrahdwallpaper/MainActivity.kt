package com.civamel.ultrahdwallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color

class MainActivity : Activity() {
    private val ink = Color.rgb(18, 20, 25)
    private val accent = Color.rgb(91, 112, 245)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink
        window.decorView.systemUiVisibility = 0
        buildHome()
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
            setPadding(dp(20), dp(22), dp(20), dp(18))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(label("ULTRA HD", 13, Color.LTGRAY, true))
        content.addView(label("Wallpaper", 30, Color.WHITE, true))
        content.addView(label("Discover wallpapers for every screen.", 14, Color.rgb(180,184,195), false))
        content.addView(space(22))
        content.addView(section("Featured wallpapers"))
        content.addView(wallpaperCard("Aurora Dreams", "4K • Nature", intArrayOf(0xff102b46.toInt(), 0xff397e9b.toInt()), false))
        content.addView(wallpaperCard("Midnight Flow", "4K • Abstract", intArrayOf(0xff17152e.toInt(), 0xff624a8a.toInt()), false))
        content.addView(space(12))
        content.addView(section("Live wallpapers"))
        content.addView(wallpaperCard("Living Aurora", "Animated • Live wallpaper", intArrayOf(0xff102b46.toInt(), 0xff397e9b.toInt()), true))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = TextView(this).apply {
            text = "⌂  Home                         ◈  Categories                         ♡  Favorites"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun wallpaperCard(title: String, subtitle: String, colors: IntArray, live: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(35, 38, 47))
        }
        val preview = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colors
            ).apply { cornerRadius = dp(14).toFloat() }
        }
        card.addView(preview, LinearLayout.LayoutParams(-1, dp(178)))
        card.addView(space(10))
        card.addView(label(title, 18, Color.WHITE, true))
        card.addView(label(subtitle, 12, Color.LTGRAY, false))
        card.addView(space(8))
        val button = Button(this).apply {
            text = if (live) "Preview & set live wallpaper" else "Preview & apply wallpaper"
            setTextColor(Color.WHITE)
            setBackgroundColor(accent)
            isAllCaps = false
            setOnClickListener {
                if (live) openLiveWallpaper() else applyWallpaper(colors)
            }
        }
        card.addView(button, LinearLayout.LayoutParams(-1, dp(48)))
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(16)
        card.layoutParams = params
        return card
    }

    private fun applyWallpaper(colors: IntArray) {
        val choices = arrayOf("Home Screen", "Lock Screen", "Keduanya")
        android.app.AlertDialog.Builder(this)
            .setTitle("Terapkan wallpaper")
            .setItems(choices) { _, which -> setWallpaper(colors, which) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setWallpaper(colors: IntArray, destination: Int) {
        var bitmap: Bitmap? = null
        try {
            bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = android.graphics.LinearGradient(0f, 0f, 1080f, 1920f, colors[0], colors[1], android.graphics.Shader.TileMode.CLAMP)
            canvas.drawRect(RectF(0f, 0f, 1080f, 1920f), paint)
            val manager = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= 24) {
                val flags = when (destination) {
                    1 -> WallpaperManager.FLAG_LOCK
                    2 -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    else -> WallpaperManager.FLAG_SYSTEM
                }
                manager.setBitmap(bitmap, null, true, flags)
            } else {
                manager.setBitmap(bitmap)
                if (destination != 0) Toast.makeText(this, "Android versi ini hanya mendukung layar utama dari aplikasi.", Toast.LENGTH_LONG).show()
            }
            Toast.makeText(this, "Wallpaper berhasil diterapkan", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat menerapkan wallpaper: " + e.localizedMessage, Toast.LENGTH_LONG).show()
        } finally {
            bitmap?.recycle()
        }
    }
    private fun openLiveWallpaper() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, LiveWallpaperService::class.java))
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Live wallpaper tidak tersedia di perangkat ini", Toast.LENGTH_LONG).show()
        }
    }

    private fun section(text: String) = label(text, 20, Color.WHITE, true).apply { setPadding(0, dp(4), 0, dp(12)) }
    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
