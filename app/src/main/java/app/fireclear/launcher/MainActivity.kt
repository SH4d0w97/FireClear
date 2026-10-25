package app.fireclear.launcher

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
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
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors

/** Custom wallpapers above this size are ignored. */
private const val MAX_WALLPAPER_BYTES = 25L * 1024 * 1024

/** Constant count of apps in the top favorites dock (Apple TV top shelf). */
private const val DOCK_SLOT_COUNT = 4

/** Apps per row in the grid below the dock. */
private const val APPS_PER_ROW = 5

/** Smooth vertical scroll view that never jumps on focus: MainActivity glides smoothly. */
private class CalmVScroll(c: Context) : ScrollView(c) {
    init { overScrollMode = View.OVER_SCROLL_NEVER }
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
}

/**
 * Dynamic ambient illumination view that renders Apple tvOS-style fluid backlight bloom
 * reflecting the focused app's dominant signature color with zero allocations during animation.
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
            w * 0.5f, h * 0.52f, w * 0.68f,
            intArrayOf(Color.argb(92, cr, cg, cb), Color.argb(38, cr, cg, cb), Color.TRANSPARENT),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, w, h, p)
    }
}

class MainActivity : Activity() {

    private class Sec(val id: String, val header: View?, val row: LinearLayout)

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val decel = DecelerateInterpolator(2.0f)

    // Art is rendered once at exact tile size, then cached. 12 MB cap keeps RAM minimal.
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val colorMap = HashMap<String, Int>()

    private lateinit var prefs: SharedPreferences
    private lateinit var wallpaper: ImageView
    private lateinit var ambientGlow: AmbientBloomView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var wifiIcon: WifiView
    private lateinit var wifiText: TextView
    private val sections = ArrayList<Sec>()
    private var vAnim: ObjectAnimator? = null
    private var ambientAnim: ValueAnimator? = null
    private var currentAmbient = Color.TRANSPARENT
    private var targetAmbient = Color.TRANSPARENT
    private var lastKey = ""
    private var signature = ""
    private var appSig = ""
    private var wifiState = ""
    private var restoring = false
    private var animateIn = true
    private var reduce = false

    private val tick = object : Runnable {
        override fun run() { refreshWifi(); ui.postDelayed(this, 10000) }
    }

    private val presets = listOf(
        "Cosmos" to intArrayOf(0xFF080D1A.toInt(), 0xFF121B35.toInt(), 0xFF241C48.toInt()),
        "Sunset" to intArrayOf(0xFF140818.toInt(), 0xFF4E1638.toInt(), 0xFF9E3A30.toInt()),
        "Pacific" to intArrayOf(0xFF02121E.toInt(), 0xFF083244.toInt(), 0xFF106864.toInt()),
        "Aurora" to intArrayOf(0xFF03100B.toInt(), 0xFF0A2B1D.toInt(), 0xFF1D5A3A.toInt()),
        "Slate" to intArrayOf(0xFF0B0D11.toInt(), 0xFF161922.toInt(), 0xFF252936.toInt())
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

        val root = FrameLayout(this)
        wallpaper = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))

        ambientGlow = AmbientBloomView(this)
        root.addView(ambientGlow, FrameLayout.LayoutParams(-1, -1))

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }

        // Apple tvOS-style top bar: minimalist, elegant, floating
        val dateV = TextClock(this).apply {
            format12Hour = "EEEE, MMMM d"; format24Hour = "EEEE, MMMM d"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(0x99FFFFFF.toInt())
            letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            setPadding(dp(52), dp(22), dp(52), dp(4))
        }
        bar.addView(dateV, LinearLayout.LayoutParams(0, -2, 1f))

        // tvOS top-right status capsule cluster: Time, Wi-Fi, Wallpaper, Settings
        val timeV = TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val timeCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0x28FFFFFF)
            }
            addView(timeV)
        }
        bar.addView(timeCapsule)

        wifiIcon = WifiView(this)
        val wifi = pill(wifiIcon, "Wi-Fi") { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        wifiText = wifi.getChildAt(1) as TextView
        bar.addView(wifi, pillLp())
        bar.addView(pill(null, "Wallpaper") { customize() }, pillLp())
        bar.addView(pill(null, "Settings") { openSettings(Settings.ACTION_SETTINGS) }, pillLp())

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(80))
        }
        scroll = CalmVScroll(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            addView(content)
        }
        page.addView(bar)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        window.setBackgroundDrawable(null)
        applyWallpaper()
    }

    override fun onResume() {
        super.onResume()
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        allTiles().firstOrNull()?.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { currentFocus?.performLongClick(); return true }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Home screen: Back does nothing")
    override fun onBackPressed() {}

    // ───────────────────────── Data & Dock ─────────────────────────

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

    /** Returns exactly the 4 packages assigned to the dock, auto-filling remaining slots if needed. */
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
        if (currentSig(aSig) != signature) rebuild(all)
    }

    // ───────────────────────── tvOS Layout ─────────────────────────

    private fun rebuild(all: List<ResolveInfo> = loadApps()) {
        val hidden = prefs.getStringSet("hidden", emptySet<String>()) ?: emptySet()
        val byPkg = all.associateBy { it.activityInfo.packageName }

        val dockPkgs = getDockPackages(all)
        val dockApps = dockPkgs.mapNotNull { byPkg[it] }
        val otherApps = all.filter { it.activityInfo.packageName !in hidden && it.activityInfo.packageName !in dockPkgs }

        signature = currentSig(appSig)
        content.removeAllViews()
        sections.clear()

        // ───────────────── Fully Screen-Adaptive Dimensions ─────────────────
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Active layout container spans 84% of display width (symmetrically centered with 8% side margins)
        val contentW = (screenW * 0.84f).toInt()

        // 1. Dock shelf & 4-card calculations
        val dockGap = dp(14)
        val shelfPadH = dp(18)
        val shelfPadV = dp(14)
        val dockCardW = (contentW - (2 * shelfPadH) - (DOCK_SLOT_COUNT - 1) * dockGap) / DOCK_SLOT_COUNT
        val dockCardH = (dockCardW * 9 / 16)
        val dockCardR = dockCardH * 0.16f
        val shelfR = dockCardH * 0.22f

        // 2. Multi-row grid calculations (5 cards per row)
        val gridGap = dp(14)
        val gridCardW = (contentW - (APPS_PER_ROW - 1) * gridGap) / APPS_PER_ROW
        val gridCardH = (gridCardW * 9 / 16)
        val gridCardR = gridCardH * 0.16f

        // 3. Viewport & below-the-fold peek calculation
        val topBarH = dp(56)
        val availableH = screenH - topBarH
        val peekH = (gridCardH * 0.36f).toInt()
        val targetWrapperH = availableH - peekH
        val shelfH = dockCardH + (2 * shelfPadV)
        val topSpace = maxOf(dp(50), targetWrapperH - shelfH - dp(26))

        // 1. APPLE TV-STYLE TOP SHELF FAVORITES DOCK
        addDockSection(
            dockApps = dockApps,
            byPkg = byPkg,
            hasAppsBelow = otherApps.isNotEmpty(),
            targetWrapperH = targetWrapperH,
            topSpace = topSpace,
            shelfPadH = shelfPadH,
            shelfPadV = shelfPadV,
            shelfR = shelfR,
            cardW = dockCardW,
            cardH = dockCardH,
            cardR = dockCardR,
            cardGap = dockGap
        )

        // 2. APPS GRID BELOW (Multi-row grid, revealed on scrolling down)
        if (otherApps.isNotEmpty()) {
            addMultiRowAppsSection(
                apps = otherApps,
                cardW = gridCardW,
                cardH = gridCardH,
                cardR = gridCardR,
                cardGap = gridGap,
                rowWidth = contentW
            )
        }

        content.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                content.viewTreeObserver.removeOnGlobalLayoutListener(this)
                restoring = true
                val t = allTiles()
                (t.firstOrNull { it.tag == lastKey } ?: t.firstOrNull())?.requestFocus()
                restoring = false
                if (animateIn) {
                    animateIn = false
                    content.alpha = 0f
                    content.translationY = dp(24).toFloat()
                    content.animate().alpha(1f).translationY(0f).setDuration(ms(360))
                        .setInterpolator(decel).withLayer().start()
                }
            }
        })
    }

    private fun allTiles(): List<View> {
        val out = ArrayList<View>()
        for (s in sections) {
            for (j in 0 until s.row.childCount) out.add(s.row.getChildAt(j))
        }
        return out
    }

    /**
     * Apple tvOS 4-App Favorites Dock.
     * Encapsulated in a frosted glass shelf capsule with rounded corners,
     * fitted symmetrically to screen width and height.
     */
    private fun addDockSection(
        dockApps: List<ResolveInfo>,
        byPkg: Map<String, ResolveInfo>,
        hasAppsBelow: Boolean,
        targetWrapperH: Int,
        topSpace: Int,
        shelfPadH: Int,
        shelfPadV: Int,
        shelfR: Float,
        cardW: Int,
        cardH: Int,
        cardR: Float,
        cardGap: Int
    ) {
        val dockWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            if (hasAppsBelow) {
                minimumHeight = targetWrapperH
                setPadding(0, topSpace, 0, dp(26))
            } else {
                setPadding(0, dp(120), 0, dp(60))
            }
        }

        val dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }

        // Apple tvOS signature frosted glass capsule shelf
        val shelf = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
            setPadding(shelfPadH, shelfPadV, shelfPadH, shelfPadV)
            background = GradientDrawable().apply {
                cornerRadius = shelfR
                setColor(0x44101626.toInt()) // Sleek frosted obsidian dark-glass
                setStroke(dp(1), 0x30FFFFFF.toInt()) // Subtle glass rim specular reflection
            }
            addView(dockRow)
        }
        dockWrapper.addView(shelf)
        content.addView(dockWrapper)

        val sec = Sec("dock", null, dockRow)
        sections.add(sec)

        dockApps.forEachIndexed { slot, ri ->
            val dominant = getOrComputeColor(ri)
            val tileView = appTile(
                ri = ri,
                sec = sec,
                big = true,
                isDock = true,
                dockSlot = slot,
                byPkg = byPkg,
                cardW = cardW,
                cardH = cardH,
                cardR = cardR,
                dominantColor = dominant
            )
            val lp = LinearLayout.LayoutParams(cardW, -2).apply {
                if (slot < dockApps.size - 1) rightMargin = cardGap
            }
            dockRow.addView(tileView, lp)
        }
    }

    /** Multi-row Apps grid below the fold (5 apps per row, screen-adaptive). */
    private fun addMultiRowAppsSection(
        apps: List<ResolveInfo>,
        cardW: Int,
        cardH: Int,
        cardR: Float,
        cardGap: Int,
        rowWidth: Int
    ) {
        val byPkg = apps.associateBy { it.activityInfo.packageName }
        val rows = apps.chunked(APPS_PER_ROW)

        rows.forEachIndexed { rowIndex, rowApps ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(8), 0, dp(14))
            }
            val rowLp = LinearLayout.LayoutParams(rowWidth, -2)
            content.addView(row, rowLp)

            val sec = Sec("app_row_$rowIndex", null, row)
            sections.add(sec)

            rowApps.forEachIndexed { colIndex, ri ->
                val dominant = getOrComputeColor(ri)
                val tileView = appTile(
                    ri = ri,
                    sec = sec,
                    big = false,
                    isDock = false,
                    dockSlot = -1,
                    byPkg = byPkg,
                    cardW = cardW,
                    cardH = cardH,
                    cardR = cardR,
                    dominantColor = dominant
                )
                val lp = LinearLayout.LayoutParams(cardW, -2).apply {
                    if (colIndex < rowApps.size - 1) rightMargin = cardGap
                }
                row.addView(tileView, lp)
            }
        }
    }

    // ───────────────────────── Tiles & Focus Animations ─────────────────────────

    private fun appTile(
        ri: ResolveInfo,
        sec: Sec,
        big: Boolean,
        isDock: Boolean,
        dockSlot: Int,
        byPkg: Map<String, ResolveInfo>,
        cardW: Int,
        cardH: Int,
        cardR: Float,
        dominantColor: Int
    ): View {
        val pkg = ri.activityInfo.packageName
        val cls = ri.activityInfo.name
        val name = ri.loadLabel(packageManager).toString()
        return tile(
            sec = sec,
            id = pkg,
            name = name,
            big = big,
            cardW = cardW,
            cardH = cardH,
            cardR = cardR,
            dominantColor = dominantColor,
            make = { w, h, r -> appArt(ri, w, h, r, dominantColor) },
            click = { launch(pkg, cls) },
            long = { openTileMenu(pkg, name, isDock, dockSlot, byPkg) }
        )
    }

    private fun tile(
        sec: Sec,
        id: String,
        name: String,
        big: Boolean,
        cardW: Int,
        cardH: Int,
        cardR: Float,
        dominantColor: Int,
        make: (Int, Int, Float) -> Bitmap,
        click: () -> Unit,
        long: (() -> Unit)?
    ): View {
        val key = sec.id + "|" + id
        val artKey = id + "|" + cardW + "|" + cardH

        val border = GradientDrawable().apply {
            cornerRadius = cardR
            setColor(Color.TRANSPARENT)
            setStroke(0, Color.TRANSPARENT)
        }

        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            background = GradientDrawable().apply {
                setColor(blendColor(dominantColor, 0xFF141926.toInt(), 0.75f))
                cornerRadius = cardR
            }
            foreground = border
            tag = artKey
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, cardR) }
            }
        }
        loadArt(img, artKey) { make(cardW, cardH, cardR) }

        val label = TextView(this).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (big) 12.5f else 11.5f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            alpha = 0f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(7), 0, 0)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            isFocusable = true
            isClickable = true
            tag = key
            addView(img, LinearLayout.LayoutParams(cardW, cardH))
            addView(label, LinearLayout.LayoutParams(cardW, -2))

            // Apple tvOS focus animation:
            // Tiles lift forward (-14dp dock / -7dp grid), scale with overshoot physics (1.18x),
            // project a deep 36dp drop shadow, and show a high-contrast white focus halo.
            setOnFocusChangeListener { v, f ->
                val s = if (f) (if (big) 1.18f else 1.16f) else 1.0f
                val lift = if (f) (if (big) -dp(14).toFloat() else -dp(7).toFloat()) else 0f
                val shadowZ = if (f) (if (big) dp(36).toFloat() else dp(26).toFloat()) else 0f

                border.setStroke(dp(3.5f), if (f) Color.WHITE else Color.TRANSPARENT)

                img.animate()
                    .scaleX(s)
                    .scaleY(s)
                    .translationY(lift)
                    .translationZ(shadowZ)
                    .setInterpolator(decel)
                    .setDuration(ms(180))
                    .withLayer()
                    .start()

                label.animate()
                    .alpha(if (f) 1f else 0f)
                    .setDuration(ms(140))
                    .start()

                if (f) {
                    lastKey = key
                    onTileFocused(v, sec, dominantColor)
                }
            }
            setOnClickListener { click() }
            if (long != null) setOnLongClickListener { long(); true }
        }
    }

    /** Off-thread art rendering; subsequent draws hit the 12 MB RAM cache instantly. */
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

    /** Extracts the dominant signature hue of an app's icon with zero allocations. */
    private fun extractDominantColor(bmp: Bitmap): Int {
        val w = bmp.width
        val h = bmp.height
        val stepX = maxOf(1, w / 16)
        val stepY = maxOf(1, h / 16)
        var maxScore = -1f
        var bestColor = 0xFF2A364F.toInt()
        val hsv = FloatArray(3)

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val pixel = bmp.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 100) continue

                Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val val_ = hsv[2]

                // Prioritize saturated, medium-bright colors representing the brand identity
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
            c.drawColor(0xFF141926.toInt())
            banner.setBounds(0, 0, w, h)
            banner.draw(c)
        } else {
            // Apple tvOS-style card:
            // Rich gradient tinted with the app's signature dominant brand color
            val topColor = blendColor(dominantColor, 0xFF2A364F.toInt(), 0.50f)
            val bottomColor = blendColor(dominantColor, 0xFF0D121D.toInt(), 0.85f)

            val p = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    topColor, bottomColor,
                    Shader.TileMode.CLAMP
                )
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

            // Apple tvOS glossy top specular sheen
            val sheen = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h * 0.48f,
                    0x33FFFFFF, 0x00FFFFFF,
                    Shader.TileMode.CLAMP
                )
            }
            c.drawRect(0f, 0f, w.toFloat(), h * 0.48f, sheen)

            // Subtle specular inner glass rim
            val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = 0x2EFFFFFF
            }
            c.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), r, r, rim)

            // Centered native app icon with crisp proportions
            val icon = ri.loadIcon(pm)
            val iconSize = (h * 0.52f).toInt()
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

    // ───────────────────────── Smooth Scrolling & Ambient Bloom ─────────────────────────

    private fun onTileFocused(tile: View, sec: Sec, dominantColor: Int) {
        val maxY = maxOf(0, content.height - scroll.height)
        // In the dock: stay at the pristine top of the home screen (scrollY = 0) with peek visible.
        // In app rows: smoothly glide down to center the active row comfortably on screen.
        val wantY = if (sec.id == "dock") 0 else {
            val rowTop = sec.row.top
            (rowTop - dp(70)).coerceIn(0, maxY)
        }
        glideY(wantY)
        transitionAmbient(dominantColor)
    }

    private fun transitionAmbient(color: Int) {
        if (reduce || targetAmbient == color) return
        targetAmbient = color
        ambientAnim?.cancel()
        val from = if (currentAmbient == Color.TRANSPARENT) 0x00000000 else currentAmbient
        val to = color
        ambientAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 380
            interpolator = decel
            addUpdateListener { va ->
                currentAmbient = va.animatedValue as Int
                ambientGlow.ambientColor = currentAmbient
            }
            start()
        }
    }

    private fun glideY(wantY: Int) {
        vAnim?.cancel()
        if (restoring) scroll.scrollTo(0, wantY)
        else if (scroll.scrollY != wantY) {
            vAnim = ObjectAnimator.ofInt(scroll, "scrollY", scroll.scrollY, wantY).apply {
                duration = ms(300); interpolator = decel; start()
            }
        }
    }

    // ───────────────────────── Actions & Dock Management ─────────────────────────

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

    /** Contextual tvOS menu for rearranging the 4-app Favorites Dock, hiding apps, or viewing app info. */
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
                opt("Move Left in Dock") {
                    Collections.swap(currentDock, dockSlot, dockSlot - 1)
                    save("fav", currentDock)
                    lastKey = "dock|$pkg"
                    rebuild()
                }
            }
            if (dockSlot < currentDock.size - 1) {
                opt("Move Right in Dock") {
                    Collections.swap(currentDock, dockSlot, dockSlot + 1)
                    save("fav", currentDock)
                    lastKey = "dock|$pkg"
                    rebuild()
                }
            }
            opt("Remove from Dock") {
                currentDock.remove(pkg)
                save("fav", currentDock)
                lastKey = ""
                rebuild()
            }
        } else {
            // Pin or replace in one of the 4 dock slots
            opt("Pin to Favorites Dock") {
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
                        lastKey = "dock|$pkg"
                        rebuild()
                    }
                    .show()
            }
        }

        opt("Hide from Home") {
            val hid = prefs.getStringSet("hidden", emptySet<String>())!!.toMutableSet()
            hid.add(pkg)
            prefs.edit().putStringSet("hidden", hid).apply()
            currentDock.remove(pkg)
            save("fav", currentDock)
            lastKey = ""
            rebuild()
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
        AlertDialog.Builder(this).setTitle("Customize Wallpaper")
            .setItems(names.toTypedArray()) { _, i ->
                when {
                    i < presets.size -> { prefs.edit().putInt("wp", i).apply(); applyWallpaper() }
                    i == presets.size -> {
                        if (customFile() != null) { prefs.edit().putInt("wp", i).apply(); applyWallpaper() }
                        else Toast.makeText(this,
                            "Run on your PC:\nadb push photo.jpg /sdcard/Android/data/$packageName/files/wallpaper.jpg",
                            Toast.LENGTH_LONG).show()
                    }
                    i == presets.size + 1 -> { prefs.edit().remove("hidden").apply(); rebuild() }
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

    // ───────────────────────── Wallpaper ─────────────────────────

    private fun customFile(): File? {
        val dir = getExternalFilesDir(null) ?: return null
        return listOf("wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png")
            .map { File(dir, it) }.firstOrNull { it.exists() }
    }

    private fun shade(cv: Canvas, w: Int, h: Int) {
        val p = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x50000000.toInt(), 0x05000000, 0x77000000.toInt()),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    private fun gradientBitmap(c: IntArray): Bitmap {
        val w = 640
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.85f, h * 0.12f, w * 0.6f,
            intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        shade(cv, w, h)
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
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
                inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565; inMutable = true
            }) ?: return null
            shade(Canvas(bmp), bmp.width, bmp.height)
            return bmp
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
                    } else {
                        Toast.makeText(this, "Could not load wallpaper, using a preset", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ───────────────────────── Top Bar Bits ─────────────────────────

    private fun pillLp() = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) }

    private fun pill(icon: View?, text: String, onClick: () -> Unit): LinearLayout {
        val bg = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0x28FFFFFF) }
        val label = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(15), dp(6))
            background = bg
            isFocusable = true
            isClickable = true
            if (icon != null) addView(icon, LinearLayout.LayoutParams(dp(18), dp(14)).apply { rightMargin = dp(6) })
            addView(label)
            setOnFocusChangeListener { v, f ->
                bg.setColor(if (f) 0xF2FFFFFF.toInt() else 0x28FFFFFF)
                label.setTextColor(if (f) 0xFF111111.toInt() else Color.WHITE)
                (icon as? WifiView)?.let { it.dark = f; it.invalidate() }
                v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f)
                    .setInterpolator(decel).setDuration(ms(150)).start()
                if (f) {
                    glideY(0)
                    transitionAmbient(Color.TRANSPARENT)
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun refreshWifi() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            var level = 3
            var text = "Online"
            when {
                caps == null -> { level = 0; text = "Offline" }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    @Suppress("DEPRECATION")
                    val rssi = wm.connectionInfo.rssi
                    @Suppress("DEPRECATION")
                    level = WifiManager.calculateSignalLevel(rssi, 4).coerceIn(1, 3)
                    text = "Wi-Fi"
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> text = "Ethernet"
            }
            val st = "$level|$text"
            if (st != wifiState) {
                wifiState = st
                wifiIcon.level = level
                wifiText.text = text
                wifiIcon.invalidate()
            }
        } catch (e: Exception) { /* ignore */ }
    }
}

/** Tiny hand-drawn Wi-Fi glyph (no image assets). level 0..3 */
internal class WifiView(c: Context) : View(c) {
    var level = 0
    var dark = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    override fun onDraw(cv: Canvas) {
        val h = height.toFloat()
        val cx = width / 2f
        val cy = h * 0.95f
        val base = if (dark) 0xFF111111.toInt() else Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = h * 0.11f
        for (i in 1..3) {
            p.color = base
            p.alpha = if (i <= level) 255 else 70
            val r = h * 0.3f * i
            cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 225f, 90f, false, p)
        }
        p.style = Paint.Style.FILL
        p.color = base
        p.alpha = if (level > 0) 255 else 70
        cv.drawCircle(cx, cy, h * 0.07f, p)
    }
}
