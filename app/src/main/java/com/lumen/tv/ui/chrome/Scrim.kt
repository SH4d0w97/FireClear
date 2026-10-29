package com.lumen.tv.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumen.tv.ui.theme.Tokens
import com.lumen.tv.ui.theme.blurCompat

/**
 * Chrome Scrim: 84dp top gradient rgba(5,5,6,.72) -> .38 @ 55% -> 0 with 14dp blur.
 * Keeps global chrome readable over bright wallpapers.
 */
@Composable
fun TopChromeScrim(modifier: Modifier = Modifier) {
    val scrimBrush = Brush.verticalGradient(
        0.0f to Color(5, 5, 6, (0.72f * 255).toInt()),
        0.55f to Color(5, 5, 6, (0.38f * 255).toInt()),
        1.0f to Color.Transparent
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .blurCompat(Tokens.ChromeScrimBlurDp)
            .background(scrimBrush)
    )
}
