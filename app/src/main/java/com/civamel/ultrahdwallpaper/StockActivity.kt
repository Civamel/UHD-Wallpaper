package com.civamel.ultrahdwallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class StockActivity : Activity() {
    private data class StockItem(val title: String, val category: String, val thumbnail: String, val image: String)
    private val items = mutableListOf<StockItem>()
    private val executor = Executors.newFixedThreadPool(3)
    private var query = ""
    private var selectedCategory = "All"
    private var gridHostView: LinearLayout? = null
    private val blue = Color.rgb(0, 119, 255)
    private val ink = Color.rgb(28, 28, 30)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        loadCatalog()
    }

    private fun loadCatalog() {
        try {
            val body = assets.open("wallpapers.json").bufferedReader().use { it.readText() }
            parseCatalog(body)
        } catch (_: Exception) {
            showError("Katalog bawaan tidak dapat dibaca.")
            return
        }
        showGallery()
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://raw.githubusercontent.com/Civamel/UHD-Wallpaper/main/catalog/wallpapers.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    requestMethod = "GET"
                }
                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseItems(body)
                    if (parsed.isNotEmpty()) runOnUiThread {
                        items.clear()
                        items.addAll(parsed)
                        showGallery()
                    }
                }
            } catch (_: Exception) {
                // Keep the bundled stock catalog available offline.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseCatalog(body: String) {
        val parsed = parseItems(body)
        items.clear()
        items.addAll(parsed)
    }

    private fun parseItems(body: String): List<StockItem> {
        val result = mutableListOf<StockItem>()
        val array = JSONObject(body).optJSONArray("wallpapers") ?: return result
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            val category = item.optString("category", "Stock").ifBlank { "Stock" }
            val thumbnail = item.optString("thumbnail").trim()
            val image = item.optString("image").trim()
            if (title.isNotEmpty() && thumbnail.startsWith("https://") && image.startsWith("https://")) {
                result.add(StockItem(title, category, thumbnail, image))
            }
        }
        return result
    }

    private fun showGallery() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(248, 248, 250))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(16), dp(4))
        }
        header.addView(label("‹", 32, ink, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(label("Stock", 24, ink, true), LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(label(items.size.toString() + " foto", 13, Color.GRAY, false))
        root.addView(header)
        root.addView(label("Koleksi wallpaper pilihan untuk layar perangkat", 13, Color.GRAY, false).apply {
            setPadding(dp(18), 0, dp(18), dp(12))
        })
        val search = EditText(this).apply {
            hint = "Cari stock wallpaper"
            textSize = 14f
            isSingleLine = true
            setText(query)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.WHITE, 14)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                    renderGrid()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(dp(16), 0, dp(16), dp(10))
        })
        val categories = listOf("All") + items.map { it.category }.distinct().sorted()
        val categoryScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(12), dp(10)) }
        categories.forEach { category ->
            val chip = TextView(this).apply {
                text = category
                textSize = 13f
                setTextColor(if (selectedCategory == category) Color.WHITE else ink)
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(9), dp(15), dp(9))
                background = rounded(if (selectedCategory == category) blue else Color.WHITE, 22)
                setOnClickListener {
                    selectedCategory = category
                    showGallery()
                }
            }
            categoryRow.addView(chip, LinearLayout.LayoutParams(-2, dp(38)).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        categoryScroll.addView(categoryRow)
        root.addView(categoryScroll)
        val gridScroll = ScrollView(this)
        val gridHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gridHostView = gridHost
        gridScroll.addView(gridHost)
        root.addView(gridScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val ad = label("ADVERTISEMENT  •  Ultra HD Wallpaper", 10, Color.DKGRAY, false).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(240, 240, 242))
        }
        root.addView(ad, LinearLayout.LayoutParams(-1, dp(40)))
        setContentView(root)
        renderGridInto(gridHost)
    }

    private fun renderGrid() {
        gridHostView?.let { renderGridInto(it) }
    }

    private fun renderGridInto(host: LinearLayout) {
        host.removeAllViews()
        val matches = items.filter {
            (selectedCategory == "All" || it.category == selectedCategory) &&
                (query.isBlank() || it.title.contains(query, true) || it.category.contains(query, true))
        }
        if (matches.isEmpty()) {
            host.addView(label("Tidak ada wallpaper yang cocok.", 14, Color.GRAY, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(48), dp(24), dp(48))
            })
            return
        }
        val grid = GridLayout(this).apply { columnCount = 2; useDefaultMargins = false }
        matches.forEach { item ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(3), dp(3), dp(3), dp(8))
                setOnClickListener { openItem(item) }
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(0xffdddddd.toInt(), 10)
                setImageDrawable(GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xff303746.toInt(), 0xff8996aa.toInt())))
            }
            cell.addView(image, LinearLayout.LayoutParams(-1, dp(210)))
            loadThumbnail(item.thumbnail) { bitmap ->
                if (bitmap != null && image.isAttachedToWindow) image.setImageBitmap(bitmap)
            }
            cell.addView(label(item.title, 14, ink, true).apply { setPadding(dp(5), dp(7), dp(5), 0); maxLines = 1 })
            cell.addView(label(item.category, 12, Color.GRAY, false).apply { setPadding(dp(5), dp(2), dp(5), 0) })
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = -2
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(4), dp(5), dp(4))
            }
            grid.addView(cell, params)
        }
        host.addView(grid)
    }

    private fun loadThumbnail(url: String, done: (Bitmap?) -> Unit) {
        executor.execute {
            var connection: HttpURLConnection? = null
            val bitmap = try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    doInput = true
                }
                if (connection.responseCode in 200..299) connection.inputStream.use { BitmapFactory.decodeStream(it) } else null
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
            runOnUiThread { done(bitmap) }
        }
    }

    private fun openItem(item: StockItem) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(preview, LinearLayout.LayoutParams(-1, dp(360)))
        AlertDialog.Builder(this).setTitle(item.title).setView(layout)
            .setNegativeButton("Tutup", null)
            .setPositiveButton("Terapkan", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        applyItem(item)
                        dialog.dismiss()
                    }
                }
                dialog.show()
                loadThumbnail(item.image) { bitmap -> if (bitmap != null && preview.isAttachedToWindow) preview.setImageBitmap(bitmap) }
            }
    }

    private fun applyItem(item: StockItem) {
        Toast.makeText(this, "Menyiapkan wallpaper…", Toast.LENGTH_SHORT).show()
        executor.execute {
            var connection: HttpURLConnection? = null
            val bitmap = try {
                connection = (URL(item.image).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    doInput = true
                }
                if (connection.responseCode in 200..299) connection.inputStream.use { BitmapFactory.decodeStream(it) } else null
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                if (bitmap == null) {
                    Toast.makeText(this, "Gambar gagal dimuat. Periksa koneksi internet.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                try {
                    WallpaperManager.getInstance(this).setBitmap(bitmap)
                    Toast.makeText(this, "Wallpaper layar utama berhasil diterapkan", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal menerapkan wallpaper: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showError(message: String) {
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(label(message, 16, ink, true))
            addView(Button(this@StockActivity).apply { text = "Kembali"; setOnClickListener { finish() } })
        })
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
