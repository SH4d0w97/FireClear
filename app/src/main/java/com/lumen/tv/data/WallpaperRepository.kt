package com.lumen.tv.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.launcherDataStore by preferencesDataStore(name = "lumen_launcher_prefs")

enum class WallpaperPreset(
    val title: String,
    val baseColor: Color,
    val radial1Color: Color,
    val radial1Center: Pair<Float, Float>, // x, y percentages
    val radial2Color: Color,
    val radial2Center: Pair<Float, Float>
) {
    GRAPHITE(
        title = "Graphite",
        baseColor = Color(0xFF050506),
        radial1Color = Color(125, 130, 145, (0.20f * 255).toInt()),
        radial1Center = 0.75f to 0.12f,
        radial2Color = Color(70, 75, 92, (0.18f * 255).toInt()),
        radial2Center = 0.12f to 0.88f
    ),
    DUNE(
        title = "Dune",
        baseColor = Color(0xFF060504),
        radial1Color = Color(185, 125, 62, (0.24f * 255).toInt()),
        radial1Center = 0.72f to 1.00f,
        radial2Color = Color(225, 165, 95, (0.12f * 255).toInt()),
        radial2Center = 0.85f to 0.15f
    ),
    DEEP_SEA(
        title = "Deep Sea",
        baseColor = Color(0xFF04070A),
        radial1Color = Color(22, 95, 115, (0.30f * 255).toInt()),
        radial1Center = 0.18f to 0.92f,
        radial2Color = Color(32, 72, 125, (0.22f * 255).toInt()),
        radial2Center = 0.82f to 0.08f
    ),
    EMBER(
        title = "Ember",
        baseColor = Color(0xFF060405),
        radial1Color = Color(165, 52, 62, (0.22f * 255).toInt()),
        radial1Center = 0.80f to 0.88f,
        radial2Color = Color(125, 42, 82, (0.18f * 255).toInt()),
        radial2Center = 0.12f to 0.12f
    )
}

data class WallpaperState(
    val preset: WallpaperPreset = WallpaperPreset.DEEP_SEA,
    val isDimmed: Boolean = false
)

class WallpaperRepository(private val context: Context) {
    companion object {
        val KEY_WALLPAPER = stringPreferencesKey("wallpaper_preset")
        val KEY_DIMMED = booleanPreferencesKey("wallpaper_dimmed")
    }

    val stateFlow: Flow<WallpaperState> = context.launcherDataStore.data.map { prefs ->
        val presetName = prefs[KEY_WALLPAPER] ?: WallpaperPreset.DEEP_SEA.name
        val preset = try {
            WallpaperPreset.valueOf(presetName)
        } catch (e: Exception) {
            WallpaperPreset.DEEP_SEA
        }
        val isDimmed = prefs[KEY_DIMMED] ?: false
        WallpaperState(preset, isDimmed)
    }

    suspend fun setPreset(preset: WallpaperPreset) {
        context.launcherDataStore.edit { it[KEY_WALLPAPER] = preset.name }
    }

    suspend fun setDimmed(dimmed: Boolean) {
        context.launcherDataStore.edit { it[KEY_DIMMED] = dimmed }
    }
}
