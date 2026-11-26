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
import kotlin.random.Random
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
    private val blue = Color.rgb(183, 110, 121)
    private val purple = Color.rgb(158, 67, 238)
    private val ink = Color.rgb(28, 28, 30)
    private val tabs = listOf("Home", "Live", "3D", "Wallpaper", "Aesthetic")
    private val names = mutableListOf<String>()
    private val palettes = listOf(
        intArrayOf(0xff09090f.toInt(),0xffb76e79.toInt()), intArrayOf(0xfff5d8b4.toInt(),0xff00a9c7.toInt()),
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
    private val imageCache = object : LruCache<String, Bitmap>(48 * 1024) {
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
    private var downloadSortMode = "Name A-Z"
    private var downloadsSearchQuery = ""
    private var downloadSelectionMode = false
    private val selectedDownloadRecords = mutableSetOf<String>()
    private var appLanguage = "en"
    private var adminUiActive = false
    private val supportedLanguages = listOf(
        "🇬🇧 English" to "en", "🇮🇩 Indonesia" to "id", "🇪🇸 Español" to "es",
        "🇳🇱 Nederlands" to "nl", "🇵🇹 Português" to "pt", "🇨🇳 中文（普通话）" to "zh",
        "🇰🇷 한국어" to "ko", "🇧🇷 Português (Brasil)" to "pt-BR", "🇷🇺 Русский" to "ru",
        "🇰🇿 Қазақша" to "kk", "🇫🇮 Suomi" to "fi", "🇸🇦 العربية" to "ar",
        "🇷🇴 Română" to "ro", "🇵🇦 Español (Panamá)" to "es-PA", "🇯🇵 日本語" to "ja",
        "🇩🇯 Français" to "fr", "🇨🇭 Deutsch" to "de", "🇧🇻 Norsk" to "no",
        "🇦🇷 Español (Argentina)" to "es-AR", "🇮🇹 Italiano" to "it"
    )
    private val translations = mapOf(
        "en" to mapOf("Pengaturan" to "Settings","Bahasa Aplikasi" to "App Language","Notifikasi" to "Notifications","Favorit" to "Favorites","Recently viewed" to "Recently Viewed","Unduh" to "Downloads","Share Aplikasi" to "Share App","Nilai kami" to "Rate Us","Umpan Balik" to "Feedback","Manage Cache" to "Manage Cache","Admin" to "Admin","Beranda" to "Home","Wallpaper" to "Wallpaper","Kategori" to "Categories","Search" to "Search","Semua" to "All","Belum ada wallpaper" to "No wallpapers yet","Memuat wallpaper…" to "Loading wallpapers…","Simpan" to "Save","Cancel" to "Cancel","Tutup" to "Close","Pilih Bahasa" to "Choose Language","Press back again to exit" to "Press back again to exit"),
        "id" to mapOf("Pengaturan" to "Pengaturan","Bahasa Aplikasi" to "Bahasa Aplikasi","Notifikasi" to "Notifikasi","Favorit" to "Favorit","Recently viewed" to "Recently viewed","Unduh" to "Downloads","Share Aplikasi" to "Share Aplikasi","Nilai kami" to "Nilai Kami","Umpan Balik" to "Masukan","Manage Cache" to "Manage Cache","Beranda" to "Beranda","Kategori" to "Kategori","Search" to "Search","Semua" to "Semua","Belum ada wallpaper" to "Belum ada wallpaper","Simpan" to "Simpan","Cancel" to "Cancel","Tutup" to "Tutup","Pilih Bahasa" to "Pilih Bahasa"),
        "es" to mapOf("Pengaturan" to "Ajustes","Bahasa Aplikasi" to "Idioma de la aplicación","Notifikasi" to "Notificaciones","Favorit" to "Favoritos","Recently viewed" to "Vistos recientemente","Unduh" to "Descargas","Share Aplikasi" to "Compartir aplicación","Nilai kami" to "Califícanos","Umpan Balik" to "Comentarios","Manage Cache" to "Gestionar caché","Simpan" to "Guardar","Cancel" to "Cancelar","Tutup" to "Cerrar","Pilih Bahasa" to "Elegir idioma"),
        "nl" to mapOf("Pengaturan" to "Instellingen","Bahasa Aplikasi" to "App-taal","Notifikasi" to "Meldingen","Favorit" to "Favorieten","Recently viewed" to "Recent bekeken","Unduh" to "Downloads","Share Aplikasi" to "App delen","Nilai kami" to "Beoordeel ons","Umpan Balik" to "Feedback","Manage Cache" to "Cache beheren","Simpan" to "Opslaan","Cancel" to "Annuleren","Tutup" to "Sluiten","Pilih Bahasa" to "Kies taal"),
        "pt" to mapOf("Pengaturan" to "Configurações","Bahasa Aplikasi" to "Idioma do aplicativo","Notifikasi" to "Notificações","Favorit" to "Favoritos","Recently viewed" to "Vistos recentemente","Unduh" to "Downloads","Share Aplikasi" to "Compartilhar aplicativo","Nilai kami" to "Avalie-nos","Umpan Balik" to "Feedback","Manage Cache" to "Gerenciar cache","Simpan" to "Salvar","Cancel" to "Cancelar","Tutup" to "Fechar","Pilih Bahasa" to "Escolher idioma"),
        "pt-BR" to mapOf("Pengaturan" to "Configurações","Bahasa Aplikasi" to "Idioma do aplicativo","Notifikasi" to "Notificações","Favorit" to "Favoritos","Recently viewed" to "Vistos recentemente","Unduh" to "Downloads","Share Aplikasi" to "Compartilhar aplicativo","Nilai kami" to "Avalie-nos","Umpan Balik" to "Comentários","Manage Cache" to "Gerenciar cache","Simpan" to "Salvar","Cancel" to "Cancelar","Tutup" to "Fechar","Pilih Bahasa" to "Escolher idioma"),
        "zh" to mapOf("Pengaturan" to "设置","Bahasa Aplikasi" to "应用语言","Notifikasi" to "通知","Favorit" to "收藏","Recently viewed" to "最近查看","Unduh" to "下载","Share Aplikasi" to "分享应用","Nilai kami" to "评价我们","Umpan Balik" to "反馈","Manage Cache" to "管理缓存","Simpan" to "保存","Cancel" to "取消","Tutup" to "关闭","Pilih Bahasa" to "选择语言"),
        "ko" to mapOf("Pengaturan" to "설정","Bahasa Aplikasi" to "앱 언어","Notifikasi" to "알림","Favorit" to "즐겨찾기","Recently viewed" to "최근 본 항목","Unduh" to "다운로드","Share Aplikasi" to "앱 공유","Nilai kami" to "평가하기","Umpan Balik" to "피드백","Manage Cache" to "캐시 관리","Simpan" to "저장","Cancel" to "취소","Tutup" to "닫기","Pilih Bahasa" to "언어 선택"),
        "ru" to mapOf("Pengaturan" to "Настройки","Bahasa Aplikasi" to "Язык приложения","Notifikasi" to "Уведомления","Favorit" to "Избранное","Recently viewed" to "Недавно просмотренные","Unduh" to "Загрузки","Share Aplikasi" to "Поделиться приложением","Nilai kami" to "Оцените нас","Umpan Balik" to "Отзывы","Manage Cache" to "Управление кэшем","Simpan" to "Сохранить","Cancel" to "Отмена","Tutup" to "Закрыть","Pilih Bahasa" to "Выбрать язык"),
        "kk" to mapOf("Pengaturan" to "Баптаулар","Bahasa Aplikasi" to "Қолданба тілі","Notifikasi" to "Хабарландырулар","Favorit" to "Таңдаулылар","Unduh" to "Жүктеулер","Simpan" to "Сақтау","Cancel" to "Болдырмау","Tutup" to "Жабу","Pilih Bahasa" to "Тілді таңдау"),
        "fi" to mapOf("Pengaturan" to "Asetukset","Bahasa Aplikasi" to "Sovelluksen kieli","Notifikasi" to "Ilmoitukset","Favorit" to "Suosikit","Unduh" to "Lataukset","Simpan" to "Tallenna","Cancel" to "Peruuta","Tutup" to "Sulje","Pilih Bahasa" to "Valitse kieli"),
        "ar" to mapOf("Pengaturan" to "الإعدادات","Bahasa Aplikasi" to "لغة التطبيق","Notifikasi" to "الإشعارات","Favorit" to "المفضلة","Unduh" to "التنزيلات","Simpan" to "حفظ","Cancel" to "إلغاء","Tutup" to "إغلاق","Pilih Bahasa" to "اختر اللغة"),
        "ro" to mapOf("Pengaturan" to "Setări","Bahasa Aplikasi" to "Limba aplicației","Notifikasi" to "Notificări","Favorit" to "Favorite","Unduh" to "Descărcări","Simpan" to "Salvează","Cancel" to "Anulează","Tutup" to "Închide","Pilih Bahasa" to "Alege limba"),
        "ja" to mapOf("Pengaturan" to "設定","Bahasa Aplikasi" to "アプリの言語","Notifikasi" to "通知","Favorit" to "お気に入り","Unduh" to "ダウンロード","Simpan" to "保存","Cancel" to "キャンセル","Tutup" to "閉じる","Pilih Bahasa" to "言語を選択"),
        "fr" to mapOf("Pengaturan" to "Paramètres","Bahasa Aplikasi" to "Langue de l’application","Notifikasi" to "Notifications","Favorit" to "Favoris","Unduh" to "Téléchargements","Simpan" to "Enregistrer","Cancel" to "Annuler","Tutup" to "Fermer","Pilih Bahasa" to "Choisir la langue"),
        "de" to mapOf("Pengaturan" to "Einstellungen","Bahasa Aplikasi" to "App-Sprache","Notifikasi" to "Benachrichtigungen","Favorit" to "Favoriten","Unduh" to "Downloads","Simpan" to "Speichern","Cancel" to "Abbrechen","Tutup" to "Schließen","Pilih Bahasa" to "Sprache wählen"),
        "no" to mapOf("Pengaturan" to "Innstillinger","Bahasa Aplikasi" to "Appspråk","Notifikasi" to "Varsler","Favorit" to "Favoritter","Unduh" to "Nedlastinger","Simpan" to "Lagre","Cancel" to "Avbryt","Tutup" to "Lukk","Pilih Bahasa" to "Velg språk"),
        "it" to mapOf("Pengaturan" to "Impostazioni","Bahasa Aplikasi" to "Lingua dell'app","Notifikasi" to "Notifiche","Favorit" to "Preferiti","Recently viewed" to "Visualizzati di recente","Unduh" to "Download","Share Aplikasi" to "Condividi app","Nilai kami" to "Valutaci","Umpan Balik" to "Feedback","Manage Cache" to "Gestisci cache","Simpan" to "Salva","Cancel" to "Annulla","Tutup" to "Chiudi","Pilih Bahasa" to "Scegli la lingua")
    )
    private val extraTranslations = mapOf(
        "en" to mapOf("Popular Tags" to "Popular Tags","Filter :" to "Filter:","Paling populer" to "Most popular","Newest" to "Newest","Acak" to "Random","Sort by" to "Sort by","Search wallpaper" to "Search wallpapers","Search" to "Search","Delete" to "Delete","Cancel" to "Cancel","Terapkan" to "Apply","Downloads" to "Downloads","Recently viewed" to "Recently viewed","Up to 30 recently viewed wallpapers" to "Up to 30 recently viewed wallpapers","No wallpaper history yet." to "No wallpaper history yet.","Konten belum tersedia" to "Content unavailable","Try changing the category or search term." to "Try changing the category or search term.","Items will appear here when the catalog provides this content type." to "Items will appear here when the catalog has this content type.","Image unavailable" to "Image unavailable","Pull down to try again" to "Pull down to try again","Preparing the original UHD file…" to "Preparing original UHD file…","Failed to load image. Check your internet connection." to "Failed to load image. Check your internet connection.","Share wallpaper" to "Share wallpaper","Wallpaper tersimpan" to "Wallpaper saved","Tema gelap aktif" to "Dark theme enabled","Tema terang aktif" to "Light theme enabled","Filter: paling populer" to "Filter: most popular","Filter: kategori" to "Filter: categories","Home" to "Home","Live" to "Live","3D" to "3D","Wallpaper" to "Wallpaper","Category" to "Categories","Get All Wallpaper Pro!" to "Get All Wallpaper Pro!"),
        "id" to mapOf("Popular Tags" to "Tag Populer","Filter :" to "Filter:","Paling populer" to "Paling populer","Newest" to "Newest","Acak" to "Acak","Sort by" to "Sort by","Search wallpaper" to "Search wallpaper","Search" to "Search","Delete" to "Delete","Downloads" to "Downloads","Recently viewed" to "Recently viewed","No wallpaper history yet." to "No wallpaper history yet.","Konten belum tersedia" to "Konten belum tersedia","Image unavailable" to "Image unavailable","Tema gelap aktif" to "Tema gelap aktif","Tema terang aktif" to "Tema terang aktif","Home" to "Beranda","Live" to "Live","Wallpaper" to "Wallpaper","Category" to "Kategori"),
        "es" to mapOf("Popular Tags" to "Etiquetas populares","Newest" to "Más recientes","Acak" to "Aleatorio","Sort by" to "Ordenar por","Search wallpaper" to "Buscar fondos de pantalla","Search" to "Buscar","Delete" to "Eliminar","Downloads" to "Descargas","Recently viewed" to "Vistos recientemente","No wallpaper history yet." to "Aún no hay historial.","Konten belum tersedia" to "Contenido no disponible","Image unavailable" to "Imagen no disponible","Home" to "Inicio","Wallpaper" to "Fondos","Category" to "Categorías"),
        "nl" to mapOf("Popular Tags" to "Populaire tags","Newest" to "Nieuwste","Acak" to "Willekeurig","Sort by" to "Sorteren op","Search wallpaper" to "Achtergronden zoeken","Search" to "Zoeken","Delete" to "Verwijderen","Downloads" to "Downloads","Recently viewed" to "Recent bekeken","No wallpaper history yet." to "Nog geen kijkgeschiedenis.","Konten belum tersedia" to "Inhoud niet beschikbaar","Image unavailable" to "Afbeelding niet beschikbaar","Home" to "Startpagina","Wallpaper" to "Achtergronden","Category" to "Categorieën"),
        "pt" to mapOf("Popular Tags" to "Tags populares","Newest" to "Mais recentes","Acak" to "Aleatório","Sort by" to "Ordenar por","Search wallpaper" to "Pesquisar papéis de parede","Search" to "Pesquisar","Delete" to "Excluir","Downloads" to "Downloads","Recently viewed" to "Vistos recentemente","No wallpaper history yet." to "Ainda não há histórico.","Konten belum tersedia" to "Conteúdo indisponível","Image unavailable" to "Imagem indisponível","Home" to "Início","Wallpaper" to "Papéis de parede","Category" to "Categorias"),
        "pt-BR" to mapOf("Popular Tags" to "Tags populares","Newest" to "Mais recentes","Acak" to "Aleatório","Sort by" to "Ordenar por","Search wallpaper" to "Buscar papéis de parede","Search" to "Buscar","Delete" to "Excluir","Downloads" to "Downloads","Recently viewed" to "Vistos recentemente","No wallpaper history yet." to "Ainda não há histórico.","Konten belum tersedia" to "Conteúdo indisponível","Image unavailable" to "Imagem indisponível","Home" to "Início","Wallpaper" to "Papéis de parede","Category" to "Categorias"),
        "zh" to mapOf("Popular Tags" to "热门标签","Newest" to "最新","Acak" to "随机","Sort by" to "排序方式","Search wallpaper" to "搜索壁纸","Search" to "搜索","Delete" to "删除","Downloads" to "下载","Recently viewed" to "最近浏览","No wallpaper history yet." to "暂无浏览记录","Konten belum tersedia" to "暂无内容","Image unavailable" to "图片不可用","Home" to "首页","Wallpaper" to "壁纸","Category" to "分类"),
        "ko" to mapOf("Popular Tags" to "인기 태그","Newest" to "최신순","Acak" to "무작위","Sort by" to "정렬 기준","Search wallpaper" to "배경화면 검색","Search" to "검색","Delete" to "삭제","Downloads" to "다운로드","Recently viewed" to "최근 본 항목","No wallpaper history yet." to "최근 기록이 없습니다.","Konten belum tersedia" to "콘텐츠 없음","Image unavailable" to "이미지 없음","Home" to "홈","Wallpaper" to "배경화면","Category" to "카테고리"),
        "it" to mapOf("Popular Tags" to "Tag popolari","Newest" to "Più recenti","Acak" to "Casuale","Sort by" to "Ordina per","Search wallpaper" to "Cerca sfondi","Search" to "Cerca","Delete" to "Elimina","Downloads" to "Download","Recently viewed" to "Visualizzati di recente","No wallpaper history yet." to "Nessuna cronologia.","Konten belum tersedia" to "Contenuto non disponibile","Image unavailable" to "Immagine non disponibile","Home" to "Home","Wallpaper" to "Sfondi","Category" to "Categorie")
    )
    private fun localized(value: String): String {
        if (adminUiActive) return value
        val extras = extraTranslations[appLanguage] ?: extraTranslations[appLanguage.substringBefore("-")]
        extras?.get(value)?.let { return it }
        val langMap = translations[appLanguage] ?: translations[appLanguage.substringBefore("-")] ?: translations["en"]!!
        return langMap[value] ?: englishFallback(value)
    }
    private fun englishFallback(value: String): String = when (value) {
        "Batal" -> "Cancel"
        "Tutup" -> "Close"
        "Delete" -> "Delete"
        "Cari" -> "Search"
        "Cari wallpaper" -> "Search wallpapers"
        "Urutkan berdasarkan" -> "Sort by"
        "Terbaru" -> "Newest"
        "Acak" -> "Random"
        "Katalog bawaan gagal dibaca" -> "Built-in catalog failed to load"
        "Ukuran gambar melebihi batas" -> "Image size exceeds the limit"
        "File gambar tidak valid" -> "Invalid image file"
        "Banner tidak valid" -> "Invalid banner"

        "Memuat wallpaper…" -> "Loading wallpapers…"
        "Belum ada wallpaper" -> "No wallpapers yet"
        "Paling populer" -> "Most popular"
        "Name A-Z" -> "Name A-Z"
        "Press back again to exit" -> "Press back again to exit"
        "Built-in catalog failed to load" -> "Unable to read the built-in catalog"
        "Katalog online kosong" -> "Online catalog is empty"
        "Simpan" -> "Save"
        "Cancel" -> "Cancel"
        "Tutup" -> "Close"
        else -> value
    }
    private fun showLanguagePicker() {
        val labels = supportedLanguages.map { it.first }.toTypedArray()
        val selected = supportedLanguages.indexOfFirst { it.second == appLanguage }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(localized("Pilih Bahasa"))
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                appLanguage = supportedLanguages[which].second
                preferences.edit().putString("app_language", appLanguage).apply()
                dialog.dismiss()
                buildHome()
            }
            .setNegativeButton(localized("Tutup"), null)
            .show()
    }
    private var darkMode = false
    private val categoryNames: List<String>
        get() = listOf("All") + itemTags.flatten().map { it.trim() }
            .filter { it in wallpaperTagChoices }.distinct().sorted()
    private val wallpaperTagChoices = listOf(
        "Nature", "City", "Cars", "Technology", "Gaming", "Space", "Minimalist",
        "Abstract", "AMOLED", "Dark", "3D", "Anime", "Architecture", "Animals",
        "Girl", "Fantasy", "Superheroes", "Foods", "Luxury", "Aesthetic"
    )
    private val itemCategories = mutableListOf<String>()
    private val itemTypes = mutableListOf<String>()
    private val itemTags = mutableListOf<List<String>>()
    private var remoteCatalogIndex: CatalogManifest.Index? = null
    private var loadedShardCount = 0
    private var shardLoading = false
    private var initialCatalogLoading = true
    private var adsInitialized = false
    private var adsInitializationStarted = false
    private var bannerAdRequested = false
    private var bannerAdView: com.google.android.libraries.ads.mobile.sdk.banner.AdView? = null
    private var adminSelectedImage: Uri? = null
    private var adminImageLabel: TextView? = null
    private var homeScreenActive = false
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var filterSwipeTopY = Float.MAX_VALUE
    private var previewScreenActive = false
    private var previewIndex = 0
    private var backPressedOnce = false
    private val backPressHandler = Handler(Looper.getMainLooper())
    private val resetBackPress = Runnable { backPressedOnce = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= 23) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        favorites.addAll(preferences.getStringSet("favorite_ids", emptySet()) ?: emptySet())
        legacyFavoriteTitles.addAll(preferences.getStringSet("favorite_titles", emptySet()) ?: emptySet())
        darkMode = preferences.getBoolean("dark_mode", false)
        appLanguage = preferences.getString("app_language", "en") ?: "en"
        sortMode = preferences.getString("wallpaper_sort_mode", "Paling populer") ?: "Paling populer"
        downloadSortMode = preferences.getString("download_sort_mode", "Name A-Z") ?: "Name A-Z"
        selectedTab = preferences.getString("selected_wallpaper_tab", "Wallpaper") ?: "Wallpaper"
        if (selectedTab !in tabs && selectedTab != "Home") selectedTab = "Wallpaper"
        selectedCategory = preferences.getString("selected_wallpaper_category", "All") ?: "All"
        showStartupLoadingScreen()
        initializeAds()
        // Start with a clean catalog: only wallpapers added by the owner in Admin are shown.
        loadFirestoreWallpapers {
            initialCatalogLoading = false
            if (homeScreenActive && !isFinishing && !isDestroyed) buildHome()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) buildHome()
        }, 5000L)
    }

    private fun showStartupLoadingScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(24), dp(24), dp(28))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.launcher_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ultra HD Wallpaper"
        }
        root.addView(logo, LinearLayout.LayoutParams(dp(160), dp(160)))
        root.addView(text("Ultra HD Wallpaper", 22, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        })
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(ProgressBar(this).apply { isIndeterminate = true }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { gravity = Gravity.CENTER })
        root.addView(text("Memuat wallpaper…", 14, 0xffcccccc.toInt(), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
        setContentView(root)
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { gestureStartX = event.x; gestureStartY = event.y }
            android.view.MotionEvent.ACTION_UP -> {
                val dx = event.x - gestureStartX
                val dy = event.y - gestureStartY
                val threshold = dp(72).toFloat()
                val horizontalSwipe = kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f
                if (previewScreenActive && horizontalSwipe && names.isNotEmpty()) {
                    val next = (previewIndex + if (dx < 0) 1 else -1 + names.size) % names.size
                    // Keep navigation within the available catalog and refresh the preview after
                    // the current touch has been delivered to its original view.
                    val target = if (dx < 0) (previewIndex + 1) % names.size else (previewIndex - 1 + names.size) % names.size
                    val handled = super.dispatchTouchEvent(event)
                    showPreview(target)
                    return handled
                }
                if (homeScreenActive && horizontalSwipe && gestureStartY >= filterSwipeTopY) {
                    val current = tabs.indexOf(selectedTab).coerceAtLeast(0)
                    val next = (current + if (dx < 0) 1 else -1).coerceIn(0, tabs.lastIndex)
                    if (next != current) {
                        selectedTab = tabs[next]
                        preferences.edit().putString("selected_wallpaper_tab", selectedTab).apply()
                        showOnlyFavorites = false
                        visibleItemLimit = 24
                        buildHome()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val isHomeRoot = homeScreenActive &&
            !previewScreenActive &&
            !showOnlyFavorites &&
            selectedCategory == "All" &&
            searchQuery.isBlank() &&
            selectedTab == "Wallpaper"

        if (!isHomeRoot) {
            // Kembali dari halaman/kategori apa pun selalu menuju beranda utama.
            backPressHandler.removeCallbacks(resetBackPress)
            backPressedOnce = false
            previewScreenActive = false
            showOnlyFavorites = false
            selectedCategory = "All"
            searchQuery = ""
            selectedTab = "Wallpaper"
            preferences.edit()
                .putString("selected_wallpaper_category", selectedCategory)
                .putString("selected_wallpaper_tab", selectedTab)
                .apply()
            visibleItemLimit = 24
            buildHome()
            return
        }

        if (backPressedOnce) {
            backPressHandler.removeCallbacks(resetBackPress)
            backPressedOnce = false
            super.onBackPressed()
            return
        }
        backPressedOnce = true
        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
        backPressHandler.removeCallbacks(resetBackPress)
        backPressHandler.postDelayed(resetBackPress, 2000L)
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
            Toast.makeText(this, "Built-in catalog failed to load", Toast.LENGTH_LONG).show()
        }
    }

    private fun wallpaperDiskCacheFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val key = digest.joinToString("") { byte -> "%02x".format(byte) }
        val directory = File(filesDir, "wallpaper_images").apply { if (!exists()) mkdirs() }
        return File(directory, key + ".img")
    }

    private fun trimWallpaperDiskCache(maxBytes: Long = 512L * 1024L * 1024L) {
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
                    if (contentLength > maxBytes) throw IllegalStateException("Image size exceeds the limit")
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
                                    if (total > maxBytes) throw IllegalStateException("Image size exceeds the limit")
                                    output.write(buffer, 0, count)
                                }
                                output.fd.sync()
                            }
                        }
                        if (!isValidWallpaperFile(temporary)) throw IllegalStateException("Invalid image file")
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
                var failureReason = "unknown"
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
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                            setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                            setRequestProperty("Connection", "keep-alive")
                        }
                        if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
                        android.util.Log.d("WallpaperImage", "HTTP ${connection.responseCode} for $url")
                        val maxBytes = 40L * 1024L * 1024L
                        if (connection.contentLengthLong > maxBytes) throw IllegalStateException("Image size exceeds the limit")
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
                                        if (total > maxBytes) throw IllegalStateException("Image size exceeds the limit")
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
                } catch (error: Exception) {
                    bitmap = null
                    failureReason = error.javaClass.simpleName + ": " + (error.message ?: "no message")
                    android.util.Log.e("WallpaperImage", "Failed to load $url — $failureReason", error)
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

    private val homeBanners = listOf(
        "Anime" to "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEicfF9-wrnVfMAuqu5VV12jVk3WSBye3dXzgdz200gzS_lZLcoUEJZO3MppRD8dPVY7CHqlEYUjDIdfMaZveUAusfb5ukrkqzsCMuubyhGt_Y3S7nU0bmXVerCBlCsCIreIxVwduQ8D3D8sUQ1yM2iZJsDJN8F3Mfjy__VcYaEVpg8BQS4MkW4wjaPubzs/s1652/slide%2001_Anime.png",
        "Alam" to "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEi0utvpF1HPiNHC3bYVra1U9duzhFcsLqExA_AZYxDcE1BL1ymo0I37g1tPKCWla15Yw7-2MBHQlHRRH426fVWgak5MRdGd6okO4b5cP6Teg_ogltu_MMDmbHUUiDjWYSp_qRuX3SSdojy2oO_5cuX6nnspslifevR_pFax8l4vmgfZKjMdi5SDcqA0-iM/s1660/slide%2002_Alam.png",
        "Girls" to "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjc1leve-gjB_v71ScfB0rWb3JGn3iHbrYwWn7WuwNvrp0LbwmR9O02in81wXywWdDDwg6qx4nGhQ7AoM5GtPJfh1bIQSlb-ORXejNyu8MzDKD979zklOsKbpEuCFE_dXyDzm9ZhMDlhJc2rrNREONud39xRYr_rTF8M-T2MCyyleQVFsfxKWwRmdoBuqE/s1652/03_Girls_red_garnet_1652x458(1).png",
        "Game" to "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEiqj70pupE3IMt4r2fD9d9o9HtE8QihXoqDTgDRJOl68jzhFtIO5fl42K0rnuXLKIKb0RU56lKVg9FWfP9VERtd4KyG9BStLVQr0ReSP1K18YphhHB4D4IXagEYCTg5YFiLWYGFEvFUgjwJq_W6uDP19W4mJ0Qk4eDHjwjFmEIt69pGdMxc-FlrPs15tPg/s1660/slide%2004_Game.png",
        "Super Hero" to "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjoXIMV9HIT7BPxBHWM__UcjcWe1eN7UgKvwVBS4ur86qwna7qhYLn3dEhjGsW4vwb8g8KDzm3PpJNH5_zXeRwJ0sRAMb4iDCFBPMH63KNCdnbYqp5ymZJ6T9vMgze2ADMIQt41uqSUNfV4WEzVRuVAdOFTZz81Qy7dxYzD5jpgBvO6sYOZerqYGDU4nbw/s1652/slide%2005_Super_Hero.png",
        "Teknologi" to "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhqecIsGGc_eZpcZliBH83O6TUf2d5Qf1coPPBV0EP2VzueIUTY8iqAbNsf61g25DAJiQ1M9W6JY4gGwmjXBacqjPbYOQyBI41ZYSDrQZGqQQOlJ368VMhcOzuzPxVP95ZtMiqejjDtRk5x_T_1qic70488Cjxz8o86F8E-8PZ96p8_-jQQtkrFwLa117c/s1660/slide%2006_Teknologi.png",
        "3D" to "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEh78ya8FPyT2Kerg5FCEQAHGPl6402RvPM3qMVUWu5KH3DSK1pXPPMcKGbU6VBB4SbhyphenhyphenXbOW8xTpvTlW0o49sL9KM4Gi1PeTk21Z83aub1Bf0FkaYBpQZp_9kRUFeX0ivizht0_37302t18urEm0nx7yY6oIvnr17XRhf_NkyWn2JHOYUr2Vb5tYvb95sY/s1660/slide%2007_3D.png"
    )

    private fun bannerCacheFile(url: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val directory = File(filesDir, "home_banners").apply { if (!exists()) mkdirs() }
        return File(directory, "$key.png")
    }

    private fun loadHomeBanner(url: String, target: ImageView) {
        val file = bannerCacheFile(url)
        target.tag = url
        imageExecutor.execute {
            var bitmap: Bitmap? = null
            try {
                if (!file.isFile || file.length() <= 0L) {
                    var connection: HttpURLConnection? = null
                    try {
                        connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 25000
                            doInput = true
                            instanceFollowRedirects = true
                            requestMethod = "GET"
                        }
                        if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
                        val temporary = File(file.parentFile, file.name + ".download")
                        try {
                            connection.inputStream.use { input ->
                                FileOutputStream(temporary).use { output ->
                                    val buffer = ByteArray(16 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        if (total > 12L * 1024L * 1024L) throw IllegalStateException("Banner terlalu besar")
                                        output.write(buffer, 0, count)
                                    }
                                    output.fd.sync()
                                }
                            }
                            if (BitmapFactory.decodeFile(temporary.absolutePath) == null) throw IllegalStateException("Invalid banner")
                            if (!temporary.renameTo(file)) temporary.copyTo(file, overwrite = true)
                        } finally { if (temporary.exists()) temporary.delete() }
                    } finally { connection?.disconnect() }
                }
                bitmap = BitmapFactory.decodeFile(file.absolutePath)
            } catch (error: Exception) {
                android.util.Log.w("HomeBanner", "Banner failed to load: $url", error)
            }
            val loaded = bitmap
            runOnUiThread {
                if (!isFinishing && !isDestroyed && target.tag == url && loaded != null && !loaded.isRecycled) {
                    target.setImageBitmap(loaded)
                }
            }
        }
    }

    private fun buildHome() {
        adminUiActive = false
        homeScreenActive = true
        previewScreenActive = false
        val pageBg = if (darkMode) 0xff121318.toInt() else Color.WHITE
        val pageText = if (darkMode) 0xfff2f2f5.toInt() else ink
        val panelSurface = if (darkMode) Color.BLACK else Color.WHITE
        val root = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        val header = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(15),dp(6),dp(12),dp(5)); setBackgroundColor(panelSurface) }
        header.addView(text(if (showOnlyFavorites) "Favorit (${favorites.size})" else "Ultra HD\nWallpaper",25,pageText,true).apply {
            if (!showOnlyFavorites) {
                textSize = 16.45f
                gravity = Gravity.CENTER_VERTICAL
                setLineSpacing(0f, 0.9f)
            }
        }, LinearLayout.LayoutParams(0,dp(48),1f))
        if (!showOnlyFavorites) header.addView(animatedDiamond(dp(34)))
        listOf("moon-soft","⌕","♡","gear-filled").forEachIndexed { i,glyph ->
            val icon = modernIcon(glyph, 40)
            icon.contentDescription = listOf("Theme","Search","Favorites","Settings")[i]
            icon.setOnClickListener {
                when(i){0->toggleTheme();1->search();2->{showOnlyFavorites=true;selectedCategory="All";searchQuery="";selectedTab="Wallpaper";visibleItemLimit=24;buildHome()};3->showSettings()}
            }
            header.addView(icon,LinearLayout.LayoutParams(dp(40),dp(40)))
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
            item.addView(text(tab,16,if(tab==selectedTab) blue else pageText,true).apply { gravity=Gravity.CENTER; setPadding(dp(15),dp(12),dp(15),dp(12)) })
            item.addView(View(this@MainActivity).apply { setBackgroundColor(if(tab==selectedTab) blue else Color.TRANSPARENT) },LinearLayout.LayoutParams(-1,dp(3)))
            tabRow.addView(item)
        }
        tabScroll.addView(tabRow); root.addView(tabScroll)
        val scroll=ScrollView(this@MainActivity)
        val content=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(pageBg) }
        if(promoVisible) {
            val promo=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),0,dp(12),0); background=rounded(0xff70434b.toInt(),28) }
            promo.addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.crown_premium); scaleType=ImageView.ScaleType.FIT_CENTER; setPadding(0,0,dp(7),0); contentDescription="Pro" },LinearLayout.LayoutParams(dp(29),dp(36)))
            promo.addView(text("Get All Wallpaper Pro!",17,Color.WHITE,true).apply { gravity = Gravity.CENTER },LinearLayout.LayoutParams(0,dp(36),1f))
            promo.addView(text("×",25,Color.WHITE,false).apply { gravity=Gravity.CENTER; setOnClickListener { promoVisible=false; buildHome() } },LinearLayout.LayoutParams(dp(30),dp(34)))
            val pp=LinearLayout.LayoutParams(-1,dp(38)); pp.setMargins(dp(12),dp(6),dp(12),dp(2)); content.addView(promo,pp)
        }
        val showDiscoverHeader = !showOnlyFavorites && (selectedTab == "Home" || selectedTab == "Wallpaper") && searchQuery.isBlank() && selectedCategory == "All"
        if (showDiscoverHeader) {
            categorySlideRunnable?.let { slideHandler.removeCallbacks(it) }
            val slideScroll = HorizontalScrollView(this@MainActivity).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                clipToPadding = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val slideRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            val slideWidth = resources.displayMetrics.widthPixels
            homeBanners.forEach { (category, url) ->
                val slide = FrameLayout(this@MainActivity).apply {
                    background = rounded(if (darkMode) 0xff0B3D4A.toInt() else 0xffe9e9ed.toInt(), 14)
                    clipToOutline = true
                    setOnClickListener {
                        searchQuery = category
                        selectedCategory = "All"
                        selectedTab = "Wallpaper"
                        showOnlyFavorites = false
                        visibleItemLimit = 24
                        buildHome()
                    }
                }
                val slideImage = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Banner $category"
                    setImageDrawable(rounded(if (darkMode) 0xff0B3D4A.toInt() else 0xffe9e9ed.toInt(), 14))
                }
                slide.addView(slideImage, FrameLayout.LayoutParams(-1, -1))
                slide.addView(text(category, 15, Color.BLACK, true).apply {
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    background = rounded(0x99ffffff.toInt(), 8)
                    gravity = Gravity.CENTER
                }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(8), 0, 0, dp(8)) })
                loadHomeBanner(url, slideImage)
                slideRow.addView(slide, LinearLayout.LayoutParams(slideWidth, dp(105)))
            }
            slideScroll.addView(slideRow)
            content.addView(slideScroll, LinearLayout.LayoutParams(-1, dp(105)))
            var slidePosition = 0
            val autoAdvance = object : Runnable {
                override fun run() {
                    if (isFinishing || isDestroyed || !slideScroll.isAttachedToWindow) return
                    slidePosition = (slidePosition + 1) % homeBanners.size
                    slideScroll.smoothScrollTo(slidePosition * slideWidth, 0)
                    slideHandler.postDelayed(this, 4000L)
                }
            }
            categorySlideRunnable = autoAdvance
            slideHandler.postDelayed(autoAdvance, 4000L)
            val popularPanel = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(3), dp(10), dp(3))
                background = rounded(0xff70434b.toInt(), 10)
            }
            popularPanel.addView(text("Popular Tags", 11, Color.WHITE, true).apply { setPadding(dp(2), 0, 0, dp(2)) })
            val tagScroll = HorizontalScrollView(this@MainActivity).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
            val tagRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val popularTags = listOf(
                "Nature", "City", "Cars", "Technology", "Gaming",
                "Space", "Minimalist", "Abstract", "AMOLED", "Dark",
                "3D", "Anime", "Architecture", "Animals", "Girl",
                "Fantasy", "Superheroes", "Art", "Luxury", "Aesthetic"
            )
            popularTags.forEach { tag ->
                tagRow.addView(text(tag, 10, 0xff70434b.toInt(), true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(3), dp(10), dp(3))
                    background = rounded(0xffffe8ee.toInt(), 16)
                    setOnClickListener { searchQuery = tag; showOnlyFavorites = false; visibleItemLimit = 24; buildHome() }
                }, LinearLayout.LayoutParams(-2, dp(23)).apply { setMargins(dp(2), 0, dp(5), 0) })
            }
            tagScroll.addView(tagRow)
            popularPanel.addView(tagScroll, LinearLayout.LayoutParams(-1, dp(25)))
            content.addView(popularPanel, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(dp(12), dp(1), dp(12), dp(3)) })
        }
        val sort=TextView(this@MainActivity).apply { text=localized("Filter :")+" "+localized(sortMode)+"   ▾"; textSize=13f; setTextColor(if (darkMode) Color.WHITE else Color.BLACK); setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(dp(18),dp(1),dp(12),dp(4)); setOnClickListener {
            val options = arrayOf("Paling populer", "Newest", "A-Z", "Acak").map { localized(it) }.toTypedArray()
            AlertDialog.Builder(this@MainActivity).setTitle("Sort by").setItems(options) { _, w ->
                sortMode = listOf("Paling populer", "Newest", "A-Z", "Acak")[w]
                preferences.edit().putString("wallpaper_sort_mode", sortMode).apply()
                visibleItemLimit = 24
                buildHome()
            }.show()
        }}
        content.addView(sort)
        sort.post {
            val location = IntArray(2)
            sort.getLocationOnScreen(location)
            filterSwipeTopY = location[1].toFloat()
        }
        // Three portrait phone-wallpaper cards per row (9:16 ratio).
        // Fixed three-column portrait grid. Each tile is cropped visually to a 9:16 phone-wallpaper frame.
        val tileWidthPx = ((resources.displayMetrics.widthPixels - dp(16)) / 3).coerceAtLeast(dp(72))
        val tileHeightPx = (tileWidthPx * (16f / 9f)).toInt()
        val grid=GridLayout(this@MainActivity).apply {
            columnCount = 3
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        val filteredIndexes = names.indices.filter { index ->
            val currentTags = itemTags.getOrNull(index).orEmpty()
            val categoryMatch = selectedCategory == "All" || currentTags.any { it.equals(selectedCategory, ignoreCase = true) }
            val searchMatch = searchQuery.isBlank() || names[index].contains(searchQuery, true) || currentTags.any { it.contains(searchQuery, true) }
            val favoriteMatch = !showOnlyFavorites || favorites.contains(itemIds.getOrNull(index) ?: names[index])
            val type = itemTypes.getOrNull(index) ?: "wallpaper"
            val tabMatch = when (selectedTab) {
                "Live" -> type == "live"
                "Parallax" -> type == "parallax"
                "3D" -> itemTags.getOrNull(index).orEmpty().any { it.equals("3D", ignoreCase = true) }
                "Aesthetic" -> itemTags.getOrNull(index).orEmpty().any { it.equals("Aesthetic", ignoreCase = true) }
                // Wallpaper is the complete collection: standard, live, and 3D.
                "Wallpaper" -> type != "live"
                "Home" -> true
                else -> true
            }
            categoryMatch && searchMatch && favoriteMatch && tabMatch
        }
        val visibleIndexes = if (selectedTab == "Home") {
            val now = System.currentTimeMillis()
            val lastShuffle = preferences.getLong("home_shuffle_time", 0L)
            var seed = preferences.getLong("home_shuffle_seed", 0L)
            if (seed == 0L || now - lastShuffle >= 12L * 60L * 60L * 1000L) {
                seed = Random.nextLong()
                preferences.edit().putLong("home_shuffle_seed", seed).putLong("home_shuffle_time", now).apply()
            }
            filteredIndexes.shuffled(Random(seed))
        } else when (sortMode) {
            "Newest" -> filteredIndexes.reversed()
            "A-Z" -> filteredIndexes.sortedBy { names[it].lowercase() }
            "Acak" -> filteredIndexes.shuffled()
            else -> filteredIndexes
        }
        if (visibleIndexes.isEmpty()) {
            if (initialCatalogLoading) {
                val loadingState = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(28), dp(54), dp(28), dp(54))
                    val spinner = ProgressBar(this@MainActivity).apply {
                        isIndeterminate = true
                        if (Build.VERSION.SDK_INT >= 21) indeterminateTintList = android.content.res.ColorStateList.valueOf(pageText)
                    }
                    addView(spinner, LinearLayout.LayoutParams(dp(36), dp(36)).apply { gravity = Gravity.CENTER })
                    addView(text("Loading...", 14, pageText, true).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, dp(12), 0, 0)
                    })
                }
                content.addView(loadingState, LinearLayout.LayoutParams(-1, -2))
            } else {
                val emptyTitle = if (selectedTab == "Wallpaper" || selectedTab == "Home") "Belum ada wallpaper" else "Konten belum tersedia"
                val emptyMessage = if (selectedTab == "Wallpaper" || selectedTab == "Home") "Try changing the category or search term." else "Items will appear here when the catalog provides this content type."
                val emptyState = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(28), dp(54), dp(28), dp(54))
                    addView(text(emptyTitle, 18, pageText, true).apply { gravity = Gravity.CENTER })
                    addView(text(emptyMessage, 13, if (darkMode) 0xffaaaaaa.toInt() else Color.GRAY, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
                }
                content.addView(emptyState, LinearLayout.LayoutParams(-1, -2))
            }
        }
        val liveTilePlayers = mutableListOf<Pair<VideoView, FrameLayout>>()
        visibleIndexes.take(visibleItemLimit).forEach { index ->
            val name = names[index]
            val cell=FrameLayout(this@MainActivity)
            val isLiveTile = itemTypes.getOrNull(index) == "live"
            val art=ImageView(this@MainActivity).apply {
                scaleType=ImageView.ScaleType.CENTER_CROP
                setImageDrawable(artDrawable(index))
                setOnClickListener { showPreview(index) }
            }
            if (isLiveTile) {
                val videoUrl = fullUrls.getOrNull(index).orEmpty()
                val liveVideo = VideoView(this@MainActivity).apply {
                    setBackgroundColor(Color.BLACK)
                    setOnClickListener { showPreview(index) }
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f)
                        tag = true
                        if (isShown) start()
                    }
                    setOnErrorListener { _, _, _ ->
                        tag = "failed"
                        android.util.Log.w("LiveTile", "Unable to play live tile: $name")
                        true
                    }
                }
                cell.addView(liveVideo, FrameLayout.LayoutParams(-1, -1))
                liveTilePlayers.add(liveVideo to cell)
            } else {
                cell.addView(art,FrameLayout.LayoutParams(-1, -1))
            }
            cell.addView(text(if ((itemIds.getOrNull(index) ?: names[index]) in favorites) "♥" else "♡", 15, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = rounded(0xcc000000.toInt(), 18)
                setOnClickListener {
                    val favoriteId = itemIds.getOrNull(index) ?: names[index]
                    if (!favorites.add(favoriteId)) favorites.remove(favoriteId)
                    preferences.edit().putStringSet("favorite_ids", favorites.toSet()).putStringSet("favorite_titles", emptySet()).apply()
                    buildHome()
                }
            }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.START).apply {
                setMargins(dp(7), dp(7), 0, 0)
            })
            if (!isLiveTile && index < thumbnailUrls.size) {
                val tileUrl = thumbnailUrls[index]
                art.tag = tileUrl
                loadImage(tileUrl) { bitmap ->
                    // Cache hits are synchronous; attachment state must not discard them.
                    if (art.tag == tileUrl) {
                        if (bitmap != null && !bitmap.isRecycled) art.setImageBitmap(bitmap)
                        else {
                            art.setImageDrawable(artDrawable(index))
                            art.setBackgroundColor(0xff21151b.toInt())
                            val failed = text("Image unavailable\\nPull down to try again", 10, 0xfff2cbd8.toInt(), true).apply {
                                gravity = Gravity.CENTER
                                setPadding(dp(8), dp(8), dp(8), dp(8))
                                setBackgroundColor(0x66000000)
                                tag = "image-load-failed"
                            }
                            (art.parent as? FrameLayout)?.let { parent ->
                                if (parent.findViewWithTag<View>("image-load-failed") == null) parent.addView(failed, FrameLayout.LayoutParams(-1, -1))
                            }
                        }
                    }
                }
            }
            if(index%5==1) cell.addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.crown_premium); scaleType=ImageView.ScaleType.FIT_CENTER; setPadding(dp(5),dp(4),dp(8),0); contentDescription="Pro" },FrameLayout.LayoutParams(dp(28),dp(28),Gravity.TOP or Gravity.END))
            val params=GridLayout.LayoutParams().apply { width=tileWidthPx; height=tileHeightPx; setMargins(dp(2),dp(2),dp(2),dp(2)) }
            grid.addView(cell,params)
        }
        content.addView(grid)
        fun updateVisibleLiveTiles() {
            liveTilePlayers.forEach { (video, tile) ->
                val visibleNow = tile.isShown && tile.getLocalVisibleRect(android.graphics.Rect())
                try {
                    if (visibleNow) {
                        if (video.tag == null) {
                            video.tag = "loading"
                            val tileIndex = liveTilePlayers.indexOfFirst { it.first === video }
                            val sourceIndex = visibleIndexes.take(visibleItemLimit).filter { itemTypes.getOrNull(it) == "live" }.getOrNull(tileIndex)
                            val sourceUrl = sourceIndex?.let { fullUrls.getOrNull(it) }.orEmpty()
                            if (sourceUrl.isNotBlank()) video.setVideoURI(Uri.parse(sourceUrl))
                        } else if (video.tag == true && !video.isPlaying) {
                            video.start()
                        }
                    } else if (video.isPlaying) {
                        video.pause()
                    }
                } catch (_: Exception) { }
            }
        }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> updateVisibleLiveTiles() }
        scroll.post { updateVisibleLiveTiles() }
        if (visibleIndexes.size > visibleItemLimit || hasMoreCatalogShards()) {
            val hasVisibleRemainder = visibleIndexes.size > visibleItemLimit
            val remaining = (visibleIndexes.size - visibleItemLimit).coerceAtLeast(0)
            val moreButton = Button(this@MainActivity).apply {
                text = if (shardLoading) "Loading catalog page…" else "Next"
                isAllCaps = false
                isEnabled = !shardLoading
                setTextColor(Color.WHITE)
                background = rounded(0xff70434b.toInt(), 28)
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
            setColorSchemeColors(0xff8c5961.toInt(), 0xffb76e79.toInt())
            setOnRefreshListener {
                loadFirestoreWallpapers { isRefreshing = false }
            }
        }
        swipeRefresh.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        root.addView(swipeRefresh, LinearLayout.LayoutParams(-1, 0, 1f))
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

    private fun showPreview(index: Int) {
        if (index !in names.indices) return
        homeScreenActive = false
        previewScreenActive = true
        previewIndex = index
        recordRecentlyViewed(index)

        val root = FrameLayout(this@MainActivity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xff064b3d.toInt(), 0xff16a6bd.toInt(), 0xffd8c39a.toInt())
            )
        }

        fun loadCard(target: ImageView, itemIndex: Int, preferFull: Boolean) {
            val full = fullUrls.getOrNull(itemIndex).orEmpty()
            val thumb = thumbnailUrls.getOrNull(itemIndex).orEmpty()
            val isLiveItem = itemTypes.getOrNull(itemIndex) == "live"
            val first = if (preferFull && !isLiveItem) full else thumb
            val second = if (preferFull && !isLiveItem) thumb else full
            if (first.isNotBlank()) {
                target.tag = first
                loadImage(first) { bitmap ->
                    if (target.tag == first) {
                        if (bitmap != null && !bitmap.isRecycled) target.setImageBitmap(bitmap)
                        else if (second.isNotBlank() && second != first) {
                            android.util.Log.w("WallpaperImage", "Primary preview source failed; trying fallback for ${names.getOrNull(itemIndex)}")
                            target.tag = second
                            loadImage(second) { fallback ->
                                if (target.tag == second) {
                                    if (fallback != null && !fallback.isRecycled) target.setImageBitmap(fallback)
                                    else {
                                        target.setImageDrawable(artDrawable(itemIndex))
                                        android.util.Log.e("WallpaperImage", "Both full and thumbnail sources failed for ${names.getOrNull(itemIndex)}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val previousIndex = (index - 1 + names.size) % names.size
        val nextIndex = (index + 1) % names.size

        val leftPreview = ImageView(this@MainActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(0xff111820.toInt(), 18)
            clipToOutline = true
            alpha = 0.82f
            setImageDrawable(artDrawable(previousIndex))
        }
        root.addView(leftPreview, FrameLayout.LayoutParams(dp(96), dp(430), Gravity.CENTER_VERTICAL or Gravity.START).apply {
            leftMargin = -dp(38)
        })
        loadCard(leftPreview, previousIndex, false)

        val rightPreview = ImageView(this@MainActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(0xff111820.toInt(), 18)
            clipToOutline = true
            alpha = 0.82f
            setImageDrawable(artDrawable(nextIndex))
        }
        root.addView(rightPreview, FrameLayout.LayoutParams(dp(96), dp(430), Gravity.CENTER_VERTICAL or Gravity.END).apply {
            rightMargin = -dp(38)
        })
        loadCard(rightPreview, nextIndex, false)

        val art = ImageView(this@MainActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(0xff14242b.toInt(), 22)
            clipToOutline = true
            setImageDrawable(artDrawable(index))
            contentDescription = names[index]
        }
        root.addView(art, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(dp(55), dp(118), dp(55), dp(112))
        })
        // Full-resolution URL is preferred; thumbnail is a real fallback if it fails.
        loadCard(art, index, true)
        if (itemTypes.getOrNull(index) == "live") {
            val videoPreview = VideoView(this@MainActivity).apply {
                setBackgroundColor(Color.BLACK)
                setVideoURI(Uri.parse(fullUrls.getOrNull(index).orEmpty()))
                setOnPreparedListener { player ->
                    player.isLooping = true
                    player.setVolume(0f, 0f)
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@MainActivity, "Video preview failed. Check the direct MP4 URL.", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            root.addView(videoPreview, FrameLayout.LayoutParams(-1, -1).apply {
                setMargins(dp(55), dp(118), dp(55), dp(112))
            })
        }

        val top = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), 0)
        }
        top.addView(previewCircle("‹") { previewScreenActive = false; buildHome() })
        top.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(animatedDiamond(dp(48)))
        top.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(previewCircle("⚑") {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Report wallpaper")
                .setMessage("Report this wallpaper if the image is inappropriate or misleading.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close", null)
                .show()
        })
        top.addView(previewCircle("ⓘ") {
            AlertDialog.Builder(this@MainActivity)
                .setTitle(names[index])
                .setMessage("Tags: " + itemTags.getOrNull(index).orEmpty().joinToString(", ").ifBlank { "None" } +
                    "\nType: " + (itemTypes.getOrNull(index) ?: "wallpaper"))
                .setPositiveButton("Mengerti", null)
                .show()
        })
        top.addView(previewCircle("↗") { shareWallpaper(index) })
        root.addView(top, FrameLayout.LayoutParams(-1, dp(76), Gravity.TOP))

        val bottom = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(0x44000000)
        }
        val favoriteId = itemIds.getOrNull(index) ?: names[index]
        bottom.addView(previewCircle(if (favoriteId in favorites) "♥" else "♡") {
            if (!favorites.add(favoriteId)) favorites.remove(favoriteId)
            preferences.edit().putStringSet("favorite_ids", favorites.toSet())
                .putStringSet("favorite_titles", emptySet()).apply()
            showPreview(index)
        })
        bottom.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        val apply = Button(this@MainActivity).apply {
            text = "Set Wallpaper"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(0xff0878ef.toInt(), 32)
            setOnClickListener {
                if (itemTypes.getOrNull(index) == "live") applyLiveWallpaper(index)
                else applyWallpaper(index)
            }
        }
        bottom.addView(apply, LinearLayout.LayoutParams(dp(158), dp(48)))
        bottom.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        bottom.addView(previewCircle("⇩") { requestDownload(index) })
        root.addView(bottom, FrameLayout.LayoutParams(-1, dp(78), Gravity.BOTTOM))

        val ad = TextView(this@MainActivity).apply {
            text = "ADVERTISEMENT"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xff292929.toInt())
        }
        root.addView(ad, FrameLayout.LayoutParams(-1, dp(42), Gravity.BOTTOM).apply {
            bottomMargin = dp(78)
        })
        setContentView(root)
    }

    private fun animatedDiamond(sizePx: Int): View {
        val movie = Movie.decodeStream(resources.openRawResource(R.drawable.file_0000000087f082118f963b729b6ee9ed))
        return object : View(this@MainActivity) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val duration = movie?.duration()?.takeIf { it > 0 } ?: 1000
                movie?.let {
                    val scale = minOf(width.toFloat() / it.width(), height.toFloat() / it.height())
                    val save = canvas.save()
                    canvas.translate((width - it.width() * scale) / 2f, (height - it.height() * scale) / 2f)
                    canvas.scale(scale, scale)
                    it.setTime((android.os.SystemClock.uptimeMillis() % duration).toInt())
                    it.draw(canvas, 0f, 0f, paint)
                    canvas.restoreToCount(save)
                    postInvalidateOnAnimation()
                }
            }
        }.apply {
            contentDescription = "Permata biru animasi"
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }
    }

    private fun previewCircle(symbol: String, action: () -> Unit): View {
        val glyph = if (symbol == "↗") "share-arrow" else symbol
        val baseColor = Color.WHITE
        val icon = OutlineIconView(glyph, baseColor)
        val button = FrameLayout(this@MainActivity).apply {
            // Clean floating outline icon: no blurred circle or filled background.
            background = null
            isClickable = true
            isFocusable = true
            contentDescription = symbol
            addView(icon, FrameLayout.LayoutParams(-1, -1))
            setOnClickListener { action() }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> icon.setActive(true)
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> icon.setActive(false)
                }
                false
            }
        }
        return button.also {
            it.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
        }
    }

    private fun applyWallpaper(index: Int) {
        if (index !in fullUrls.indices) {
            Toast.makeText(this, "Image unavailable", Toast.LENGTH_LONG).show()
            return
        }
        val dialog = android.app.Dialog(this@MainActivity)
        val panel = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        val handle = View(this@MainActivity).apply {
            background = rounded(0xffe8e8e8.toInt(), 20)
        }
        panel.addView(handle, LinearLayout.LayoutParams(dp(38), dp(5)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(14)
        })
        panel.addView(TextView(this@MainActivity).apply {
            text = "Set As :"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1, dp(42)))

        val options = listOf(
            Triple("⌂", "Home Screen", WallpaperManager.FLAG_SYSTEM),
            Triple("▣", "Lock Screen", WallpaperManager.FLAG_LOCK),
            Triple("▣", "Home Screen & Lock Screen", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
            Triple("⚙", "System Default", WallpaperManager.FLAG_SYSTEM)
        )
        options.forEachIndexed { optionIndex, option ->
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener {
                    dialog.dismiss()
                    applyWallpaperTarget(index, option.third)
                }
            }
            row.addView(TextView(this@MainActivity).apply {
                text = option.first
                textSize = 27f
                gravity = Gravity.CENTER
                setTextColor(0xff222222.toInt())
            }, LinearLayout.LayoutParams(dp(52), dp(58)))
            row.addView(TextView(this@MainActivity).apply {
                text = option.second
                textSize = 17f
                setTextColor(Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(58), 1f))
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(58)))
            if (optionIndex != options.lastIndex) {
                panel.addView(View(this@MainActivity).apply { setBackgroundColor(0xffeeeeee.toInt()) },
                    LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        dialog.setContentView(panel)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, -2)
            setGravity(Gravity.BOTTOM)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.55f }
        }
        dialog.show()
        dialog.window?.setLayout(-1, -2)
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    /**
     * Membuat wallpaper statis dengan ukuran tepat sebesar layar perangkat.
     * Launcher tidak menerima bidang gambar ekstra di sisi kiri/kanan yang
     * biasanya dipakai untuk efek geser/parallax antar-halaman Home.
     */
    private fun decodeScreenSizedWallpaper(file: File): Bitmap {
        // Standard portrait output: intentionally smaller than common phone
        // display resolutions so WallpaperManager/launcher can scale it as needed.
        // Keep this fixed across devices; the source is center-cropped to 9:20.
        val targetWidth = 900
        val targetHeight = 2000

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Image file could not be read")
        }

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetWidth &&
            bounds.outHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalStateException("Failed to read wallpaper image")

        try {
            val sourceRatio = decoded.width.toFloat() / decoded.height.toFloat()
            val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
            val sourceRect = if (sourceRatio > targetRatio) {
                val cropWidth = (decoded.height * targetRatio).toInt().coerceAtLeast(1)
                val left = ((decoded.width - cropWidth) / 2).coerceAtLeast(0)
                Rect(left, 0, left + cropWidth, decoded.height)
            } else {
                val cropHeight = (decoded.width / targetRatio).toInt().coerceAtLeast(1)
                val top = ((decoded.height - cropHeight) / 2).coerceAtLeast(0)
                Rect(0, top, decoded.width, top + cropHeight)
            }
            val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            Canvas(output).drawBitmap(
                decoded,
                sourceRect,
                Rect(0, 0, targetWidth, targetHeight),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            )
            return output
        } finally {
            decoded.recycle()
        }
    }

    private fun applyWallpaperTarget(index: Int, flags: Int) {
        if (index !in fullUrls.indices) return
        Toast.makeText(this@MainActivity, "Preparing wallpaper for your screen…", Toast.LENGTH_SHORT).show()
        loadWallpaperFile(fullUrls[index]) { file ->
            if (file == null) {
                Toast.makeText(this@MainActivity, "Image failed to load or the file is invalid.", Toast.LENGTH_LONG).show()
                return@loadWallpaperFile
            }
            Thread {
                var bitmap: Bitmap? = null
                try {
                    bitmap = decodeScreenSizedWallpaper(file)
                    val manager = WallpaperManager.getInstance(this@MainActivity)
                    // Use the same null crop hint as the downloaded-wallpaper path.
                    // Passing the full bitmap as an explicit crop hint can trigger a
                    // different crop/offset treatment in wallpaper providers.
                    if (Build.VERSION.SDK_INT >= 24) {
                        manager.setBitmap(bitmap, null, true, flags)
                    } else {
                        manager.setBitmap(bitmap)
                    }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) Toast.makeText(this@MainActivity,
                            "Wallpaper applied successfully", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) Toast.makeText(this@MainActivity,
                            "Failed: " + (e.localizedMessage ?: "error"), Toast.LENGTH_LONG).show()
                    }
                } finally {
                    bitmap?.recycle()
                }
            }.start()
        }
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
        homeScreenActive = false
        val directories = cacheDirectories()
        val sizes = directories.map { (label, directory) -> label to directoryBytes(directory) }
        val total = sizes.sumOf { it.second }
        val details = sizes.joinToString("\\n") { (label, bytes) ->
            "$label: " + formatCacheSize(bytes)
        }
        AlertDialog.Builder(this)
            .setTitle("Manage Cache")
            .setMessage("Image cache details:\\n$details\\n\\nTotal: " + formatCacheSize(total) +
                "\\n\\nThe cache contains temporary copies to speed up image loading. Downloaded files and catalog data will not be deleted.")
            .setPositiveButton("Clear cache") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Confirmation")
                    .setMessage("Clear all image cache (" + formatCacheSize(total) + ")? Wallpapers saved through Downloads will remain safe.")
                    .setPositiveButton("Yes, clear") { _, _ ->
                        var removed = 0L
                        directories.forEach { (_, directory) ->
                            if (directory.exists()) {
                                removed += directoryBytes(directory)
                                directory.deleteRecursively()
                            }
                        }
                        imageCache.evictAll()
                        Toast.makeText(this, "Cache cleared: " + formatCacheSize(removed), Toast.LENGTH_LONG).show()
                        showCacheManager()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun settingsIcon(glyph: String, size: Int): View = modernIcon(glyph, size)

    private fun showSettings() {
        adminUiActive = false
        homeScreenActive = false
        val settingsBg = if (darkMode) 0xff121318.toInt() else Color.WHITE
        val settingsText = if (darkMode) 0xfff2f2f5.toInt() else ink
        val root=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(settingsBg); setPadding(dp(22),dp(22),dp(22),dp(12)) }
        root.addView(text("‹   " + localized("Pengaturan"),22,settingsText,true).apply { setOnClickListener { buildHome() } })
        root.addView(space(28)); root.addView(text(localized("Pengaturan"),24,settingsText,true)); root.addView(space(12))
        listOf(
            "bell-modern" to localized("Notifikasi"),"♡" to localized("Favorit"),"◷" to localized("Recently viewed"),"language-dual" to localized("Bahasa Aplikasi"),
            "⇩" to localized("Unduh"),"share-arrow" to localized("Share Aplikasi"),"☆" to localized("Nilai kami"),"✉" to localized("Umpan Balik"),"cache-modern" to localized("Manage Cache")
        ).forEach { (glyph,item) ->
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12),dp(9),dp(12),dp(9))
                background = rounded(if(darkMode) 0xff24262d.toInt() else 0xfff7f8fc.toInt(),16)
                setOnClickListener {
                    when {
                        glyph == "language-dual" -> showLanguagePicker()
                        glyph == "♡" -> showFavorites()
                        glyph == "◷" -> showRecentlyViewed()
                        glyph == "⇩" -> showDownloads()
                        glyph == "cache-modern" -> showCacheManager()
                        glyph == "share-arrow" -> shareApplication()
                        glyph == "✉" -> sendFeedback()
                        item.contains("Favorit") -> showFavorites()
                        item.contains("Riwayat") -> showRecentlyViewed()
                        item.contains("Unduh") -> showDownloads()
                        item.contains("Manage Cache") -> showCacheManager()
                        item.contains("Share Aplikasi") -> shareApplication()
                        item.contains("Umpan Balik") -> sendFeedback()
                        else -> Toast.makeText(this@MainActivity,item,Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row.addView(settingsIcon(glyph,34),LinearLayout.LayoutParams(dp(42),dp(42)))
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
        adminUiActive = true
        homeScreenActive = false
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
        adminUiActive = true
        homeScreenActive = false
        val bg = if (darkMode) 0xff121318.toInt() else Color.WHITE
        val fg = if (darkMode) 0xfff2f2f5.toInt() else ink
        val muted = if (darkMode) 0xffa6a6ad.toInt() else Color.GRAY
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
        val manageWallpapers = Button(this).apply {
            text = "🪄   Kelola Wallpaper"
            isAllCaps = false
            setOnClickListener { showAdminWallpaperManager() }
        }
        root.addView(manageWallpapers)
        val imageUrl = EditText(this).apply {
            hint = "URL gambar / video MP4 (https://...)"
            setTextColor(fg)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val thumbnailUrl = EditText(this).apply {
            hint = "URL thumbnail gambar (khusus wallpaper biasa)"
            setTextColor(fg)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val wallpaperTypeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("wallpaper", "live"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val live = selectedItem?.toString() == "live"
                    imageUrl.hint = if (live) "URL video MP4 langsung (https://...mp4)" else "URL gambar (https://...)"
                    thumbnailUrl.visibility = if (live) View.GONE else View.VISIBLE
                }
            }
        }
        val tagChoices = listOf(
            "Nature", "City", "Cars", "Technology", "Gaming",
            "Space", "Minimalist", "Abstract", "AMOLED", "Dark",
            "3D", "Anime", "Architecture", "Animals", "Girl",
            "Fantasy", "Superheroes", "Art", "Luxury", "Aesthetic"
        )
        val selectedTags = linkedSetOf<String>()
        val tagGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tagChoices.chunked(2).forEach { pair ->
            val tagRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            pair.forEach { tag ->
                val check = CheckBox(this).apply {
                    text = tag
                    textSize = 14f
                    setTextColor(fg)
                    buttonTintList = android.content.res.ColorStateList.valueOf(if (darkMode) 0xffb76e79.toInt() else blue)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedTags.add(tag) else selectedTags.remove(tag)
                    }
                }
                tagRow.addView(check, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    rightMargin = dp(6)
                    bottomMargin = dp(3)
                })
            }
            if (pair.size == 1) tagRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            tagGrid.addView(tagRow)
        }
        val save = Button(this).apply { text = "Simpan wallpaper" }
        root.addView(title)
        root.addView(text("Pilih tag wallpaper (boleh lebih dari 4)", 14, fg, true).apply {
            setPadding(0, dp(16), 0, dp(5))
        })
        root.addView(tagGrid)
        root.addView(imageUrl)
        root.addView(thumbnailUrl)
        root.addView(text("Jenis konten", 13, muted, false).apply { setPadding(0, dp(10), 0, 0) })
        root.addView(wallpaperTypeSpinner)
        root.addView(save)
        root.addView(text("Pilih beberapa tag dengan mencentang kotaknya. Untuk Live, cukup masukkan URL MP4 langsung. Video akan otomatis bergerak di kartu beranda dan diputar berulang tanpa suara.", 12, muted, false).apply {
            setPadding(0, dp(12), 0, 0)
        })
        save.setOnClickListener {
            val name = title.text.toString().trim()
            val url = imageUrl.text.toString().trim()
            val isLive = wallpaperTypeSpinner.selectedItem.toString() == "live"
            val thumb = if (isLive) url else thumbnailUrl.text.toString().trim().ifBlank { url }
            if (name.isBlank() || selectedTags.isEmpty() || !url.startsWith("https://", ignoreCase = true) ||
                (isLive && !url.substringBefore("?").lowercase().endsWith(".mp4"))) {
                Toast.makeText(this, if (isLive) "Isi judul, pilih minimal satu tag, dan masukkan URL MP4 langsung yang valid" else "Isi judul, pilih minimal satu tag, dan masukkan URL gambar HTTPS yang valid", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            save.isEnabled = false
            save.text = "Menyimpan…"
            val id = "wp-" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val data = hashMapOf(
                "id" to id,
                "title" to name,
                "thumbnail" to thumb,
                "image" to url,
                "type" to wallpaperTypeSpinner.selectedItem.toString(),
                "tags" to selectedTags.toList(),
                "tagSchemaVersion" to 2,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            FirebaseFirestore.getInstance().collection("wallpapers").document(id).set(data)
                .addOnSuccessListener {
                    names.add(name)
                    itemIds.add(id)
                    itemCategories.add("")
                    itemTypes.add(wallpaperTypeSpinner.selectedItem.toString())
                    @Suppress("UNCHECKED_CAST")
                    itemTags.add(data["tags"] as? List<String> ?: emptyList())
                    thumbnailUrls.add(thumb)
                    fullUrls.add(url)
                    title.text.clear()
                    selectedTags.clear()
                    for (rowIndex in 0 until tagGrid.childCount) {
                        val row = tagGrid.getChildAt(rowIndex) as? LinearLayout ?: continue
                        for (childIndex in 0 until row.childCount) {
                            (row.getChildAt(childIndex) as? CheckBox)?.isChecked = false
                        }
                    }
                    imageUrl.text.clear()
                    thumbnailUrl.text.clear()
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

    private fun showAdminWallpaperManager() {
        adminUiActive = true
        val dark = darkMode
        val bg = if (dark) 0xff121318.toInt() else Color.WHITE
        val fg = if (dark) 0xfff2f2f5.toInt() else ink
        val muted = if (dark) 0xffa6a6ad.toInt() else Color.GRAY
        val tags = listOf("Nature", "City", "Cars", "Technology", "Gaming", "Space", "Minimalist", "Abstract", "AMOLED", "Dark", "3D", "Anime", "Architecture", "Animals", "Girl", "Fantasy", "Superheroes", "Foods", "Luxury", "Aesthetic")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(20))
            setBackgroundColor(bg)
        }
        root.addView(text("‹   Kelola Wallpaper", 21, fg, true).apply {
            setPadding(0, 0, 0, dp(14))
            setOnClickListener { showAdminPanel() }
        })
        val status = text("Memuat daftar wallpaper...", 13, muted, false)
        root.addView(status)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        setContentView(ScrollView(this).apply { addView(root) })
        FirebaseFirestore.getInstance().collection("wallpapers").get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val legacyDocs = snapshot.documents.filter { (it.getLong("tagSchemaVersion") ?: 0L) < 2L }
                if (legacyDocs.isNotEmpty()) {
                    status.text = "Membersihkan kategori dan tag lama..."
                    val commits = legacyDocs.chunked(450).map { chunk ->
                        val batch = FirebaseFirestore.getInstance().batch()
                        chunk.forEach { oldDoc ->
                            batch.update(oldDoc.reference, mapOf(
                                "category" to com.google.firebase.firestore.FieldValue.delete(),
                                "tags" to emptyList<String>(),
                                "tagSchemaVersion" to 2
                            ))
                        }
                        batch.commit()
                    }
                    com.google.android.gms.tasks.Tasks.whenAll(commits)
                        .addOnSuccessListener {
                            if (!isFinishing && !isDestroyed) showAdminWallpaperManager()
                        }
                        .addOnFailureListener { error ->
                            status.text = "Gagal membersihkan tag lama: " + (error.localizedMessage ?: "unknown")
                        }
                    return@addOnSuccessListener
                }
                list.removeAllViews()
                status.text = if (snapshot.isEmpty) "Belum ada wallpaper yang diunggah." else "Pilih wallpaper untuk mengedit judul dan tag."
                for (doc in snapshot.documents) {
                    val itemTitle = doc.getString("title").orEmpty()
                    val type = doc.getString("type")?.lowercase() ?: "wallpaper"
                    val imageUrl = doc.getString("thumbnail")?.takeIf { it.startsWith("https://") } ?: doc.getString("image").orEmpty()
                    val itemTags = doc.get("tags") as? List<String> ?: emptyList()
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        background = rounded(if (dark) 0xff20232b.toInt() else 0xfff2f3f6.toInt(), 12)
                        isClickable = true
                        isFocusable = true
                    }
                    val thumb = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(if (dark) 0xff343944.toInt() else 0xffe1e4e9.toInt())
                    }
                    row.addView(thumb, LinearLayout.LayoutParams(dp(66), dp(82)))
                    if (type == "live") {
                        thumb.setImageResource(android.R.drawable.ic_media_play)
                    } else if (imageUrl.startsWith("https://")) {
                        loadImage(imageUrl) { bitmap ->
                            if (bitmap != null && !isFinishing && !isDestroyed) thumb.setImageBitmap(bitmap)
                        }
                    }
                    val details = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), 0, 0, 0)
                    }
                    details.addView(text(itemTitle.ifBlank { "(Tanpa judul)" }, 14, fg, true))
                    details.addView(text(itemTags.joinToString(" • ").ifBlank { "Belum ada tag" }, 11, muted, false).apply { maxLines = 2 })
                    details.addView(text(if (type == "live") "LIVE WALLPAPER" else if (itemTags.contains("3D")) "3D / WALLPAPER" else "WALLPAPER", 10, muted, true))
                    row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    row.setOnClickListener {
                        val editTitle = EditText(this).apply { setText(itemTitle); hint = "Judul wallpaper"; setTextColor(fg) }
                        val selected = itemTags.toMutableSet()
                        val tagGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                        tags.chunked(2).forEach { pair ->
                            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                            pair.forEach { tag ->
                                val check = CheckBox(this).apply {
                                    text = tag
                                    textSize = 13f
                                    setTextColor(fg)
                                    isChecked = selected.contains(tag)
                                    buttonTintList = android.content.res.ColorStateList.valueOf(if (dark) 0xffb76e79.toInt() else blue)
                                    setOnCheckedChangeListener { _, checked -> if (checked) selected.add(tag) else selected.remove(tag) }
                                }
                                line.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
                            }
                            tagGrid.addView(line)
                        }
                        val editor = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(4), 0, dp(4), 0)
                            addView(editTitle)
                            addView(text("Pilih tag (20 pilihan)", 13, fg, true).apply { setPadding(0, dp(12), 0, dp(5)) })
                            addView(tagGrid)
                        }
                        val dialog = AlertDialog.Builder(this).setTitle("Edit wallpaper")
                            .setView(ScrollView(this).apply { addView(editor) })
                            .setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create()
                        dialog.setOnShowListener {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                val newTitle = editTitle.text.toString().trim()
                                if (newTitle.isBlank() || selected.isEmpty()) {
                                    Toast.makeText(this, "Judul dan minimal satu tag wajib diisi", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }
                                val updatedTags = tags.filter { selected.contains(it) }
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                                doc.reference.update(mapOf(
                                    "title" to newTitle,
                                    "tags" to updatedTags,
                                    "category" to com.google.firebase.firestore.FieldValue.delete(),
                                    "tagSchemaVersion" to 2
                                ))
                                    .addOnSuccessListener {
                                        val index = itemIds.indexOf(doc.id)
                                        if (index >= 0) {
                                            names[index] = newTitle
                                            this@MainActivity.itemTags.set(index, updatedTags)
                                            itemCategories[index] = ""
                                        }
                                        Toast.makeText(this, "Judul dan tag berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                        showAdminWallpaperManager()
                                    }.addOnFailureListener { error ->
                                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                        Toast.makeText(this, "Gagal menyimpan: " + (error.localizedMessage ?: "unknown"), Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                        dialog.show()
                    }
                    val params = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                    list.addView(row, params)
                }
            }
            .addOnFailureListener { error -> status.text = "Gagal memuat daftar wallpaper: " + (error.localizedMessage ?: "unknown") }
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
                    if (title.isBlank() || !thumb.startsWith("https://") || !image.startsWith("https://")) continue

                    val type = doc.getString("type")?.lowercase() ?: "wallpaper"
                    // Never surface pre-checkbox metadata; Admin performs the one-time database cleanup.
                    val tags = if ((doc.getLong("tagSchemaVersion") ?: 0L) >= 2L) {
                        (doc.get("tags") as? List<*>)?.mapNotNull { it?.toString()?.trim() }
                            ?.filter { it in wallpaperTagChoices } ?: emptyList()
                    } else emptyList()
                    val existingIndex = itemIds.indexOf(id)
                    if (existingIndex >= 0) {
                        names[existingIndex] = title
                        itemCategories[existingIndex] = ""
                        itemTypes[existingIndex] = type
                        itemTags[existingIndex] = tags
                        thumbnailUrls[existingIndex] = thumb
                        fullUrls[existingIndex] = image
                    } else {
                        names.add(title)
                        itemIds.add(id)
                        itemCategories.add("")
                        itemTypes.add(type)
                        itemTags.add(tags)
                        thumbnailUrls.add(thumb)
                        fullUrls.add(image)
                    }
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
                Toast.makeText(this, "Failed to refresh wallpapers. Check your internet connection.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun shareApplication() {
        val appLink = "https://play.google.com/store/apps/details?id=" + packageName
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Ultra HD Wallpaper")
            putExtra(Intent.EXTRA_TEXT, "Try the Ultra HD Wallpaper app: " + appLink)
        }
        try {
            startActivity(Intent.createChooser(sendIntent, "Share Ultra HD Wallpaper"))
        } catch (e: Exception) {
            Toast.makeText(this, "Sharing is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendFeedback() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_SUBJECT, "Feedback for Ultra HD Wallpaper")
            putExtra(Intent.EXTRA_TEXT, "Hello, I would like to share feedback about the Ultra HD Wallpaper app:")
        }
        try {
            startActivity(Intent.createChooser(sendIntent, "Send Feedback"))
        } catch (e: Exception) {
            Toast.makeText(this, "No email app is available", Toast.LENGTH_LONG).show()
        }
    }

    private var pendingDownloadFile: File? = null
    private var pendingDownloadTitle: String = ""
    private val createImageRequest = 701

    private fun requestDownload(index: Int) {
        val url = fullUrls.getOrNull(index)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Image unavailable", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Preparing the original UHD file…", Toast.LENGTH_SHORT).show()
        loadWallpaperFile(url) { imageFile ->
            if (imageFile == null) {
                Toast.makeText(this, "Failed to load image. Check your internet connection.", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, "Storage unavailable", Toast.LENGTH_LONG).show()
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
                    !saved -> "Failed to save wallpaper"
                    librarySaved -> "Original wallpaper saved to Downloads"
                    else -> "Wallpaper saved, but could not be added to Downloads"
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
        homeScreenActive = false
        val pageBg = if (darkMode) 0xff121318.toInt() else 0xfff5f5f7.toInt()
        val pageText = if (darkMode) 0xfff2f2f5.toInt() else ink
        val root = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(12))
            setBackgroundColor(pageBg)
        }
        root.addView(text("‹   Downloads", 23, pageText, true).apply {
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
                "Name Z-A" -> saved.sortedByDescending { it.substringAfter("\t", "Wallpaper").lowercase() }
                "Largest first" -> saved.sortedByDescending { File(File(filesDir, "downloads"), it.substringBefore("\t")).length() }
                "Oldest" -> saved.sortedBy { File(File(filesDir, "downloads"), it.substringBefore("\t")).lastModified() }
                "Newest" -> saved.sortedByDescending { File(File(filesDir, "downloads"), it.substringBefore("\t")).lastModified() }
                else -> saved.sortedBy { it.substringAfter("\t", "Wallpaper").lowercase() }
            }
        }
        val searchRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val searchBox = EditText(this@MainActivity).apply {
            setSingleLine(true)
            hint = "Search downloaded wallpapers..."
            textSize = 14f
            setTextColor(pageText)
            setHintTextColor(if (darkMode) 0xff9296a2.toInt() else 0xff888888.toInt())
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(if (darkMode) 0xff24262d.toInt() else Color.WHITE, 12)
            setText(downloadsSearchQuery)
            setOnEditorActionListener { _, _, _ -> downloadsSearchQuery = text.toString(); showDownloads(); true }
        }
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, -2, 1f))
        searchRow.addView(text("Search", 14, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(blue, 12)
            setOnClickListener { downloadsSearchQuery = searchBox.text.toString(); showDownloads() }
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        root.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        if (downloadsSearchQuery.isNotBlank()) {
            root.addView(text("Clear search  x", 12, if (darkMode) 0xffaebeff.toInt() else blue, true).apply {
                gravity = Gravity.END
                setPadding(dp(8), dp(7), dp(4), dp(10))
                setOnClickListener { downloadsSearchQuery = ""; showDownloads() }
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            root.addView(space(8))
        }
        root.addView(text("Sort: $downloadSortMode  ▾", 14, if (darkMode) 0xffc5c9d4.toInt() else 0xff555966.toInt(), true).apply {
            setPadding(0, 0, 0, dp(14))
            setOnClickListener {
                val modes = arrayOf("Newest", "Oldest", "Name A-Z", "Name Z-A", "Largest first")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Sort downloads")
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
            root.addView(text(if (query.isBlank()) "Delete all downloads" else "Delete search results (" + records.size + ")", 14, if (darkMode) 0xffff8b8b.toInt() else 0xffc62828.toInt(), true).apply {
                gravity = Gravity.END
                setPadding(0, 0, 0, dp(12))
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete all downloads?")
                        .setMessage("All ${records.size} saved wallpapers will be deleted from Downloads.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete all") { _, _ ->
                            val updated = (preferences.getStringSet("download_records", emptySet()) ?: emptySet()).toMutableSet()
                            records.forEach { record ->
                                val file = File(File(filesDir, "downloads"), record.substringBefore("\t"))
                                if (file.exists() && !file.delete()) {
                                    Toast.makeText(this@MainActivity, "Some files could not be deleted", Toast.LENGTH_LONG).show()
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
            bar.addView(text(if (downloadSelectionMode) selectedDownloadRecords.size.toString() + " selected" else "Select multiple", 13, pageText, true), LinearLayout.LayoutParams(0, -2, 1f))
            bar.addView(text("Select all", 12, blue, true).apply { setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { downloadSelectionMode = true; selectedDownloadRecords.addAll(records); showDownloads() } })
            bar.addView(text("Cancel", 12, pageText, true).apply { setPadding(dp(8), dp(8), dp(4), dp(8)); setOnClickListener { selectedDownloadRecords.clear(); downloadSelectionMode = false; showDownloads() } })
            root.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            if (downloadSelectionMode && selectedDownloadRecords.isNotEmpty()) {
                root.addView(text("Delete selected", 14, 0xffc62828.toInt(), true).apply { gravity = Gravity.END; setPadding(0, dp(5), 0, dp(10)); setOnClickListener {
                    val targets = selectedDownloadRecords.toSet()
                    AlertDialog.Builder(this@MainActivity).setTitle("Delete selected wallpapers?").setMessage(targets.size.toString() + " files will be deleted.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
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
            "${records.size} wallpapers saved  •  $downloadSize used"
        } else {
            "${records.size} of ${allRecords.size} matching wallpapers  •  $downloadSize shown"
        }
        root.addView(text(summary, 13, if (darkMode) 0xffb9bdc8.toInt() else 0xff666a75.toInt(), false)
            .apply { setPadding(0, 0, 0, dp(14)) })
        if (records.isEmpty()) {
            val noDownloads = allRecords.isEmpty()
            val emptyColumn = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(30), dp(24), dp(30))
                addView(text(if (noDownloads) "No wallpapers downloaded yet" else "No wallpapers found", 17, pageText, true).apply { gravity = Gravity.CENTER })
                addView(text(if (noDownloads) "Download wallpapers first. Saved files will appear here." else "Try another keyword or clear the search to view all downloads.", 13, if (darkMode) 0xffaeb3bf.toInt() else 0xff777b86.toInt(), false).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(9), 0, dp(18))
                })
                if (!noDownloads) addView(text("Clear search", 14, if (darkMode) 0xff9bbcff.toInt() else blue, true).apply {
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
                            startActivity(Intent.createChooser(intent, "Share wallpaper"))
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "Unable to share wallpaper", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                row.addView(share, LinearLayout.LayoutParams(dp(42), dp(48)))
                val delete = text("✕", 21, if (darkMode) 0xffff8b8b.toInt() else 0xffc62828.toInt(), true).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Delete download?")
                            .setMessage("Wallpaper \"$title\" will be deleted from Downloads.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete") { _, _ ->
                                if (imageFile.exists() && !imageFile.delete()) {
                                    Toast.makeText(this@MainActivity, "Unable to delete file. Try again.", Toast.LENGTH_LONG).show()
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
                        .setPositiveButton("Close", null).show()
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
        val format = file.extension.uppercase().ifBlank { "Unknown" }
        val date = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
        val message = "Name: " + title + "\\nResolution: " + options.outWidth + " x " + options.outHeight + "\\nFormat: " + format + "\\nSize: " + size + "\\nDate: " + date
        AlertDialog.Builder(this@MainActivity).setTitle("Wallpaper information").setMessage(message).setPositiveButton("Close", null).show()
    }

    private fun showDownloadedApplyOptions(file: File, title: String) {
        val choices = arrayOf("Home screen", "Lock screen", "Both")
        AlertDialog.Builder(this@MainActivity).setTitle("Apply " + title).setItems(choices) { _, which ->
            if (Build.VERSION.SDK_INT < 24 && which != 0) { Toast.makeText(this@MainActivity, "This option requires Android 7.0 or later", Toast.LENGTH_LONG).show(); return@setItems }
            val flags = when (which) { 1 -> WallpaperManager.FLAG_LOCK; 2 -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK; else -> WallpaperManager.FLAG_SYSTEM }
            Thread {
                var bitmap: Bitmap? = null
                try {
                    bitmap = decodeScreenSizedWallpaper(file)
                    val manager = WallpaperManager.getInstance(this@MainActivity)
                    if (Build.VERSION.SDK_INT >= 24) {
                        manager.setBitmap(bitmap, null, true, flags)
                    } else {
                        manager.setBitmap(bitmap)
                    }
                    runOnUiThread { Toast.makeText(this@MainActivity, "Wallpaper applied successfully", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Failed to apply wallpaper: " + (e.localizedMessage ?: "error"), Toast.LENGTH_LONG).show() }
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
        homeScreenActive = false
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
        header.addView(text("Recently viewed", 22, if (darkMode) 0xfff2f2f5.toInt() else ink, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("Delete", 14, blue, true).apply {
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Clear history?")
                    .setMessage("Your recently viewed wallpaper history will be cleared.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        preferences.edit().remove("recent_wallpaper_ids").apply()
                        showRecentlyViewed()
                    }.show()
            }
        })
        root.addView(header)
        root.addView(text("Up to 30 recently viewed wallpapers", 13,
            if (darkMode) 0xffaaaaaa.toInt() else Color.GRAY, false).apply {
            setPadding(dp(4), dp(4), 0, dp(14))
        })
        if (indexes.isEmpty()) {
            root.addView(text("No wallpaper history yet.", 15,
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
                    thumbnail.tag = url
                    loadImage(url) { bitmap ->
                        if (thumbnail.tag == url && bitmap != null && !bitmap.isRecycled) thumbnail.setImageBitmap(bitmap)
                    }
                }
            }
            scroll.addView(list)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
    }

    private fun showFavorites(){
        homeScreenActive = false
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
        homeScreenActive = false
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
        header.addView(text("Tags", 23, if(darkMode) 0xfff2f2f5.toInt() else ink, true))
        root.addView(header)
        root.addView(text("Explore wallpapers by tag", 14, if(darkMode) 0xffaaaaaa.toInt() else Color.GRAY, false).apply {
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
            val count = if (category == "All") names.size else itemTags.count { tags -> tags.any { it.equals(category, ignoreCase = true) } }
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
        homeScreenActive = false
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
        header.addView(text("♡ Saved", 13, blue, true).apply {
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
                    Toast.makeText(this@MainActivity, "Quote removed from saved items", Toast.LENGTH_SHORT).show()
                } else {
                    saved.add(0, current)
                    persistSavedQuotes(saved)
                    Toast.makeText(this@MainActivity, "Quote saved successfully", Toast.LENGTH_SHORT).show()
                }
                updateQuote()
            }
        }
        updateQuote = {
            val current = quotes[currentIndex]
            quoteText.text = current.first
            categoryText.text = current.second.uppercase()
            saveButton.text = if (savedQuotes().any { it.first == current.first }) "♥ Remove from saved" else "♡ Save quote"
        }
        root.addView(saveButton, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(Button(this).apply {
            text = "Next quote"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(blue, 28)
            setOnClickListener { currentIndex = (currentIndex + 1) % quotes.size; updateQuote() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        root.addView(Button(this).apply {
            text = "Share quote"
            isAllCaps = false
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, quotes[currentIndex].first + "\n\n— Ultra HD Wallpaper")
                startActivity(Intent.createChooser(send, "Share quote"))
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        updateQuote()
        setContentView(root)
    }

    private fun showSavedQuotes() {
        homeScreenActive = false
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
        header.addView(text("Saved quotes", 21, foreground, true))
        root.addView(header)
        root.addView(text("${saved.size} quotes saved on this device", 13, muted, false).apply {
            setPadding(dp(4), dp(4), 0, dp(14))
        })
        if (saved.isEmpty()) {
            root.addView(text("No saved quotes yet.", 15, muted, false).apply {
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
                actions.addView(text("Share", 13, blue, true).apply {
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setOnClickListener {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, quote + "\n\n— Ultra HD Wallpaper")
                        startActivity(Intent.createChooser(send, "Share quote"))
                    }
                })
                actions.addView(text("Delete", 13, Color.RED, true).apply {
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
    private fun search(){ val input=EditText(this@MainActivity).apply { hint=localized("Search wallpaper"); setText(searchQuery) }; AlertDialog.Builder(this@MainActivity).setTitle(localized("Search")).setView(input).setPositiveButton(localized("Search")){_,_-> searchQuery=input.text.toString().trim(); showOnlyFavorites=false; visibleItemLimit=60; buildHome() }.setNeutralButton(localized("Delete")){_,_-> searchQuery=""; visibleItemLimit=60; buildHome() }.setNegativeButton(localized("Cancel"),null).show() }
    private fun shareWallpaper(index: Int) {
        val url = fullUrls.getOrNull(index)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Image unavailable", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Preparing the original file for sharing…", Toast.LENGTH_SHORT).show()
        loadWallpaperFile(url) { imageFile ->
            if (imageFile == null) {
                Toast.makeText(this, "Image failed to load. Check your internet connection.", Toast.LENGTH_LONG).show()
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
                startActivity(Intent.createChooser(sendIntent, "Share wallpaper"))
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
    // Consistent outline icon treatment used throughout the app.
    private fun modernIcon(glyph: String, size: Int): FrameLayout {
        val iconColor = if (darkMode) Color.WHITE else ink
        val icon = FrameLayout(this@MainActivity).apply {
            background = null
            isClickable = true
            isFocusable = true
            clipToOutline = false
            contentDescription = glyph
        }
        val art = OutlineIconView(glyph, iconColor).apply {
            contentDescription = glyph
            isFocusable = false
        }
        icon.addView(art, FrameLayout.LayoutParams(-1, -1))
        icon.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> art.setActive(true)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> art.setActive(false)
            }
            false
        }
        icon.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        return icon
    }

    private inner class OutlineIconView(
        private val glyph: String,
        private val iconColor: Int
    ) : View(this@MainActivity) {
        private var active = false
        fun setActive(value: Boolean) { active = value; paint.color = if (active) 0xffffc928.toInt() else iconColor; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scale = minOf(width, height) * 0.72f
            val left = (width - scale) / 2f
            val top = (height - scale) / 2f
            canvas.save()
            canvas.translate(left, top)
            canvas.scale(scale / 48f, scale / 48f)
            val p = paint
            when (glyph) {
                "moon-modern" -> { canvas.drawArc(8f,6f,40f,42f,55f,250f,false,p); canvas.drawArc(17f,6f,43f,36f,45f,250f,false,p) }
                "moon-soft" -> {
                    val oldStyle = p.style
                    p.style = Paint.Style.FILL
                    val moon = Path().apply {
                        moveTo(34f, 7f)
                        cubicTo(19f, 7f, 10f, 18f, 11f, 29f)
                        cubicTo(12f, 40f, 23f, 46f, 34f, 40f)
                        cubicTo(26f, 41f, 19f, 35f, 18f, 27f)
                        cubicTo(17f, 18f, 23f, 10f, 34f, 7f)
                        close()
                    }
                    canvas.drawPath(moon, p)
                    p.style = oldStyle
                }
                "moon-filled" -> {
                    val oldStyle = p.style
                    p.style = Paint.Style.FILL
                    val moon = Path().apply {
                        moveTo(34f, 5f)
                        cubicTo(17f, 6f, 7f, 19f, 9f, 31f)
                        cubicTo(11f, 44f, 25f, 48f, 37f, 39f)
                        cubicTo(25f, 41f, 16f, 32f, 17f, 21f)
                        cubicTo(18f, 13f, 24f, 7f, 34f, 5f)
                        close()
                    }
                    canvas.save(); canvas.scale(0.86f, 0.86f, 24f, 24f); canvas.drawPath(moon, p); canvas.restore()
                    p.style = oldStyle
                }
                "gear-filled" -> {
                    val oldStyle = p.style
                    p.style = Paint.Style.FILL
                    val gear = Path()
                    for (i in 0 until 32) {
                        val angle = Math.toRadians(i * 360.0 / 32.0 - 90.0)
                        val radius = if (i % 4 == 0 || i % 4 == 1) 22f else 17f
                        val x = 24f + kotlin.math.cos(angle).toFloat() * radius
                        val y = 24f + kotlin.math.sin(angle).toFloat() * radius
                        if (i == 0) gear.moveTo(x, y) else gear.lineTo(x, y)
                    }
                    gear.close(); canvas.drawPath(gear, p)
                    p.color = if (darkMode) 0xff121318.toInt() else Color.WHITE
                    canvas.drawCircle(24f, 24f, 7f, p)
                    p.color = if (active) 0xff36a3ff.toInt() else iconColor
                    p.style = oldStyle
                }
                "bell-modern" -> { val path=Path().apply { moveTo(10f,35f); cubicTo(15f,30f,13f,22f,15f,15f); cubicTo(19f,5f,29f,5f,33f,15f); cubicTo(35f,22f,33f,30f,38f,35f); close() }; canvas.drawPath(path,p); canvas.drawLine(10f,35f,38f,35f,p); canvas.drawArc(19f,34f,29f,43f,0f,180f,false,p) }
                "language-modern" -> { canvas.drawCircle(24f,24f,19f,p); canvas.drawOval(15f,5f,33f,43f,p); canvas.drawLine(6f,24f,42f,24f,p); canvas.drawLine(10f,14f,38f,14f,p); canvas.drawLine(10f,34f,38f,34f,p) }
                "language-dual" -> {
                    val oldStyle = p.style
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.6f
                    val back = Path().apply { moveTo(19f,8f); lineTo(39f,8f); quadTo(43f,8f,43f,12f); lineTo(43f,36f); quadTo(43f,40f,39f,40f); lineTo(29f,40f) }
                    val front = Path().apply { moveTo(8f,5f); lineTo(25f,5f); quadTo(29f,5f,29f,9f); lineTo(29f,34f); quadTo(29f,38f,25f,38f); lineTo(8f,38f); quadTo(4f,38f,4f,34f); lineTo(4f,9f); quadTo(4f,5f,8f,5f); close() }
                    canvas.drawPath(back,p); canvas.drawPath(front,p)
                    p.style = Paint.Style.FILL
                    p.textSize = 21f; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); canvas.drawText("G",7f,29f,p)
                    p.textSize = 15f; canvas.drawText("文",20f,32f,p)
                    p.style = oldStyle
                }
                "share-curved" -> { val path=Path().apply { moveTo(12f,31f); cubicTo(12f,14f,26f,8f,36f,16f); moveTo(27f,8f); lineTo(36f,16f); lineTo(27f,21f) }; canvas.drawPath(path,p) }
                "share-arrow" -> {
                    val arrow = Path().apply {
                        moveTo(10f,40f); cubicTo(10f,25f,15f,17f,27f,15f)
                        lineTo(27f,7f); cubicTo(27f,5f,29f,4f,31f,6f)
                        lineTo(44f,19f); cubicTo(46f,21f,46f,23f,44f,25f)
                        lineTo(31f,38f); cubicTo(29f,40f,27f,39f,27f,36f)
                        lineTo(27f,29f); cubicTo(19f,29f,15f,33f,10f,40f); close()
                    }
                    val oldStyle=p.style; p.style=Paint.Style.STROKE; p.strokeWidth=3.4f
                    canvas.drawPath(arrow,p); p.style=oldStyle
                }
                "⚑" -> { canvas.drawLine(12f,5f,12f,43f,p); val flag=Path().apply { moveTo(13f,7f); lineTo(37f,12f); lineTo(29f,22f); lineTo(13f,19f); close() }; canvas.drawPath(flag,p) }
                "ⓘ" -> { canvas.drawCircle(24f,24f,19f,p); canvas.drawCircle(24f,14f,1.5f,p); canvas.drawLine(24f,21f,24f,34f,p) }
                "cache-modern" -> { canvas.drawRoundRect(7f,8f,41f,40f,5f,5f,p); canvas.drawLine(7f,17f,41f,17f,p); canvas.drawLine(7f,31f,41f,31f,p); canvas.drawLine(17f,17f,17f,31f,p); canvas.drawLine(31f,17f,31f,31f,p) }
                "⌕", "🔍" -> {
                    // Clean magnifier outline, matching the supplied header reference.
                    canvas.drawCircle(20f, 20f, 13f, p)
                    canvas.drawLine(29.5f, 29.5f, 43f, 43f, p)
                }
                "☾" -> {
                    // Crescent moon drawn as a smooth vector path (not a font glyph).
                    val moon = Path().apply {
                        moveTo(31f, 5f)
                        cubicTo(16f, 7f, 7f, 19f, 9f, 31f)
                        cubicTo(11f, 43f, 24f, 47f, 35f, 40f)
                        cubicTo(22f, 42f, 14f, 32f, 16f, 21f)
                        cubicTo(17f, 14f, 23f, 8f, 31f, 5f)
                        close()
                    }
                    canvas.drawPath(moon, p)
                }
                "⚙" -> {
                    // Eight-tooth outlined gear with a hollow center.
                    for (i in 0..7) {
                        canvas.save()
                        canvas.rotate(i * 45f, 24f, 24f)
                        canvas.drawRoundRect(20f, 3f, 28f, 14f, 2f, 2f, p)
                        canvas.restore()
                    }
                    canvas.drawCircle(24f, 24f, 16f, p)
                    canvas.drawCircle(24f, 24f, 6f, p)
                }
                "♡", "♥" -> {
                    val path = Path().apply { moveTo(24f, 41f); cubicTo(19f,36f,5f,27f,5f,16f); cubicTo(5f,5f,19f,3f,24f,13f); cubicTo(29f,3f,43f,5f,43f,16f); cubicTo(43f,27f,29f,36f,24f,41f); close() }
                    val oldStyle = p.style
                    if (glyph == "♥") { p.style = Paint.Style.FILL; p.color = 0xffe53935.toInt() } else p.style = Paint.Style.STROKE
                    canvas.drawPath(path,p)
                    p.style = oldStyle
                    if (glyph == "♥") p.color = if (active) 0xff36a3ff.toInt() else iconColor
                }
                "♟" -> {
                    canvas.drawCircle(24f,12f,6f,p)
                    canvas.drawPath(Path().apply { moveTo(15f,22f); lineTo(33f,22f); lineTo(37f,38f); lineTo(11f,38f); close() },p)
                    canvas.drawLine(9f,42f,39f,42f,p)
                }
                "◷" -> {
                    canvas.drawCircle(24f,24f,18f,p)
                    canvas.drawLine(24f,24f,24f,12f,p)
                    canvas.drawLine(24f,24f,34f,29f,p)
                }
                "文" -> {
                    canvas.drawCircle(24f,24f,19f,p)
                    canvas.drawLine(8f,24f,40f,24f,p)
                    canvas.drawOval(17f,5f,31f,43f,p)
                    canvas.drawLine(12f,14f,36f,14f,p)
                    canvas.drawLine(12f,34f,36f,34f,p)
                }
                "▧" -> {
                    canvas.drawRoundRect(6f,7f,42f,41f,4f,4f,p)
                    canvas.drawLine(6f,17f,42f,17f,p)
                    canvas.drawLine(6f,31f,42f,31f,p)
                    canvas.drawLine(18f,7f,18f,41f,p)
                    canvas.drawLine(30f,7f,30f,41f,p)
                    canvas.drawLine(12f,12f,36f,36f,p)
                }
                "⇩", "⬇" -> { canvas.drawLine(24f,5f,24f,33f,p); canvas.drawLine(13f,23f,24f,34f,p); canvas.drawLine(35f,23f,24f,34f,p); canvas.drawRoundRect(7f,38f,41f,44f,3f,3f,p) }
                "▤" -> {
                    canvas.drawRoundRect(6f,5f,42f,43f,4f,4f,p)
                    canvas.drawLine(13f,15f,35f,15f,p)
                    canvas.drawLine(13f,24f,35f,24f,p)
                    canvas.drawLine(13f,33f,35f,33f,p)
                }
                "↗" -> { canvas.drawLine(9f,39f,39f,9f,p); canvas.drawLine(20f,9f,39f,9f,p); canvas.drawLine(39f,9f,39f,28f,p) }
                "☆" -> {
                    val star=Path().apply { for(i in 0..9){ val a=Math.toRadians(-90.0+i*36.0); val r=if(i%2==0)20.0 else 9.0; val x=(24+ r*kotlin.math.cos(a)).toFloat(); val y=(24+r*kotlin.math.sin(a)).toFloat(); if(i==0) moveTo(x,y) else lineTo(x,y) }; close() }
                    canvas.drawPath(star,p)
                }
                "✉" -> {
                    canvas.drawRoundRect(5f,10f,43f,38f,4f,4f,p)
                    canvas.drawLine(6f,12f,24f,27f,p)
                    canvas.drawLine(42f,12f,24f,27f,p)
                }
                "▣" -> {
                    canvas.drawRoundRect(5f,5f,43f,43f,5f,5f,p)
                    canvas.drawRoundRect(12f,12f,36f,36f,3f,3f,p)
                    canvas.drawLine(17f,24f,31f,24f,p)
                }
                "‹", "›" -> { val path=Path().apply { if(glyph=="‹"){moveTo(30f,8f);lineTo(14f,24f);lineTo(30f,40f)}else{moveTo(18f,8f);lineTo(34f,24f);lineTo(18f,40f)} }; canvas.drawPath(path,p) }
                "⌂" -> { val path=Path().apply{moveTo(4f,22f);lineTo(24f,5f);lineTo(44f,22f);moveTo(10f,19f);lineTo(10f,43f);lineTo(38f,43f);lineTo(38f,19f);moveTo(20f,43f);lineTo(20f,29f);lineTo(28f,29f);lineTo(28f,43f)};canvas.drawPath(path,p) }
                "▦", "▧", "▤" -> { canvas.drawRoundRect(5f,5f,43f,43f,4f,4f,p); canvas.drawLine(5f,19f,43f,19f,p); canvas.drawLine(5f,31f,43f,31f,p); if(glyph=="▦"){canvas.drawLine(19f,5f,19f,43f,p);canvas.drawLine(31f,5f,31f,43f,p)} }
                "✦", "✧", "💎" -> { val path=Path().apply{moveTo(24f,4f);lineTo(29f,18f);lineTo(44f,24f);lineTo(29f,29f);lineTo(24f,44f);lineTo(19f,29f);lineTo(4f,24f);lineTo(19f,18f);close()};canvas.drawPath(path,p) }
                "●" -> canvas.drawCircle(24f,24f,15f,p)
                "▶" -> canvas.drawPath(Path().apply{moveTo(15f,7f);lineTo(39f,24f);lineTo(15f,41f);close()},p)
                "◈" -> canvas.drawPath(Path().apply{moveTo(24f,4f);lineTo(43f,24f);lineTo(24f,44f);lineTo(5f,24f);close();moveTo(24f,13f);lineTo(33f,24f);lineTo(24f,35f);lineTo(15f,24f);close()},p)
                "≈", "⌁" -> { canvas.drawArc(5f,10f,43f,30f,0f,180f,false,p);canvas.drawArc(5f,22f,43f,42f,0f,180f,false,p) }
                else -> {
                    // Generic outline tile for less common symbols, avoiding platform-dependent glyph fonts.
                    canvas.drawRoundRect(5f,5f,43f,43f,8f,8f,p)
                    canvas.drawLine(15f,24f,33f,24f,p)
                    canvas.drawLine(24f,15f,24f,33f,p)
                }
            }
            if (active) {
                val sparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xffffc928.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2.2f
                    strokeCap = Paint.Cap.ROUND
                }
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val inner = 22f
                    val outer = if (i % 2 == 0) 29f else 26f
                    canvas.drawLine(
                        24f + kotlin.math.cos(angle).toFloat() * inner,
                        24f + kotlin.math.sin(angle).toFloat() * inner,
                        24f + kotlin.math.cos(angle).toFloat() * outer,
                        24f + kotlin.math.sin(angle).toFloat() * outer,
                        sparkle
                    )
                }
                canvas.drawCircle(8f, 12f, 1.8f, sparkle)
                canvas.drawCircle(40f, 11f, 1.8f, sparkle)
                canvas.drawCircle(10f, 39f, 1.8f, sparkle)
                canvas.drawCircle(39f, 38f, 1.8f, sparkle)
            }
            canvas.restore()
        }
    }

    private fun circle(symbol:String, action:()->Unit):View = modernIcon(symbol,40).apply {
        setOnClickListener { action() }
    }.also { it.layoutParams=LinearLayout.LayoutParams(dp(40),dp(40)).apply { setMargins(dp(3),0,dp(3),0) } }
    private fun artDrawable(index:Int):GradientDrawable { val a=palettes[index%palettes.size]; return GradientDrawable(GradientDrawable.Orientation.TL_BR,a).apply { cornerRadius=dp(2).toFloat() } }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun text(s:String,size:Int,color:Int,bold:Boolean)=TextView(this@MainActivity).apply { text=if (adminUiActive) s else localized(s); textSize=size.toFloat(); setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD }
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
