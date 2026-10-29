package com.lumen.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val monogram: String
)

class FavouritesStore(private val context: Context) {
    companion object {
        val KEY_FAVOURITES = stringPreferencesKey("favourite_packages")
        const val MAX_FAVOURITES = 6
    }

    private val pm: PackageManager = context.packageManager
    private val iconCache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    val favouritesFlow: Flow<List<String>> = context.launcherDataStore.data.map { prefs ->
        val raw = prefs[KEY_FAVOURITES] ?: ""
        if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }.take(MAX_FAVOURITES)
    }

    suspend fun addFavourite(pkg: String) {
        context.launcherDataStore.edit { prefs ->
            val current = (prefs[KEY_FAVOURITES] ?: "").split(",").filter { it.isNotBlank() }.toMutableList()
            if (!current.contains(pkg)) {
                current.add(pkg)
                prefs[KEY_FAVOURITES] = current.take(MAX_FAVOURITES).joinToString(",")
            }
        }
    }

    suspend fun removeFavourite(pkg: String) {
        context.launcherDataStore.edit { prefs ->
            val current = (prefs[KEY_FAVOURITES] ?: "").split(",").filter { it.isNotBlank() }.toMutableList()
            current.remove(pkg)
            prefs[KEY_FAVOURITES] = current.joinToString(",")
        }
    }

    suspend fun queryAllLaunchableApps(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        val resolved = (pm.queryIntentActivities(launcherIntent, 0) +
                        pm.queryIntentActivities(leanbackIntent, 0))
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }

        resolved.map { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            val act = resolveInfo.activityInfo.name
            val label = resolveInfo.loadLabel(pm).toString()
            val mono = if (label.isNotBlank()) label.first().uppercase() else "A"

            // Pre-cache 92dp downsampled icon
            val key = "$pkg/$act"
            if (iconCache.get(key) == null) {
                val drawable = resolveInfo.loadIcon(pm)
                val bmp = downsample(drawable, 184, 184)
                iconCache.put(key, bmp.asImageBitmap())
            }

            LaunchableApp(pkg, act, label, mono)
        }.sortedBy { it.label.lowercase() }
    }

    fun getCachedIcon(pkg: String, act: String): ImageBitmap? {
        return iconCache.get("$pkg/$act")
    }

    private fun downsample(drawable: Drawable, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }
}
