package com.lumen.tv.ui.chrome

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import com.lumen.tv.data.WallpaperPreset
import com.lumen.tv.data.WallpaperState
import com.lumen.tv.ui.theme.Tokens

/**
 * Ambient Wallpaper Layer:
 * Top scrim gradient (top .28-.35 -> mid(55%) .42-.45 -> bottom .75-.78, near-black base)
 * over 2 custom radials, with 500ms crossfade and Dim Wallpaper color matrix.
 */
@Composable
fun WallpaperLayer(
    state: WallpaperState,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = state,
        animationSpec = tween(Tokens.WallpaperCrossfadeDuration),
        label = "wallpaperCrossfade",
        modifier = modifier.fillMaxSize()
    ) { current ->
        val preset = current.preset
        val isDimmed = current.isDimmed

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(preset.baseColor)
                .drawWithCache {
                    val w = size.width
                    val h = size.height

                    val radial1Center = Offset(
                        w * preset.radial1Center.first,
                        h * preset.radial1Center.second
                    )
                    val radial1Brush = Brush.radialGradient(
                        colors = listOf(preset.radial1Color, Color.Transparent),
                        center = radial1Center,
                        radius = (w * 0.75f).coerceAtLeast(100f)
                    )

                    val radial2Center = Offset(
                        w * preset.radial2Center.first,
                        h * preset.radial2Center.second
                    )
                    val radial2Brush = Brush.radialGradient(
                        colors = listOf(preset.radial2Color, Color.Transparent),
                        center = radial2Center,
                        radius = (w * 0.70f).coerceAtLeast(100f)
                    )

                    // Scrim gradient overlay (top .32 -> mid .44 @ 55% -> bottom .76)
                    val scrimBrush = Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.32f),
                        0.55f to Color.Black.copy(alpha = 0.44f),
                        1.00f to Color.Black.copy(alpha = 0.76f)
                    )

                    // Dim matrix: brightness x0.55, desaturate x0.85
                    val dimMatrix = ColorMatrix().apply {
                        if (isDimmed) {
                            setToSaturation(0.85f)
                            // Scale RGB channels by 0.55
                            val values = floatArrayOf(
                                0.55f, 0f, 0f, 0f, 0f,
                                0f, 0.55f, 0f, 0f, 0f,
                                0f, 0f, 0.55f, 0f, 0f,
                                0f, 0f, 0f, 1f, 0f
                            )
                            timesAssign(ColorMatrix(values))
                        }
                    }

                    onDrawBehind {
                        drawRect(radial1Brush)
                        drawRect(radial2Brush)
                        drawRect(scrimBrush)

                        if (isDimmed) {
                            // Apply dimming scrim overlay
                            drawRect(Color.Black.copy(alpha = 0.45f))
                        }
                    }
                }
        )
    }
}
