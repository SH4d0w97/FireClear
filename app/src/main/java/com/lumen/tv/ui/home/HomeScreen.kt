package com.lumen.tv.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tv.data.ContinueItem
import com.lumen.tv.data.FavouritesStore
import com.lumen.tv.data.LaunchableApp
import com.lumen.tv.focus.LocalShelfFocusActive
import com.lumen.tv.ui.theme.LocalReduceMotion
import com.lumen.tv.ui.theme.Tokens
import java.util.Calendar

/**
 * Page 1 — Home
 * Fixed to viewport, strictly NO vertical scrolling.
 * Dimensions derived dynamically from screen height via BoxWithConstraints.
 */
@Composable
fun HomeScreen(
    continueItems: List<ContinueItem>,
    favouriteApps: List<LaunchableApp>,
    favouritesStore: FavouritesStore,
    onLaunchApp: (LaunchableApp) -> Unit,
    playFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Tokens.OverscanHorizontal)
    ) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth

        // Compaction tiers
        val isCompactHeight = screenHeight <= 600.dp
        val isUltraCompact = screenHeight <= 460.dp

        // Dynamic height percentages with clamps per spec
        val topPadding = (screenHeight * 0.12f).coerceIn(92.dp, 122.dp)
        val sectionGap = (screenHeight * 0.042f).coerceIn(16.dp, 54.dp)
        val bottomPadding = (screenHeight * 0.035f).coerceIn(18.dp, 44.dp)

        // Continue card sizing
        val continueWidth = (screenWidth * 0.21f).coerceIn(160.dp, 224.dp)
        val continueHeight = if (isUltraCompact) {
            80.dp
        } else if (isCompactHeight) {
            100.dp
        } else {
            (screenHeight * 0.185f).coerceIn(96.dp, 168.dp)
        }

        // Favourites squircle size
        val squircleSize = if (isUltraCompact) {
            56.dp
        } else if (isCompactHeight) {
            72.dp
        } else {
            (screenHeight * 0.11f).coerceIn(56.dp, 92.dp)
        }

        // Time-aware greeting
        val greeting = remember {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 5..11 -> "Good morning, Alex."
                in 12..16 -> "Good afternoon, Alex."
                in 17..21 -> "Good evening, Alex."
                else -> "Good night, Alex."
            }
        }

        var inWatchlist by remember { mutableStateOf(false) }
        var isContinueShelfFocused by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // HERO SECTION
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Kicker
                Text(
                    text = greeting,
                    style = Tokens.KickerText,
                    color = Tokens.TextTertiary
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Hero Title
                val heroTitleSize = if (isUltraCompact) 28.sp else if (isCompactHeight) 36.sp else 48.sp
                Text(
                    text = "Aurora",
                    style = Tokens.HeroTitle.copy(fontSize = heroTitleSize),
                    color = Tokens.TextPrimary
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Hero Subtitle
                Text(
                    text = "Beyond the Horizon · Season 2",
                    style = Tokens.RowLabelText,
                    color = Tokens.TextSecondary
                )

                Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 16.dp))

                // Actions Row: [Play] pill + [＋ Watchlist]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayPillButton(
                        focusRequester = playFocusRequester,
                        onClick = { /* Demo play action */ }
                    )

                    WatchlistButton(
                        inWatchlist = inWatchlist,
                        onClick = { inWatchlist = !inWatchlist }
                    )
                }
            }

            // CONTINUE WATCHING SHELF
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CONTINUE",
                    style = Tokens.SectionLabelText,
                    color = Tokens.TextTertiary
                )

                CompositionLocalProvider(LocalShelfFocusActive provides isContinueShelfFocused) {
                    val continueListState = rememberLazyListState()
                    LazyRow(
                        state = continueListState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(continueItems, key = { it.id }) { item ->
                            ContinueCard(
                                item = item,
                                width = continueWidth,
                                height = continueHeight,
                                onFocusStateChange = { focused ->
                                    if (focused) isContinueShelfFocused = true
                                },
                                onClick = { /* Demo continue action */ }
                            )
                        }
                    }
                }
            }

            // FAVOURITES SHELF
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FAVOURITES",
                    style = Tokens.SectionLabelText,
                    color = Tokens.TextTertiary
                )

                FavouritesRow(
                    favouriteApps = favouriteApps,
                    store = favouritesStore,
                    squircleSize = squircleSize,
                    onLaunchApp = onLaunchApp
                )
            }
        }
    }
}

@Composable
private fun PlayPillButton(
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused && !reduceMotion) 1.04f else 1.0f,
        animationSpec = Tokens.MotionSpec,
        label = "playScale"
    )

    val shadowElevation = if (isFocused) 16.dp else 0.dp

    Box(
        modifier = Modifier
            .scale(scale)
            .shadow(shadowElevation, CircleShape, spotColor = Color.White.copy(alpha = 0.5f))
            .clip(CircleShape)
            .background(Color.White)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Play",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }
}

@Composable
private fun WatchlistButton(
    inWatchlist: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val textColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.TextPrimary else Tokens.TextSecondary,
        label = "watchlistText"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.CardFillFocused else Color.Transparent,
        label = "watchlistBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.CardBorderFocused else Tokens.CardBorderHairline,
        label = "watchlistBorder"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = remember { MutableInteractionSource() })
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (inWatchlist) "✓ In Watchlist" else "＋ Watchlist",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
