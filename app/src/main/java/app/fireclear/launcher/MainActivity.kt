package app.fireclear.launcher

import android.animation.ObjectAnimator
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

class MainActivity : Activity() {

    private class Sec(val id: String, val header: View?, val row: LinearLayout)

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val decel = DecelerateInterpolator(1.8f)

    // Art is rendered once at exact tile size, then cached. 12 MB cap keeps RAM minimal.
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var wallpaper: ImageView
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var wifiIcon: WifiView
    private lateinit var wifiText: TextView
    private val sections = ArrayList<Sec>()
    private var vAnim: ObjectAnimator? = null
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
        if (aSig != appSig) { appSig = aSig; cache.evictAll() }
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

        // 1. APPLE TV-STYLE TOP SHELF FAVORITES DOCK
        // Positioned in the lower screen with generous cinematic negative space above,
        // and measured so the tops of the next row of apps peek through from below the fold.
        addDockSection(dockApps, byPkg, hasAppsBelow = otherApps.isNotEmpty())

        // 2. APPS GRID BELOW (Multi-row grid, revealed on scrolling down)
        if (otherApps.isNotEmpty()) {
            addMultiRowAppsSection(otherApps)
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
     * Encapsulated in a frosted glass shelf capsule with rounded corners.
     */
    private fun addDockSection(dockApps: List<ResolveInfo>, byPkg: Map<String, ResolveInfo>, hasAppsBelow: Boolean) {
        val dm = resources.displayMetrics
        val screenH = dm.heightPixels
        val topBarH = dp(56)
        val availableH = screenH - topBarH
        val peekH = dp(34)
        val targetWrapperH = availableH - peekH
        val dockShelfH = dp(132) // 102dp card + 30dp shelf padding
        val topSpace = maxOf(dp(64), targetWrapperH - dockShelfH - dp(30))

        val dockWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            if (hasAppsBelow) {
                minimumHeight = targetWrapperH
                setPadding(0, topSpace, 0, dp(30))
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
            setPadding(dp(20), dp(14), dp(20), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0x40101626.toInt()) // Sleek frosted obsidian dark-glass
                setStroke(dp(1), 0x30FFFFFF.toInt()) // Subtle glass rim specular reflection
            }
            addView(dockRow)
        }
        dockWrapper.addView(shelf)
        content.addView(dockWrapper)

        val sec = Sec("dock", null, dockRow)
        sections.add(sec)

        dockApps.forEachIndexed { slot, ri ->
            dockRow.addView(appTile(ri, sec, big = true, isDock = true, dockSlot = slot, byPkg = byPkg))
        }
    }

    /** Multi-row Apps grid below the fold (5 apps per row, centered). */
    private fun addMultiRowAppsSection(apps: List<ResolveInfo>) {
        val byPkg = apps.associateBy { it.activityInfo.packageName }
        val rows = apps.chunked(APPS_PER_ROW)

        rows.forEachIndexed { rowIndex, rowApps ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(dp(48), dp(8), dp(48), dp(14))
            }
            content.addView(row)

            val sec = Sec("app_row_$rowIndex", null, row)
            sections.add(sec)

            rowApps.forEach { ri ->
                row.addView(appTile(ri, sec, big = false, isDock = false, dockSlot = -1, byPkg = byPkg))
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
        byPkg: Map<String, ResolveInfo>
    ): View {
        val pkg = ri.activityInfo.packageName
        val cls = ri.activityInfo.name
        val name = ri.loadLabel(packageManager).toString()
        return tile(
            sec = sec,
            id = pkg,
            name = name,
            big = big,
            make = { w, h, r -> appArt(ri, w, h, r) },
            click = { launch(pkg, cls) },
            long = { openTileMenu(pkg, name, isDock, dockSlot, byPkg) }
        )
    }

    private fun tile(
        sec: Sec,
        id: String,
        name: String,
        big: Boolean,
        make: (Int, Int, Float) -> Bitmap,
        click: () -> Unit,
        long: (() -> Unit)?
    ): View {
        // Refined tvOS proportions: compact, elegant 16:9 ratio
        val w = dp(if (big) 180 else 144)
        val h = dp(if (big) 102 else 82)
        val r = dp(if (big) 16 else 12).toFloat()
        val key = sec.id + "|" + id
        val artKey = id + "|" + w

        val border = GradientDrawable().apply {
            cornerRadius = r
            setColor(Color.TRANSPARENT)
            setStroke(0, Color.TRANSPARENT)
        }

        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            background = GradientDrawable().apply { setColor(0xFF141926.toInt()); cornerRadius = r }
            foreground = border
            tag = artKey
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, r) }
            }
        }
        loadArt(img, artKey) { make(w, h, r) }

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
            addView(img, LinearLayout.LayoutParams(w, h))
            addView(label, LinearLayout.LayoutParams(w, -2))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(dp(if (big) 9 else 8), 0, dp(if (big) 9 else 8), 0)
            }

            // Apple tvOS focus animation:
            // Dock tiles lift out of the dock capsule (-14dp), scale, and project a deep 32dp shadow.
            // Grid tiles lift -6dp with a 24dp shadow and white focus glow ring.
            setOnFocusChangeListener { v, f ->
                val s = if (f) 1.15f else 1.0f
                val lift = if (f) (if (big) -dp(14).toFloat() else -dp(6).toFloat()) else 0f
                val shadowZ = if (f) (if (big) dp(32).toFloat() else dp(24).toFloat()) else 0f

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
                    .setDuration(ms(150))
                    .start()

                if (f) {
                    lastKey = key
                    onTileFocused(v, sec)
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

    private fun appArt(ri: ResolveInfo, w: Int, h: Int, r: Float): Bitmap {
        val pm = packageManager
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(src)
        val banner = ri.activityInfo.loadBanner(pm)
        if (banner != null) {
            c.drawColor(0xFF141926.toInt())
            banner.setBounds(0, 0, w, h)
            banner.draw(c)
        } else {
            // Elegant Apple tvOS / visionOS inspired depth tile
            val p = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    0xFF222B42.toInt(), 0xFF101422.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

            // Inner subtle specular highlight ring
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = 0x22FFFFFF
            }
            c.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), r, r, rimPaint)

            // Native icon centered with crisp proportions
            val icon = ri.loadIcon(pm)
            val s = (h * 0.52f).toInt()
            val l = (w - s) / 2
            val t = (h - s) / 2
            icon.setBounds(l, t, l + s, t + s)
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

    // ───────────────────────── Smooth Scrolling ─────────────────────────

    private fun onTileFocused(tile: View, sec: Sec) {
        val maxY = maxOf(0, content.height - scroll.height)
        // In the dock: stay at the pristine top of the home screen (scrollY = 0) with peek visible.
        // In app rows: smoothly glide down to center the active row comfortably on screen.
        val wantY = if (sec.id == "dock") 0 else {
            val rowTop = sec.row.top
            (rowTop - dp(84)).coerceIn(0, maxY)
        }
        glideY(wantY)
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
                if (f) glideY(0)
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
