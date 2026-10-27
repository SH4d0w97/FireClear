package app.fireclear.launcher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// ─────────────────────────────────────────────────────────────────────────────
// Design System Tokens (Frosted Glass / Midnight Slate / OLED Black)
// ─────────────────────────────────────────────────────────────────────────────
private const val COLOR_MIDNIGHT_START  = 0xFF0F121C.toInt()
private const val COLOR_MIDNIGHT_END    = 0xFF1B2030.toInt()
private const val COLOR_OLED_BLACK      = 0xFF000000.toInt()

private const val COLOR_TEXT_PRIMARY    = 0xFFFFFFFF.toInt()
private const val COLOR_TEXT_SECONDARY  = 0xFF94A3B8.toInt()
private const val COLOR_TEXT_MUTED      = 0xFF64748B.toInt()
private const val COLOR_PILL_ACTIVE_TEXT = 0xFF0F121C.toInt()
private const val COLOR_FOCUS_RING      = 0xFFE2E8F0.toInt()

private const val GLASS_SURFACE_TOP     = 0x1AFFFFFF // 10% white
private const val GLASS_SURFACE_BOTTOM  = 0x0DFFFFFF // 5% white
private const val GLASS_BORDER_TOP      = 0x40FFFFFF // 25% white specular
private const val GLASS_BORDER_BOTTOM   = 0x0DFFFFFF // 5% white

// ─────────────────────────────────────────────────────────────────────────────
// Custom Low-Overhead Views & Containers
// ─────────────────────────────────────────────────────────────────────────────

/** Smooth vertical scroll view that does not auto-jump abruptly on focus. */
private class CalmVScroll(c: Context) : ScrollView(c) {
    init {
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
    }
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
}

/** Smooth horizontal scroll view without focus jumping. */
private class CalmHScroll(c: Context) : HorizontalScrollView(c) {
    init {
        overScrollMode = View.OVER_SCROLL_NEVER
        isHorizontalScrollBarEnabled = false
    }
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
}

/** Lightweight vector mountain silhouette canvas graphic for Featured Hero Card. */
private class MountainGraphicView(c: Context) : View(c) {
    private val pSilhouette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x24FFFFFF
    }
    private val pMoon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x38FFFFFF
    }
    private val path = Path()

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        path.reset()
        path.moveTo(0f, h)
        path.lineTo(w * 0.22f, h * 0.38f)
        path.lineTo(w * 0.44f, h * 0.68f)
        path.lineTo(w * 0.70f, h * 0.22f)
        path.lineTo(w, h * 0.74f)
        path.lineTo(w, h)
        path.close()
        cv.drawPath(path, pSilhouette)

        // Subtle glowing moon
        val radius = h * 0.14f
        cv.drawCircle(w * 0.82f, h * 0.32f, radius, pMoon)
    }
}

/** Flat 4dp horizontal progress bar for Continue Watching row. */
private class FlatProgressView(c: Context) : View(c) {
    var progress: Float = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    private val pTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x24FFFFFF
        style = Paint.Style.FILL
    }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT_PRIMARY
        style = Paint.Style.FILL
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        cv.drawRoundRect(RectF(0f, 0f, w, h), r, r, pTrack)
        if (progress > 0f) {
            cv.drawRoundRect(RectF(0f, 0f, w * progress, h), r, r, pFill)
        }
    }
}

/** Custom tvOS-style lightweight pill switch. */
private class GlassSwitchView(c: Context) : View(c) {
    var isChecked: Boolean = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }
    private val pTrack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pThumb = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TEXT_PRIMARY
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f

        pTrack.color = if (isChecked) 0x66FFFFFF else 0x1EFFFFFF
        cv.drawRoundRect(RectF(0f, 0f, w, h), r, r, pTrack)

        val thumbRadius = (h - 8f) / 2f
        val thumbX = if (isChecked) (w - thumbRadius - 4f) else (thumbRadius + 4f)
        val thumbY = h / 2f
        cv.drawCircle(thumbX, thumbY, thumbRadius, pThumb)
    }
}

/** Clean line vector glyphs for settings categories and system icons. */
private class GlyphView(c: Context, private val type: Int) : View(c) {
    companion object {
        const val TYPE_DISPLAY    = 1
        const val TYPE_NAVIGATION = 2
        const val TYPE_HOME       = 3
        const val TYPE_PRIVACY    = 4
        const val TYPE_SYSTEM     = 5
        const val TYPE_SEARCH     = 6
    }

    var dark = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val col = if (dark) COLOR_PILL_ACTIVE_TEXT else COLOR_TEXT_PRIMARY
        p.color = col
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.09f

        when (type) {
            TYPE_DISPLAY -> {
                cv.drawRoundRect(RectF(w * 0.12f, h * 0.15f, w * 0.88f, h * 0.68f), w * 0.08f, w * 0.08f, p)
                cv.drawLine(w * 0.38f, h * 0.85f, w * 0.62f, h * 0.85f, p)
                cv.drawLine(w * 0.5f, h * 0.68f, w * 0.5f, h * 0.85f, p)
            }
            TYPE_NAVIGATION -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.85f, h * 0.85f)
                    lineTo(w * 0.5f, h * 0.68f)
                    lineTo(w * 0.15f, h * 0.85f)
                    close()
                }
                cv.drawPath(path, p)
            }
            TYPE_HOME -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.15f)
                    lineTo(w * 0.85f, h * 0.45f)
                    lineTo(w * 0.85f, h * 0.85f)
                    lineTo(w * 0.15f, h * 0.85f)
                    lineTo(w * 0.15f, h * 0.45f)
                    close()
                }
                cv.drawPath(path, p)
                cv.drawLine(w * 0.40f, h * 0.85f, w * 0.40f, h * 0.60f, p)
                cv.drawLine(w * 0.60f, h * 0.85f, w * 0.60f, h * 0.60f, p)
                cv.drawLine(w * 0.40f, h * 0.60f, w * 0.60f, h * 0.60f, p)
            }
            TYPE_PRIVACY -> {
                cv.drawRoundRect(RectF(w * 0.20f, h * 0.42f, w * 0.80f, h * 0.85f), w * 0.08f, w * 0.08f, p)
                val arc = RectF(w * 0.32f, h * 0.15f, w * 0.68f, h * 0.50f)
                cv.drawArc(arc, 180f, 180f, false, p)
            }
            TYPE_SYSTEM -> {
                cv.drawCircle(w * 0.5f, h * 0.5f, w * 0.22f, p)
                for (angle in 0 until 360 step 45) {
                    val rad = Math.toRadians(angle.toDouble())
                    val x1 = (w * 0.5f + Math.cos(rad) * w * 0.30f).toFloat()
                    val y1 = (h * 0.5f + Math.sin(rad) * h * 0.30f).toFloat()
                    val x2 = (w * 0.5f + Math.cos(rad) * w * 0.42f).toFloat()
                    val y2 = (h * 0.5f + Math.sin(rad) * h * 0.42f).toFloat()
                    cv.drawLine(x1, y1, x2, y2, p)
                }
            }
            TYPE_SEARCH -> {
                cv.drawCircle(w * 0.42f, h * 0.42f, w * 0.24f, p)
                cv.drawLine(w * 0.60f, h * 0.60f, w * 0.85f, h * 0.85f, p)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

private enum class AppCategory(val title: String) {
    ALL("All Apps"),
    STREAMING("Streaming"),
    MUSIC("Music"),
    GAMES("Games"),
    UTILITIES("Utilities")
}

private data class AppModel(
    val packageName: String,
    val activityName: String,
    val label: String,
    val category: AppCategory,
    val iconKey: String
)

private data class MediaItem(
    val title: String,
    val remaining: String,
    val progress: Float
)

// ─────────────────────────────────────────────────────────────────────────────
// Main Activity
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val decel = DecelerateInterpolator(1.8f)

    // 16 MB memory cache for 128x128 icons
    private val iconCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var rootContainer: FrameLayout

    // Top Navigation Bar
    private lateinit var navPillContainer: LinearLayout
    private lateinit var tabHome: TextView
    private lateinit var tabApps: TextView
    private lateinit var tabSettings: TextView
    private lateinit var timeClock: TextView
    private lateinit var dateClock: TextView
    private var activeTab = 0 // 0 = Home, 1 = Apps, 2 = Settings

    // Content Containers
    private lateinit var viewContainer: FrameLayout
    private lateinit var viewHome: CalmVScroll
    private lateinit var homeContent: LinearLayout
    private lateinit var viewApps: LinearLayout
    private lateinit var viewSettings: LinearLayout

    // State Variables
    private var reduceMotion = false
    private var appearanceTheme = "Frosted Slate" // "Frosted Slate" or "OLED Black"
    private var screensaverTimeout = "15 minutes"
    private var hdrEnabled = true

    // Apps Screen State
    private var allApps: List<AppModel> = emptyList()
    private var filteredApps: List<AppModel> = emptyList()
    private var selectedCategory = AppCategory.ALL
    private var searchQuery = ""
    private lateinit var appsGridContainer: LinearLayout
    private val categoryPills = mutableMapOf<AppCategory, View>()
    private val categoryBadgeViews = mutableMapOf<AppCategory, TextView>()

    // Settings Screen State
    private var selectedSettingCat = "Display"
    private lateinit var settingsRowsContainer: LinearLayout

    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            ui.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
    private fun ms(v: Long): Long = if (reduceMotion) 0L else v

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("launcher_settings", MODE_PRIVATE)
        loadSettings()

        applyImmersiveMode()

        rootContainer = FrameLayout(this)
        applyRootBackground()

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        // 1. Top Navigation Bar with Centered Pill & Live Clock
        mainLayout.addView(createTopNavigationBar())

        // 2. Main Multi-Screen Content Container
        viewContainer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        mainLayout.addView(viewContainer, LinearLayout.LayoutParams(-1, 0, 1f))

        rootContainer.addView(mainLayout, FrameLayout.LayoutParams(-1, -1))
        setContentView(rootContainer)
        window.setBackgroundDrawable(null)

        setupScreens()
        loadInstalledApps()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        ui.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(clockTick)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    @Deprecated("Back navigation returns to Home, or stays on Home")
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

    private fun loadSettings() {
        reduceMotion = prefs.getBoolean("reduce_motion", false)
        appearanceTheme = prefs.getString("appearance_theme", "Frosted Slate") ?: "Frosted Slate"
        screensaverTimeout = prefs.getString("screensaver_timeout", "15 minutes") ?: "15 minutes"
        hdrEnabled = prefs.getBoolean("hdr_enabled", true)
    }

    private fun applyRootBackground() {
        if (appearanceTheme == "OLED Black") {
            rootContainer.setBackgroundColor(COLOR_OLED_BLACK)
        } else {
            val bg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(COLOR_MIDNIGHT_START, COLOR_MIDNIGHT_END)
            )
            rootContainer.background = bg
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top Navigation Bar (Centered Segmented Pill + Live Clock)
    // ─────────────────────────────────────────────────────────────────────────

    private fun createTopNavigationBar(): View {
        val header = RelativeLayout(this).apply {
            setPadding(dp(48), dp(20), dp(48), dp(12))
            clipChildren = false
            clipToPadding = false
        }

        // Live Clock (Top-Right)
        val clockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            id = View.generateViewId()
        }
        timeClock = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        dateClock = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, dp(1), 0, 0)
        }
        clockLayout.addView(timeClock)
        clockLayout.addView(dateClock)
        updateClock()

        val clockLp = RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        header.addView(clockLayout, clockLp)

        // Centered Segmented Glass Pill Bar (Home, Apps, Settings)
        navPillContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0x18FFFFFF) // Simulated glass
                setStroke(dp(1), 0x2AFFFFFF) // Specular border
            }
        }

        tabHome = createPillTab("Home", 0)
        tabApps = createPillTab("Apps", 1)
        tabSettings = createPillTab("Settings", 2)

        navPillContainer.addView(tabHome)
        navPillContainer.addView(tabApps)
        navPillContainer.addView(tabSettings)

        val navLp = RelativeLayout.LayoutParams(-2, -2).apply {
            addRule(RelativeLayout.CENTER_HORIZONTAL)
            addRule(RelativeLayout.CENTER_VERTICAL)
        }
        header.addView(navPillContainer, navLp)

        return header
    }

    private fun updateClock() {
        val now = Date()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        timeClock.text = timeFmt.format(now)
        dateClock.text = dateFmt.format(now)
    }

    private fun createPillTab(title: String, index: Int): TextView {
        return TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setPadding(dp(22), dp(8), dp(22), dp(8))
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    selectTab(index, requestContentFocus = false)
                }
                if (!reduceMotion) {
                    v.animate().scaleX(if (hasFocus) 1.03f else 1f).scaleY(if (hasFocus) 1.03f else 1f)
                        .setInterpolator(decel).setDuration(ms(150)).start()
                }
            }
            setOnClickListener {
                selectTab(index, requestContentFocus = true)
            }
        }
    }

    private fun selectTab(index: Int, requestContentFocus: Boolean) {
        activeTab = index
        updatePillTabsStyle()

        viewHome.visibility = if (index == 0) View.VISIBLE else View.GONE
        viewApps.visibility = if (index == 1) View.VISIBLE else View.GONE
        viewSettings.visibility = if (index == 2) View.VISIBLE else View.GONE

        if (requestContentFocus) {
            when (index) {
                0 -> findFirstFocusable(viewHome)?.requestFocus()
                1 -> findFirstFocusable(viewApps)?.requestFocus()
                2 -> findFirstFocusable(viewSettings)?.requestFocus()
            }
        }
    }

    private fun updatePillTabsStyle() {
        val tabs = listOf(tabHome, tabApps, tabSettings)
        tabs.forEachIndexed { i, tv ->
            val isActive = (i == activeTab)
            if (isActive) {
                tv.background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(COLOR_TEXT_PRIMARY)
                }
                tv.setTextColor(COLOR_PILL_ACTIVE_TEXT)
            } else {
                tv.background = null
                tv.setTextColor(COLOR_TEXT_SECONDARY)
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

    // ─────────────────────────────────────────────────────────────────────────
    // Glass Surface Helper (Low GPU Overhead)
    // ─────────────────────────────────────────────────────────────────────────

    private fun createFrostedCardDrawable(rDp: Float = 16f): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(GLASS_SURFACE_TOP, GLASS_SURFACE_BOTTOM)
        ).apply {
            cornerRadius = dp(rDp).toFloat()
            setStroke(dp(1), GLASS_BORDER_TOP)
        }
    }

    private fun applyFrostedFocus(v: View, bg: GradientDrawable, hasFocus: Boolean, targetScale: Float = 1.04f) {
        if (hasFocus) {
            if (reduceMotion) {
                bg.setStroke(dp(2), COLOR_FOCUS_RING)
                v.scaleX = 1f
                v.scaleY = 1f
            } else {
                bg.setStroke(dp(1.8f), COLOR_TEXT_PRIMARY)
                v.animate().scaleX(targetScale).scaleY(targetScale)
                    .setInterpolator(decel).setDuration(ms(150)).start()
            }
        } else {
            bg.setStroke(dp(1), GLASS_BORDER_TOP)
            if (!reduceMotion) {
                v.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(decel).setDuration(ms(150)).start()
            } else {
                v.scaleX = 1f
                v.scaleY = 1f
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screens Setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupScreens() {
        viewContainer.removeAllViews()

        // 1. Home Tab Screen (Scrollable)
        homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(10), dp(48), dp(48))
            clipChildren = false
            clipToPadding = false
        }
        viewHome = CalmVScroll(this).apply {
            addView(homeContent, FrameLayout.LayoutParams(-1, -2))
        }
        viewContainer.addView(viewHome, FrameLayout.LayoutParams(-1, -1))

        // 2. Apps Tab Screen (2-Pane Split: Left Sidebar 28%, Right Grid 72%)
        viewApps = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(48), dp(10), dp(48), dp(32))
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
        }
        viewContainer.addView(viewApps, FrameLayout.LayoutParams(-1, -1))

        // 3. Settings Tab Screen (2-Pane Split: Left Sidebar 30%, Right Panel 70%)
        viewSettings = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(48), dp(10), dp(48), dp(32))
            clipChildren = false
            clipToPadding = false
            visibility = View.GONE
        }
        viewContainer.addView(viewSettings, FrameLayout.LayoutParams(-1, -1))

        buildHomeScreen()
        buildAppsScreen()
        buildSettingsScreen()
        updatePillTabsStyle()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen 1: Home Tab
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildHomeScreen() {
        homeContent.removeAllViews()

        // 1. Featured Hero Card (2-Column Responsive Row)
        val heroBg = createFrostedCardDrawable(16f)
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(26), dp(22), dp(26), dp(22))
            background = heroBg
            clipChildren = false
            clipToPadding = false
        }

        // Left Side: Text details and Action Pills
        val heroLeft = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val overline = TextView(this).apply {
            text = "FEATURED"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_FOCUS_RING)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val title = TextView(this).apply {
            text = "The Last Horizon"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(4))
        }
        val desc = TextView(this).apply {
            text = "Expedition crew reaches the outer boundary of interstellar space in search of earth's genesis."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_SECONDARY)
            maxLines = 2
        }

        // Action Buttons Row (Resume & Details)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }

        // Resume Pill Button
        val resumeBg = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(COLOR_TEXT_PRIMARY)
        }
        val resumeBtn = TextView(this).apply {
            text = "Resume"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_PILL_ACTIVE_TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(22), dp(8), dp(22), dp(8))
            isFocusable = true
            isClickable = true
            background = resumeBg

            setOnFocusChangeListener { v, f ->
                if (f) {
                    resumeBg.setStroke(dp(2), COLOR_FOCUS_RING)
                    if (!reduceMotion) v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(ms(150)).start()
                } else {
                    resumeBg.setStroke(0, Color.TRANSPARENT)
                    if (!reduceMotion) v.animate().scaleX(1f).scaleY(1f).setDuration(ms(150)).start()
                }
            }
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Resuming The Last Horizon", Toast.LENGTH_SHORT).show()
            }
        }

        // Details Glass Pill Button
        val detailsBg = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(0x18FFFFFF)
            setStroke(dp(1), GLASS_BORDER_TOP)
        }
        val detailsBtn = TextView(this).apply {
            text = "Details"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(22), dp(8), dp(22), dp(8))
            isFocusable = true
            isClickable = true
            background = detailsBg

            setOnFocusChangeListener { v, f ->
                if (f) {
                    detailsBg.setStroke(dp(1.8f), COLOR_FOCUS_RING)
                    detailsBg.setColor(0x28FFFFFF)
                    if (!reduceMotion) v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(ms(150)).start()
                } else {
                    detailsBg.setStroke(dp(1), GLASS_BORDER_TOP)
                    detailsBg.setColor(0x18FFFFFF)
                    if (!reduceMotion) v.animate().scaleX(1f).scaleY(1f).setDuration(ms(150)).start()
                }
            }
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Expedition details & info", Toast.LENGTH_SHORT).show()
            }
        }

        btnRow.addView(resumeBtn)
        btnRow.addView(detailsBtn, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })

        heroLeft.addView(overline)
        heroLeft.addView(title)
        heroLeft.addView(desc)
        heroLeft.addView(btnRow)

        // Right Side: Canvas Mountain Silhouette
        val mountainView = MountainGraphicView(this)

        heroCard.addView(heroLeft, LinearLayout.LayoutParams(0, -2, 0.60f))
        heroCard.addView(mountainView, LinearLayout.LayoutParams(0, dp(150), 0.40f).apply { leftMargin = dp(24) })

        homeContent.addView(heroCard, LinearLayout.LayoutParams(-1, -2))

        // 2. Continue Watching Row
        val continueHeader = TextView(this).apply {
            text = "CONTINUE WATCHING"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.12f
            setPadding(0, dp(26), 0, dp(10))
        }
        homeContent.addView(continueHeader)

        val continueScroll = CalmHScroll(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        val continueRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        val items = listOf(
            MediaItem("Interstellar Odyssey", "42 min left", 0.65f),
            MediaItem("Wilderness Chronicles", "18 min left", 0.30f),
            MediaItem("Cyber Metropolis", "55 min left", 0.15f),
            MediaItem("Deep Ocean Mysteries", "12 min left", 0.82f)
        )

        items.forEachIndexed { i, media ->
            val cardBg = createFrostedCardDrawable(14f)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = cardBg
                isFocusable = true
                isClickable = true
                clipChildren = false
                clipToPadding = false

                setOnFocusChangeListener { v, f ->
                    applyFrostedFocus(v, cardBg, f, 1.04f)
                }
                setOnClickListener {
                    Toast.makeText(this@MainActivity, "Playing ${media.title}", Toast.LENGTH_SHORT).show()
                }
            }

            val mediaTitle = TextView(this).apply {
                text = media.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(COLOR_TEXT_PRIMARY)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 1
            }
            val remaining = TextView(this).apply {
                text = media.remaining
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(0, dp(2), 0, dp(12))
            }
            val progress = FlatProgressView(this).apply {
                this.progress = media.progress
            }

            card.addView(mediaTitle)
            card.addView(remaining)
            card.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))

            val lp = LinearLayout.LayoutParams(dp(220), -2).apply {
                if (i > 0) leftMargin = dp(14)
            }
            continueRow.addView(card, lp)
        }

        continueScroll.addView(continueRow)
        homeContent.addView(continueScroll, LinearLayout.LayoutParams(-1, -2))

        // 3. Your Favourites Row (Pinned quick-launch apps)
        val favHeader = TextView(this).apply {
            text = "FAVOURITES"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.12f
            setPadding(0, dp(26), 0, dp(10))
        }
        homeContent.addView(favHeader)

        val favScroll = CalmHScroll(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        val favRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            id = View.generateViewId()
        }
        favScroll.addView(favRow)
        homeContent.addView(favScroll, LinearLayout.LayoutParams(-1, -2))
    }

    private fun updateFavouritesRow() {
        val favRow = homeContent.findViewById<LinearLayout>(homeContent.childCount - 1)
        // Find the CalmHScroll inner row
        val scroll = homeContent.getChildAt(homeContent.childCount - 1) as? CalmHScroll ?: return
        val row = scroll.getChildAt(0) as? LinearLayout ?: return
        row.removeAllViews()

        val favs = allApps.take(6)
        favs.forEachIndexed { i, app ->
            val cardBg = createFrostedCardDrawable(14f)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(16), dp(10))
                background = cardBg
                isFocusable = true
                isClickable = true
                clipChildren = false
                clipToPadding = false

                setOnFocusChangeListener { v, f ->
                    applyFrostedFocus(v, cardBg, f, 1.05f)
                }
                setOnClickListener { launchApp(app) }
            }

            val iconImg = ImageView(this).apply {
                val bmp = iconCache.get(app.iconKey)
                if (bmp != null) setImageBitmap(bmp)
            }
            val label = TextView(this).apply {
                text = app.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(COLOR_TEXT_PRIMARY)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 1
                setPadding(dp(12), 0, 0, 0)
            }

            card.addView(iconImg, LinearLayout.LayoutParams(dp(36), dp(36)))
            card.addView(label, LinearLayout.LayoutParams(-2, -2))

            val lp = LinearLayout.LayoutParams(dp(180), -2).apply {
                if (i > 0) leftMargin = dp(14)
            }
            row.addView(card, lp)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen 2: Apps Tab (2-Pane Split)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildAppsScreen() {
        viewApps.removeAllViews()

        // 28% Left Sidebar: Search & Categories
        val leftSidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(24), 0)
        }

        val overline = TextView(this).apply {
            text = "YOUR APPS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val header = TextView(this).apply {
            text = "Installed"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(14))
        }
        leftSidebar.addView(overline)
        leftSidebar.addView(header)

        // Rounded Search Bar
        val searchBg = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(0x14FFFFFF)
            setStroke(dp(1), 0x24FFFFFF)
        }
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
            background = searchBg
        }
        val searchIcon = GlyphView(this, GlyphView.TYPE_SEARCH)
        val searchInput = EditText(this).apply {
            hint = "Search apps…"
            setHintTextColor(COLOR_TEXT_SECONDARY)
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setBackgroundColor(Color.TRANSPARENT)
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim() ?: ""
                    filterAndRefreshAppsGrid()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        searchBox.addView(searchIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
        searchBox.addView(searchInput, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(8) })
        leftSidebar.addView(searchBox, LinearLayout.LayoutParams(-1, -2))

        // Category Filter List
        val catList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }

        categoryPills.clear()
        categoryBadgeViews.clear()

        AppCategory.values().forEach { cat ->
            val pillBg = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (selectedCategory == cat) 0x26FFFFFF else Color.TRANSPARENT)
            }
            val pill = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = pillBg
                isFocusable = true
                isClickable = true

                setOnFocusChangeListener { v, f ->
                    if (f) {
                        pillBg.setStroke(dp(1.8f), COLOR_FOCUS_RING)
                        if (!reduceMotion) v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(ms(150)).start()
                    } else {
                        pillBg.setStroke(0, Color.TRANSPARENT)
                        if (!reduceMotion) v.animate().scaleX(1f).scaleY(1f).setDuration(ms(150)).start()
                    }
                }
                setOnClickListener {
                    selectedCategory = cat
                    updateCategorySelection()
                    filterAndRefreshAppsGrid()
                }
            }

            val titleTv = TextView(this).apply {
                text = cat.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (selectedCategory == cat) COLOR_TEXT_PRIMARY else COLOR_TEXT_SECONDARY)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }

            val badgeBg = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0x14FFFFFF)
            }
            val badgeTv = TextView(this).apply {
                text = "0"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                background = badgeBg
            }

            pill.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
            pill.addView(badgeTv)

            categoryPills[cat] = pill
            categoryBadgeViews[cat] = badgeTv
            catList.addView(pill, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        leftSidebar.addView(catList)

        viewApps.addView(leftSidebar, LinearLayout.LayoutParams(0, -1, 0.28f))

        // 72% Right Grid (Scrollable vertical grid of adaptive cards)
        appsGridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val gridScroll = CalmVScroll(this).apply {
            addView(appsGridContainer, FrameLayout.LayoutParams(-1, -2))
        }
        viewApps.addView(gridScroll, LinearLayout.LayoutParams(0, -1, 0.72f))
    }

    private fun updateCategorySelection() {
        categoryPills.forEach { (cat, pill) ->
            val isSel = cat == selectedCategory
            (pill.background as? GradientDrawable)?.setColor(if (isSel) 0x26FFFFFF else Color.TRANSPARENT)
            val titleTv = (pill as LinearLayout).getChildAt(0) as? TextView
            titleTv?.setTextColor(if (isSel) COLOR_TEXT_PRIMARY else COLOR_TEXT_SECONDARY)
        }
    }

    private fun updateCategoryBadges() {
        categoryBadgeViews.forEach { (cat, tv) ->
            val count = if (cat == AppCategory.ALL) allApps.size else allApps.count { it.category == cat }
            tv.text = count.toString()
        }
    }

    private fun filterAndRefreshAppsGrid() {
        filteredApps = allApps.filter { app ->
            val matchCat = selectedCategory == AppCategory.ALL || app.category == selectedCategory
            val matchQuery = searchQuery.isEmpty() || app.label.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }

        appsGridContainer.removeAllViews()
        val columns = 4
        val chunked = filteredApps.chunked(columns)

        chunked.forEachIndexed { rowIndex, rowApps ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(0, if (rowIndex == 0) 0 else dp(14), 0, 0)
            }

            rowApps.forEachIndexed { colIndex, app ->
                val cardBg = createFrostedCardDrawable(14f)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    background = cardBg
                    isFocusable = true
                    isClickable = true
                    clipChildren = false
                    clipToPadding = false

                    setOnFocusChangeListener { v, f ->
                        applyFrostedFocus(v, cardBg, f, 1.05f)
                    }
                    setOnClickListener { launchApp(app) }
                }

                val iconImg = ImageView(this).apply {
                    val bmp = iconCache.get(app.iconKey)
                    if (bmp != null) setImageBitmap(bmp)
                }
                val labelTv = TextView(this).apply {
                    text = app.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    setTextColor(COLOR_TEXT_PRIMARY)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(0, dp(10), 0, 0)
                }

                card.addView(iconImg, LinearLayout.LayoutParams(dp(54), dp(54)))
                card.addView(labelTv, LinearLayout.LayoutParams(-1, -2))

                val lp = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (colIndex > 0) leftMargin = dp(14)
                }
                row.addView(card, lp)
            }

            // Pad remainder of row if incomplete
            val remainder = columns - rowApps.size
            for (i in 0 until remainder) {
                val dummy = View(this)
                val lp = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    leftMargin = dp(14)
                }
                row.addView(dummy, lp)
            }

            appsGridContainer.addView(row)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen 3: Settings Tab (2-Pane Split)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildSettingsScreen() {
        viewSettings.removeAllViews()

        // 30% Left Sidebar
        val leftSidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(24), 0)
        }

        val overline = TextView(this).apply {
            text = "PREFERENCES"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_TEXT_SECONDARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.12f
        }
        val header = TextView(this).apply {
            text = "Settings"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(14))
        }
        leftSidebar.addView(overline)
        leftSidebar.addView(header)

        val categories = listOf(
            "Display" to GlyphView.TYPE_DISPLAY,
            "Navigation" to GlyphView.TYPE_NAVIGATION,
            "Home screen" to GlyphView.TYPE_HOME,
            "Privacy" to GlyphView.TYPE_PRIVACY,
            "System" to GlyphView.TYPE_SYSTEM
        )

        categories.forEach { (catName, glyphType) ->
            val isSel = catName == selectedSettingCat
            val pillBg = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (isSel) 0x26FFFFFF else Color.TRANSPARENT)
            }
            val pill = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = pillBg
                isFocusable = true
                isClickable = true

                setOnFocusChangeListener { v, f ->
                    if (f) {
                        pillBg.setStroke(dp(1.8f), COLOR_FOCUS_RING)
                        if (!reduceMotion) v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(ms(150)).start()
                    } else {
                        pillBg.setStroke(0, Color.TRANSPARENT)
                        if (!reduceMotion) v.animate().scaleX(1f).scaleY(1f).setDuration(ms(150)).start()
                    }
                }
                setOnClickListener {
                    selectedSettingCat = catName
                    buildSettingsScreen()
                }
            }

            val glyph = GlyphView(this, glyphType)
            val titleTv = TextView(this).apply {
                text = catName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(if (isSel) COLOR_TEXT_PRIMARY else COLOR_TEXT_SECONDARY)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(12), 0, 0, 0)
            }

            pill.addView(glyph, LinearLayout.LayoutParams(dp(20), dp(20)))
            pill.addView(titleTv, LinearLayout.LayoutParams(-1, -2))
            leftSidebar.addView(pill, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        viewSettings.addView(leftSidebar, LinearLayout.LayoutParams(0, -1, 0.30f))

        // 70% Right Panel: Glass Container with Setting Rows
        val rightPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val cardBg = createFrostedCardDrawable(16f)
        settingsRowsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = cardBg
        }

        // Setting 1: Appearance Theme
        settingsRowsContainer.addView(createSettingRow(
            title = "Appearance Theme",
            subtitle = "Toggle OLED Black or Frosted Slate background",
            valueText = appearanceTheme
        ) {
            appearanceTheme = if (appearanceTheme == "OLED Black") "Frosted Slate" else "OLED Black"
            prefs.edit().putString("appearance_theme", appearanceTheme).apply()
            applyRootBackground()
            buildSettingsScreen()
        })

        settingsRowsContainer.addView(createDivider())

        // Setting 2: Screensaver Timeout
        settingsRowsContainer.addView(createSettingRow(
            title = "Screensaver Timeout",
            subtitle = "Inactivity period before blanking display",
            valueText = screensaverTimeout
        ) {
            screensaverTimeout = when (screensaverTimeout) {
                "5 minutes" -> "15 minutes"
                "15 minutes" -> "Never"
                else -> "5 minutes"
            }
            prefs.edit().putString("screensaver_timeout", screensaverTimeout).apply()
            buildSettingsScreen()
        })

        settingsRowsContainer.addView(createDivider())

        // Setting 3: Reduce Motion (Switch)
        settingsRowsContainer.addView(createToggleRow(
            title = "Reduce Motion",
            subtitle = "Disables scale animations for low-end chipsets",
            initialChecked = reduceMotion
        ) { isChecked ->
            reduceMotion = isChecked
            prefs.edit().putBoolean("reduce_motion", reduceMotion).apply()
        })

        settingsRowsContainer.addView(createDivider())

        // Setting 4: System Brightness Action
        settingsRowsContainer.addView(createActionRow(
            title = "System Brightness",
            subtitle = "Opens Android system display preferences",
            valueText = "Open System"
        ) {
            try {
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Display settings not available", Toast.LENGTH_SHORT).show()
            }
        })

        settingsRowsContainer.addView(createDivider())

        // Setting 5: HDR Mode (Switch)
        settingsRowsContainer.addView(createToggleRow(
            title = "HDR Video Output",
            subtitle = "Allow HDR10 / Dolby Vision output formats",
            initialChecked = hdrEnabled
        ) { isChecked ->
            hdrEnabled = isChecked
            prefs.edit().putBoolean("hdr_enabled", hdrEnabled).apply()
        })

        rightPanel.addView(settingsRowsContainer, LinearLayout.LayoutParams(-1, -2))

        val footerNote = TextView(this).apply {
            text = "These settings are local to this device and do not affect your Android system settings."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(COLOR_TEXT_MUTED)
            setPadding(dp(8), dp(12), 0, 0)
        }
        rightPanel.addView(footerNote)

        viewSettings.addView(rightPanel, LinearLayout.LayoutParams(0, -1, 0.70f))
    }

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(0x14FFFFFF)
            layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        }
    }

    private fun createSettingRow(title: String, subtitle: String, valueText: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true

            val textLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            val tTv = TextView(this@MainActivity).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(COLOR_TEXT_PRIMARY)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val sTv = TextView(this@MainActivity).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(0, dp(1), 0, 0)
            }
            textLayout.addView(tTv)
            textLayout.addView(sTv)

            val vTv = TextView(this@MainActivity).apply {
                text = valueText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(COLOR_FOCUS_RING)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val chevron = TextView(this@MainActivity).apply {
                text = "›"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(dp(6), 0, 0, 0)
            }

            addView(textLayout, LinearLayout.LayoutParams(0, -2, 1f))
            addView(vTv)
            addView(chevron)

            setOnFocusChangeListener { v, f ->
                if (f) {
                    v.setBackgroundColor(0x18FFFFFF)
                    if (!reduceMotion) v.animate().scaleX(1.015f).scaleY(1.015f).setDuration(ms(150)).start()
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT)
                    if (!reduceMotion) v.animate().scaleX(1f).scaleY(1f).setDuration(ms(150)).start()
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createActionRow(title: String, subtitle: String, valueText: String, onClick: () -> Unit): View {
        return createSettingRow(title, subtitle, valueText, onClick)
    }

    private fun createToggleRow(title: String, subtitle: String, initialChecked: Boolean, onToggle: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val tTv = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(COLOR_TEXT_PRIMARY)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val sTv = TextView(this).apply {
            text = subtitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, dp(1), 0, 0)
        }
        textLayout.addView(tTv)
        textLayout.addView(sTv)

        val switchView = GlassSwitchView(this).apply {
            this.isChecked = initialChecked
        }

        row.addView(textLayout, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(switchView, LinearLayout.LayoutParams(dp(38), dp(22)))

        row.setOnFocusChangeListener { v, f ->
            if (f) {
                v.setBackgroundColor(0x18FFFFFF)
                if (!reduceMotion) v.animate().scaleX(1.015f).scaleY(1.015f).setDuration(ms(150)).start()
            } else {
                v.setBackgroundColor(Color.TRANSPARENT)
                if (!reduceMotion) v.animate().scaleX(1f).scaleY(1f).setDuration(ms(150)).start()
            }
        }
        row.setOnClickListener {
            switchView.isChecked = !switchView.isChecked
            onToggle(switchView.isChecked)
        }

        return row
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Real Package Manager & App Loading (Dispatchers / Worker Thread)
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadInstalledApps() {
        worker.execute {
            val pm = packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

            val resolved = (pm.queryIntentActivities(launcherIntent, 0) +
                            pm.queryIntentActivities(leanbackIntent, 0))
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }

            val models = resolved.map { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                val act = resolveInfo.activityInfo.name
                val label = resolveInfo.loadLabel(pm).toString()
                val iconKey = "$pkg/$act"

                // Decode and cache downsampled 128x128 icon
                if (iconCache.get(iconKey) == null) {
                    val iconDrawable = resolveInfo.loadIcon(pm)
                    val bmp = downsampleIcon(iconDrawable, 128, 128)
                    iconCache.put(iconKey, bmp)
                }

                val category = mapCategory(resolveInfo.activityInfo.applicationInfo, pkg)
                AppModel(pkg, act, label, category, iconKey)
            }.sortedBy { it.label.lowercase() }

            ui.post {
                allApps = models
                updateCategoryBadges()
                filterAndRefreshAppsGrid()
                updateFavouritesRow()
            }
        }
    }

    private fun mapCategory(appInfo: ApplicationInfo, pkg: String): AppCategory {
        val p = pkg.lowercase()
        return when {
            p.contains("youtube") || p.contains("netflix") || p.contains("prime") ||
            p.contains("disney") || p.contains("kodi") || p.contains("plex") || p.contains("video") ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_VIDEO) -> AppCategory.STREAMING

            p.contains("spotify") || p.contains("music") || p.contains("audio") || p.contains("podcast") ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_AUDIO) -> AppCategory.MUSIC

            p.contains("game") || p.contains("arcade") || p.contains("play") ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME) -> AppCategory.GAMES

            else -> AppCategory.UTILITIES
        }
    }

    private fun downsampleIcon(drawable: Drawable, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    private fun launchApp(app: AppModel) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to launch ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }
}
