package com.lumen.tv.ui.drawer

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tv.data.FavouritesStore
import com.lumen.tv.data.LaunchableApp
import com.lumen.tv.ui.theme.LocalReduceMotion
import com.lumen.tv.ui.theme.Tokens
import com.lumen.tv.ui.theme.blurCompat
import kotlinx.coroutines.launch

/**
 * Page 2 — Apps Drawer
 * Auto-fill min 118dp columns (36×18dp gaps).
 * Real launchable apps from PackageManager query (MAIN/LAUNCHER & LEANBACK_LAUNCHER, excluding self).
 * Vertical scroll allowed.
 */
@Composable
fun AppsScreen(
    apps: List<LaunchableApp>,
    favouritesStore: FavouritesStore,
    gridState: LazyGridState,
    onLaunchApp: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val favourites by favouritesStore.favouritesFlow.collectAsState(initial = emptyList())

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(
            start = Tokens.OverscanHorizontal,
            end = Tokens.OverscanHorizontal,
            top = 100.dp,
            bottom = 44.dp
        ),
        modifier = modifier.fillMaxSize()
    ) {
        items(apps, key = { "${it.packageName}/${it.activityName}" }) { app ->
            val isFav = favourites.contains(app.packageName)
            AppDrawerTile(
                app = app,
                isFavourite = isFav,
                favouritesStore = favouritesStore,
                onClick = { onLaunchApp(app) },
                onLongClick = {
                    coroutineScope.launch {
                        if (isFav) {
                            favouritesStore.removeFavourite(app.packageName)
                            Toast.makeText(context, "Removed from Favourites", Toast.LENGTH_SHORT).show()
                        } else {
                            if (favourites.size >= FavouritesStore.MAX_FAVOURITES) {
                                Toast.makeText(context, "Favourites full (max 6)", Toast.LENGTH_SHORT).show()
                            } else {
                                favouritesStore.addFavourite(app.packageName)
                                Toast.makeText(context, "Added to Favourites", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawerTile(
    app: LaunchableApp,
    isFavourite: Boolean,
    favouritesStore: FavouritesStore,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) 1.06f else 1.0f,
        animationSpec = Tokens.MotionSpec,
        label = "drawerTileScale"
    )

    val translationY by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) -2f else 0f,
        animationSpec = Tokens.MotionSpec,
        label = "drawerTileLift"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.CardBorderFocused else Tokens.CardBorderHairline,
        label = "drawerTileBorder"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.TextPrimary else Tokens.TextTertiary,
        label = "drawerTileText"
    )

    val iconBitmap = remember(app) { favouritesStore.getCachedIcon(app.packageName, app.activityName) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(118.dp)
            .scale(scale)
            .offset(y = translationY.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Squircle Icon Container (92dp)
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(Tokens.RadiusSquircle))
                .blurCompat(Tokens.BlurGlass)
                .background(if (isFocused) Tokens.CardFillFocused else Tokens.CardFillGlass)
                .border(1.dp, borderColor, RoundedCornerShape(Tokens.RadiusSquircle)),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.label,
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Text(
                    text = app.monogram,
                    style = Tokens.PageTitle.copy(fontSize = 32.sp),
                    color = if (isFocused) Tokens.TextPrimary else Tokens.TextSecondary
                )
            }
        }

        // App Label
        Text(
            text = app.label,
            style = Tokens.TileNameText,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
