package com.lumen.tv

import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lumen.tv.data.*
import com.lumen.tv.focus.FocusEngine
import com.lumen.tv.focus.LauncherPage
import com.lumen.tv.focus.rememberFocusEngine
import com.lumen.tv.ui.chrome.TopChrome
import com.lumen.tv.ui.chrome.TopChromeScrim
import com.lumen.tv.ui.chrome.WallpaperLayer
import com.lumen.tv.ui.drawer.AppsScreen
import com.lumen.tv.ui.home.HomeScreen
import com.lumen.tv.ui.settings.SettingsScreen
import com.lumen.tv.ui.theme.Tokens
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var wallpaperRepo: WallpaperRepository
    private lateinit var favouritesStore: FavouritesStore
    private lateinit var settingsHolder: SettingsStateHolder

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            showDefaultHomeFallbackDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window for wallpaper display & fully immersive sticky system bars
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        wallpaperRepo = WallpaperRepository(this)
        favouritesStore = FavouritesStore(this)
        settingsHolder = SettingsStateHolder(this)

        checkTvHomeRole()

        setContent {
            LumenTvLauncherApp(
                wallpaperRepo = wallpaperRepo,
                favouritesStore = favouritesStore,
                settingsHolder = settingsHolder,
                onLaunchApp = { app ->
                    val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        settingsHolder.refreshNetworkState()
    }

    private fun checkTvHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    roleRequestLauncher.launch(intent)
                }
            }
        }
    }

    private fun showDefaultHomeFallbackDialog() {
        AlertDialog.Builder(this)
            .setTitle("Set as TV Home")
            .setMessage("To set Lumen TV as your default TV launcher, open Settings → Apps → Default apps → Home, and select Lumen TV.")
            .setPositiveButton("OK", null)
            .show()
    }
}

@Composable
fun LumenTvLauncherApp(
    wallpaperRepo: WallpaperRepository,
    favouritesStore: FavouritesStore,
    settingsHolder: SettingsStateHolder,
    onLaunchApp: (LaunchableApp) -> Unit
) {
    val wallpaperState by wallpaperRepo.stateFlow.collectAsState(initial = WallpaperState())
    val connectionState by settingsHolder.state.collectAsState()

    var activePage by remember { mutableStateOf(LauncherPage.HOME) }
    val focusEngine = rememberFocusEngine()

    // Page-specific initial focus requesters
    val playFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    val wifiRowFocusRequester = remember { FocusRequester() }

    var allApps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var favouriteApps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    val continueItems = remember { DemoContinueRepository().getContinueItems() }

    val appsGridState = rememberLazyGridState()

    // Load installed apps and favourites
    val favPackages by favouritesStore.favouritesFlow.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val apps = favouritesStore.queryAllLaunchableApps()
        allApps = apps
    }

    LaunchedEffect(allApps, favPackages) {
        favouriteApps = if (favPackages.isEmpty()) {
            allApps.take(FavouritesStore.MAX_FAVOURITES)
        } else {
            val map = allApps.associateBy { it.packageName }
            favPackages.mapNotNull { map[it] }.take(FavouritesStore.MAX_FAVOURITES)
        }
    }

    // Back key navigation hierarchy: Settings/Apps -> Home; Home -> ignore
    BackHandler(enabled = activePage != LauncherPage.HOME) {
        activePage = LauncherPage.HOME
    }

    // Focus restoration on page change
    LaunchedEffect(activePage) {
        delay(100)
        try {
            when (activePage) {
                LauncherPage.HOME -> playFocusRequester.requestFocus()
                LauncherPage.SETTINGS -> searchFocusRequester.requestFocus()
                LauncherPage.APPS -> {}
            }
        } catch (e: Exception) { /* ignore */ }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.BaseDark)
    ) {
        // Wallpaper Layer (Presets + Dim + 500ms Crossfade)
        WallpaperLayer(
            state = wallpaperState
        )

        // Page Content (300ms Crossfade)
        Crossfade(
            targetState = activePage,
            animationSpec = tween(Tokens.DurationPageCrossfade),
            label = "pageCrossfade",
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                LauncherPage.HOME -> {
                    HomeScreen(
                        continueItems = continueItems,
                        favouriteApps = favouriteApps,
                        favouritesStore = favouritesStore,
                        onLaunchApp = onLaunchApp,
                        playFocusRequester = playFocusRequester
                    )
                }

                LauncherPage.APPS -> {
                    AppsScreen(
                        apps = allApps,
                        favouritesStore = favouritesStore,
                        gridState = appsGridState,
                        onLaunchApp = onLaunchApp
                    )
                }

                LauncherPage.SETTINGS -> {
                    SettingsScreen(
                        connectionState = connectionState,
                        settingsHolder = settingsHolder,
                        wallpaperPreset = wallpaperState.preset,
                        isWallpaperDimmed = wallpaperState.isDimmed,
                        wallpaperRepo = wallpaperRepo,
                        wifiFocusRequester = wifiRowFocusRequester,
                        searchFocusRequester = searchFocusRequester
                    )
                }
            }
        }

        // Top Chrome Scrim (84dp with 14dp blur)
        TopChromeScrim()

        // Top Chrome (64dp, Live Clock, Tabs, Wi-Fi Chip)
        TopChrome(
            activePage = activePage,
            connectionState = connectionState,
            onPageSelected = { activePage = it },
            onWifiChipClicked = {
                activePage = LauncherPage.SETTINGS
            }
        )
    }
}
