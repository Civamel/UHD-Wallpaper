package com.civamel.ultrahdwallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.MessageDigest
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Typeface
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class MainActivity : Activity() {
    // Add the separately created production AdMob IDs after the account owner provides them.
    private val ADMOB_APP_ID = "ca-app-pub-8497758897094065~8774727457"
    private val ADMOB_BANNER_ID = "ca-app-pub-8497758897094065/3522400771"
    private data class ParsedCatalog(
        val names: MutableList<String>,
        val ids: MutableList<String>,
        val categories: MutableList<String>,
        val types: MutableList<String>,
        val tags: MutableList<List<String>>,
        val thumbnails: MutableList<String>,
        val images: MutableList<String>
    )
    private val blue = Color.rgb(224, 17, 95)
    private val purple = Color.rgb(158, 67, 238)
    private val ink = Color.rgb(28, 28, 30)
    private val tabs = listOf("Home", "Live", "Parallax", "Wallpaper", "Double", "Charging Live")
    private val names = mutableListOf("Neon Flow", "Color Splash", "Red Horizon", "White Wings", "Ocean Glass", "Dark Bubbles",
        "Tropical Beach", "Blue Eyes", "Liquid Planet", "Golden Sunset", "Green Forest", "Stone Texture",
        "Lightning", "Fire Glow", "Smoke Art", "Crystal Lights", "Neon Apple", "Mountain Lake")
    private val palettes = listOf(
        intArrayOf(0xff09090f.toInt(),0xffb20d34.toInt()), intArrayOf(0xfff5d8b4.toInt(),0xff00a9c7.toInt()),
        intArrayOf(0xff260000.toInt(),0xffff3428.toInt()), intArrayOf(0xff080808.toInt(),0xffe8e8e8.toInt()),
        intArrayOf(0xff00334b.toInt(),0xff80d8e9.toInt()), intArrayOf(0xff11131a.toInt(),0xff087bb5.toInt()),
        intArrayOf(0xff005b9b.toInt(),0xff53d8ef.toInt()), intArrayOf(0xff00152d.toInt(),0xff00b6df.toInt()),
        intArrayOf(0xff40104f.toInt(),0xffff8c26.toInt()), intArrayOf(0xffed8d24.toInt(),0xff1a9c36.toInt()),
        intArrayOf(0xff062f25.toInt(),0xff9bcf80.toInt()), intArrayOf(0xff20232a.toInt(),0xff656a73.toInt())
    )
    private val itemIds = mutableListOf<String>()
    private val legacyFavoriteTitles = mutableSetOf<String>()
    private val thumbnailUrls = mutableListOf<String>()
    private val fullUrls = mutableListOf<String>()
    private val imageExecutor = Executors.newFixedThreadPool(4)
    private val pendingWallpaperFileLock = Any()
    private val pendingWallpaperFileCallbacks = mutableMapOf<String, MutableList<(File?) -> Unit>>()
    private val previewCallbackLock = Any()
    private val pendingPreviewCallbacks = mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>()
    private val imageCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private var selectedTab = "Wallpaper"
    private var promoVisible = true
    private val favorites = mutableSetOf<String>()
    private var sortMode = "Paling populer"
    private val preferences by lazy { getSharedPreferences("wallpaper_preferences", MODE_PRIVATE) }
    private var searchQuery = ""
    private var selectedCategory = "All"
    private var showOnlyFavorites = false
    private var visibleItemLimit = 24
    private val slideHandler = Handler(Looper.getMainLooper())
    private var categorySlideRunnable: Runnable? = null
    private var downloadSortMode = "Nama A-Z"
    private var downloadsSearchQuery = ""
    private var downloadSelectionMode = false
    private val selectedDownloadRecords = mutableSetOf<String>()
    private var darkMode = false
    private val categoryNames: List<String>
        get() = listOf("All") + itemCategories.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    private val itemCategories = mutableListOf("Abstract","Art","Abstract","Animals","Ocean","Abstract","Nature","Animals","Space","Nature","Nature","Art","Technology","Art","Abstract","Space","Technology","Nature")
    private val itemTypes = mutableListOf<String>()
    private val itemTags = mutableListOf<List<String>>()
    private var remoteCatalogIndex: CatalogManifest.Index? = null
    private var loadedShardCount = 0
    private var shardLoading = false
    private var adsInitialized = false
    private var adsInitializationStarted = false
    private var bannerAdRequested = false
    private var bannerAdView: com.google.android.libraries.ads.mobile.sdk.banner.AdView? = null
    private var adminSelectedImage: Uri? = null
    private var adminImageLabel: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= 23) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        favorites.addAll(preferences.getStringSet("favorite_ids", emptySet()) ?: emptySet())
        legacyFavoriteTitles.addAll(preferences.getStringSet("favorite_titles", emptySet()) ?: emptySet())
        darkMode = preferences.getBoolean("dark_mode", false)
        sortMode = preferences.getString("wallpaper_sort_mode", "Paling populer") ?: "Paling populer"
        downloadSortMode = preferences.getString("download_sort_mode", "Nama A-Z") ?: "Nama A-Z"
        selectedTab = preferences.getString("selected_wallpaper_tab", "Wallpaper") ?: "Wallpaper"
        if (selectedTab !in tabs && selectedTab != "Home") selectedTab = "Wallpaper"
        selectedCategory = preferences.getString("selected_wallpaper_category", "All") ?: "All"
        buildHome()
        initializeAds()
        loadBundledCatalog()
        loadCachedRemoteCatalog()
        loadCachedShardedCatalog()
        refreshRemoteCatalog()
        loadFirestoreWallpapers()
    }

    override fun onResume() {
        super.onResume()
        refreshFavoritesFromPreferences()
        if (showOnlyFavorites) buildHome()
    }

    private fun refreshFavoritesFromPreferences() {
        favorites.clear()
        favorites.addAll(preferences.getStringSet("favorite_ids", emptySet()) ?: emptySet())
        legacyFavoriteTitles.clear()
        legacyFavoriteTitles.addAll(preferences.getStringSet("favorite_titles", emptySet()) ?: emptySet())
        migrateLegacyFavorites()
    }

    private fun stableWallpaperId(item: org.json.JSONObject): String {
        val explicit = item.optString("id").trim()
        if (explicit.isNotEmpty()) return explicit
        val source = item.optString("image").ifBlank { item.optString("title") }
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return "wp-" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun migrateLegacyFavorites() {
        if (legacyFavoriteTitles.isEmpty() || itemIds.isEmpty()) return
        names.forEachIndexed { index, title ->
            if (title in legacyFavoriteTitles) itemIds.getOrNull(index)?.let { favorites.add(it) }
        }
        legacyFavoriteTitles.clear()
        preferences.edit().putStringSet("favorite_ids", favorites.toSet()).putStringSet("favorite_titles", emptySet()).apply()
    }

    private fun loadBundledCatalog() {
        try {
            val body = assets.open("wallpapers.json").bufferedReader().use { it.readText() }
            val items = CatalogValidator.parse(body)
            val newNames = mutableListOf<String>()
            val newIds = mutableListOf<String>()
            val newCategories = mutableListOf<String>()
            val newTypes = mutableListOf<String>()
            val newTags = mutableListOf<List<String>>()
            val newThumbs = mutableListOf<String>()
            val newFull = mutableListOf<String>()
            for (i in items.indices) {
                val item = items[i]
                newNames.add(item.getString("title"))
                newIds.add(stableWallpaperId(item))
                newCategories.add(item.optString("category", "Abstract").ifBlank { "Abstract" })
                newTypes.add(item.optString("type", "wallpaper").lowercase())
                val tagsArray = item.optJSONArray("tags")
                newTags.add((0 until (tagsArray?.length() ?: 0)).map { tagsArray!!.optString(it).trim() }.filter { it.isNotEmpty() })
                newThumbs.add(item.getString("thumbnail"))
                newFull.add(item.getString("image"))
            }
            if (newNames.isNotEmpty()) {
                names.clear(); names.addAll(newNames)
                itemIds.clear(); itemIds.addAll(newIds)
                migrateLegacyFavorites()
                itemCategories.clear(); itemCategories.addAll(newCategories)
                if (selectedCategory !in categoryNames) selectedCategory = "All"
                itemTypes.clear(); itemTypes.addAll(newTypes)
                itemTags.clear(); itemTags.addAll(newTags)
                thumbnailUrls.clear(); thumbnailUrls.addAll(newThumbs)
                fullUrls.clear(); fullUrls.addAll(newFull)
                buildHome()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Katalog bawaan gagal dibaca", Toast.LENGTH_LONG).show()
        }
    }

    private fun wallpaperDiskCacheFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val key = digest.joinToString("") { byte -> "%02x".format(byte) }
        val directory = File(filesDir, "wallpaper_images").apply { if (!exists()) mkdirs() }
        return File(directory, key + ".img")
    }

    private fun trimWallpaperDiskCache(maxBytes: Long = 180L * 1024L * 1024L) {
        try {
            val directory = File(filesDir, "wallpaper_images")
            val files = directory.listFiles()?.filter { it.isFile && it.extension == "img" } ?: return
            var total = files.sumOf { it.length() }
            if (total <= maxBytes) return
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= maxBytes) break
                val size = file.length()
                if (file.delete()) total -= size
            }
        } catch (_: Exception) {
            // Cache cleanup is best-effort; it must never interrupt wallpaper loading.
        }
    }


    private fun isValidWallpaperFile(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L || file.length() > 40L * 1024L * 1024L) return false
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun loadWallpaperFile(url: String, onLoaded: (File?) -> Unit) {
        val shouldStart = synchronized(pendingWallpaperFileLock) {
            val existing = pendingWallpaperFileCallbacks[url]
            if (existing != null) {
                existing.add(onLoaded)
                false
            } else {
                pendingWallpaperFileCallbacks[url] = mutableListOf(onLoaded)
                true
            }
        }
        if (!shouldStart) return

        imageExecutor.execute {
            var connection: HttpURLConnection? = null
            var result: File? = null
            try {
                val target = wallpaperDiskCacheFile(url)
                if (isValidWallpaperFile(target)) {
                    target.setLastModified(System.currentTimeMillis())
                    result = target
                } else {
                    target.delete()
                    connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 25000
                        doInput = true
                        instanceFollowRedirects = true
                        requestMethod = "GET"
                    }
                    if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
                    val contentLength = connection.contentLengthLong
                    val maxBytes = 40L * 1024L * 1024L
                    if (contentLength > maxBytes) throw IllegalStateException("Ukuran gambar melebihi batas")
                    val temporary = File(target.parentFile, target.name + "." + Thread.currentThread().id + ".download")
                    try {
                        connection.inputStream.use { input ->
                            FileOutputStream(temporary).use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > maxBytes) throw IllegalStateException("Ukuran gambar melebihi batas")
                                    output.write(buffer, 0, count)
                                }
                                output.fd.sync()
                            }
                        }
                        if (!isValidWallpaperFile(temporary)) throw IllegalStateException("File gambar tidak valid")
                        if (!temporary.renameTo(target)) {
                            temporary.copyTo(target, overwrite = true)
                            temporary.delete()
                        }
                        if (isValidWallpaperFile(target)) {
                            target.setLastModified(System.currentTimeMillis())
                            trimWallpaperDiskCache()
                            result = target
                        } else {
                            target.delete()
                        }
                    } finally {
                        if (temporary.exists()) temporary.delete()
                    }
                }
            } catch (_: Exception) {
                result = null
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                val callbacks = synchronized(pendingWallpaperFileLock) {
                    pendingWallpaperFileCallbacks.remove(url)?.toList() ?: emptyList()
                }
                if (!isFinishing && !isDestroyed) {
                    callbacks.forEach { callback ->
                        try { callback(result) } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    private fun loadCachedRemoteCatalog() {
        try {
            val cacheFile = File(filesDir, "catalog_cache.json")
            if (!cacheFile.isFile || cacheFile.length() <= 0L) return
            val items = CatalogValidator.parse(cacheFile.readText())
            val titles = mutableListOf<String>()
            val ids = mutableListOf<String>()
            val categories = mutableListOf<String>()
            val types = mutableListOf<String>()
            val tags = mutableListOf<List<String>>()
            val thumbs = mutableListOf<String>()
            val images = mutableListOf<String>()
            for (i in items.indices) {
                val item = items[i]
                val title = item.optString("title").trim()
                val thumbnail = item.optString("thumbnail").trim()
                val image = item.optString("image").trim()
                if (title.isEmpty() || thumbnail.isEmpty() || image.isEmpty()) continue
                if (!thumbnail.startsWith("https://") || !image.startsWith("https://")) continue
                titles.add(title)
                ids.add(stableWallpaperId(item))
                categories.add(item.optString("category", "Abstract").ifBlank { "Abstract" })
                types.add(item.optString("type", "wallpaper").lowercase())
                val tagsArray = item.optJSONArray("tags")
                tags.add((0 until (tagsArray?.length() ?: 0)).map { tagsArray!!.optString(it).trim() }.filter { it.isNotEmpty() })
                thumbs.add(thumbnail)
                images.add(image)
            }
            if (titles.isEmpty()) return
            names.clear(); names.addAll(titles)
            itemIds.clear(); itemIds.addAll(ids)
            migrateLegacyFavorites()
            itemCategories.clear(); itemCategories.addAll(categories)
            if (selectedCategory !in categoryNames) selectedCategory = "All"
            itemTypes.clear(); itemTypes.addAll(types)
            itemTags.clear(); itemTags.addAll(tags)
            thumbnailUrls.clear(); thumbnailUrls.addAll(thumbs)
            fullUrls.clear(); fullUrls.addAll(images)
            imageCache.evictAll()
            buildHome()
        } catch (_: Exception) {
            // Keep the bundled catalog if the saved online catalog is invalid.
        }
    }

    private fun decodeWallpaperPreview(file: File): Bitmap? {
        if (!file.isFile || file.length() <= 0L || file.length() > 40L * 1024L * 1024L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val maxEdge = 2048
            var sample = 1
            while (maxOf(bounds.outWidth / (sample * 2), bounds.outHeight / (sample * 2)) >= maxEdge) {
                sample *= 2
            }
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
                inDither = true
            })
        } catch (_: Exception) {
            null
        }
    }

    private fun loadImage(url: String, onLoaded: (Bitmap?) -> Unit) {
        val cached = imageCache.get(url)
        if (cached != null && !cached.isRecycled) {
            onLoaded(cached)
            return
        }

        val shouldStart = synchronized(previewCallbackLock) {
            val waiting = pendingPreviewCallbacks[url]
            if (waiting != null) {
                waiting.add(onLoaded)
                false
            } else {
                pendingPreviewCallbacks[url] = mutableListOf(onLoaded)
                true
            }
        }
        if (!shouldStart) return

        try {
            imageExecutor.execute {
                var connection: HttpURLConnection? = null
                var bitmap: Bitmap? = null
                try {
                    val cacheFile = wallpaperDiskCacheFile(url)
                    if (cacheFile.isFile) {
                        bitmap = decodeWallpaperPreview(cacheFile)
                        if (bitmap != null) cacheFile.setLastModified(System.currentTimeMillis())
                        else cacheFile.delete()
                    }
                    if (bitmap == null) {
                        connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 20000
                            doInput = true
                            instanceFollowRedirects = true
                            requestMethod = "GET"
                        }
                        if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
                        val maxBytes = 40L * 1024L * 1024L
                        if (connection.contentLengthLong > maxBytes) throw IllegalStateException("Ukuran gambar melebihi batas")
                        val temporaryFile = File(cacheFile.parentFile, cacheFile.name + "." + Thread.currentThread().id + ".tmp")
                        try {
                            connection.inputStream.use { input ->
                                FileOutputStream(temporaryFile).use { output ->
                                    val buffer = ByteArray(32 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > maxBytes) throw IllegalStateException("Ukuran gambar melebihi batas")
                                        output.write(buffer, 0, count)
                                    }
                                    output.fd.sync()
                                }
                            }
                            bitmap = decodeWallpaperPreview(temporaryFile)
                            if (bitmap != null) {
                                if (!temporaryFile.renameTo(cacheFile)) {
                                    temporaryFile.copyTo(cacheFile, overwrite = true)
                                    temporaryFile.delete()
                                }
                                if (cacheFile.isFile) {
                                    cacheFile.setLastModified(System.currentTimeMillis())
                                    trimWallpaperDiskCache()
                                }
                            } else {
                                cacheFile.delete()
                            }
                        } finally {
                            if (temporaryFile.exists()) temporaryFile.delete()
                        }
                    }
                } catch (_: Exception) {
                    bitmap = null
                } finally {
                    connection?.disconnect()
                    val cacheFile = wallpaperDiskCacheFile(url)
                    val temporaryFile = File(cacheFile.parentFile, cacheFile.name + "." + Thread.currentThread().id + ".tmp")
                    if (temporaryFile.exists()) temporaryFile.delete()
                }

                if (bitmap != null) imageCache.put(url, bitmap)
                val callbacks = synchronized(previewCallbackLock) {
                    pendingPreviewCallbacks.remove(url)?.toList().orEmpty()
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        callbacks.forEach { callback ->
                            try { callback(bitmap) } catch (_: Exception) { }
                        }
                    } else {
                        bitmap?.recycle()
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            val callbacks = synchronized(previewCallbackLock) {
                pendingPreviewCallbacks.remove(url)?.toList().orEmpty()
            }
            callbacks.forEach { callback ->
                try { callback(null) } catch (_: Exception) { }
            }
        }
    }

    private fun initializeAds() {
        if (ADMOB_APP_ID.isBlank() || ADMOB_BANNER_ID.isBlank()) return
        if (adsInitializationStarted) return
        adsInitializationStarted = true
        Thread {
            try {
                com.google.android.libraries.ads.mobile.sdk.MobileAds.initialize(
                    this,
                    com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig.Builder(ADMOB_APP_ID).build()
                ) {
                    runOnUiThread {
                        adsInitialized = true
                        bannerAdView?.let { loadBannerAd(it) }
                    }
                }
            } catch (error: Exception) {
                android.util.Log.w("AdMob", "SDK initialization failed", error)
            }
        }.start()
    }

    private fun loadBannerAd(adView: com.google.android.libraries.ads.mobile.sdk.banner.AdView) {
        if (!adsInitialized || bannerAdRequested) return
        val widthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        if (widthDp <= 0) return
        bannerAdRequested = true
        val adSize = com.google.android.libraries.ads.mobile.sdk.banner.AdSize.getLargeAnchoredAdaptiveBannerAdSize(this, widthDp)
        val request = com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest.Builder(ADMOB_BANNER_ID, adSize).build()
        adView.loadAd(
            request,
            object : com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback<com.google.android.libraries.ads.mobile.sdk.banner.BannerAd> {
                override fun onAdLoaded(ad: com.google.android.libraries.ads.mobile.sdk.banner.BannerAd) {
                    android.util.Log.d("AdMob", "Production banner loaded")
                }

                override fun onAdFailedToLoad(adError: com.google.android.libraries.ads.mobile.sdk.common.LoadAdError) {
                    bannerAdRequested = false
                    android.util.Log.w("AdMob", "Production banner failed: $adError")
                }
            }
        )
    }

    private fun releaseBannerAd() {
        bannerAdView?.let { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.destroy()
        }
        bannerAdView = null
    }

    override fun onDestroy() {
        categorySlideRunnable?.let { slideHandler.removeCallbacks(it) }
        categorySlideRunnable = null
        releaseBannerAd()
        super.onDestroy()
    }

    private fun buildHome() {
        val pageBg = if (darkMode) 0xff121318.toInt() else Color.WHITE
        val pageText = if (darkMode) 0xfff2f2f5.toInt() else ink
        val panelSurface = if (darkMode) 0xff77736e.toInt() else 0xff050608.toInt()
        val root = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        val header = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(15),dp(6),dp(12),dp(5)); setBackgroundColor(panelSurface) }
        header.addView(text(if (showOnlyFavorites) "Favorit (${favorites.size})" else "4k Ultra HD Wallpaper",25,0xfff4f7ff.toInt(),true).apply { if (!showOnlyFavorites) textSize = 16.25f }, LinearLayout.LayoutParams(0,dp(38),1f))
        listOf("✦","☾","⌕","⚙").forEachIndexed { i,glyph ->
            val icon = modernIcon(glyph, 44)
            icon.contentDescription = listOf("Premium","Tema","Cari","Pengaturan")[i]
            icon.setOnClickListener {
                when(i){0->Toast.makeText(this@MainActivity,"Wallpaper Premium",Toast.LENGTH_SHORT).show();1->toggleTheme();2->search();3->showSettings()}
            }
            header.addView(icon,LinearLayout.LayoutParams(dp(44),dp(40)))
        }
        root.addView(header)
        val tabScroll=HorizontalScrollView(this@MainActivity).apply { isHorizontalScrollBarEnabled=false }
        val tabRow=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; setBackgroundColor(panelSurface) }
        tabs.forEach { tab ->
            val item=LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener {
                    selectedTab = tab
                    preferences.edit().putString("selected_wallpaper_tab", selectedTab).apply()
                    showOnlyFavorites = false
                    visibleItemLimit = 24
                    buildHome()
                }
            }
            item.addView(text(tab,16,if(tab==selectedTab) blue else Color.WHITE,true).apply { gravity=Gravity.CENTER; setPadding(dp(15),dp(12),dp(15),dp(12)) })
            item.addView(View(this@MainActivity).apply { setBackgroundColor(if(tab==selectedTab) blue else Color.TRANSPARENT) },LinearLayout.LayoutParams(-1,dp(3)))
            tabRow.addView(item)
        }
        tabScroll.addView(tabRow); root.addView(tabScroll)
        val scroll=ScrollView(this@MainActivity)
        val content=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        if(promoVisible) {
            val promo=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),0,dp(12),0); background=rounded(0xff733044.toInt(),28) }
            promo.addView(text("♛   Get All Wallpaper Pro!",17,Color.WHITE,true),LinearLayout.LayoutParams(0,dp(36),1f))
            promo.addView(text("×",25,Color.WHITE,false).apply { gravity=Gravity.CENTER; setOnClickListener { promoVisible=false; buildHome() } },LinearLayout.LayoutParams(dp(30),dp(34)))
            val pp=LinearLayout.LayoutParams(-1,dp(38)); pp.setMargins(dp(12),dp(8),dp(12),dp(3)); content.addView(promo,pp)
        }
        val showDiscoverHeader = !showOnlyFavorites && (selectedTab == "Home" || selectedTab == "Wallpaper") && searchQuery.isBlank() && selectedCategory == "All"
        if (showDiscoverHeader && names.isNotEmpty()) {
            categorySlideRunnable?.let { slideHandler.removeCallbacks(it) }
            val categorySlides = categoryNames.filter { it != "All" }.mapNotNull { category ->
                val index = itemCategories.indexOf(category)
                if (index >= 0) category to index else null
            }
            val slideScroll = HorizontalScrollView(this@MainActivity).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                clipToPadding = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val slideRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val slideWidth = resources.displayMetrics.widthPixels
            categorySlides.forEach { pair ->
                val category = pair.first
                val wallpaperIndex = pair.second
                val slide = FrameLayout(this@MainActivity).apply {
                    background = rounded(if (darkMode) 0xff77736e.toInt() else 0xffe9e9ed.toInt(), 14)
                    clipToOutline = true
                    setOnClickListener {
                        selectedCategory = category
                        selectedTab = "Wallpaper"
                        searchQuery = ""
                        showOnlyFavorites = false
                        preferences.edit().putString("selected_wallpaper_category", category).putString("selected_wallpaper_tab", selectedTab).apply()
                        visibleItemLimit = 24
                        buildHome()
                    }
                }
                val image = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(artDrawable(wallpaperIndex))
                }
                slide.addView(image, FrameLayout.LayoutParams(-1, -1))
                slide.addView(View(this@MainActivity).apply {
                    background = android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0x11000000, 0xdd000000.toInt())
                    )
                }, FrameLayout.LayoutParams(-1, dp(68), Gravity.BOTTOM))
                slide.addView(text(category, 21, Color.WHITE, true).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    setPadding(dp(18), 0, dp(16), dp(8))
                    maxLines = 1
                    setShadowLayer(1.2f, 0f, dp(1).toFloat(), 0x99000000.toInt())
                }, FrameLayout.LayoutParams(-1, dp(38), Gravity.BOTTOM))
                slideRow.addView(slide, LinearLayout.LayoutParams(slideWidth, dp(105)))
                thumbnailUrls.getOrNull(wallpaperIndex)?.let { imageUrl ->
                    loadImage(imageUrl) { bitmap ->
                        if (bitmap != null && image.isAttachedToWindow) image.setImageBitmap(bitmap)
                    }
                }
            }
            slideScroll.addView(slideRow)
            content.addView(slideScroll, LinearLayout.LayoutParams(-1, dp(105)))
            if (categorySlides.size > 1) {
                var slidePosition = 0
                val autoAdvance = object : Runnable {
                    override fun run() {
                        if (isFinishing || isDestroyed || !slideScroll.isAttachedToWindow) return
                        slidePosition = (slidePosition + 1) % categorySlides.size
                        slideScroll.smoothScrollTo(slidePosition * slideWidth, 0)
                        slideHandler.postDelayed(this, 4000L)
                    }
                }
                categorySlideRunnable = autoAdvance
                slideHandler.postDelayed(autoAdvance, 4000L)
            }
            val popularPanel = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = rounded(0xff733044.toInt(), 10)
            }
            popularPanel.addView(text("Popular Tags", 11, Color.WHITE, true).apply { setPadding(dp(2), 0, 0, dp(2)) })
            val tagScroll = HorizontalScrollView(this@MainActivity).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
            val tagRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            listOf("Anime", "Cartoon", "Girl", "Nature", "Neon & Fireworks", "Tourism", "Vehicles", "Fantasy", "AI Generated").forEach { tag ->
                tagRow.addView(text(tag, 10, 0xff542332.toInt(), true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(3), dp(10), dp(3))
                    background = rounded(0xffffe8ee.toInt(), 16)
                    setOnClickListener { searchQuery = tag; showOnlyFavorites = false; visibleItemLimit = 24; buildHome() }
                }, LinearLayout.LayoutParams(-2, dp(23)).apply { setMargins(dp(2), 0, dp(5), 0) })
            }
            tagScroll.addView(tagRow)
            popularPanel.addView(tagScroll, LinearLayout.LayoutParams(-1, dp(25)))
            content.addView(popularPanel, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(dp(12), dp(1), dp(12), dp(5)) })
        }
        val sort=TextView(this@MainActivity).apply { text="Filter : "+sortMode+"   ▾"; textSize=13f; setTextColor(0xff50c878.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(18),dp(2),dp(12),dp(6)); setOnClickListener {
            val options = arrayOf("Paling populer", "Terbaru", "A-Z", "Acak")
            AlertDialog.Builder(this@MainActivity).setTitle("Urutkan berdasarkan").setItems(options) { _, w ->
                sortMode = options[w]
                preferences.edit().putString("wallpaper_sort_mode", sortMode).apply()
                visibleItemLimit = 24
                buildHome()
            }.show()
        }}
        content.addView(sort)
        val grid=GridLayout(this@MainActivity).apply { columnCount=3; useDefaultMargins=false }
        val filteredIndexes = names.indices.filter { index ->
            val categoryMatch = selectedCategory == "All" || itemCategories[index] == selectedCategory
            val searchMatch = searchQuery.isBlank() || names[index].contains(searchQuery, true) || itemCategories[index].contains(searchQuery, true) || itemTags.getOrNull(index).orEmpty().any { it.contains(searchQuery, true) }
            val favoriteMatch = !showOnlyFavorites || favorites.contains(itemIds.getOrNull(index) ?: names[index])
            val type = itemTypes.getOrNull(index) ?: "wallpaper"
            val tabMatch = when (selectedTab) {
                "Live" -> type == "live"
                "Parallax" -> type == "parallax"
                "Double" -> type == "double"
                "Charging Live" -> type == "charging"
                "Wallpaper" -> type == "wallpaper"
                else -> true
            }
            categoryMatch && searchMatch && favoriteMatch && tabMatch
        }
        val visibleIndexes = when (sortMode) {
            "Terbaru" -> filteredIndexes.reversed()
            "A-Z" -> filteredIndexes.sortedBy { names[it].lowercase() }
            "Acak" -> filteredIndexes.shuffled()
            else -> filteredIndexes
        }
        if (visibleIndexes.isEmpty()) {
            val emptyTitle = if (selectedTab == "Wallpaper" || selectedTab == "Home") "Belum ada wallpaper" else "Konten belum tersedia"
            val emptyMessage = if (selectedTab == "Wallpaper" || selectedTab == "Home") "Coba ubah kategori atau kata pencarian." else "Koleksi untuk tab ini akan muncul setelah katalog menyediakan jenis kontennya."
            val emptyState = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(54), dp(28), dp(54))
                addView(text(emptyTitle, 18, pageText, true).apply { gravity = Gravity.CENTER })
                addView(text(emptyMessage, 13, if (darkMode) 0xffaaaaaa.toInt() else Color.GRAY, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
            }
            content.addView(emptyState, LinearLayout.LayoutParams(-1, -2))
        }
        visibleIndexes.take(visibleItemLimit).forEach { index ->
            val name = names[index]
            val cell=FrameLayout(this@MainActivity)
            val art=ImageView(this@MainActivity).apply {
                scaleType=ImageView.ScaleType.CENTER_CROP
                setImageDrawable(artDrawable(index))
                setOnClickListener { showPreview(index) }
            }
            cell.addView(art,FrameLayout.LayoutParams(-1,dp(148)))
            cell.addView(text(if ((itemIds.getOrNull(index) ?: names[index]) in favorites) "♥" else "♡", 21, 0xffe0115f.toInt(), true).apply {
                gravity = Gravity.CENTER
                background = rounded(0xcc000000.toInt(), 18)
                setOnClickListener {
                    val favoriteId = itemIds.getOrNull(index) ?: names[index]
                    if (!favorites.add(favoriteId)) favorites.remove(favoriteId)
                    preferences.edit().putStringSet("favorite_ids", favorites.toSet()).putStringSet("favorite_titles", emptySet()).apply()
                    buildHome()
                }
            }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.START).apply {
                setMargins(dp(7), dp(7), 0, 0)
            })
            if (index < thumbnailUrls.size) loadImage(thumbnailUrls[index]) { bitmap -> if (bitmap != null && art.isAttachedToWindow) art.setImageBitmap(bitmap) }
            if(index%5==1) cell.addView(text("♛",20,Color.WHITE,true).apply { gravity=Gravity.TOP or Gravity.END; setPadding(dp(5),dp(4),dp(8),0) })
            val params=GridLayout.LayoutParams().apply { width=0; height=dp(148); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(1),dp(1),dp(1),dp(1)) }
            grid.addView(cell,params)
        }
        content.addView(grid)
        if (visibleIndexes.size > visibleItemLimit || hasMoreCatalogShards()) {
            val hasVisibleRemainder = visibleIndexes.size > visibleItemLimit
            val remaining = (visibleIndexes.size - visibleItemLimit).coerceAtLeast(0)
            val moreButton = Button(this@MainActivity).apply {
                text = when {
                    shardLoading -> "Memuat halaman katalog…"
                    hasVisibleRemainder -> "Muat lebih banyak ($remaining tersisa)"
                    else -> "Berikutnya"
                }
                isAllCaps = false
                isEnabled = !shardLoading
                setTextColor(Color.WHITE)
                background = rounded(0xff733044.toInt(), 28)
                setOnClickListener {
                    if (visibleIndexes.size > visibleItemLimit) {
                        visibleItemLimit += 24
                        buildHome()
                    } else {
                        loadNextCatalogShard()
                    }
                }
            }
            content.addView(moreButton, LinearLayout.LayoutParams(-1, dp(35)).apply {
                setMargins(dp(18), dp(14), dp(18), dp(18))
            })
        }
        scroll.addView(content)
        val swipeRefresh = SwipeRefreshLayout(this@MainActivity).apply {
            setColorSchemeColors(0xff8f3048.toInt(), 0xffe0115f.toInt())
            setOnRefreshListener {
                loadFirestoreWallpapers { isRefreshing = false }
            }
        }
        swipeRefresh.addView(scroll, SwipeRefreshLayout.LayoutParams(-1, -1))
        root.addView(swipeRefresh, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(0,dp(4),0,dp(3)); setBackgroundColor(panelSurface) }
        listOf("⌂" to "Wallpaper","▦" to "Category","✦" to "Quotes","▧" to "Stock").forEachIndexed { i,item ->
            val navItem = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener {
                    when(i) {
                        0 -> {
                            showOnlyFavorites = false
                            selectedCategory = "All"
                            selectedTab = "Wallpaper"
                            preferences.edit()
                                .putString("selected_wallpaper_category", selectedCategory)
                                .putString("selected_wallpaper_tab", selectedTab)
                                .apply()
                            visibleItemLimit = 24
                            buildHome()
                        }
                        1 -> showCategories()
                        2 -> showQuotes()
                        3 -> startActivity(Intent(this@MainActivity, StockActivity::class.java))
                    }
                }
            }
            navItem.addView(modernIcon(item.first, 34), LinearLayout.LayoutParams(dp(42),dp(32)))
            navItem.addView(text(item.second,11,if(i==0) blue else Color.WHITE,true).apply { gravity=Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0,dp(2),0,0) })
            bottom.addView(navItem,LinearLayout.LayoutParams(0,dp(48),1f))
        }
        root.addView(bottom)
        val adContainer = FrameLayout(this@MainActivity).apply {
            setBackgroundColor(if (darkMode) 0xff202127.toInt() else Color.rgb(245, 245, 245))
        }
        if (bannerAdView == null) {
            bannerAdView = com.google.android.libraries.ads.mobile.sdk.banner.AdView(this@MainActivity)
        }
        (bannerAdView?.parent as? android.view.ViewGroup)?.removeView(bannerAdView)
        adContainer.addView(bannerAdView, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        root.addView(adContainer, LinearLayout.LayoutParams(-1, -2))
        if (adsInitialized) bannerAdView?.let { loadBannerAd(it) }
        setContentView(root)
    }

    private fun showPreview(index:Int) {
        recordRecentlyViewed(index)
        val root=FrameLayout(this@MainActivity).apply { setBackgroundColor(Color.BLACK) }
        val art=ImageView(this@MainActivity).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageDrawable(artDrawable(index)) }
        root.addView(art,FrameLayout.LayoutParams(-1,-1))
        if (index < fullUrls.size) loadImage(fullUrls[index]) { bitmap -> if (bitmap != null && art.isAttachedToWindow) art.setImageBitmap(bitmap) }
        val top=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(18),dp(18),dp(18),0) }
        top.addView(circle("‹"){buildHome()})
        top.addView(View(this@MainActivity),LinearLayout.LayoutParams(0,1,1f))
        top.addView(circle("💎"){Toast.makeText(this@MainActivity,"Premium",Toast.LENGTH_SHORT).show()})
        top.addView(circle("ⓘ"){ AlertDialog.Builder(this@MainActivity).setTitle(names[index]).setMessage("Kategori: "+itemCategories.getOrNull(index).orEmpty()+"\nJenis: "+(itemTypes.getOrNull(index) ?: "wallpaper")+"\n\nWallpaper Ultra HD. Pilih Terapkan untuk memasang wallpaper atau Unduh untuk menyimpan gambar.").setPositiveButton("Mengerti",null).show() })
        top.addView(circle("↗"){shareWallpaper(index)})
        root.addView(top,FrameLayout.LayoutParams(-1,dp(76),Gravity.TOP))
        val bottom=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(20)) }
        val details=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL }
        details.addView(text(names[index],15,Color.WHITE,true))
        details.addView(text(itemCategories.getOrNull(index).orEmpty(),12,0xffcccccc.toInt(),false))
        bottom.addView(details,LinearLayout.LayoutParams(0,dp(54),1f))
        bottom.addView(circle(if((itemIds.getOrNull(index) ?: names[index]) in favorites) "♥" else "♡"){ val favoriteId = itemIds.getOrNull(index) ?: names[index]; if(!favorites.add(favoriteId)) favorites.remove(favoriteId); preferences.edit().putStringSet("favorite_ids", favorites.toSet()).putStringSet("favorite_titles", emptySet()).apply(); showPreview(index) })
        bottom.addView(View(this@MainActivity),LinearLayout.LayoutParams(0,1,1f))
        val apply=Button(this@MainActivity).apply { text="Terapkan"; isAllCaps=false; textSize=16f; setTextColor(Color.WHITE); background=rounded(blue,32); setOnClickListener { if(itemTypes.getOrNull(index) == "live") applyLiveWallpaper(index) else applyWallpaper(index) } }
        bottom.addView(apply,LinearLayout.LayoutParams(dp(150),dp(54)))
        bottom.addView(View(this@MainActivity),LinearLayout.LayoutParams(dp(20),1))
        bottom.addView(circle("⇩"){ requestDownload(index) })
        root.addView(bottom,FrameLayout.LayoutParams(-1,dp(90),Gravity.BOTTOM))
        val ad=text("ADVERTISEMENT  •  Ultra HD Wallpaper",10,Color.WHITE,false).apply { gravity=Gravity.CENTER; setBackgroundColor(0xff292929.toInt()) }
        root.addView(ad,FrameLayout.LayoutParams(-1,dp(42),Gravity.BOTTOM).apply { bottomMargin=dp(90) })
        setContentView(root)
    }

    private fun applyWallpaper(index: Int) {
        val targets = if (Build.VERSION.SDK_INT >= 24) arrayOf("Layar utama", "Layar kunci", "Keduanya") else arrayOf("Layar utama")
        AlertDialog.Builder(this@MainActivity).setTitle("Terapkan wallpaper").setItems(targets) { _, which ->
            val flags = when (which) {
                1 -> WallpaperManager.FLAG_LOCK
                2 -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM
            }
            if (index < fullUrls.size) {
                Toast.makeText(this@MainActivity, "Menyiapkan file UHD…", Toast.LENGTH_SHORT).show()
                loadWallpaperFile(fullUrls[index]) { file ->
                    if (file == null) {
                        Toast.makeText(this@MainActivity, "Gambar gagal dimuat atau file tidak valid.", Toast.LENGTH_LONG).show()
                        return@loadWallpaperFile
                    }
                    Thread {
                        try {
                            FileInputStream(file).use { input ->
                                val manager = WallpaperManager.getInstance(this@MainActivity)
                                if (Build.VERSION.SDK_INT >= 24) manager.setStream(input, null, true, flags)
                                else manager.setStream(input)
                            }
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) Toast.makeText(this@MainActivity, "Wallpaper berhasil diterapkan", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) Toast.makeText(this@MainActivity, "Gagal: " + e.localizedMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }.start()
                }
            } else {
                val colors = palettes[index % palettes.size]
                val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(0f, 0f, 1080f, 1920f, colors[0], colors[1], Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, 1080f, 1920f, paint)
                Thread {
                    try {
                        val manager = WallpaperManager.getInstance(this@MainActivity)
                        if (Build.VERSION.SDK_INT >= 24) manager.setBitmap(bitmap, null, true, flags) else manager.setBitmap(bitmap)
                        bitmap.recycle()
                        runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(this@MainActivity, "Wallpaper berhasil diterapkan", Toast.LENGTH_LONG).show() }
                    } catch (e: Exception) {
                        if (!bitmap.isRecycled) bitmap.recycle()
                        runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(this@MainActivity, "Gagal: " + e.localizedMessage, Toast.LENGTH_LONG).show() }
                    }
                }.start()
            }
        }.setNegativeButton("Batal", null).show()
    }

    private fun cacheDirectories(): List<Pair<String, File>> = listOf(
        "Wallpaper UHD" to File(filesDir, "wallpaper_images"),
        "Live Wallpaper" to File(filesDir, "live_wallpaper_cache"),
        "Stock Thumbnail" to File(cacheDir, "stock_thumbnails"),
        "Stock UHD" to File(filesDir, "stock_full_images")
    )

    private fun directoryBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directoryBytes(it) } ?: 0L
    }

    private fun formatCacheSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.1f MB", mb)
        else String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    }

    private fun showCacheManager() {
        val directories = cacheDirectories()
        val sizes = directories.map { (label, directory) -> label to directoryBytes(directory) }
        val total = sizes.sumOf { it.second }
        val details = sizes.joinToString("\\n") { (label, bytes) ->
            "$label: " + formatCacheSize(bytes)
        }
        AlertDialog.Builder(this)
            .setTitle("Kelola Cache")
            .setMessage("Rincian cache gambar:\\n$details\\n\\nTotal: " + formatCacheSize(total) +
                "\\n\\nCache berisi salinan sementara untuk mempercepat pemuatan gambar. File pada menu Unduh dan katalog tidak ikut dihapus.")
            .setPositiveButton("Bersihkan cache") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Konfirmasi")
                    .setMessage("Bersihkan seluruh cache gambar sebesar " + formatCacheSize(total) + "? Wallpaper yang sudah disimpan melalui menu Unduh tetap aman.")
                    .setPositiveButton("Ya, bersihkan") { _, _ ->
                        var removed = 0L
                        directories.forEach { (_, directory) ->
                            if (directory.exists()) {
                                removed += directoryBytes(directory)
                                directory.deleteRecursively()
                            }
                        }
                        imageCache.evictAll()
                        Toast.makeText(this, "Cache dibersihkan: " + formatCacheSize(removed), Toast.LENGTH_LONG).show()
                        showCacheManager()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showSettings() {
        val settingsBg = if (darkMode) 0xff121318.toInt() else Color.WHITE
        val settingsText = if (darkMode) 0xfff2f2f5.toInt() else ink
        val root=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(settingsBg); setPadding(dp(22),dp(22),dp(22),dp(12)) }
        root.addView(text("‹   Pengaturan",22,settingsText,true).apply { setOnClickListener { buildHome() } })
        root.addView(space(28)); root.addView(text("Pengaturan",24,settingsText,true)); root.addView(space(12))
        listOf(
            "♟" to "Notifikasi","♡" to "Favorit","◷" to "Riwayat dilihat","文" to "Bahasa Aplikasi",
            "▧" to "Pengubah Wallpaper Otomatis","⇩" to "Unduh","▤" to "Wallpaper Penerangan Tepi",
            "↗" to "Bagikan Aplikasi","☆" to "Nilai kami","✉" to "Umpan Balik","▣" to "Kelola Cache"
        ).forEach { (glyph,item) ->
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12),dp(9),dp(12),dp(9))
                background = rounded(if(darkMode) 0xff24262d.toInt() else 0xfff7f8fc.toInt(),16)
                setOnClickListener {
                    when {
                        item.contains("Favorit") -> showFavorites()
                        item.contains("Riwayat") -> showRecentlyViewed()
                        item.contains("Unduh") -> showDownloads()
                        item.contains("Kelola Cache") -> showCacheManager()
                        item.contains("Bagikan Aplikasi") -> shareApplication()
                        item.contains("Umpan Balik") -> sendFeedback()
                        else -> Toast.makeText(this@MainActivity,item,Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row.addView(modernIcon(glyph,34),LinearLayout.LayoutParams(dp(42),dp(42)))
            row.addView(text(item,15,settingsText,false),LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(12) })
            row.addView(text("›",23,if(darkMode) 0xffb9c8e8.toInt() else 0xff6f7f9b.toInt(),false))
            val rowParams = LinearLayout.LayoutParams(-1,-2)
            rowParams.bottomMargin = dp(8)
            root.addView(row,rowParams)
        }
        root.addView(text("Admin",11,if(darkMode) 0xff85858d.toInt() else 0xff999999.toInt(),false).apply { gravity=Gravity.CENTER; setPadding(0,dp(12),0,dp(4)); setOnClickListener { openAdminGate() } })
        setContentView(ScrollView(this@MainActivity).apply { addView(root) })
    }


    private fun openAdminGate() {
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser
        if (current != null) {
            current.getIdToken(true).addOnSuccessListener { token ->
                if (token.claims["admin"] == true) showAdminPanel()
                else {
                    auth.signOut()
                    showAdminLogin()
                }
            }.addOnFailureListener { showAdminLogin() }
        } else showAdminLogin()
    }

    private fun showAdminLogin() {
        val email = EditText(this).apply { hint = "Email admin"; inputType = 33 }
        val password = EditText(this).apply { hint = "Kata sandi"; inputType = 129 }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(email)
            addView(password)
        }
        AlertDialog.Builder(this).setTitle("Admin Login").setView(form)
            .setPositiveButton("Masuk", null).setNegativeButton("Batal", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val e = email.text.toString().trim()
                        val p = password.text.toString()
                        if (e.isBlank() || p.isBlank()) {
                            Toast.makeText(this, "Email dan kata sandi wajib diisi", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        FirebaseAuth.getInstance().signInWithEmailAndPassword(e, p)
                            .addOnSuccessListener {
                                it.user?.getIdToken(true)?.addOnSuccessListener { token ->
                                    if (token.claims["admin"] == true) {
                                        dialog.dismiss()
                                        showAdminPanel()
                                    } else {
                                        FirebaseAuth.getInstance().signOut()
                                        Toast.makeText(this, "Akun ini tidak memiliki akses admin", Toast.LENGTH_LONG).show()
                                    }
                                }?.addOnFailureListener { error ->
                                    FirebaseAuth.getInstance().signOut()
                                    Toast.makeText(this, "Verifikasi admin gagal: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                            .addOnFailureListener { error ->
                                Toast.makeText(this, "Login gagal: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
                dialog.show()
            }
    }

    private fun showAdminPanel() {
        val bg = if (darkMode) 0xff121318.toInt() else Color.WHITE
        val fg = if (darkMode) 0xfff2f2f5.toInt() else ink
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(bg)
        }
        root.addView(text("‹   Admin Wallpaper", 22, fg, true).apply {
            setPadding(0, 0, 0, dp(18))
            setOnClickListener { showSettings() }
        })
        val title = EditText(this).apply { hint = "Judul wallpaper"; setTextColor(fg) }
        val category = EditText(this).apply { hint = "Kategori, contoh: Nature"; setTextColor(fg) }
        val imageUrl = EditText(this).apply {
            hint = "URL gambar (https://...)"
            setTextColor(fg)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val wallpaperTypeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("wallpaper", "live"))
        }
        val save = Button(this).apply { text = "Simpan wallpaper" }
        root.addView(title)
        root.addView(category)
        root.addView(imageUrl)
        root.addView(wallpaperTypeSpinner)
        root.addView(save)
        root.addView(text("Gunakan URL gambar HTTPS yang dapat dibuka publik. Data wallpaper disimpan di Cloud Firestore.", 12,
            if (darkMode) 0xffa6a6ad.toInt() else Color.GRAY, false).apply { setPadding(0, dp(12), 0, 0) })
        save.setOnClickListener {
            val name = title.text.toString().trim()
            val group = category.text.toString().trim()
            val url = imageUrl.text.toString().trim()
            if (name.isBlank() || group.isBlank() || !url.startsWith("https://", ignoreCase = true)) {
                Toast.makeText(this, "Isi judul, kategori, dan URL HTTPS yang valid", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            save.isEnabled = false
            save.text = "Menyimpan…"
            val id = "wp-" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val data = hashMapOf(
                "id" to id,
                "title" to name,
                "category" to group,
                "thumbnail" to url,
                "image" to url,
                "type" to wallpaperTypeSpinner.selectedItem.toString(),
                "tags" to emptyList<String>(),
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            FirebaseFirestore.getInstance().collection("wallpapers").document(id).set(data)
                .addOnSuccessListener {
                    title.text.clear()
                    category.text.clear()
                    imageUrl.text.clear()
                    Toast.makeText(this, "Wallpaper berhasil disimpan", Toast.LENGTH_LONG).show()
                    save.isEnabled = true
                    save.text = "Simpan wallpaper"
                }
                .addOnFailureListener { error ->
                    save.isEnabled = true
                    save.text = "Simpan wallpaper"
                    Toast.makeText(this, "Data gagal disimpan: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun loadFirestoreWallpapers(onComplete: (() -> Unit)? = null) {
        FirebaseFirestore.getInstance().collection("wallpapers").get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) { onComplete?.invoke(); return@addOnSuccessListener }
                if (snapshot.isEmpty) { onComplete?.invoke(); return@addOnSuccessListener }
                var changed = false
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val title = doc.getString("title")?.trim().orEmpty()
                    val thumb = doc.getString("thumbnail")?.trim().orEmpty()
                    val image = doc.getString("image")?.trim().orEmpty()
                    if (title.isBlank() || !thumb.startsWith("https://") || !image.startsWith("https://") || itemIds.contains(id)) continue
                    names.add(title)
                    itemIds.add(id)
                    itemCategories.add(doc.getString("category")?.ifBlank { "Abstract" } ?: "Abstract")
                    itemTypes.add(doc.getString("type")?.lowercase() ?: "wallpaper")
                    itemTags.add(doc.get("tags") as? List<String> ?: emptyList())
                    thumbnailUrls.add(thumb)
                    fullUrls.add(image)
                    changed = true
                }
                if (changed) {
                    imageCache.evictAll()
                    buildHome()
                }
                onComplete?.invoke()
            }
            .addOnFailureListener {
                onComplete?.invoke()
                Toast.makeText(this, "Gagal memuat ulang wallpaper. Periksa koneksi internet.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun shareApplication() {
        val appLink = "https://play.google.com/store/apps/details?id=" + packageName
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ultra HD Wallpaper")
            putExtra(Intent.EXTRA_TEXT, "Coba aplikasi Ultra HD Wallpaper: " + appLink)
        }
        try {
            startActivity(Intent.createChooser(sendIntent, "Bagikan Ultra HD Wallpaper"))
        } catch (e: Exception) {
            Toast.makeText(this, "Fitur berbagi tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendFeedback() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "Masukan untuk Ultra HD Wallpaper")
            putExtra(Intent.EXTRA_TEXT, "Halo, saya ingin memberikan masukan tentang aplikasi Ultra HD Wallpaper:")
        }
        try {
            startActivity(Intent.createChooser(sendIntent, "Kirim Umpan Balik"))
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi email yang tersedia", Toast.LENGTH_LONG).show()
        }
    }

    private var pendingDownloadFile: File? = null
    private var pendingDownloadTitle: String = ""
    private val createImageRequest = 701

    private fun requestDownload(index: Int) {
        val url = fullUrls.getOrNull(index)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Gambar belum tersedia", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Menyiapkan file UHD asli…", Toast.LENGTH_SHORT).show()
        loadWallpaperFile(url) { imageFile ->
            if (imageFile == null) {
                Toast.makeText(this, "Gagal memuat gambar. Periksa internet.", Toast.LENGTH_LONG).show()
                return@loadWallpaperFile
            }
            pendingDownloadFile = imageFile
            pendingDownloadTitle = names[index]
            val safeName = names[index].replace(Regex("[^A-Za-z0-9_-]"), "_")
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/jpeg"
                putExtra(Intent.EXTRA_TITLE, safeName + ".jpg")
            }
            try {
                startActivityForResult(intent, createImageRequest)
            } catch (_: Exception) {
                pendingDownloadFile = null
                pendingDownloadTitle = ""
                Toast.makeText(this, "Penyimpanan tidak tersedia", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated API retained for minSdk compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7412) {
            if (resultCode == RESULT_OK) {
                adminSelectedImage = data?.data
                adminSelectedImage?.let { uri ->
                    try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                    adminImageLabel?.text = "Gambar berhasil dipilih"
                    Toast.makeText(this, "Gambar dipilih", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        if (requestCode != createImageRequest) return
        val sourceFile = pendingDownloadFile
        val title = pendingDownloadTitle
        pendingDownloadFile = null
        pendingDownloadTitle = ""
        if (resultCode != RESULT_OK || sourceFile == null || !sourceFile.isFile) return
        val uri: Uri = data?.data ?: return
        Thread {
            val saved = try {
                val output = contentResolver.openOutputStream(uri)
                if (output == null) {
                    false
                } else {
                    output.use { stream ->
                        FileInputStream(sourceFile).use { input -> input.copyTo(stream) }
                    }
                    true
                }
            } catch (_: Exception) { false }
            val librarySaved = saved && saveToDownloadsLibrary(sourceFile, title)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val message = when {
                    !saved -> "Gagal menyimpan wallpaper"
                    librarySaved -> "Wallpaper asli tersimpan dan masuk ke Unduhan"
                    else -> "Wallpaper tersimpan, tetapi gagal masuk ke pustaka Unduhan"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun saveToDownloadsLibrary(sourceFile: File, title: String): Boolean {
        return try {
            if (!isValidWallpaperFile(sourceFile)) return false
            val directory = File(filesDir, "downloads").apply { if (!exists()) mkdirs() }
            val fileName = "wallpaper_" + System.currentTimeMillis() + ".jpg"
            val imageFile = File(directory, fileName)
            sourceFile.copyTo(imageFile, overwrite = true)
            if (!isValidWallpaperFile(imageFile)) {
                imageFile.delete()
                return false
            }
            val records = (preferences.getStringSet("download_records", emptySet()) ?: emptySet()).toMutableSet()
            records.add(fileName + "\t" + title)
            preferences.edit().putStringSet("download_records", records).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun showDownloads() {
        val pageBg = if (darkMode) 0xff121318.toInt() else 0xfff5f5f7.toInt()
        val pageText = if (darkMode) 0xfff2f2f5.toInt() else ink
        val root = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(pageBg)
        }
        root.addView(text("‹   Unduhan", 23, pageText, true).apply {
            setPadding(0, 0, 0, dp(14))
            setOnClickListener { showSettings() }
        })
        val allRecords = (preferences.getStringSet("download_records", emptySet()) ?: emptySet())
            .toList().filter { record ->
                File(File(filesDir, "downloads"), record.substringBefore("\t")).isFile
            }
        val query = downloadsSearchQuery.trim()
        val records = allRecords.filter { record ->
            query.isEmpty() || record.substringAfter("\t", "Wallpaper").contains(query, ignoreCase = true)
        }.let { saved ->
            when (downloadSortMode) {
                "Nama Z-A" -> saved.sortedByDescending { it.substringAfter("\t", "Wallpaper").lowercase() }
                "Ukuran terbesar" -> saved.sortedByDescending { File(File(filesDir, "downloads"), it.substringBefore("\t")).length() }
                "Terlama" -> saved.sortedBy { File(File(filesDir, "downloads"), it.substringBefore("\t")).lastModified() }
                "Terbaru" -> saved.sortedByDescending { File(File(filesDir, "downloads"), it.substringBefore("\t")).lastModified() }
                else -> saved.sortedBy { it.substringAfter("\t", "Wallpaper").lowercase() }
            }
        }
        val searchRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val searchBox = EditText(this@MainActivity).apply {
            setSingleLine(true)
            hint = "Cari wallpaper yang diunduh..."
            textSize = 14f
            setTextColor(pageText)
            setHintTextColor(if (darkMode) 0xff9296a2.toInt() else 0xff888888.toInt())
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(if (darkMode) 0xff24262d.toInt() else Color.WHITE, 12)
            setText(downloadsSearchQuery)
            setOnEditorActionListener { _, _, _ -> downloadsSearchQuery = text.toString(); showDownloads(); true }
        }
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, -2, 1f))
        searchRow.addView(text("Cari", 14, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(blue, 12)
            setOnClickListener { downloadsSearchQuery = searchBox.text.toString(); showDownloads() }
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        root.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        if (downloadsSearchQuery.isNotBlank()) {
            root.addView(text("Hapus pencarian  x", 12, if (darkMode) 0xffaebeff.toInt() else blue, true).apply {
                gravity = Gravity.END
                setPadding(dp(8), dp(7), dp(4), dp(10))
                setOnClickListener { downloadsSearchQuery = ""; showDownloads() }
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            root.addView(space(8))
        }
        root.addView(text("Urutkan: $downloadSortMode  ▾", 14, if (darkMode) 0xffc5c9d4.toInt() else 0xff555966.toInt(), true).apply {
            setPadding(0, 0, 0, dp(14))
            setOnClickListener {
                val modes = arrayOf("Terbaru", "Terlama", "Nama A-Z", "Nama Z-A", "Ukuran terbesar")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Urutkan unduhan")
                     .setSingleChoiceItems(modes, modes.indexOf(downloadSortMode).coerceAtLeast(0)) { dialog, which ->
                        downloadSortMode = modes[which]
                        preferences.edit().putString("download_sort_mode", downloadSortMode).apply()
                        dialog.dismiss()
                        showDownloads()
                    }
                    .show()
            }
        })
        if (records.isNotEmpty()) {
            root.addView(text(if (query.isBlank()) "Hapus semua unduhan" else "Hapus hasil pencarian (" + records.size + ")", 14, if (darkMode) 0xffff8b8b.toInt() else 0xffc62828.toInt(), true).apply {
                gravity = Gravity.END
                setPadding(0, 0, 0, dp(12))
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Hapus semua unduhan?")
                        .setMessage("Semua ${records.size} wallpaper yang tersimpan akan dihapus dari pustaka Unduhan.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus semua") { _, _ ->
                            val updated = (preferences.getStringSet("download_records", emptySet()) ?: emptySet()).toMutableSet()
                            records.forEach { record ->
                                val file = File(File(filesDir, "downloads"), record.substringBefore("\t"))
                                if (file.exists() && !file.delete()) {
                                    Toast.makeText(this@MainActivity, "Sebagian file tidak dapat dihapus", Toast.LENGTH_LONG).show()
                                } else updated.remove(record)
                            }
                            preferences.edit().putStringSet("download_records", updated).apply()
                            showDownloads()
                        }
                        .show()
                }
            })
        }
        selectedDownloadRecords.retainAll(allRecords.toSet())
        if (selectedDownloadRecords.isEmpty()) downloadSelectionMode = false
        if (records.isNotEmpty()) {
            val bar = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(6)); background = rounded(if (darkMode) 0xff242f42.toInt() else 0xffe8f1ff.toInt(), 12) }
            bar.addView(text(if (downloadSelectionMode) selectedDownloadRecords.size.toString() + " dipilih" else "Pilih beberapa", 13, pageText, true), LinearLayout.LayoutParams(0, -2, 1f))
            bar.addView(text("Pilih semua", 12, blue, true).apply { setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { downloadSelectionMode = true; selectedDownloadRecords.addAll(records); showDownloads() } })
            bar.addView(text("Batal", 12, pageText, true).apply { setPadding(dp(8), dp(8), dp(4), dp(8)); setOnClickListener { selectedDownloadRecords.clear(); downloadSelectionMode = false; showDownloads() } })
            root.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            if (downloadSelectionMode && selectedDownloadRecords.isNotEmpty()) {
                root.addView(text("Hapus seleksi", 14, 0xffc62828.toInt(), true).apply { gravity = Gravity.END; setPadding(0, dp(5), 0, dp(10)); setOnClickListener {
                    val targets = selectedDownloadRecords.toSet()
                    AlertDialog.Builder(this@MainActivity).setTitle("Hapus wallpaper terpilih?").setMessage(targets.size.toString() + " file akan dihapus.")
                        .setNegativeButton("Batal", null).setPositiveButton("Hapus") { _, _ ->
                            val updated = (preferences.getStringSet("download_records", emptySet()) ?: emptySet()).toMutableSet()
                            targets.forEach { record -> val file = File(File(filesDir, "downloads"), record.substringBefore("\\t")); if (!file.exists() || file.delete()) updated.remove(record) }
                            preferences.edit().putStringSet("download_records", updated).apply(); selectedDownloadRecords.clear(); downloadSelectionMode = false; showDownloads()
                        }.show()
                } })
            }
        }
        val downloadBytes = records.sumOf { record ->
            File(File(filesDir, "downloads"), record.substringBefore("\t")).length()
        }
        val downloadSize = if (downloadBytes < 1024L * 1024L) {
            "${downloadBytes / 1024L} KB"
        } else {
            String.format("%.1f MB", downloadBytes / (1024.0 * 1024.0))
        }
        val summary = if (query.isBlank()) {
            "${records.size} wallpaper tersimpan  •  $downloadSize digunakan"
        } else {
            "${records.size} dari ${allRecords.size} wallpaper cocok  •  $downloadSize ditampilkan"
        }
        root.addView(text(summary, 13, if (darkMode) 0xffb9bdc8.toInt() else 0xff666a75.toInt(), false)
            .apply { setPadding(0, 0, 0, dp(14)) })
        if (records.isEmpty()) {
            val noDownloads = allRecords.isEmpty()
            val emptyColumn = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(30), dp(24), dp(30))
                addView(text(if (noDownloads) "Belum ada wallpaper yang diunduh" else "Wallpaper tidak ditemukan", 17, pageText, true).apply { gravity = Gravity.CENTER })
                addView(text(if (noDownloads) "Unduh wallpaper terlebih dahulu. File yang disimpan akan muncul di sini." else "Coba kata kunci lain atau hapus pencarian untuk melihat semua unduhan.", 13, if (darkMode) 0xffaeb3bf.toInt() else 0xff777b86.toInt(), false).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(9), 0, dp(18))
                })
                if (!noDownloads) addView(text("Hapus pencarian", 14, if (darkMode) 0xff9bbcff.toInt() else blue, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    background = rounded(if (darkMode) 0xff242f42.toInt() else 0xffe8f1ff.toInt(), 12)
                    setOnClickListener { downloadsSearchQuery = ""; showDownloads() }
                })
            }
            root.addView(emptyColumn, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val list = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            records.forEach { record ->
                val fileName = record.substringBefore("\t")
                val title = record.substringAfter("\t", "Wallpaper")
                val imageFile = File(File(filesDir, "downloads"), fileName)
                val selected = selectedDownloadRecords.contains(record)
                val row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(9), dp(9), dp(9), dp(9))
                    background = rounded(if (selected) 0xffdceaff.toInt() else if (darkMode) 0xff24262d.toInt() else Color.WHITE, 14)
                    setOnLongClickListener { downloadSelectionMode = true; selectedDownloadRecords.add(record); showDownloads(); true }
                }
                if (downloadSelectionMode) row.addView(text(if (selected) "✓" else "○", 21, blue, true).apply { gravity = Gravity.CENTER; setOnClickListener { if (selected) selectedDownloadRecords.remove(record) else selectedDownloadRecords.add(record); showDownloads() } }, LinearLayout.LayoutParams(dp(32), dp(48)))
                val thumb = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(rounded(if (darkMode) 0xff343741.toInt() else 0xffe4e6eb.toInt(), 8))
                    val decoded = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (decoded != null) setImageBitmap(decoded)
                    contentDescription = "Pratinjau $title"
                }
                row.addView(thumb, LinearLayout.LayoutParams(dp(72), dp(88)))
                val labelColumn = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(8), 0)
                }
                val label = text(title, 15, pageText, true).apply { maxLines = 2 }
                val fileBytes = imageFile.length()
                val fileSize = if (fileBytes < 1024L * 1024L) {
                    "${fileBytes / 1024L} KB"
                } else {
                    String.format("%.1f MB", fileBytes / (1024.0 * 1024.0))
                }
                val savedDate = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(imageFile.lastModified()))
                val details = text("$savedDate  •  $fileSize", 11,
                    if (darkMode) 0xffaeb3bf.toInt() else 0xff777b86.toInt(), false)
                labelColumn.addView(label)
                labelColumn.addView(details)
                row.addView(labelColumn, LinearLayout.LayoutParams(0, -2, 1f))
                val share = text("↗", 22, if (darkMode) 0xff9bbcff.toInt() else blue, true).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        try {
                            val uri = FileProvider.getUriForFile(
                                this@MainActivity,
                                "com.civamel.ultrahdwallpaper.fileprovider",
                                imageFile
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TITLE, title)
                                clipData = android.content.ClipData.newUri(contentResolver, title, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(intent, "Bagikan wallpaper"))
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "Tidak dapat membagikan wallpaper", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                row.addView(share, LinearLayout.LayoutParams(dp(42), dp(48)))
                val delete = text("✕", 21, if (darkMode) 0xffff8b8b.toInt() else 0xffc62828.toInt(), true).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Hapus unduhan?")
                            .setMessage("Wallpaper \"$title\" akan dihapus dari pustaka Unduhan.")
                            .setNegativeButton("Batal", null)
                            .setPositiveButton("Hapus") { _, _ ->
                                if (imageFile.exists() && !imageFile.delete()) {
                                    Toast.makeText(this@MainActivity, "File tidak dapat dihapus. Coba lagi.", Toast.LENGTH_LONG).show()
                                } else {
                                    val updated = (preferences.getStringSet("download_records", emptySet()) ?: emptySet()).toMutableSet()
                                    updated.remove(record)
                                    preferences.edit().putStringSet("download_records", updated).apply()
                                    showDownloads()
                                }
                            }
                            .show()
                    }
                }
                row.addView(delete, LinearLayout.LayoutParams(dp(42), dp(48)))
                row.setOnClickListener {
                    val preview = ImageView(this@MainActivity).apply {
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageBitmap(BitmapFactory.decodeFile(imageFile.absolutePath))
                    }
                    AlertDialog.Builder(this@MainActivity).setTitle(title).setView(preview)
                        .setNegativeButton("Info") { _, _ -> showDownloadedImageInfo(imageFile, title) }
                        .setNeutralButton("Terapkan") { _, _ -> showDownloadedApplyOptions(imageFile, title) }
                        .setPositiveButton("Tutup", null).show()
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
            root.addView(ScrollView(this@MainActivity).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
    }

    private fun showDownloadedImageInfo(file: File, title: String) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val size = String.format(java.util.Locale.getDefault(), "%.2f MB", file.length() / (1024.0 * 1024.0))
        val format = file.extension.uppercase().ifBlank { "Tidak diketahui" }
        val date = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
        val message = "Nama: " + title + "\\nResolusi: " + options.outWidth + " x " + options.outHeight + "\\nFormat: " + format + "\\nUkuran: " + size + "\\nTanggal: " + date
        AlertDialog.Builder(this@MainActivity).setTitle("Informasi wallpaper").setMessage(message).setPositiveButton("Tutup", null).show()
    }

    private fun showDownloadedApplyOptions(file: File, title: String) {
        val choices = arrayOf("Layar utama", "Layar kunci", "Keduanya")
        AlertDialog.Builder(this@MainActivity).setTitle("Terapkan " + title).setItems(choices) { _, which ->
            if (Build.VERSION.SDK_INT < 24 && which != 0) { Toast.makeText(this@MainActivity, "Pilihan ini memerlukan Android 7.0 atau lebih baru", Toast.LENGTH_LONG).show(); return@setItems }
            val flags = when (which) { 1 -> WallpaperManager.FLAG_LOCK; 2 -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK; else -> WallpaperManager.FLAG_SYSTEM }
            Thread {
                var bitmap: Bitmap? = null
                try {
                    bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: throw IllegalStateException("File gambar tidak dapat dibaca")
                    WallpaperManager.getInstance(this@MainActivity).setBitmap(bitmap, null, true, flags)
                    runOnUiThread { Toast.makeText(this@MainActivity, "Wallpaper berhasil diterapkan", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Gagal menerapkan wallpaper: " + (e.localizedMessage ?: "kesalahan"), Toast.LENGTH_LONG).show() }
                } finally { bitmap?.recycle() }
            }.start()
        }.show()
    }
    private fun recordRecentlyViewed(index: Int) {
        val id = itemIds.getOrNull(index) ?: return
        val history = org.json.JSONArray()
        history.put(id)
        val previous = try {
            org.json.JSONArray(preferences.getString("recent_wallpaper_ids", "[]") ?: "[]")
        } catch (_: Exception) {
            org.json.JSONArray()
        }
        for (position in 0 until previous.length()) {
            val oldId = previous.optString(position)
            if (oldId.isNotBlank() && oldId != id && history.length() < 30) history.put(oldId)
        }
        preferences.edit().putString("recent_wallpaper_ids", history.toString()).apply()
    }

    private fun showRecentlyViewed() {
        val ids = try {
            val saved = org.json.JSONArray(preferences.getString("recent_wallpaper_ids", "[]") ?: "[]")
            (0 until saved.length()).map { saved.optString(it) }.filter { it.isNotBlank() }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
        val indexes = ids.mapNotNull { id ->
            val index = itemIds.indexOf(id)
            if (index >= 0) index else null
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(if (darkMode) 0xff121318.toInt() else Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("‹", 30, if (darkMode) 0xfff2f2f5.toInt() else ink, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(text("Riwayat dilihat", 22, if (darkMode) 0xfff2f2f5.toInt() else ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("Hapus", 14, blue, true).apply {
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Hapus riwayat?")
                    .setMessage("Daftar wallpaper yang pernah dibuka akan dikosongkan.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus") { _, _ ->
                        preferences.edit().remove("recent_wallpaper_ids").apply()
                        showRecentlyViewed()
                    }.show()
            }
        })
        root.addView(header)
        root.addView(text("Maksimal 30 wallpaper terakhir yang dibuka", 13,
            if (darkMode) 0xffaaaaaa.toInt() else Color.GRAY, false).apply {
            setPadding(dp(4), dp(4), 0, dp(14))
        })
        if (indexes.isEmpty()) {
            root.addView(text("Belum ada riwayat wallpaper.", 15,
                if (darkMode) 0xfff2f2f5.toInt() else ink, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, dp(48))
            })
        } else {
            val scroll = ScrollView(this)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            indexes.forEach { index ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = rounded(if (darkMode) 0xff24262d.toInt() else 0xfff4f4f7.toInt(), 14)
                    setOnClickListener { showPreview(index) }
                }
                val thumbnail = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageDrawable(artDrawable(index))
                }
                row.addView(thumbnail, LinearLayout.LayoutParams(dp(72), dp(72)))
                row.addView(text(names[index], 15, if (darkMode) 0xfff2f2f5.toInt() else ink, true),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
                row.addView(text("›", 24, if (darkMode) 0xfff2f2f5.toInt() else ink, false))
                list.addView(row, LinearLayout.LayoutParams(-1, dp(88)).apply { bottomMargin = dp(8) })
                thumbnailUrls.getOrNull(index)?.let { url ->
                    loadImage(url) { bitmap ->
                        if (bitmap != null && thumbnail.isAttachedToWindow) thumbnail.setImageBitmap(bitmap)
                    }
                }
            }
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
    }

    private fun showFavorites(){
        refreshFavoritesFromPreferences()
        showOnlyFavorites = true
        selectedCategory = "All"
        selectedTab = "Wallpaper"
        preferences.edit()
            .putString("selected_wallpaper_category", selectedCategory)
            .putString("selected_wallpaper_tab", selectedTab)
            .apply()
        visibleItemLimit = 24
        buildHome()
    }
    private fun showCategories(){
        val root = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if(darkMode) 0xff121318.toInt() else Color.WHITE)
            setPadding(dp(18), dp(18), dp(18), dp(12))
        }
        val header = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("‹", 30, if(darkMode) 0xfff2f2f5.toInt() else ink, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { buildHome() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(text("Category", 23, if(darkMode) 0xfff2f2f5.toInt() else ink, true))
        root.addView(header)
        root.addView(text("Explore wallpapers by category", 14, if(darkMode) 0xffaaaaaa.toInt() else Color.GRAY, false).apply {
            setPadding(dp(4), dp(4), 0, dp(18))
        })
        val scroll = ScrollView(this@MainActivity)
        val list = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        val symbols = listOf("▦", "✦", "●", "▶", "◈", "⌁", "❋", "≈", "✧", "⚙")
        categoryNames.forEachIndexed { index, category ->
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = rounded(if(darkMode) 0xff24262d.toInt() else 0xfff4f4f7.toInt(), 14)
                setOnClickListener {
                    selectedCategory = category
                    selectedTab = "Wallpaper"
                    preferences.edit()
                        .putString("selected_wallpaper_category", selectedCategory)
                        .putString("selected_wallpaper_tab", selectedTab)
                        .apply()
                    visibleItemLimit = 24
                    showOnlyFavorites = false
                    buildHome()
                }
            }
            row.addView(modernIcon(symbols.getOrElse(index) { "✧" },38),
                LinearLayout.LayoutParams(dp(48), dp(48)))
            row.addView(text(category, 16, if(darkMode) 0xfff2f2f5.toInt() else ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            val count = if (category == "All") names.size else itemCategories.count { it == category }
            row.addView(text(count.toString(), 13, if(darkMode) 0xffaaaaaa.toInt() else Color.GRAY, false))
            row.addView(text("›", 25, if(darkMode) 0xfff2f2f5.toInt() else ink, false))
            val params = LinearLayout.LayoutParams(-1, dp(64))
            params.bottomMargin = dp(9)
            list.addView(row, params)
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    private fun savedQuotes(): List<Pair<String, String>> {
        return try {
            val array = org.json.JSONArray(preferences.getString("saved_quotes", "[]") ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val quote = item.optString("quote").trim()
                if (quote.isBlank()) null else quote to item.optString("category")
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun persistSavedQuotes(quotes: List<Pair<String, String>>) {
        val array = org.json.JSONArray()
        quotes.distinctBy { it.first }.forEach { (quote, category) ->
            array.put(org.json.JSONObject().put("quote", quote).put("category", category))
        }
        preferences.edit().putString("saved_quotes", array.toString()).apply()
    }

    private fun showQuotes() {
        val quotes = listOf(
            "The best view comes after the hardest climb." to "Motivation",
            "Small steps every day lead to big changes." to "Daily Inspiration",
            "Make today meaningful, one moment at a time." to "Mindfulness",
            "Your future is created by what you do today." to "Motivation",
            "Find beauty in the little things." to "Life",
            "Stay patient. Great things take time." to "Daily Inspiration",
            "Let your dreams be bigger than your fears." to "Motivation",
            "A calm mind brings a clearer perspective." to "Mindfulness"
        )
        var currentIndex = 0
        val foreground = if (darkMode) 0xfff2f2f5.toInt() else ink
        val muted = if (darkMode) 0xffaaaaaa.toInt() else Color.GRAY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (darkMode) 0xff121318.toInt() else Color.rgb(248, 248, 251))
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("‹", 30, foreground, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { buildHome() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(text("Quotes", 23, foreground, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("♡ Tersimpan", 13, blue, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showSavedQuotes() }
        })
        root.addView(header)
        root.addView(text("Words to inspire your day", 14, muted, false).apply {
            setPadding(dp(4), dp(2), 0, dp(20))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(34), dp(24), dp(34))
            background = rounded(if (darkMode) 0xff24262d.toInt() else Color.WHITE, 22)
            elevation = dp(3).toFloat()
        }
        val quoteText = text("", 23, foreground, true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(5).toFloat(), 1f)
        }
        val categoryText = text("", 13, purple, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }
        card.addView(text("“", 48, purple, true).apply { gravity = Gravity.CENTER })
        card.addView(quoteText)
        card.addView(categoryText)
        root.addView(card, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(0, dp(12), 0, dp(18))
        })
        lateinit var updateQuote: () -> Unit
        val saveButton = Button(this).apply {
            isAllCaps = false
            setOnClickListener {
                val current = quotes[currentIndex]
                val saved = savedQuotes().toMutableList()
                if (saved.any { it.first == current.first }) {
                    persistSavedQuotes(saved.filterNot { it.first == current.first })
                    Toast.makeText(this@MainActivity, "Kutipan dihapus dari tersimpan", Toast.LENGTH_SHORT).show()
                } else {
                    saved.add(0, current)
                    persistSavedQuotes(saved)
                    Toast.makeText(this@MainActivity, "Kutipan berhasil disimpan", Toast.LENGTH_SHORT).show()
                }
                updateQuote()
            }
        }
        updateQuote = {
            val current = quotes[currentIndex]
            quoteText.text = current.first
            categoryText.text = current.second.uppercase()
            saveButton.text = if (savedQuotes().any { it.first == current.first }) "♥ Hapus dari tersimpan" else "♡ Simpan kutipan"
        }
        root.addView(saveButton, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(Button(this).apply {
            text = "Kutipan berikutnya"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(blue, 28)
            setOnClickListener { currentIndex = (currentIndex + 1) % quotes.size; updateQuote() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        root.addView(Button(this).apply {
            text = "Bagikan kutipan"
            isAllCaps = false
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, quotes[currentIndex].first + "\n\n— Ultra HD Wallpaper")
                startActivity(Intent.createChooser(send, "Bagikan kutipan"))
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        updateQuote()
        setContentView(root)
    }

    private fun showSavedQuotes() {
        val saved = savedQuotes()
        val foreground = if (darkMode) 0xfff2f2f5.toInt() else ink
        val muted = if (darkMode) 0xffaaaaaa.toInt() else Color.GRAY
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(if (darkMode) 0xff121318.toInt() else Color.WHITE)
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("‹", 30, foreground, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showQuotes() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(text("Kutipan tersimpan", 21, foreground, true))
        root.addView(header)
        root.addView(text("${saved.size} kutipan disimpan di perangkat ini", 13, muted, false).apply {
            setPadding(dp(4), dp(4), 0, dp(14))
        })
        if (saved.isEmpty()) {
            root.addView(text("Belum ada kutipan tersimpan.", 15, muted, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, dp(48))
            })
        } else {
            val scroll = ScrollView(this)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            saved.forEach { (quote, category) ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(12))
                    background = rounded(if (darkMode) 0xff24262d.toInt() else 0xfff4f4f7.toInt(), 14)
                }
                card.addView(text("“$quote”", 16, foreground, true))
                card.addView(text(category.uppercase(), 11, purple, true).apply { setPadding(0, dp(8), 0, dp(8)) })
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
                actions.addView(text("Bagikan", 13, blue, true).apply {
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, quote + "\n\n— Ultra HD Wallpaper")
                        startActivity(Intent.createChooser(send, "Bagikan kutipan"))
                    }
                })
                actions.addView(text("Hapus", 13, Color.RED, true).apply {
                    setPadding(dp(12), dp(8), dp(4), dp(8))
                    setOnClickListener {
                        persistSavedQuotes(savedQuotes().filterNot { it.first == quote })
                        showSavedQuotes()
                    }
                })
                card.addView(actions)
                list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
    }
    private fun search(){ val input=EditText(this@MainActivity).apply { hint="Cari wallpaper"; setText(searchQuery) }; AlertDialog.Builder(this@MainActivity).setTitle("Search").setView(input).setPositiveButton("Cari"){_,_-> searchQuery=input.text.toString().trim(); showOnlyFavorites=false; visibleItemLimit=60; buildHome() }.setNeutralButton("Hapus"){_,_-> searchQuery=""; visibleItemLimit=60; buildHome() }.setNegativeButton("Batal",null).show() }
    private fun shareWallpaper(index: Int) {
        val url = fullUrls.getOrNull(index)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Gambar belum tersedia", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Menyiapkan file asli untuk dibagikan…", Toast.LENGTH_SHORT).show()
        loadWallpaperFile(url) { imageFile ->
            if (imageFile == null) {
                Toast.makeText(this, "Gambar gagal dimuat. Periksa koneksi internet.", Toast.LENGTH_LONG).show()
                return@loadWallpaperFile
            }
            try {
                val imageUri = FileProvider.getUriForFile(this, "com.civamel.ultrahdwallpaper.fileprovider", imageFile)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_SUBJECT, names[index])
                    putExtra(Intent.EXTRA_TEXT, names[index] + " — Ultra HD Wallpaper")
                    clipData = android.content.ClipData.newUri(contentResolver, names[index], imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(sendIntent, "Bagikan wallpaper"))
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membagikan gambar: " + e.localizedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun applyLiveWallpaper(index: Int) {
        val liveUrl = fullUrls.getOrNull(index)
        if (liveUrl.isNullOrBlank()) {
            Toast.makeText(this@MainActivity, "Gambar live wallpaper belum tersedia", Toast.LENGTH_LONG).show()
            return
        }
        preferences.edit().putString("selected_live_wallpaper_url", liveUrl).putString("selected_live_wallpaper_title", names[index]).apply()
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, LiveWallpaperService::class.java))
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity,"Pemilih live wallpaper tidak tersedia: "+e.localizedMessage,Toast.LENGTH_LONG).show()
        }
    }
    private fun toggleTheme(){ darkMode = !darkMode; preferences.edit().putBoolean("dark_mode", darkMode).apply(); buildHome(); Toast.makeText(this@MainActivity,if(darkMode) "Tema gelap aktif" else "Tema terang aktif",Toast.LENGTH_SHORT).show() }
    private fun modernIcon(glyph: String, size: Int): FrameLayout {
        val rubyRed = 0xffe0115f.toInt()
        val icon = FrameLayout(this@MainActivity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipToOutline = false
            contentDescription = glyph
        }
        val resource = when (glyph) {
            "⌕" -> android.R.drawable.ic_menu_search
            "⚙" -> android.R.drawable.ic_menu_preferences
            "▦", "▧" -> android.R.drawable.ic_menu_gallery
            "⇩" -> android.R.drawable.ic_menu_save
            "↗" -> android.R.drawable.ic_menu_share
            "ⓘ" -> android.R.drawable.ic_menu_info_details
            "‹", "›" -> android.R.drawable.ic_media_previous
            "✦", "💎" -> android.R.drawable.btn_star_big_on
            "⌂" -> android.R.drawable.ic_menu_view
            else -> 0
        }
        if (resource != 0) {
            val standardIcon = ImageView(this@MainActivity).apply {
                setImageResource(resource)
                setColorFilter(rubyRed)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = glyph
                setPadding(dp(5), dp(5), dp(5), dp(5))
            }
            icon.addView(standardIcon, FrameLayout.LayoutParams(-1, -1))
        } else {
            icon.addView(text(glyph, (size * 0.72f).toInt().coerceAtLeast(20), rubyRed, true).apply {
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        return icon
    }

    private fun circle(symbol:String, action:()->Unit):View = modernIcon(symbol,48).apply {
        setOnClickListener { action() }
    }.also { it.layoutParams=LinearLayout.LayoutParams(dp(48),dp(48)).apply { setMargins(dp(4),0,dp(4),0) } }
    private fun artDrawable(index:Int):GradientDrawable { val a=palettes[index%palettes.size]; return GradientDrawable(GradientDrawable.Orientation.TL_BR,a).apply { cornerRadius=dp(2).toFloat() } }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun text(s:String,size:Int,color:Int,bold:Boolean)=TextView(this@MainActivity).apply { text=s; textSize=size.toFloat(); setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD }
    private fun space(h:Int)=View(this@MainActivity).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    private fun appendCatalogItems(items: List<org.json.JSONObject>) {
        for (item in items) {
            val title = item.optString("title").trim()
            val thumbnail = item.optString("thumbnail").trim()
            val image = item.optString("image").trim()
            val id = stableWallpaperId(item)
            if (title.isEmpty() || !thumbnail.startsWith("https://") || !image.startsWith("https://")) continue
            if (itemIds.contains(id)) continue
            names.add(title)
            itemIds.add(id)
            itemCategories.add(item.optString("category", "Abstract").ifBlank { "Abstract" })
            itemTypes.add(item.optString("type", "wallpaper").lowercase())
            val tagArray = item.optJSONArray("tags")
            itemTags.add((0 until (tagArray?.length() ?: 0)).map { tagArray!!.optString(it).trim() }.filter { it.isNotEmpty() })
            thumbnailUrls.add(thumbnail)
            fullUrls.add(image)
        }
        migrateLegacyFavorites()
        imageCache.evictAll()
    }

    private fun cachedShardFile(index: Int): File = File(filesDir, "catalog_page_${index + 1}.json")

    private fun loadCachedShardedCatalog() {
        try {
            val manifestFile = File(filesDir, "catalog_manifest_cache.json")
            if (!manifestFile.isFile || manifestFile.length() <= 0L) return
            val index = CatalogManifest.parse(manifestFile.readText())
            var loaded = 0
            val cachedItems = mutableListOf<org.json.JSONObject>()
            for ((position, shard) in index.shards.withIndex()) {
                val pageFile = cachedShardFile(position)
                if (!pageFile.isFile || pageFile.length() <= 0L) break
                cachedItems.addAll(CatalogManifest.parseShard(pageFile.readText(), shard))
                loaded++
            }
            if (cachedItems.isEmpty()) return
            names.clear(); itemIds.clear(); itemCategories.clear(); itemTypes.clear()
            itemTags.clear(); thumbnailUrls.clear(); fullUrls.clear()
            appendCatalogItems(cachedItems)
            remoteCatalogIndex = index
            loadedShardCount = loaded
            visibleItemLimit = 24
            buildHome()
        } catch (_: Exception) {
            // Keep the previously loaded bundled or legacy cache when sharded cache is invalid.
        }
    }

    private fun hasMoreCatalogShards(): Boolean =
        (remoteCatalogIndex?.shards?.size ?: 0) > loadedShardCount

    private fun loadNextCatalogShard() {
        val index = remoteCatalogIndex
        if (index == null || shardLoading || loadedShardCount >= index.shards.size) return
        val shardPosition = loadedShardCount
        val shard = index.shards[shardPosition]
        shardLoading = true
        buildHome()
        Thread {
            var connection: HttpURLConnection? = null
            val result = try {
                val url = URL("https://raw.githubusercontent.com/Civamel/UHD-Wallpaper/main/catalog/" + shard.path)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 18000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                CatalogManifest.parseShard(body, shard) to body
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                shardLoading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (result == null) {
                    Toast.makeText(this, "Halaman katalog gagal dimuat. Periksa koneksi lalu coba lagi.", Toast.LENGTH_LONG).show()
                    buildHome()
                    return@runOnUiThread
                }
                try {
                    cachedShardFile(shardPosition).writeText(result.second)
                    appendCatalogItems(result.first)
                    loadedShardCount = shardPosition + 1
                    visibleItemLimit = maxOf(visibleItemLimit, names.size)
                    buildHome()
                } catch (_: Exception) {
                    Toast.makeText(this, "Data halaman katalog tidak dapat disimpan.", Toast.LENGTH_LONG).show()
                    buildHome()
                }
            }
        }.start()
    }

    // Load only the first shard at startup; remaining pages are fetched on demand.
    // Fetch all catalog shards at startup so a page can show up to 24 available items.
    private fun refreshRemoteCatalog() {
        Thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = (URL("https://raw.githubusercontent.com/Civamel/UHD-Wallpaper/main/catalog/manifest.json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 18000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                if (connection.responseCode !in 200..299) throw IllegalStateException("Manifest HTTP " + connection.responseCode)
                val manifestBody = connection.inputStream.bufferedReader().use { it.readText() }
                val index = CatalogManifest.parse(manifestBody)
                connection.disconnect()
                connection = null
                val allItems = mutableListOf<org.json.JSONObject>()
                val pageBodies = mutableListOf<Pair<Int, String>>()
                for ((position, shard) in index.shards.withIndex()) {
                    connection = (URL("https://raw.githubusercontent.com/Civamel/UHD-Wallpaper/main/catalog/" + shard.path).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 18000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                    }
                    if (connection.responseCode !in 200..299) throw IllegalStateException("Shard HTTP " + connection.responseCode)
                    val pageBody = connection.inputStream.bufferedReader().use { it.readText() }
                    allItems.addAll(CatalogManifest.parseShard(pageBody, shard))
                    pageBodies.add(position to pageBody)
                    connection.disconnect()
                    connection = null
                }
                if (allItems.isEmpty()) throw IllegalStateException("Katalog online kosong")
                Triple(index, allItems, Pair(manifestBody, pageBodies))
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
            if (result == null) {
                refreshLegacyRemoteCatalog()
                return@Thread
            }
            try {
                File(filesDir, "catalog_manifest_cache.json").writeText(result.third.first)
                result.third.second.forEach { (position, body) -> cachedShardFile(position).writeText(body) }
            } catch (_: Exception) {
                // The online catalog remains usable if caching is unavailable.
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                names.clear(); itemIds.clear(); itemCategories.clear(); itemTypes.clear()
                itemTags.clear(); thumbnailUrls.clear(); fullUrls.clear()
                remoteCatalogIndex = result.first
                appendCatalogItems(result.second)
                loadedShardCount = result.first.shards.size
                visibleItemLimit = 24
                buildHome()
            }
        }.start()
    }

    // Backward-compatible fallback for repositories that still publish one catalog JSON.
    private fun refreshLegacyRemoteCatalog() {
        Thread {
            var connection: HttpURLConnection? = null
            var validatedCatalogBody: String? = null
            val parsed = try {
                val url = URL("https://raw.githubusercontent.com/Civamel/UHD-Wallpaper/main/catalog/wallpapers.json")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 18000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val items = CatalogValidator.parse(body)
                val titles = mutableListOf<String>()
                val ids = mutableListOf<String>()
                val categories = mutableListOf<String>()
                val types = mutableListOf<String>()
                val tags = mutableListOf<List<String>>()
                val thumbs = mutableListOf<String>()
                val images = mutableListOf<String>()
                for (i in items.indices) {
                    val item = items[i]
                    val title = item.optString("title").trim()
                    val thumbnail = item.optString("thumbnail").trim()
                    val image = item.optString("image").trim()
                    if (title.isEmpty() || thumbnail.isEmpty() || image.isEmpty()) continue
                    if (!thumbnail.startsWith("https://") || !image.startsWith("https://")) continue
                    titles.add(title)
                    ids.add(stableWallpaperId(item))
                    categories.add(item.optString("category", "Abstract").ifBlank { "Abstract" })
                    types.add(item.optString("type", "wallpaper").lowercase())
                    val tagsArray = item.optJSONArray("tags")
                    tags.add((0 until (tagsArray?.length() ?: 0)).map { tagsArray!!.optString(it).trim() }.filter { it.isNotEmpty() })
                    thumbs.add(thumbnail)
                    images.add(image)
                }
                if (titles.isEmpty()) throw IllegalStateException("Katalog online kosong atau tidak valid")
                validatedCatalogBody = body
                ParsedCatalog(titles, ids, categories, types, tags, thumbs, images)
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
            if (parsed != null) {
                try {
                    val cacheFile = File(filesDir, "catalog_cache.json")
                    FileOutputStream(cacheFile).use { output -> output.write(validatedCatalogBody.orEmpty().toByteArray(Charsets.UTF_8)) }
                } catch (_: Exception) {
                    // The current session can still use the fetched catalog if disk caching fails.
                }
                runOnUiThread {
                    names.clear()
                    names.addAll(parsed.names)
                    itemIds.clear()
                    itemIds.addAll(parsed.ids)
                    migrateLegacyFavorites()
                    itemCategories.clear()
                    itemCategories.addAll(parsed.categories)
                    itemTypes.clear()
                    itemTypes.addAll(parsed.types)
                    itemTags.clear()
                    itemTags.addAll(parsed.tags)
                    thumbnailUrls.clear()
                    thumbnailUrls.addAll(parsed.thumbnails)
                    fullUrls.clear()
                    fullUrls.addAll(parsed.images)
                    imageCache.evictAll()
                    buildHome()
                }
            }
        }.start()
    }
}
