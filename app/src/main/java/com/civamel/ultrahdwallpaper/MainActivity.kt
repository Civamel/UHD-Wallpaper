package com.civamel.ultrahdwallpaper

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Typeface

class MainActivity : Activity() {
    private val blue = Color.rgb(0, 119, 255)
    private val purple = Color.rgb(158, 67, 238)
    private val ink = Color.rgb(28, 28, 30)
    private val tabs = listOf("Home", "Live", "Parallax", "Wallpaper", "Double", "Charging Live")
    private val names = listOf("Neon Flow", "Color Splash", "Red Horizon", "White Wings", "Ocean Glass", "Dark Bubbles",
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
    private var selectedTab = "Wallpaper"
    private var promoVisible = true
    private val favorites = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= 23) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildHome()
    }

    private fun buildHome() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(15),dp(12),dp(12),dp(10)) }
        header.addView(text("4k Wallpaper",25,ink,true), LinearLayout.LayoutParams(0,dp(54),1f))
        listOf("💎","☾","⌕","⚙").forEachIndexed { i,s ->
            val v=text(s, i==0 && s=="💎" ? 25 : 32,ink,false).apply { gravity=Gravity.CENTER; setOnClickListener {
                when(i){0->Toast.makeText(this@MainActivity,"Wallpaper Premium",Toast.LENGTH_SHORT).show();1->toggleTheme();2->search();3->showSettings()}
            }}
            header.addView(v,LinearLayout.LayoutParams(dp(48),dp(54)))
        }
        root.addView(header)
        val tabScroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        val tabRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        tabs.forEach { tab ->
            val item=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setOnClickListener { selectedTab=tab; buildHome() } }
            item.addView(text(tab,16,if(tab==selectedTab) blue else ink,true).apply { gravity=Gravity.CENTER; setPadding(dp(15),dp(12),dp(15),dp(12)) })
            item.addView(View(this).apply { setBackgroundColor(if(tab==selectedTab) blue else Color.TRANSPARENT) },LinearLayout.LayoutParams(-1,dp(3)))
            tabRow.addView(item)
        }
        tabScroll.addView(tabRow); root.addView(tabScroll)
        val scroll=ScrollView(this)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        if(promoVisible) {
            val promo=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),0,dp(12),0); background=rounded(purple,28) }
            promo.addView(text("♛   Get All Wallpaper Pro!",17,Color.WHITE,true),LinearLayout.LayoutParams(0,dp(52),1f))
            promo.addView(text("×",25,Color.WHITE,false).apply { gravity=Gravity.CENTER; setOnClickListener { promoVisible=false; buildHome() } },LinearLayout.LayoutParams(dp(34),dp(48)))
            val pp=LinearLayout.LayoutParams(-1,dp(54)); pp.setMargins(dp(12),dp(8),dp(12),dp(12)); content.addView(promo,pp)
        }
        val sort=TextView(this).apply { text="Urutkan berdasarkan: PALING POPULER   ▾"; textSize=14f; setTextColor(blue); setPadding(dp(18),dp(12),dp(12),dp(18)); setOnClickListener {
            AlertDialog.Builder(this@MainActivity).setTitle("Urutkan berdasarkan").setItems(arrayOf("Paling populer","Terbaru","Acak","Paling banyak diunduh")) { _,w -> text="Urutkan berdasarkan: "+arrayOf("PALING POPULER","TERBARU","ACAK","PALING BANYAK DIUNDUH")[w]+"   ▾" }.show()
        }}
        content.addView(sort)
        val grid=GridLayout(this).apply { columnCount=3; useDefaultMargins=false }
        names.forEachIndexed { index,name ->
            val cell=FrameLayout(this)
            val art=View(this).apply { background=artDrawable(index); setOnClickListener { showPreview(index) } }
            cell.addView(art,FrameLayout.LayoutParams(-1,dp(202)))
            if(index%5==1) cell.addView(text("♛",20,Color.WHITE,true).apply { gravity=Gravity.TOP or Gravity.END; setPadding(dp(5),dp(4),dp(8),0) })
            val params=GridLayout.LayoutParams().apply { width=0; height=dp(202); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(1),dp(1),dp(1),dp(1)) }
            grid.addView(cell,params)
        }
        content.addView(grid)
        scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val bottom=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(0,dp(8),0,dp(7)); setBackgroundColor(Color.WHITE) }
        listOf("▣\nWallpaper","▦\nCategory","▤\nQuotes","♧\nStock").forEachIndexed { i,label ->
            bottom.addView(text(label,13,if(i==0) blue else ink,i==0).apply { gravity=Gravity.CENTER; setOnClickListener { if(i==1) showCategories() else if(i==2) showQuotes() else if(i==3) Toast.makeText(this@MainActivity,"Stock wallpapers",Toast.LENGTH_SHORT).show() } },LinearLayout.LayoutParams(0,dp(62),1f))
        }
        root.addView(bottom)
        val ad=text("ADVERTISEMENT  •  Ultra HD Wallpaper",10,Color.DKGRAY,false).apply { gravity=Gravity.CENTER; setBackgroundColor(Color.rgb(245,245,245)) }
        root.addView(ad,LinearLayout.LayoutParams(-1,dp(42)))
        setContentView(root)
    }

    private fun showPreview(index:Int) {
        val root=FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val art=View(this).apply { background=artDrawable(index) }
        root.addView(art,FrameLayout.LayoutParams(-1,-1))
        val top=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(18),dp(18),dp(18),0) }
        top.addView(circle("‹"){buildHome()})
        top.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        top.addView(circle("💎"){Toast.makeText(this@MainActivity,"Premium",Toast.LENGTH_SHORT).show()})
        top.addView(circle("ⓘ"){Toast.makeText(this@MainActivity,names[index],Toast.LENGTH_SHORT).show()})
        top.addView(circle("↗"){shareWallpaper()})
        root.addView(top,FrameLayout.LayoutParams(-1,dp(76),Gravity.TOP))
        val bottom=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(20),dp(12),dp(20),dp(20)) }
        bottom.addView(circle(if(index in favorites) "♥" else "♡"){ if(!favorites.add(index)) favorites.remove(index); showPreview(index) })
        bottom.addView(View(this),LinearLayout.LayoutParams(0,1,1f))
        val apply=Button(this).apply { text="Terapkan"; isAllCaps=false; textSize=16f; setTextColor(Color.WHITE); background=rounded(blue,32); setOnClickListener { applyWallpaper(index) } }
        bottom.addView(apply,LinearLayout.LayoutParams(dp(150),dp(54)))
        bottom.addView(View(this),LinearLayout.LayoutParams(dp(20),1))
        bottom.addView(circle("⇩"){Toast.makeText(this@MainActivity,"Unduhan akan tersedia setelah katalog online terhubung",Toast.LENGTH_LONG).show()})
        root.addView(bottom,FrameLayout.LayoutParams(-1,dp(90),Gravity.BOTTOM))
        setContentView(root)
    }

    private fun applyWallpaper(index:Int) {
        AlertDialog.Builder(this).setTitle("Terapkan wallpaper").setItems(arrayOf("Layar utama","Layar kunci","Keduanya")) { _,which ->
            val colors=palettes[index%palettes.size]
            val bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888)
            val c=Canvas(bitmap); val p=Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader=LinearGradient(0f,0f,1080f,1920f,colors[0],colors[1],Shader.TileMode.CLAMP); c.drawRect(0f,0f,1080f,1920f,p)
            try {
                val wm=WallpaperManager.getInstance(this)
                if(Build.VERSION.SDK_INT>=24) wm.setBitmap(bitmap,null,true,when(which){1->WallpaperManager.FLAG_LOCK;2->WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK;else->WallpaperManager.FLAG_SYSTEM}) else wm.setBitmap(bitmap)
                Toast.makeText(this,"Wallpaper berhasil diterapkan",Toast.LENGTH_LONG).show()
            } catch(e:Exception){Toast.makeText(this,"Gagal: "+e.localizedMessage,Toast.LENGTH_LONG).show()}
            finally { bitmap.recycle() }
        }.setNegativeButton("Batal",null).show()
    }

    private fun showSettings() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(22),dp(22),dp(22),dp(12)) }
        root.addView(text("‹   Pengaturan",22,ink,true).apply { setOnClickListener { buildHome() } })
        root.addView(space(28)); root.addView(text("Pengaturan",24,ink,true)); root.addView(space(12))
        listOf("♟   Notifikasi","♡   Favorit","文   Bahasa Aplikasi","▧   Pengubah Wallpaper Otomatis","⇩   Unduh","▤   Wallpaper Penerangan Tepi","Bagikan Aplikasi","☆   Nilai kami","♧   Umpan Balik").forEach { item ->
            val row=text(item+"     ›",17,ink,false).apply { setPadding(dp(16),dp(18),dp(10),dp(18)); background=rounded(Color.WHITE,16); setOnClickListener { if(item.contains("Favorit")) showFavorites() else Toast.makeText(this@MainActivity,item,Toast.LENGTH_SHORT).show() } }
            root.addView(row,LinearLayout.LayoutParams(-1,-2))
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showFavorites(){ val ids=favorites.toList(); if(ids.isEmpty()){Toast.makeText(this,"Belum ada wallpaper favorit",Toast.LENGTH_SHORT).show();return}; showPreview(ids.first()) }
    private fun showCategories(){ AlertDialog.Builder(this).setTitle("Category").setItems(arrayOf("Nature","Abstract","Animals","Ocean","Space","Minimal")){_,w-> selectedTab="Wallpaper";buildHome();Toast.makeText(this,arrayOf("Nature","Abstract","Animals","Ocean","Space","Minimal")[w],Toast.LENGTH_SHORT).show()}.show() }
    private fun showQuotes(){ AlertDialog.Builder(this).setTitle("Quotes").setMessage("Inspirasi harian untuk layar kamu.").setPositiveButton("OK",null).show() }
    private fun search(){ val input=EditText(this).apply { hint="Cari wallpaper" }; AlertDialog.Builder(this).setTitle("Search").setView(input).setPositiveButton("Cari"){_,_->Toast.makeText(this,"Pencarian: "+input.text,Toast.LENGTH_SHORT).show()}.setNegativeButton("Batal",null).show() }
    private fun shareWallpaper(){ val i=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Ultra HD Wallpaper"); startActivity(Intent.createChooser(i,"Bagikan")) }
    private fun toggleTheme(){ Toast.makeText(this,"Tema gelap akan ditambahkan pada pengembangan berikutnya",Toast.LENGTH_SHORT).show() }
    private fun circle(symbol:String, action:()->Unit):View = TextView(this).apply { text=symbol; textSize=24f; gravity=Gravity.CENTER; setTextColor(Color.BLACK); background=rounded(0xffdddddd.toInt(),50); setOnClickListener{action()} }.also { it.layoutParams=LinearLayout.LayoutParams(dp(48),dp(48)).apply { setMargins(dp(4),0,dp(4),0) } }
    private fun artDrawable(index:Int):GradientDrawable { val a=palettes[index%palettes.size]; return GradientDrawable(GradientDrawable.Orientation.TL_BR,a).apply { cornerRadius=dp(2).toFloat() } }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun text(s:String,size:Int,color:Int,bold:Boolean)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD }
    private fun space(h:Int)=View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
