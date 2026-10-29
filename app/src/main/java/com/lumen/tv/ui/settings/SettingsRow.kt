package com.lumen.tv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tv.ui.theme.Tokens

/**
 * Standard Settings Row:
 * 28dp radius-8dp icon chip with 0.5dp inset ring.
 * Padding: 11dp vertical, 14dp horizontal.
 * Focus: fill white @8%, label -> primary, value stays tertiary.
 * Inset separator: 54dp from left.
 */
@Composable
fun SettingsRow(
    label: String,
    icon: ImageVector? = null,
    iconTint: Color = Tokens.ChipGray,
    value: String? = null,
    showChevron: Boolean = true,
    showDivider: Boolean = true,
    customLeading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.CardFillFocused else Color.Transparent,
        label = "settingsRowBg"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.TextPrimary else Tokens.TextSecondary,
        label = "settingsRowLabel"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable(interactionSource = remember { MutableInteractionSource() })
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading icon chip or custom leading content
            if (customLeading != null) {
                customLeading()
                Spacer(modifier = Modifier.width(12.dp))
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint)
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Row Label
            Text(
                text = label,
                style = Tokens.RowLabelText,
                color = labelColor,
                modifier = Modifier.weight(1f)
            )

            // Optional Value
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = Tokens.ValueText,
                    color = Tokens.TextTertiary,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            // Optional Chevron
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Tokens.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Inset Separator (54dp from left, aligned after icon chip)
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 54.dp),
                thickness = 0.5.dp,
                color = Tokens.CardBorderHairline
            )
        }
    }
}
