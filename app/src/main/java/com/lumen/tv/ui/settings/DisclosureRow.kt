package com.lumen.tv.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tv.data.WallpaperPreset
import com.lumen.tv.ui.theme.Tokens

/**
 * Wallpaper Disclosure Row:
 * Cyan chip, value = preset name, chevron rotates 90° on expand.
 * Expands inline panel with 4 gradient swatches (64×40dp): Graphite, Dune, Deep Sea, Ember.
 * Swatch focus: white border + 3dp outer glow ring.
 */
@Composable
fun WallpaperDisclosureRow(
    currentPreset: WallpaperPreset,
    onPresetSelected: (WallpaperPreset) -> Unit,
    showDivider: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var isHeaderFocused by remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = Tokens.MotionSpec,
        label = "chevronRotation"
    )

    val headerBgColor by animateColorAsState(
        targetValue = if (isHeaderFocused) Tokens.CardFillFocused else Color.Transparent,
        label = "wallpaperHeaderBg"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isHeaderFocused) Tokens.TextPrimary else Tokens.TextSecondary,
        label = "wallpaperLabel"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Main expandable row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBgColor)
                .onFocusChanged { isHeaderFocused = it.isFocused }
                .focusable(interactionSource = remember { MutableInteractionSource() })
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cyan Wallpaper chip
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Tokens.ChipCyan)
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Label
            Text(
                text = "Wallpaper",
                style = Tokens.RowLabelText,
                color = labelColor,
                modifier = Modifier.weight(1f)
            )

            // Current preset value
            Text(
                text = currentPreset.title,
                style = Tokens.ValueText,
                color = Tokens.TextTertiary,
                modifier = Modifier.padding(end = 6.dp)
            )

            // Rotating Chevron
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Tokens.TextTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }

        // Expanded Swatches Panel
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = Tokens.MotionSpec),
            exit = shrinkVertically(animationSpec = Tokens.MotionSpec)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WallpaperPreset.entries.forEach { preset ->
                    WallpaperSwatch(
                        preset = preset,
                        isSelected = preset == currentPreset,
                        onSelect = { onPresetSelected(preset) }
                    )
                }
            }
        }

        // Inset Separator
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                thickness = 0.5.dp,
                color = Tokens.CardBorderHairline
            )
        }
    }
}

@Composable
private fun WallpaperSwatch(
    preset: WallpaperPreset,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val gradientBrush = remember(preset) {
        Brush.linearGradient(
            colors = listOf(preset.radial1Color, preset.baseColor, preset.radial2Color)
        )
    }

    val glowElevation = if (isFocused) 6.dp else 0.dp
    val borderColor = if (isFocused || isSelected) Color.White else Tokens.CardBorderHairline

    Column(
        horizontalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .shadow(glowElevation, RoundedCornerShape(8.dp), spotColor = Color.White)
                .clip(RoundedCornerShape(8.dp))
                .background(gradientBrush)
                .border(
                    width = if (isFocused || isSelected) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .focusable(interactionSource = remember { MutableInteractionSource() })
                .clickable { onSelect() }
        )

        Text(
            text = preset.title,
            style = Tokens.FootnoteText.copy(fontSize = 11.sp),
            color = if (isFocused || isSelected) Tokens.TextPrimary else Tokens.TextTertiary
        )
    }
}
