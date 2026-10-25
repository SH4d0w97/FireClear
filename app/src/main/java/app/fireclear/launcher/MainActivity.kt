package app.fireclear.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors

/** Custom wallpapers above this size are ignored. */
private const val MAX_WALLPAPER_BYTES = 25L * 1024 * 1024

/** Constant count of apps in the Home screen pinned dock. */
private const val DOCK_SLOT_COUNT = 4

/** Apps per row in the All Apps grid. */
private const val APPS_PER_ROW = 5

// ───────────────────────── Smart TV Launcher Design Tokens ─────────────────────────
private const val COLOR_MIDNIGHT_BLACK = 0xFF080C14.toInt() // #080C14 (Deep Midnight Navy base)
private const val COLOR_SMOKY_CHARCOAL = 0xFF1B222D.toInt() // #1B222D (Frosted Charcoal surface)
private const val COLOR_SOFT_WHITE     = 0xFFFFFFFF.toInt() // #FFFFFF (Crisp Soft White)
private const val COLOR_COOL_GRAY      = 0xFFB4BFCD.toInt() // #B4BFCD (Cool Slate Gray secondary)
private const val COLOR_MUTED_BLUE     = 0xFF4D6D85.toInt() // #4D6D85 (Ambient Blue fallback)
private const val COLOR_TEXT_DARK      = 0xFF0E131A.toInt() // #0E131A (Ink Dark for white surfaces)

/** Smooth vertical scroll view that never jumps on focus. */
private class CalmVScroll(c: Context) : ScrollView(c) {
    init { overScrollMode = View.OVER_SCROLL_NEVER }
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
}

/**
 * Subtle contrast gradient near the top and bottom to keep text, header,
 * and app tiles readable over any full-screen wallpaper.
 */
private class ContrastGradientView(c: Context) : View(c) {
    private val pTop = Paint()
    private val pBottom = Paint()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val hF = h.toFloat()
        val wF = w.toFloat()
        pTop.shader = LinearGradient(
            0f, 0f, 0f, hF * 0.30f,
            0x88080C14.toInt(), Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        pBottom.shader = LinearGradient(
            0f, hF * 0.44f, 0f, hF,
            Color.TRANSPARENT, 0x99080C14.toInt(),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(cv: Canvas) {
        val wF = width.toFloat()
        val hF = height.toFloat()
        cv.drawRect(0f, 0f, wF, hF * 0.30f, pTop)
        cv.drawRect(0f, hF * 0.44f, wF, hF, pBottom)
    }
}

/**
 * Broad, soft, low-opacity adaptive ambient glow near the bottom dock,
 * smoothly tinted by the currently focused app.
 */
private class AmbientBloomView(c: Context) : View(c) {
    var ambientColor: Int = Color.TRANSPARENT
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        if (ambientColor == Color.TRANSPARENT || Color.alpha(ambientColor) == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val cr = Color.red(ambientColor)
        val cg = Color.green(ambientColor)
        val cb = Color.blue(ambientColor)
        p.shader = RadialGradient(
            w * 0.5f, h * 0.68f, w * 0.60f,
            intArrayOf(Color.argb(55, cr, cg, cb), Color.argb(18, cr, cg, cb), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, p)
    }
}

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val decel = DecelerateInterpolator(1.8f)

    // 12 MB memory cache for tile bitmaps
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val colorMap = HashMap<String, Int>()

    private lateinit var prefs: SharedPreferences
    private lateinit var wallpaper: ImageView
    private lateinit var ambientGlow: AmbientBloomView

    // Top Header & Interactive System Elements
    private lateinit var wifiPill: LinearLayout
    private lateinit var wifiIcon: WifiView
    private lateinit var wifiText: TextView
    private lateinit var navPillContainer: LinearLayout
    private lateinit var tabFavourites: TextView
    private lateinit var tabAllApps: TextView
    private lateinit var tabSettings: TextView
    private var activeTab = 0 // 0 = Favourites, 1 = All Apps, 2 = Settings

    // Content Containers
    private lateinit var viewContainer: FrameLayout
    private lateinit var viewFavourites: FrameLayout
    private lateinit var viewAllApps: CalmVScroll
    private lateinit var allAppsContent: LinearLayout
    private lateinit var viewSettings: FrameLayout

    // Hero Showcase elements on Favourites screen
    private lateinit var heroLayout: LinearLayout
    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var heroHint: TextView

    private var ambientAnim: ValueAnimator? = null
    private var currentAmbient = Color.TRANSPARENT
    private var targetAmbient = Color.TRANSPARENT

    private var signature = ""
    private var appSig = ""
    private var wifiState = ""
    private var wifiSsid = "Connected"
    private var reduce = false

    private val tick = object : Runnable {
        override fun run() { refreshWifi(); ui.postDelayed(this, 10000) }
    }

    private val presets = listOf(
        "Cosmos" to intArrayOf(0xFF070A10.toInt(), 0xFF0E1526.toInt(), 0xFF191D3A.toInt()),
        "Pacific" to intArrayOf(0xFF030D16.toInt(), 0xFF082536.toInt(), 0xFF0E4A4C.toInt()),
        "Aurora" to intArrayOf(0xFF030E0B.toInt(), 0xFF09241B.toInt(), 0xFF154432.toInt()),
        "Sunset" to intArrayOf(0xFF100714.toInt(), 0xFF3D1330.toInt(), 0xFF7A2E2A.toInt()),
        "Slate" to intArrayOf(0xFF080A0E.toInt(), 0xFF12151C.toInt(), 0xFF1E232E.toInt())
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()
    private fun ms(v: Long) = if (reduce) 0L else v

    // ───────────────────────── Lifecycle ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        reduce = prefs.getBoolean("reduce", false)
        getExternalFilesDir(null)

        applyImmersiveMode()

        val root = FrameLayout(this)

        // 1. Full-screen Edge-to-Edge Wallpaper
        wallpaper = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))

        // 2. Subtle Contrast Gradients (Top & Bottom for text/tile legibility)
        root.addView(ContrastGradientView(this), FrameLayout.LayoutParams(-1, -1))

        // 3. Adaptive Ambient Glow Layer (Tinted by focused app)
        ambientGlow = AmbientBloomView(this)
        root.addView(ambientGlow, FrameLayout.LayoutParams(-1, -1))

        // 4. Main Page Structure (Header + View Container)
        val mainPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        // Header bar with independently centered navigation pill and interactive Wi-Fi pill
        mainPage.addView(createHeaderBar())

        // Content Area Switcher
        viewContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        mainPage.addView(viewContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(mainPage, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window.setBackgroundDrawable(null)

        applyWallpaper()
        setupViews()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        refresh()
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        selectTab(0, requestContentFocus = true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { currentFocus?.performLongClick(); return true }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Home screen: Back does nothing")
    override fun onBackPressed() {
        if (activeTab != 0) {
            selectTab(0, requestContentFocus = true)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    // ───────────────────────── Header & Centered Glass Pill ─────────────────────────

    private fun createHeaderBar(): View {
        val header = RelativeLayout(this).apply {
            setPadding(dp(48), dp(22), dp(48), dp(4))
            clipChildren = false
            clipToPadding = false
        }

        // Clock on Top-Left (No container, quiet typography)
        val clockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
        }
        val timeV = TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 27f)
            setTextColor(COLOR_SOFT_WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val dateV = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_COOL_GRAY)
            setPadding(0, dp(1), 0, 0)
        }
        clockLayout.addView(timeV)
        clockLayout.addView(dateV)

        val clockLp = RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        header.addView(clockLayout, clockLp)

        // Floating Glass Navigation Pill — Strictly Centered Horizontally
        navPillContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0x991B222D.toInt()) // Smoky charcoal (~60% opacity)
                setStroke(dp(1), 0x20FFFFFF) // Fine translucent border
            }
        }

        tabFavourites = createPillTab("Favourites", 0)
        tabAllApps = createPillTab("All Apps", 1)
        tabSettings = createPillTab("Settings", 2)

        navPillContainer.addView(tabFavourites)
        navPillContainer.addView(tabAllApps)
        navPillContainer.addView(tabSettings)

        val navLp = RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(RelativeLayout.CENTER_HORIZONTAL)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        header.addView(navPillContainer, navLp)

        // Interactive Wi-Fi System Pill on Top-Right
        val wifiBg = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(0x801B222D.toInt())
            setStroke(dp(1), 0x20FFFFFF)
        }
        wifiIcon = WifiView(this)
        wifiText = TextView(this).apply {
            text = "Wi-Fi"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_SOFT_WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        wifiPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(16), dp(6))
            background = wifiBg
            isFocusable = true
            isClickable = true
            addView(wifiIcon, LinearLayout.LayoutParams(dp(18), dp(14)).apply { rightMargin = dp(8) })
            addView(wifiText)

            setOnFocusChangeListener { v, f ->
                wifiBg.setColor(if (f) COLOR_SOFT_WHITE else 0x801B222D.toInt())
                wifiBg.setStroke(dp(1), if (f) COLOR_SOFT_WHITE else 0x20FFFFFF)
                wifiText.setTextColor(if (f) COLOR_TEXT_DARK else COLOR_SOFT_WHITE)
                wifiIcon.dark = f
                wifiIcon.invalidate()
                v.animate().scaleX(if (f) 1.04f else 1f).scaleY(if (f) 1.04f else 1f)
                    .setInterpolator(decel).setDuration(ms(180)).start()
                if (f) transitionAmbient(COLOR_MUTED_BLUE)
            }
            setOnClickListener {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
            }
        }

        val sysLp = RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        header.addView(wifiPill, sysLp)

        return header
    }

    private fun createPillTab(title: String, index: Int): TextView {
        return TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(18), dp(7), dp(18), dp(7))
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    selectTab(index, requestContentFocus = false)
                    transitionAmbient(COLOR_MUTED_BLUE)
                }
                v.animate().scaleX(if (hasFocus) 1.03f else 1f).scaleY(if (hasFocus) 1.03f else 1f)
                    .setInterpolator(decel).setDuration(ms(180)).start()
            }
            setOnClickListener {
                selectTab(index, requestContentFocus = true)
            }
        }
    }

    private fun selectTab(index: Int, requestContentFocus: Boolean) {
        activeTab = index
        updatePillTabsStyle()

        viewFavourites.visibility = if (index == 0) View.VISIBLE else View.GONE
        viewAllApps.visibility = if (index == 1) View.VISIBLE else View.GONE
        viewSettings.visibility = if (index == 2) View.VISIBLE else View.GONE

        if (requestContentFocus) {
            when (index) {
                0 -> findFirstFocusable(viewFavourites)?.requestFocus()
                1 -> findFirstFocusable(viewAllApps)?.requestFocus()
                2 -> findFirstFocusable(viewSettings)?.requestFocus()
            }
        }
    }

    private fun updatePillTabsStyle() {
        val tabs = listOf(tabFavourites, tabAllApps, tabSettings)
        tabs.forEachIndexed { i, tv ->
            val isActive = (i == activeTab)
            if (isActive) {
                tv.background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(COLOR_SOFT_WHITE)
                }
                tv.setTextColor(COLOR_TEXT_DARK)
            } else {
                tv.background = null
                tv.setTextColor(0xFFCBD5E1.toInt())
            }
        }
    }

    private fun findFirstFocusable(root: View?): View? {
        if (root == null) return null
        if (root.isFocusable && root.visibility == View.VISIBLE) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val f = findFirstFocusable(root.getChildAt(i))
                if (f != null) return f
            }
        }
        return null
    }

    // ───────────────────────── Views Setup ─────────────────────────

    private fun setupViews() {
        viewContainer.removeAllViews()

        // 1. Favourites View (Hero Header + 4-app pinned dock)
        viewFavourites = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        viewContainer.addView(viewFavourites, FrameLayout.LayoutParams(-1, -1))

        // 2. All Apps View (Categorized / 5-column grid)
        allAppsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(14), 0, dp(64))
        }
        viewAllApps = CalmVScroll(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
            addView(allAppsContent)
        }
        viewContainer.addView(viewAllApps, FrameLayout.LayoutParams(-1, -1))

        // 3. Settings View (Quick Settings grid + All Settings card)
        viewSettings = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
        }
        viewContainer.addView(viewSettings, FrameLayout.LayoutParams(-1, -1))

        rebuildSettingsView()
        updatePillTabsStyle()
    }

    private fun loadApps(): List<ResolveInfo> {
        val pm = packageManager
        fun q(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        return (q(Intent.CATEGORY_LEANBACK_LAUNCHER) + q(Intent.CATEGORY_LAUNCHER))
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    private fun list(k: String): List<String> =
        (prefs.getString(k, "") ?: "").split("\n").filter { it.isNotEmpty() }

    private fun save(k: String, l: List<String>) {
        prefs.edit().putString(k, l.joinToString("\n")).apply()
    }

    private fun getDockPackages(all: List<ResolveInfo>): List<String> {
        val hidden = prefs.getStringSet("hidden", emptySet<String>()) ?: emptySet()
        val byPkg = all.associateBy { it.activityInfo.packageName }
        val explicit = list("fav").filter { it in byPkg && it !in hidden }.distinct().toMutableList()

        for (app in all) {
            if (explicit.size >= DOCK_SLOT_COUNT) break
            val pkg = app.activityInfo.packageName
            if (pkg !in explicit && pkg !in hidden) {
                explicit.add(pkg)
            }
        }
        return explicit.take(DOCK_SLOT_COUNT)
    }

    private fun currentSig(a: String) =
        a + "|" + prefs.getString("fav", "") + "|" + prefs.getString("rec", "") + "|" +
            prefs.getStringSet("hidden", emptySet<String>()).toString()

    private fun refresh() {
        val all = loadApps()
        val aSig = all.joinToString(",") { it.activityInfo.packageName }
        if (aSig != appSig) { appSig = aSig; cache.evictAll(); colorMap.clear() }
        if (currentSig(aSig) != signature) rebuildAppViews(all)
    }

    // ───────────────────────── App Views Construction ─────────────────────────

    private fun rebuildAppViews(all: List<ResolveInfo> = loadApps()) {
        val hidden = prefs.getStringSet("hidden", emptySet<String>()) ?: emptySet()
        val byPkg = all.associateBy { it.activityInfo.packageName }

        val dockPkgs = getDockPackages(all)
        val dockApps = dockPkgs.mapNotNull { byPkg[it] }
        val otherApps = all.filter { it.activityInfo.packageName !in hidden && it.activityInfo.packageName !in dockPkgs }

        signature = currentSig(appSig)

        // Screen adaptive measurements
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val contentW = (screenW * 0.84f).toInt()

        // 1. REBUILD FAVOURITES VIEW (Hero Showcase Header + 4-Card Dock)
        viewFavourites.removeAllViews()

        // Cinematic Hero Header
        heroTitle = TextView(this).apply {
            text = "Favourites"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTextColor(COLOR_SOFT_WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        heroSubtitle = TextView(this).apply {
            text = "Pinned Applications • Quick Access"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_COOL_GRAY)
            setPadding(0, dp(4), 0, 0)
        }
        heroHint = TextView(this).apply {
            text = "Press OK to open • Hold for options"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(0x80B4BFCD.toInt())
            setPadding(0, dp(6), 0, 0)
        }

        heroLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(36), dp(48), 0)
            addView(heroTitle)
            addView(heroSubtitle)
            addView(heroHint)
        }
        val heroLp = FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        viewFavourites.addView(heroLayout, heroLp)

        val dockGap = dp(16)
        val dockCardW = (contentW - (DOCK_SLOT_COUNT - 1) * dockGap) / DOCK_SLOT_COUNT
        val dockCardH = (dockCardW * 10 / 16) // 16:10 landscape rectangle
        val dockCardR = dp(14).toFloat()

        val dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        dockApps.forEachIndexed { slot, ri ->
            val dominant = getOrComputeColor(ri)
            val tile = createTile(
                ri = ri,
                secId = "fav",
                cardW = dockCardW,
                cardH = dockCardH,
                cardR = dockCardR,
                dominantColor = dominant,
                isDock = true,
                dockSlot = slot,
                byPkg = byPkg
            )
            val lp = LinearLayout.LayoutParams(dockCardW, -2).apply {
                if (slot < dockApps.size - 1) rightMargin = dockGap
            }
            dockRow.addView(tile, lp)
        }

        val dockLp = FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(56)
        }
        viewFavourites.addView(dockRow, dockLp)

        // Initial hero text matches first dock app
        dockApps.firstOrNull()?.let { updateHero(it) }

        // 2. REBUILD ALL APPS VIEW (Categorized / 5-Column Grid)
        allAppsContent.removeAllViews()

        val allAppsList = (dockApps + otherApps).distinctBy { it.activityInfo.packageName }

        // Title Header with Count Badge
        val headLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        val headText = TextView(this).apply {
            text = "All applications"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(COLOR_SOFT_WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val countBadge = TextView(this).apply {
            text = "${allAppsList.size} apps"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(COLOR_COOL_GRAY)
            setPadding(dp(12), 0, 0, 0)
        }
        headLayout.addView(headText)
        headLayout.addView(countBadge)

        allAppsContent.addView(headLayout, LinearLayout.LayoutParams(contentW, -2))

        val gridGap = dp(14)
        val gridCardW = (contentW - (APPS_PER_ROW - 1) * gridGap) / APPS_PER_ROW
        val gridCardH = (gridCardW * 10 / 16)
        val gridCardR = dp(11).toFloat()

        val rows = allAppsList.chunked(APPS_PER_ROW)

        rows.forEachIndexed { rowIndex, rowApps ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(6), 0, dp(14))
            }
            rowApps.forEachIndexed { colIndex, ri ->
                val dominant = getOrComputeColor(ri)
                val tile = createTile(
                    ri = ri,
                    secId = "grid_$rowIndex",
                    cardW = gridCardW,
                    cardH = gridCardH,
                    cardR = gridCardR,
                    dominantColor = dominant,
                    isDock = false,
                    dockSlot = -1,
                    byPkg = byPkg
                )
                val lp = LinearLayout.LayoutParams(gridCardW, -2).apply {
                    if (colIndex < rowApps.size - 1) rightMargin = gridGap
                }
                row.addView(tile, lp)
            }
            allAppsContent.addView(row, LinearLayout.LayoutParams(contentW, -2))
        }
    }

    private fun updateHero(ri: ResolveInfo) {
        val name = ri.loadLabel(packageManager).toString()
        val pkg = ri.activityInfo.packageName
        val cat = when {
            pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("prime") ||
                pkg.contains("disney") || pkg.contains("kodi") || pkg.contains("plex") || pkg.contains("player") -> "Streaming & Video"
            pkg.contains("spotify") || pkg.contains("music") || pkg.contains("audio") || pkg.contains("podcast") -> "Music & Audio"
            pkg.contains("game") || pkg.contains("arcade") || pkg.contains("dan") -> "Games & Entertainment"
            pkg.contains("browser") || pkg.contains("brave") || pkg.contains("chrome") -> "Web Browser"
            else -> "Installed Application"
        }

        heroTitle.animate().alpha(0f).setDuration(ms(80)).withEndAction {
            heroTitle.text = name
            heroSubtitle.text = "$cat • Quick Launch"
            heroTitle.animate().alpha(1f).setDuration(ms(140)).start()
        }.start()
    }

    // ───────────────────────── Redesigned Control Center Settings (Image 3) ─────────────────────────

    private fun rebuildSettingsView() {
        viewSettings.removeAllViews()
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val contentW = (screenW * 0.78f).toInt()

        val settingsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(16), 0, dp(48))
        }

        // Section header
        val headerTv = TextView(this).apply {
            text = "Quick settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(COLOR_SOFT_WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(14))
        }
        settingsContainer.addView(headerTv, LinearLayout.LayoutParams(contentW, -2))

        val tileGap = dp(14)
        val tileW = (contentW - tileGap) / 2
        val tileH = dp(76)

        // 6 Distinct, Meaningful Control Center Tiles with Rich Metadata
        val quickSettings = listOf(
            ControlTile("Network & Wi-Fi", "Wi-Fi, Ethernet & speed", GlyphView.TYPE_WIFI) {
                openSettings(Settings.ACTION_WIFI_SETTINGS)
            },
            ControlTile("Remotes & Bluetooth", "Pair controllers, remotes & audio", GlyphView.TYPE_BLUETOOTH) {
                openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            },
            ControlTile("Wallpaper & Themes", "5 atmospheric themes & custom photo", GlyphView.TYPE_WALLPAPER) {
                customize()
            },
            ControlTile("Applications", "Manage installed apps & clear cache", GlyphView.TYPE_APPS) {
                openSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
            },
            ControlTile("Display & Sounds", "Resolution, HDR & volume", GlyphView.TYPE_DISPLAY) {
                openSettings(Settings.ACTION_DISPLAY_SETTINGS)
            },
            ControlTile("Device & About", "Storage, system info & updates", GlyphView.TYPE_INFO) {
                openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
            }
        )

        val rows = quickSettings.chunked(2)
        rows.forEach { rowTiles ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, tileGap)
            }
            rowTiles.forEachIndexed { i, item ->
                val tile = createQuickSettingTile(item.title, item.subtitle, item.glyphType, tileW, tileH, item.action)
                val lp = LinearLayout.LayoutParams(tileW, tileH).apply {
                    if (i == 0) rightMargin = tileGap
                }
                row.addView(tile, lp)
            }
            settingsContainer.addView(row, LinearLayout.LayoutParams(contentW, -2))
        }

        // Full-width "All Settings" Gateway
        val allSettingsCard = createAllSettingsTile(contentW, dp(64)) {
            openSettings(Settings.ACTION_SETTINGS)
        }
        settingsContainer.addView(allSettingsCard, LinearLayout.LayoutParams(contentW, -2).apply {
            topMargin = dp(6)
        })

        val scroll = CalmVScroll(this).apply {
            isVerticalScrollBarEnabled = false
            addView(settingsContainer)
        }
        viewSettings.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private class ControlTile(val title: String, val subtitle: String, val glyphType: Int, val action: () -> Unit)

    private fun createQuickSettingTile(
        title: String,
        subtitle: String,
        glyphType: Int,
        w: Int,
        h: Int,
        action: () -> Unit
    ): View {
        val r = dp(12).toFloat()
        val bg = GradientDrawable().apply {
            cornerRadius = r
            setColor(0x801B222D.toInt()) // Frosted smoky charcoal surface
            setStroke(dp(1), 0x1AFFFFFF) // Fine translucent border
        }
        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x22FFFFFF)
        }
        val iconBadge = FrameLayout(this).apply {
            background = badgeBg
        }
        val glyph = GlyphView(this, glyphType)
        iconBadge.addView(glyph, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))

        val titleTv = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTextColor(COLOR_SOFT_WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subTv = TextView(this).apply {
            text = subtitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_COOL_GRAY)
            setPadding(0, dp(1), 0, 0)
        }
        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleTv)
            addView(subTv)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = bg
            isFocusable = true
            isClickable = true
            addView(iconBadge, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(14) })
            addView(textLayout, LinearLayout.LayoutParams(0, -2, 1f))

            setOnFocusChangeListener { v, f ->
                bg.setColor(if (f) COLOR_SOFT_WHITE else 0x801B222D.toInt())
                bg.setStroke(dp(1), if (f) COLOR_SOFT_WHITE else 0x1AFFFFFF)
                badgeBg.setColor(if (f) 0x1C000000 else 0x22FFFFFF)
                titleTv.setTextColor(if (f) COLOR_TEXT_DARK else COLOR_SOFT_WHITE)
                subTv.setTextColor(if (f) 0xFF4E5866.toInt() else COLOR_COOL_GRAY)
                glyph.dark = f
                glyph.invalidate()
                v.animate().scaleX(if (f) 1.03f else 1f).scaleY(if (f) 1.03f else 1f)
                    .setInterpolator(decel).setDuration(ms(180)).start()
                if (f) transitionAmbient(COLOR_MUTED_BLUE)
            }
            setOnClickListener { action() }
        }
    }

    private fun createAllSettingsTile(w: Int, h: Int, action: () -> Unit): View {
        val r = dp(12).toFloat()
        val bg = GradientDrawable().apply {
            cornerRadius = r
            setColor(0x801B222D.toInt())
            setStroke(dp(1), 0x1AFFFFFF)
        }
        val glyph = GlyphView(this, GlyphView.TYPE_SLIDERS)
        val titleTv = TextView(this).apply {
            text = "All Settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_SOFT_WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subTv = TextView(this).apply {
            text = "Complete Android system preferences, accounts, accessibility & privacy"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(COLOR_COOL_GRAY)
        }
        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleTv)
            addView(subTv)
        }
        val chevron = TextView(this).apply {
            text = "›"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(COLOR_COOL_GRAY)
            gravity = Gravity.CENTER
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(22), dp(14))
            background = bg
            isFocusable = true
            isClickable = true
            addView(glyph, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(16) })
            addView(textLayout, LinearLayout.LayoutParams(0, -2, 1f))
            addView(chevron)

            setOnFocusChangeListener { v, f ->
                bg.setColor(if (f) COLOR_SOFT_WHITE else 0x801B222D.toInt())
                bg.setStroke(dp(1), if (f) COLOR_SOFT_WHITE else 0x1AFFFFFF)
                titleTv.setTextColor(if (f) COLOR_TEXT_DARK else COLOR_SOFT_WHITE)
                subTv.setTextColor(if (f) 0xFF4E5866.toInt() else COLOR_COOL_GRAY)
                chevron.setTextColor(if (f) COLOR_TEXT_DARK else COLOR_COOL_GRAY)
                glyph.dark = f
                glyph.invalidate()
                v.animate().scaleX(if (f) 1.025f else 1f).scaleY(if (f) 1.025f else 1f)
                    .setInterpolator(decel).setDuration(ms(180)).start()
                if (f) transitionAmbient(COLOR_MUTED_BLUE)
            }
            setOnClickListener { action() }
        }
    }

    // ───────────────────────── Tile Creation & Focused Physics ─────────────────────────

    private fun createTile(
        ri: ResolveInfo,
        secId: String,
        cardW: Int,
        cardH: Int,
        cardR: Float,
        dominantColor: Int,
        isDock: Boolean,
        dockSlot: Int,
        byPkg: Map<String, ResolveInfo>
    ): View {
        val pkg = ri.activityInfo.packageName
        val cls = ri.activityInfo.name
        val name = ri.loadLabel(packageManager).toString()
        val artKey = pkg + "|" + cardW + "|" + cardH

        val border = GradientDrawable().apply {
            cornerRadius = cardR
            setColor(Color.TRANSPARENT)
            setStroke(0, Color.TRANSPARENT)
        }

        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            background = GradientDrawable().apply {
                setColor(blendColor(dominantColor, COLOR_MIDNIGHT_BLACK, 0.70f))
                cornerRadius = cardR
            }
            foreground = border
            tag = artKey
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, cardR) }
            }
        }
        loadArt(img, artKey) { appArt(ri, cardW, cardH, cardR, dominantColor) }

        val label = TextView(this).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isDock) 12.5f else 11.5f)
            setTextColor(COLOR_COOL_GRAY)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(6), 0, 0)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            isClickable = true
            addView(img, LinearLayout.LayoutParams(cardW, cardH))
            addView(label, LinearLayout.LayoutParams(cardW, -2))

            // Focused state: Restrained 1.05x elevation, crisp soft-white focus ring, 180ms transition
            setOnFocusChangeListener { v, f ->
                val s = if (f) (if (isDock) 1.05f else 1.04f) else 1.0f
                val shadowZ = if (f) dp(12).toFloat() else 0f
                border.setStroke(dp(1.8f), if (f) COLOR_SOFT_WHITE else Color.TRANSPARENT)
                label.setTextColor(if (f) COLOR_SOFT_WHITE else COLOR_COOL_GRAY)

                img.animate()
                    .scaleX(s)
                    .scaleY(s)
                    .translationZ(shadowZ)
                    .setInterpolator(decel)
                    .setDuration(ms(180))
                    .withLayer()
                    .start()

                if (f) {
                    if (isDock) updateHero(ri)
                    transitionAmbient(dominantColor)
                }
            }
            setOnClickListener { launch(pkg, cls) }
            setOnLongClickListener {
                openTileMenu(pkg, name, isDock, dockSlot, byPkg)
                true
            }
        }
    }

    private fun loadArt(img: ImageView, artKey: String, make: () -> Bitmap) {
        val hit = cache.get(artKey)
        if (hit != null) { img.background = null; img.setImageBitmap(hit); return }
        worker.execute {
            var bmp: Bitmap? = cache.get(artKey)
            if (bmp == null) {
                bmp = try { make() } catch (e: Throwable) { null }
                if (bmp != null) cache.put(artKey, bmp)
            }
            val done = bmp
            if (done != null) ui.post {
                if (img.tag == artKey) {
                    img.background = null
                    img.alpha = 0f
                    img.setImageBitmap(done)
                    img.animate().alpha(1f).setDuration(ms(160)).start()
                }
            }
        }
    }

    private fun extractDominantColor(bmp: Bitmap): Int {
        val w = bmp.width
        val h = bmp.height
        val stepX = maxOf(1, w / 16)
        val stepY = maxOf(1, h / 16)
        var maxScore = -1f
        var bestColor = COLOR_MUTED_BLUE
        val hsv = FloatArray(3)

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val pixel = bmp.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 100) continue

                Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val val_ = hsv[2]

                if (sat > 0.22f && val_ > 0.22f && val_ < 0.95f) {
                    val score = sat * (1f - Math.abs(val_ - 0.6f))
                    if (score > maxScore) {
                        maxScore = score
                        bestColor = pixel
                    }
                }
            }
        }
        return bestColor
    }

    private fun getOrComputeColor(ri: ResolveInfo): Int {
        val pkg = ri.activityInfo.packageName
        colorMap[pkg]?.let { return it }
        val pm = packageManager
        val icon = ri.loadIcon(pm)
        val s = 64
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        icon.setBounds(0, 0, s, s)
        icon.draw(c)
        val color = extractDominantColor(bmp)
        bmp.recycle()
        colorMap[pkg] = color
        return color
    }

    private fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val ir = 1f - ratio
        val a = (Color.alpha(c1) * ir + Color.alpha(c2) * ratio).toInt()
        val r = (Color.red(c1) * ir + Color.red(c2) * ratio).toInt()
        val g = (Color.green(c1) * ir + Color.green(c2) * ratio).toInt()
        val b = (Color.blue(c1) * ir + Color.blue(c2) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun appArt(ri: ResolveInfo, w: Int, h: Int, r: Float, dominantColor: Int): Bitmap {
        val pm = packageManager
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(src)
        val banner = ri.activityInfo.loadBanner(pm)
        if (banner != null) {
            c.drawColor(COLOR_MIDNIGHT_BLACK)
            banner.setBounds(0, 0, w, h)
            banner.draw(c)
        } else {
            // Rich multi-stop gradient background tinted with the brand color
            val topColor = blendColor(dominantColor, 0xFF2A374C.toInt(), 0.35f)
            val midColor = blendColor(dominantColor, 0xFF141C2A.toInt(), 0.65f)
            val bottomColor = blendColor(dominantColor, COLOR_MIDNIGHT_BLACK, 0.88f)

            val p = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(topColor, midColor, bottomColor), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

            // Top specular glass sheen
            val sheen = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h * 0.42f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w.toFloat(), h * 0.42f, sheen)

            // Subtle inner rim stroke
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                color = 0x1EFFFFFF
            }
            c.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), r, r, rim)

            // Centered app icon with prominent 58% height presence
            val icon = ri.loadIcon(pm)
            val iconSize = (h * 0.58f).toInt()
            val left = (w - iconSize) / 2
            val top = (h - iconSize) / 2
            icon.setBounds(left, top, left + iconSize, top + iconSize)
            icon.draw(c)
        }
        return rounded(src, r)
    }

    private fun rounded(src: Bitmap, r: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, p)
        src.recycle()
        return out
    }

    private fun transitionAmbient(color: Int) {
        if (reduce || targetAmbient == color) return
        targetAmbient = color
        ambientAnim?.cancel()
        val from = if (currentAmbient == Color.TRANSPARENT) 0x00000000 else currentAmbient
        ambientAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
            duration = ms(200) // 180–220ms restrained transition
            interpolator = decel
            addUpdateListener { va ->
                currentAmbient = va.animatedValue as Int
                ambientGlow.ambientColor = currentAmbient
            }
            start()
        }
    }

    // ───────────────────────── Tile Context Menu & Actions ─────────────────────────

    private fun launch(pkg: String, cls: String) {
        val rec = list("rec").toMutableList()
        rec.remove(pkg); rec.add(0, pkg)
        save("rec", rec.take(12))
        try {
            startActivity(Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open this app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTileMenu(
        pkg: String,
        name: String,
        isDock: Boolean,
        dockSlot: Int,
        byPkg: Map<String, ResolveInfo>
    ) {
        val all = loadApps()
        val currentDock = getDockPackages(all).toMutableList()
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()

        fun opt(t: String, a: () -> Unit) { labels.add(t); actions.add(a) }

        if (isDock) {
            if (dockSlot > 0) {
                opt("Move Left") {
                    Collections.swap(currentDock, dockSlot, dockSlot - 1)
                    save("fav", currentDock)
                    rebuildAppViews()
                }
            }
            if (dockSlot < currentDock.size - 1) {
                opt("Move Right") {
                    Collections.swap(currentDock, dockSlot, dockSlot + 1)
                    save("fav", currentDock)
                    rebuildAppViews()
                }
            }
            opt("Remove from Favourites") {
                currentDock.remove(pkg)
                save("fav", currentDock)
                rebuildAppViews()
            }
        } else {
            opt("Pin to Favourites") {
                val slotOptions = (0 until DOCK_SLOT_COUNT).map { slot ->
                    val occupantPkg = currentDock.getOrNull(slot)
                    val occupantName = if (occupantPkg != null) {
                        byPkg[occupantPkg]?.loadLabel(packageManager)?.toString() ?: occupantPkg
                    } else "Empty"
                    "Slot ${slot + 1} ($occupantName)"
                }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("Select Dock Slot")
                    .setItems(slotOptions) { _, chosenSlot ->
                        if (chosenSlot < currentDock.size) {
                            currentDock[chosenSlot] = pkg
                        } else {
                            currentDock.add(pkg)
                        }
                        save("fav", currentDock.take(DOCK_SLOT_COUNT))
                        rebuildAppViews()
                    }
                    .show()
            }
        }

        opt("Hide from launcher") {
            val hid = prefs.getStringSet("hidden", emptySet<String>())!!.toMutableSet()
            hid.add(pkg)
            prefs.edit().putStringSet("hidden", hid).apply()
            currentDock.remove(pkg)
            save("fav", currentDock)
            rebuildAppViews()
        }

        opt("App info / Uninstall") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        AlertDialog.Builder(this).setTitle(name)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun customize() {
        val anim = "Animations: " + (if (reduce) "Off" else "On") + " (select to switch)"
        val names = presets.map { it.first } + listOf("Custom photo", "Unhide all apps", anim)
        AlertDialog.Builder(this).setTitle("Wallpaper & Style")
            .setItems(names.toTypedArray()) { _, i ->
                when {
                    i < presets.size -> { prefs.edit().putInt("wp", i).apply(); applyWallpaper() }
                    i == presets.size -> {
                        if (customFile() != null) { prefs.edit().putInt("wp", i).apply(); applyWallpaper() }
                        else Toast.makeText(this,
                            "Push photo via ADB:\nadb push photo.jpg /sdcard/Android/data/$packageName/files/wallpaper.jpg",
                            Toast.LENGTH_LONG).show()
                    }
                    i == presets.size + 1 -> { prefs.edit().remove("hidden").apply(); rebuildAppViews() }
                    else -> { reduce = !reduce; prefs.edit().putBoolean("reduce", reduce).apply() }
                }
            }.show()
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                Toast.makeText(this, "Settings not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ───────────────────────── Full-Screen Wallpaper ─────────────────────────

    private fun customFile(): File? {
        val dir = getExternalFilesDir(null) ?: return null
        return listOf("wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png")
            .map { File(dir, it) }.firstOrNull { it.exists() }
    }

    private fun gradientBitmap(c: IntArray): Bitmap {
        val w = 640
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }

    private fun decodeWallpaper(f: File): Bitmap? {
        try {
            if (f.length() > MAX_WALLPAPER_BYTES) return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            val longest = maxOf(o.outWidth, o.outHeight)
            if (o.outWidth <= 0 || o.outHeight <= 0 || longest > 20000) return null
            var s = 1
            while (longest / (s * 2) >= 1920) s *= 2
            return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
                inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565; inMutable = true
            })
        } catch (e: Throwable) {
            return null
        }
    }

    private fun applyWallpaper() {
        val i = prefs.getInt("wp", 0)
        wallpaper.scaleType = ImageView.ScaleType.FIT_XY
        wallpaper.setImageBitmap(gradientBitmap(presets[i.coerceIn(0, presets.size - 1)].second))
        if (i == presets.size) {
            val f = customFile() ?: return
            worker.execute {
                val bmp = decodeWallpaper(f)
                ui.post {
                    if (bmp != null) {
                        wallpaper.scaleType = ImageView.ScaleType.CENTER_CROP
                        wallpaper.setImageBitmap(bmp)
                    }
                }
            }
        }
    }

    private fun refreshWifi() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            var level = 3
            var label = "Offline"
            when {
                caps == null -> {
                    level = 0
                    label = "Offline"
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val rssi = wm.connectionInfo.rssi
                    @Suppress("DEPRECATION")
                    level = WifiManager.calculateSignalLevel(rssi, 4).coerceIn(1, 3)
                    @Suppress("DEPRECATION")
                    val rawSsid = wm.connectionInfo.ssid?.replace("\"", "") ?: ""
                    label = if (rawSsid.isNotEmpty() && rawSsid != "<unknown ssid>") rawSsid else "Wi-Fi"
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    level = 3
                    label = "Ethernet"
                }
            }
            val st = "$level|$label"
            if (st != wifiState) {
                wifiState = st
                wifiSsid = label
                wifiIcon.level = level
                wifiText.text = label
                wifiIcon.invalidate()
            }
        } catch (e: Exception) { /* ignore */ }
    }
}

/** Minimal line Wi-Fi glyph (consistent stroke weight). */
internal class WifiView(c: Context) : View(c) {
    var level = 3
    var dark = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(cv: Canvas) {
        val h = height.toFloat()
        val cx = width / 2f
        val cy = h * 0.95f
        p.color = if (dark) 0xFF111418.toInt() else COLOR_SOFT_WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.12f
        for (i in 1..3) {
            p.alpha = if (i <= level) 255 else 60
            val r = h * 0.31f * i
            cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 225f, 90f, false, p)
        }
        p.style = Paint.Style.FILL
        p.alpha = if (level > 0) 255 else 60
        cv.drawCircle(cx, cy, h * 0.08f, p)
    }
}

/** Clean line vector glyphs for Quick Settings tiles. */
internal class GlyphView(c: Context, private val type: Int) : View(c) {
    companion object {
        const val TYPE_WIFI = 1
        const val TYPE_BLUETOOTH = 2
        const val TYPE_WALLPAPER = 3
        const val TYPE_DISPLAY = 4
        const val TYPE_APPS = 5
        const val TYPE_INFO = 6
        const val TYPE_SLIDERS = 7
    }

    var dark = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val color = if (dark) 0xFF111418.toInt() else COLOR_SOFT_WHITE
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.09f

        when (type) {
            TYPE_WIFI -> {
                val cx = w * 0.5f
                val cy = h * 0.90f
                for (i in 1..3) {
                    val r = h * 0.28f * i
                    cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 225f, 90f, false, p)
                }
                p.style = Paint.Style.FILL
                cv.drawCircle(cx, cy, h * 0.08f, p)
            }
            TYPE_BLUETOOTH -> {
                val cx = w * 0.5f
                cv.drawLine(cx, h * 0.12f, cx, h * 0.88f, p)
                val path = Path().apply {
                    moveTo(w * 0.28f, h * 0.35f)
                    lineTo(w * 0.72f, h * 0.65f)
                    lineTo(cx, h * 0.88f)
                    lineTo(w * 0.72f, h * 0.35f)
                    lineTo(w * 0.28f, h * 0.65f)
                }
                cv.drawPath(path, p)
            }
            TYPE_WALLPAPER -> {
                cv.drawRoundRect(RectF(w * 0.1f, h * 0.12f, w * 0.9f, h * 0.88f), w * 0.12f, w * 0.12f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(w * 0.32f, h * 0.38f, w * 0.10f, p)
                p.style = Paint.Style.STROKE
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.76f)
                    lineTo(w * 0.44f, h * 0.52f)
                    lineTo(w * 0.62f, h * 0.68f)
                    lineTo(w * 0.74f, h * 0.58f)
                    lineTo(w * 0.84f, h * 0.76f)
                }
                cv.drawPath(path, p)
            }
            TYPE_DISPLAY -> {
                cv.drawCircle(w * 0.5f, h * 0.5f, w * 0.22f, p)
                for (angle in 0 until 360 step 45) {
                    val rad = Math.toRadians(angle.toDouble())
                    val x1 = (w * 0.5f + Math.cos(rad) * w * 0.32f).toFloat()
                    val y1 = (h * 0.5f + Math.sin(rad) * h * 0.32f).toFloat()
                    val x2 = (w * 0.5f + Math.cos(rad) * w * 0.44f).toFloat()
                    val y2 = (h * 0.5f + Math.sin(rad) * h * 0.44f).toFloat()
                    cv.drawLine(x1, y1, x2, y2, p)
                }
            }
            TYPE_APPS -> {
                val s = w * 0.28f
                cv.drawRoundRect(RectF(w * 0.15f, h * 0.15f, w * 0.15f + s, h * 0.15f + s), w * 0.06f, w * 0.06f, p)
                cv.drawRoundRect(RectF(w * 0.55f, h * 0.15f, w * 0.55f + s, h * 0.15f + s), w * 0.06f, w * 0.06f, p)
                cv.drawRoundRect(RectF(w * 0.15f, h * 0.55f, w * 0.15f + s, h * 0.55f + s), w * 0.06f, w * 0.06f, p)
                cv.drawRoundRect(RectF(w * 0.55f, h * 0.55f, w * 0.55f + s, h * 0.55f + s), w * 0.06f, w * 0.06f, p)
            }
            TYPE_INFO -> {
                cv.drawCircle(w * 0.5f, h * 0.5f, w * 0.38f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(w * 0.5f, h * 0.34f, w * 0.06f, p)
                p.style = Paint.Style.STROKE
                cv.drawLine(w * 0.5f, h * 0.46f, w * 0.5f, h * 0.68f, p)
            }
            TYPE_SLIDERS -> {
                cv.drawLine(w * 0.12f, h * 0.32f, w * 0.88f, h * 0.32f, p)
                cv.drawLine(w * 0.12f, h * 0.68f, w * 0.88f, h * 0.68f, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(w * 0.35f, h * 0.32f, w * 0.11f, p)
                cv.drawCircle(w * 0.65f, h * 0.68f, w * 0.11f, p)
            }
        }
    }
}
