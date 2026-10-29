package com.lumen.tv.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tv.data.FavouritesStore
import com.lumen.tv.data.LaunchableApp
import com.lumen.tv.ui.theme.LocalReduceMotion
import com.lumen.tv.ui.theme.Tokens
import com.lumen.tv.ui.theme.blurCompat

/**
 * Favourites Row: Exactly max 6 app squircle tiles.
 * Focus: Glyph scales 1.06, lifts 2dp, border white @34%, label brightens to primary.
 */
@Composable
fun FavouritesRow(
    favouriteApps: List<LaunchableApp>,
    store: FavouritesStore,
    squircleSize: Dp,
    onLaunchApp: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        favouriteApps.take(6).forEach { app ->
            FavouriteTile(
                app = app,
                store = store,
                size = squircleSize,
                onClick = { onLaunchApp(app) }
            )
        }
    }
}

@Composable
fun FavouriteTile(
    app: LaunchableApp,
    store: FavouritesStore,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) 1.06f else 1.0f,
        animationSpec = Tokens.MotionSpec,
        label = "favTileScale"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) -2f else 0f,
        animationSpec = Tokens.MotionSpec,
        label = "favTileLift"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.TileBorderFocused else Tokens.Hairline,
        animationSpec = Tokens.MotionColorSpec,
        label = "favTileBorder"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.TextPrimary else Tokens.TextTertiary,
        animationSpec = Tokens.MotionColorSpec,
        label = "favTileLabelColor"
    )

    Column(
        modifier = modifier
            .width(size)
            .offset(y = translateY.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 92dp Squircle Container (or adapted to size)
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(Tokens.ShapeIcon)
                .blurCompat(Tokens.GlassBlurDp)
                .background(Tokens.GlassFill)
                .border(1.dp, borderColor, Tokens.ShapeIcon),
            contentAlignment = Alignment.Center
        ) {
            val icon = store.getCachedIcon(app.packageName, app.activityName)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(size * 0.60f)
                )
            } else {
                Text(
                    text = app.monogram,
                    color = if (isFocused) Tokens.TextPrimary else Tokens.TextSecondary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // App Name Label (13sp tertiary -> primary on focus)
        Text(
            text = app.label,
            color = labelColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
