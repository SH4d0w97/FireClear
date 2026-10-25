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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors

/** Custom wallpapers above this size are ignored (another app could drop a huge file in our folder). */
private const val MAX_WALLPAPER_BYTES = 25L * 1024 * 1024

/** Scroll views that never jump on focus: MainActivity glides them itself (tvOS-style centring). */
private class CalmHScroll(c: Context) : HorizontalScrollView(c) {
    init { overScrollMode = View.OVER_SCROLL_NEVER }
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
}

private class CalmVScroll(c: Context) : ScrollView(c) {
    init { overScrollMode = View.OVER_SCROLL_NEVER }
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
}

private class Shortcut(val title: String, val action: String)

class MainActivity : Activity() {

    private class Sec(val id: String, val header: View, val hs: HorizontalScrollView, val row: LinearLayout) {
        var anim: ObjectAnimator? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val decel = DecelerateInterpolator(1.4f)

    // Art is rendered once at exact tile size, then shared. 12 MB cap keeps RAM low.
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
        "Aurora" to intArrayOf(0xFF0B1026.toInt(), 0xFF1B2A5C.toInt(), 0xFF5B2A86.toInt()),
        "Sunset" to intArrayOf(0xFF1A0B1E.toInt(), 0xFF7A2E4D.toInt(), 0xFFE8743B.toInt()),
        "Ocean" to intArrayOf(0xFF031B2B.toInt(), 0xFF0B4F6C.toInt(), 0xFF1BA8A0.toInt()),
        "Forest" to intArrayOf(0xFF07140F.toInt(), 0xFF14452F.toInt(), 0xFF5E9E5A.toInt()),
        "Graphite" to intArrayOf(0xFF0D0D10.toInt(), 0xFF1F2128.toInt(), 0xFF3A3D49.toInt())
    )

    // Every system settings page we know about. Only the ones this Fire OS can open are shown.
    private val shortcuts = listOf(
        Shortcut("All Settings", Settings.ACTION_SETTINGS),
        Shortcut("Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
        Shortcut("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
        Shortcut("Display", Settings.ACTION_DISPLAY_SETTINGS),
        Shortcut("Sound", "android.settings.SOUND_SETTINGS"),
        Shortcut("Apps", Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),
        Shortcut("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        Shortcut("Date & Time", Settings.ACTION_DATE_SETTINGS),
        Shortcut("Language", Settings.ACTION_LOCALE_SETTINGS),
        Shortcut("Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS),
        Shortcut("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Shortcut("Privacy", Settings.ACTION_PRIVACY_SETTINGS),
        Shortcut("Developer", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Shortcut("About", Settings.ACTION_DEVICE_INFO_SETTINGS)
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun ms(v: Long) = if (reduce) 0L else v

    // ───────────────────────── lifecycle ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        reduce = prefs.getBoolean("reduce", false)
        getExternalFilesDir(null) // make sure the custom-wallpaper folder exists

        val root = FrameLayout(this)
        wallpaper = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }

        val timeV = TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        val dateV = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(0xB3FFFFFF.toInt())
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(timeV); addView(dateV)
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            setPadding(dp(56), dp(22), dp(56), dp(2))
        }
        bar.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        wifiIcon = WifiView(this)
        val wifi = pill(wifiIcon, "Wi-Fi") { openSettings(Settings.ACTION_WIFI_SETTINGS) }
        wifiText = wifi.getChildAt(1) as TextView
        bar.addView(wifi, pillLp())
        bar.addView(pill(null, "Customize") { customize() }, pillLp())
        bar.addView(pill(null, "Settings") { openSettings(Settings.ACTION_SETTINGS) }, pillLp())

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(0, dp(4), 0, dp(60))
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
        window.setBackgroundDrawable(null) // wallpaper already covers the screen: skip one full-screen fill
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

    // Home pressed while we're already on top: glide back to the first tile
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

    // ───────────────────────── data ─────────────────────────

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

    private fun currentSig(a: String) =
        a + "|" + prefs.getString("fav", "") + "|" + prefs.getString("rec", "") + "|" +
            prefs.getStringSet("hidden", emptySet<String>()).toString()

    private fun refresh() {
        val all = loadApps()
        val aSig = all.joinToString(",") { it.activityInfo.packageName }
        if (aSig != appSig) { appSig = aSig; cache.evictAll() }
        if (currentSig(aSig) != signature) rebuild(all)
    }

    // ───────────────────────── home rows ─────────────────────────

    private fun rebuild(all: List<ResolveInfo> = loadApps()) {
        val hidden = prefs.getStringSet("hidden", emptySet<String>())!!
        val byPkg = all.associateBy { it.activityInfo.packageName }
        val favs = list("fav").filter { byPkg.containsKey(it) && it !in hidden }.mapNotNull { byPkg[it] }
        val recents = list("rec").filter { byPkg.containsKey(it) && it !in hidden }.take(10).mapNotNull { byPkg[it] }
        val shown = all.filter { it.activityInfo.packageName !in hidden }
        val sets = shortcuts.filter {
            it.action == Settings.ACTION_SETTINGS || Intent(it.action).resolveActivity(packageManager) != null
        }
        signature = currentSig(appSig)

        content.removeAllViews()
        sections.clear()

        if (favs.isEmpty()) {
            addSection("fav", "FAVORITES") { _, _ -> }
            hint("Hold OK or press the menu button on any app to add it here")
        } else {
            addSection("fav", "FAVORITES") { row, sec -> favs.forEach { row.addView(appTile(it, sec, true)) } }
        }
        if (recents.isNotEmpty()) {
            addSection("rec", "RECENT") { row, sec -> recents.forEach { row.addView(appTile(it, sec, false)) } }
        }
        addSection("all", "APPS") { row, sec -> shown.forEach { row.addView(appTile(it, sec, false)) } }
        addSection("set", "SETTINGS") { row, sec -> sets.forEach { row.addView(settingTile(it, sec)) } }

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
                    content.animate().alpha(1f).translationY(0f).setDuration(ms(380))
                        .setInterpolator(decel).withLayer().start()
                }
            }
        })
    }

    private fun allTiles(): List<View> {
        val out = ArrayList<View>()
        for (s in sections) for (j in 0 until s.row.childCount) out.add(s.row.getChildAt(j))
        return out
    }

    private fun addSection(id: String, title: String, fill: (LinearLayout, Sec) -> Unit) {
        val head = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0x99FFFFFF.toInt())
            letterSpacing = 0.14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(56), dp(14), 0, 0)
        }
        content.addView(head)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        val hs = CalmHScroll(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            setPadding(dp(46), dp(22), dp(46), dp(26))
            addView(row)
        }
        content.addView(hs)
        val sec = Sec(id, head, hs, row)
        sections.add(sec)
        fill(row, sec)
    }

    private fun hint(t: String) {
        content.addView(TextView(this).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(0x88FFFFFF.toInt())
            setPadding(dp(56), dp(10), 0, dp(10))
        })
    }

    // ───────────────────────── tiles ─────────────────────────

    private fun appTile(ri: ResolveInfo, sec: Sec, big: Boolean): View {
        val pkg = ri.activityInfo.packageName
        val cls = ri.activityInfo.name
        val name = ri.loadLabel(packageManager).toString()
        return tile(sec, pkg, name, big, { w, h, r -> appArt(ri, w, h, r) },
            { launch(pkg, cls) }, { menu(pkg, name) })
    }

    private fun settingTile(s: Shortcut, sec: Sec): View =
        tile(sec, "set:" + s.title, s.title, false, { w, h, r -> cardArt(s.title, w, h, r) },
            { openSettings(s.action) }, null)

    private fun tile(
        sec: Sec, id: String, name: String, big: Boolean,
        make: (Int, Int, Float) -> Bitmap, click: () -> Unit, long: (() -> Unit)?
    ): View {
        val w = dp(if (big) 232 else 176)
        val h = dp(if (big) 130 else 99)
        val r = dp(14).toFloat()
        val key = sec.id + "|" + id
        val artKey = id + "|" + w

        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            background = GradientDrawable().apply { setColor(0xFF1E2640.toInt()); cornerRadius = r }
            tag = artKey
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, r) }
            }
        }
        loadArt(img, artKey) { make(w, h, r) }

        val label = TextView(this).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            alpha = 0f
            setPadding(0, dp(18), 0, 0)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            isFocusable = true
            isClickable = true
            tag = key
            addView(img, LinearLayout.LayoutParams(w, h))
            addView(label, LinearLayout.LayoutParams(w, -2))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(10), 0, dp(10), 0) }
            setOnFocusChangeListener { v, f ->
                val s = if (f) 1.1f else 1f
                img.animate().scaleX(s).scaleY(s)
                    .translationY(if (f) -dp(6).toFloat() else 0f)
                    .translationZ(if (f) dp(18).toFloat() else 0f)
                    .setInterpolator(decel).setDuration(ms(190)).start()
                label.animate().alpha(if (f) 1f else 0f).setDuration(ms(160)).start()
                if (f) { lastKey = key; onTileFocused(v, sec) }
            }
            setOnClickListener { click() }
            if (long != null) setOnLongClickListener { long(); true }
        }
    }

    /** Renders art off the main thread, once per size; later uses hit the cache instantly. */
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
                    img.animate().alpha(1f).setDuration(ms(180)).start()
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
            c.drawColor(0xFF1E2640.toInt())
            banner.setBounds(0, 0, w, h)
            banner.draw(c)
        } else {
            val p = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF303C6C.toInt(), 0xFF1A2040.toInt(), Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            val icon = ri.loadIcon(pm)
            val s = (h * 0.6f).toInt()
            val l = (w - s) / 2
            val t = (h - s) / 2
            icon.setBounds(l, t, l + s, t + s)
            icon.draw(c)
        }
        return rounded(src, r)
    }

    private fun cardArt(title: String, w: Int, h: Int, r: Float): Bitmap {
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(src)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF2B3358.toInt(), 0xFF171C36.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            textSize = h * 0.2f
        }
        while (tp.measureText(title) > w * 0.84f && tp.textSize > 8f) tp.textSize -= 1f
        c.drawText(title, w / 2f, h / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
        return rounded(src, r)
    }

    /** Anti-aliased rounded corners baked into the bitmap: no clipping work at draw time. */
    private fun rounded(src: Bitmap, r: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), r, r, p)
        src.recycle()
        return out
    }

    // ───────────────────────── smooth scrolling ─────────────────────────

    private fun onTileFocused(tile: View, sec: Sec) {
        val hs = sec.hs
        val maxX = maxOf(0, sec.row.right + hs.paddingRight - hs.width)
        val wantX = (sec.row.left + tile.left + tile.width / 2 - hs.width / 2).coerceIn(0, maxX)
        sec.anim?.cancel()
        if (restoring) hs.scrollTo(wantX, 0)
        else if (hs.scrollX != wantX) {
            sec.anim = ObjectAnimator.ofInt(hs, View.SCROLL_X, hs.scrollX, wantX).apply {
                duration = ms(240); interpolator = decel; start()
            }
        }

        val maxY = maxOf(0, content.height - scroll.height)
        glideY(if (sections.firstOrNull() === sec) 0 else (sec.header.top - dp(8)).coerceIn(0, maxY))
    }

    private fun glideY(wantY: Int) {
        vAnim?.cancel()
        if (restoring) scroll.scrollTo(0, wantY)
        else if (scroll.scrollY != wantY) {
            vAnim = ObjectAnimator.ofInt(scroll, View.SCROLL_Y, scroll.scrollY, wantY).apply {
                duration = ms(280); interpolator = decel; start()
            }
        }
    }

    // ───────────────────────── actions ─────────────────────────

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

    private fun menu(pkg: String, name: String) {
        val favs = list("fav").toMutableList()
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        fun opt(t: String, a: () -> Unit) { labels.add(t); actions.add(a) }

        val i = favs.indexOf(pkg)
        if (i >= 0) {
            opt("Remove from Favorites") { favs.removeAt(i); save("fav", favs); rebuild() }
        } else {
            opt("Add to Favorites") { favs.add(pkg); save("fav", favs); rebuild() }
        }
        if (i > 0) {
            opt("Move left") { Collections.swap(favs, i, i - 1); save("fav", favs); lastKey = "fav|$pkg"; rebuild() }
        }
        if (i >= 0 && i < favs.size - 1) {
            opt("Move right") { Collections.swap(favs, i, i + 1); save("fav", favs); lastKey = "fav|$pkg"; rebuild() }
        }
        opt("Hide from home") {
            val hid = prefs.getStringSet("hidden", emptySet<String>())!!.toMutableSet()
            hid.add(pkg)
            prefs.edit().putStringSet("hidden", hid).apply()
            favs.remove(pkg); save("fav", favs)
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
        AlertDialog.Builder(this).setTitle("Customize")
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

    // ───────────────────────── wallpaper ─────────────────────────

    private fun customFile(): File? {
        val dir = getExternalFilesDir(null) ?: return null
        return listOf("wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png")
            .map { File(dir, it) }.firstOrNull { it.exists() }
    }

    private fun shade(cv: Canvas, w: Int, h: Int) {
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                intArrayOf(0x88000000.toInt(), 0x10000000, 0xAA000000.toInt()), null, Shader.TileMode.CLAMP)
        }
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    /** Presets are one small bitmap stretched by the GPU: a single cheap layer, no gradient maths per frame. */
    private fun gradientBitmap(c: IntArray): Bitmap {
        val w = 640
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.85f, h * 0.12f, w * 0.6f,
            intArrayOf(0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
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

    // ───────────────────────── top bar bits ─────────────────────────

    private fun pillLp() = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) }

    private fun pill(icon: View?, text: String, onClick: () -> Unit): LinearLayout {
        val bg = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(0x33FFFFFF) }
        val label = TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.WHITE)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(9), dp(22), dp(9))
            background = bg
            isFocusable = true
            isClickable = true
            if (icon != null) addView(icon, LinearLayout.LayoutParams(dp(26), dp(20)).apply { rightMargin = dp(10) })
            addView(label)
            setOnFocusChangeListener { v, f ->
                bg.setColor(if (f) 0xF2FFFFFF.toInt() else 0x33FFFFFF)
                label.setTextColor(if (f) 0xFF111111.toInt() else Color.WHITE)
                (icon as? WifiView)?.let { it.dark = f; it.invalidate() }
                v.animate().scaleX(if (f) 1.07f else 1f).scaleY(if (f) 1.07f else 1f)
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
                    level = WifiManager.calculateSignalLevel(wm.connectionInfo.rssi, 4).coerceIn(1, 3)
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
