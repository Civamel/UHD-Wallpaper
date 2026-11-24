package com.civamel.ultrahdwallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
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
    private var selectedSort = "Default"
    private var isRefreshingCatalog = false
    private var showOnlyFavorites = false
    private val favorites = mutableSetOf<String>()
    private var gridHostView: LinearLayout? = null
    private val blue = Color.rgb(0, 119, 255)
    private val ink = Color.rgb(28, 28, 30)
    private var darkMode = false
    private val pageBg get() = if (darkMode) 0xff121318.toInt() else Color.rgb(248, 248, 250)
    private val cardBg get() = if (darkMode) 0xff24262d.toInt() else Color.WHITE
    private val primaryText get() = if (darkMode) 0xfff2f2f5.toInt() else ink
    private val secondaryText get() = if (darkMode) 0xffaaaaaa.toInt() else Color.GRAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("wallpaper_preferences", MODE_PRIVATE)
        darkMode = preferences.getBoolean("dark_mode", false)
        favorites.addAll(preferences.getStringSet("favorite_titles", emptySet()) ?: emptySet())
        window.statusBarColor = pageBg
        window.navigationBarColor = pageBg
        if (Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = if (darkMode) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        loadCatalog()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        gridHostView = null
        super.onDestroy()
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
                        if (!isFinishing && !isDestroyed) {
                            items.clear()
                            items.addAll(parsed)
                            showGallery()
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep the bundled stock catalog available offline.
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun refreshCatalogManually() {
        if (isRefreshingCatalog) {
            Toast.makeText(this, "Katalog sedang diperbarui…", Toast.LENGTH_SHORT).show()
            return
        }
        isRefreshingCatalog = true
        Toast.makeText(this, "Memperbarui koleksi Stock…", Toast.LENGTH_SHORT).show()
        executor.execute {
            var connection: HttpURLConnection? = null
            val parsed = try {
                connection = (URL("https://raw.githubusercontent.com/Civamel/UHD-Wallpaper/main/catalog/wallpapers.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    requestMethod = "GET"
                }
                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseItems(body)
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            } finally {
                connection?.disconnect()
            }
            if (!isFinishing && !isDestroyed) runOnUiThread {
                isRefreshingCatalog = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (parsed.isNotEmpty()) {
                    items.clear()
                    items.addAll(parsed)
                    if (selectedCategory != "All" && items.none { it.category == selectedCategory }) selectedCategory = "All"
                    showGallery()
                    Toast.makeText(this, "Koleksi berhasil diperbarui.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Belum dapat memperbarui katalog. Coba lagi nanti.", Toast.LENGTH_LONG).show()
                }
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
            setBackgroundColor(pageBg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(16), dp(4))
        }
        header.addView(label("‹", 32, primaryText, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(label("Stock", 24, primaryText, true), LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(label(items.size.toString() + " foto", 13, secondaryText, false))
        header.addView(label(if (showOnlyFavorites) "♥" else "♡", 24, if (showOnlyFavorites) blue else primaryText, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                showOnlyFavorites = !showOnlyFavorites
                showGallery()
            }
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        header.addView(label("↻", 25, blue, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { refreshCatalogManually() }
        }, LinearLayout.LayoutParams(dp(44), dp(48)))
        root.addView(header)
        root.addView(label("Koleksi wallpaper pilihan untuk layar perangkat", 13, secondaryText, false).apply {
            setPadding(dp(18), 0, dp(18), dp(12))
        })
        val search = EditText(this).apply {
            hint = "Cari stock wallpaper"
            textSize = 14f
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            isSingleLine = true
            setText(query)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(cardBg, 14)
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
            setMargins(dp(16), 0, dp(16), dp(8))
        })
        val sortRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        sortRow.addView(label("Urutkan:", 13, secondaryText, false), LinearLayout.LayoutParams(-2, dp(42)))
        val sortSpinner = Spinner(this).apply {
            val options = listOf("Default", "Nama A–Z", "Nama Z–A", "Kategori")
            adapter = ArrayAdapter(this@StockActivity, android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(options.indexOf(selectedSort).coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = options[position]
                    if (selectedSort != next) { selectedSort = next; renderGrid() }
                }
            }
        }
        sortRow.addView(sortSpinner, LinearLayout.LayoutParams(0, dp(42), 1f))
        root.addView(sortRow)
        val categories = listOf("All") + items.map { it.category }.distinct().sorted()
        val categoryScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(12), dp(10)) }
        categories.forEach { category ->
            val categoryCount = if (category == "All") items.size else items.count { it.category == category }
            val chip = TextView(this).apply {
                text = "$category ($categoryCount)"
                textSize = 13f
                setTextColor(if (selectedCategory == category) Color.WHITE else primaryText)
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(9), dp(15), dp(9))
                background = rounded(if (selectedCategory == category) blue else cardBg, 22)
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
            setTextColor(if (darkMode) 0xffcccccc.toInt() else Color.DKGRAY)
            setBackgroundColor(if (darkMode) 0xff202127.toInt() else Color.rgb(240, 240, 242))
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
        val filtered = items.filter {
            (selectedCategory == "All" || it.category == selectedCategory) &&
                (!showOnlyFavorites || favorites.contains(it.title)) &&
                (query.isBlank() || it.title.contains(query, true) || it.category.contains(query, true))
        }
        val matches = when (selectedSort) {
            "Nama A–Z" -> filtered.sortedBy { it.title.lowercase() }
            "Nama Z–A" -> filtered.sortedByDescending { it.title.lowercase() }
            "Kategori" -> filtered.sortedWith(compareBy<StockItem> { it.category.lowercase() }.thenBy { it.title.lowercase() })
            else -> filtered
        }
        if (matches.isEmpty()) {
            val emptyState = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(48), dp(24), dp(48))
            }
            emptyState.addView(label("Tidak ada wallpaper yang cocok.", 15, primaryText, true).apply {
                gravity = Gravity.CENTER
            })
            emptyState.addView(label("Coba kata kunci atau kategori lain.", 13, secondaryText, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            })
            emptyState.addView(Button(this).apply {
                text = "Hapus filter"
                setOnClickListener {
                    query = ""
                    selectedCategory = "All"
                    selectedSort = "Default"
                    showOnlyFavorites = false
                    showGallery()
                }
            })
            host.addView(emptyState, LinearLayout.LayoutParams(-1, -2))
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
            val imageFrame = FrameLayout(this).apply {
                addView(image, FrameLayout.LayoutParams(-1, dp(210)))
                addView(label(if (item.title in favorites) "♥" else "♡", 18, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    background = rounded(0xcc000000.toInt(), 18)
                    setOnClickListener {
                        if (!favorites.add(item.title)) favorites.remove(item.title)
                        getSharedPreferences("wallpaper_preferences", MODE_PRIVATE).edit()
                            .putStringSet("favorite_titles", favorites.toSet()).apply()
                        showGallery()
                    }
                }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.START).apply {
                    setMargins(dp(7), dp(7), 0, 0)
                })
            }
            cell.addView(imageFrame, LinearLayout.LayoutParams(-1, dp(210)))
            loadThumbnail(item.thumbnail) { bitmap ->
                if (bitmap != null && image.isAttachedToWindow && !isFinishing && !isDestroyed) image.setImageBitmap(bitmap)
            }
            cell.addView(label(item.title, 14, primaryText, true).apply { setPadding(dp(5), dp(7), dp(5), 0); maxLines = 1 })
            cell.addView(label(item.category, 12, secondaryText, false).apply { setPadding(dp(5), dp(2), dp(5), 0) })
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
            if (!isFinishing && !isDestroyed) runOnUiThread { if (!isFinishing && !isDestroyed) done(bitmap) }
        }
    }

    private fun openItem(item: StockItem) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(pageBg) }
        val preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        layout.addView(preview, LinearLayout.LayoutParams(-1, dp(360)))
        val categoryInfo = TextView(this).apply {
            text = "KATEGORI  •  " + item.category
            textSize = 12f
            setTextColor(secondaryText)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(12), dp(4), dp(6))
        }
        layout.addView(categoryInfo, LinearLayout.LayoutParams(-1, -2))
        val detailInfo = TextView(this).apply {
            text = "Pratinjau wallpaper Stock"
            textSize = 13f
            setTextColor(primaryText)
            setPadding(dp(4), 0, dp(4), dp(6))
        }
        layout.addView(detailInfo, LinearLayout.LayoutParams(-1, -2))
        val favoriteButton = Button(this).apply {
            text = if (item.title in favorites) "♥  Hapus dari favorit" else "♡  Tambahkan ke favorit"
            setOnClickListener {
                if (!favorites.add(item.title)) favorites.remove(item.title)
                getSharedPreferences("wallpaper_preferences", MODE_PRIVATE).edit()
                    .putStringSet("favorite_titles", favorites.toSet()).apply()
                text = if (item.title in favorites) "♥  Hapus dari favorit" else "♡  Tambahkan ke favorit"
                renderGrid()
            }
        }
        layout.addView(favoriteButton, LinearLayout.LayoutParams(-1, dp(48)))
        AlertDialog.Builder(this).setTitle(item.title).setView(layout)
            .setNeutralButton("Bagikan", null)
            .setNegativeButton("Tutup", null)
            .setPositiveButton("Terapkan", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        applyItem(item)
                        dialog.dismiss()
                    }
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                        shareItem(item)
                    }
                }
                dialog.show()
                // Use the smaller preview asset here; the full UHD file is only decoded when applying.
                loadThumbnail(item.thumbnail) { bitmap ->
                    if (bitmap != null && preview.isAttachedToWindow && !isFinishing && !isDestroyed) preview.setImageBitmap(bitmap)
                }
            }
    }

    private fun shareItem(item: StockItem) {
        Toast.makeText(this, "Menyiapkan gambar untuk dibagikan…", Toast.LENGTH_SHORT).show()
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
            if (!isFinishing && !isDestroyed) runOnUiThread {
                if (isFinishing || isDestroyed) {
                    bitmap?.recycle()
                    return@runOnUiThread
                }
                if (bitmap == null) {
                    Toast.makeText(this, "Gambar gagal dimuat. Periksa koneksi internet.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                try {
                    val shareDir = File(cacheDir, "shared_wallpapers").apply { mkdirs() }
                    val safeName = item.title.replace(Regex("[^A-Za-z0-9_-]"), "_")
                    val imageFile = File(shareDir, safeName + ".jpg")
                    FileOutputStream(imageFile).use { output ->
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw IllegalStateException("Gagal menyiapkan JPEG")
                    }
                    val imageUri = FileProvider.getUriForFile(this, "com.civamel.ultrahdwallpaper.fileprovider", imageFile)
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(android.content.Intent.EXTRA_STREAM, imageUri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, item.title)
                        putExtra(android.content.Intent.EXTRA_TEXT, item.title + " — Ultra HD Wallpaper")
                        clipData = android.content.ClipData.newUri(contentResolver, item.title, imageUri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(android.content.Intent.createChooser(sendIntent, "Bagikan wallpaper"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membagikan gambar: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            } else {
                bitmap?.recycle()
            }
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
            if (!isFinishing && !isDestroyed) runOnUiThread {
                if (isFinishing || isDestroyed) {
                    bitmap?.recycle()
                    return@runOnUiThread
                }
                if (bitmap == null) {
                    Toast.makeText(this, "Gambar gagal dimuat. Periksa koneksi internet.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                showApplyDestinationDialog(item, bitmap)
            } else {
                bitmap?.recycle()
            }
        }
    }

    private fun showApplyDestinationDialog(item: StockItem, bitmap: Bitmap) {
        val destinations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            arrayOf("Layar utama", "Layar kunci", "Keduanya")
        } else {
            arrayOf("Layar utama")
        }
        AlertDialog.Builder(this)
            .setTitle("Terapkan wallpaper")
            .setItems(destinations) { _, which ->
                try {
                    val manager = WallpaperManager.getInstance(this)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val flags = when (which) {
                            1 -> WallpaperManager.FLAG_LOCK
                            2 -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                            else -> WallpaperManager.FLAG_SYSTEM
                        }
                        manager.setBitmap(bitmap, null, true, flags)
                    } else {
                        manager.setBitmap(bitmap)
                    }
                    Toast.makeText(this, "Wallpaper " + item.title + " diterapkan ke " + destinations[which], Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal menerapkan wallpaper: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            .setNegativeButton("Batal") { _, _ ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            .setOnCancelListener {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            .show()
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
